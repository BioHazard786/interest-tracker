package com.biohazard786.interesttracker.parsing

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * Re-creations of the handful of JavaScript behaviours the web parsers' transaction hashes depend on.
 * A hash is SHA-256 of a string built with JS template literals, and rows already uploaded from the
 * web are deduplicated by it, so these must match V8 byte for byte (see fixtures/expected.json).
 */
class StatementException(message: String) : Exception(message)

class ParsedTxn(
  val transactionId: String?,
  val transactionHash: String,
  /** The date printed on the statement. */
  val date: LocalDate,
  val description: String,
  val amount: Double,
  /** "credit" or "debit". */
  val type: String,
  val balance: Double,
)

/** A JS `Date`: the exact instant (which feeds the hash) plus the calendar day it represents. */
internal class JsDate(val instant: Instant, val day: LocalDate) {
  /** `date.toISOString()` */
  val iso: String get() = ISO.format(instant)

  private companion object {
    val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
  }
}

/** `new Date(y, m, d)`: midnight in the device's timezone, like the browser. */
internal fun localMidnight(day: LocalDate, zone: ZoneId) = JsDate(day.atStartOfDay(zone).toInstant(), day)

/** Dates that arrive from XLSX date cells, and ISO date-only strings, are UTC midnight in JS. */
internal fun utcMidnight(day: LocalDate) = JsDate(day.atStartOfDay(ZoneOffset.UTC).toInstant(), day)

internal fun sha256Hex(text: String): String =
  MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private val FLOAT_PREFIX = Regex("""^[\s ﻿]*([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)""")

/** `Number.parseFloat`: longest numeric prefix, so "1,234.50" is 1 and "abc" is NaN. */
internal fun jsParseFloat(text: String): Double = FLOAT_PREFIX.find(text)?.groupValues?.get(1)?.toDouble() ?: Double.NaN

/** `Number(text)` for the plain integers used in dates; empty is 0, anything unparsable is NaN. */
private fun jsNumber(text: String): Double = text.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }

/**
 * `String(number)` / `${number}`: shortest digits that round-trip, no trailing ".0", "-0" is "0".
 * shortcut: no exponent form (JS uses it below 1e-6 / from 1e21), irrelevant for money.
 */
internal fun jsNumberToString(value: Double): String {
  if (value.isNaN()) return "NaN"
  if (value == 0.0) return "0"
  if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
  val exact = BigDecimal(value)
  for (digits in 1..17) {
    val rounded = exact.round(MathContext(digits, RoundingMode.HALF_EVEN))
    if (rounded.toDouble() == value) return rounded.stripTrailingZeros().toPlainString()
  }
  return exact.toPlainString()
}

/** Port of `parseDDMMYYYY` in src/lib/utils.ts, including JS Date's month/day overflow. */
internal fun parseDDMMYYYY(text: String, zone: ZoneId): JsDate? {
  val parts = text.split('-', '/').take(3).map(::jsNumber)
  if (parts.size < 3 || parts.any { it.isNaN() || it.isInfinite() || it != floor(it) }) return null
  val (day, month, year) = parts
  val fullYear = year.toInt().let { if (it in 0..99) it + 1900 else it }
  return runCatching {
      localMidnight(LocalDate.of(fullYear, 1, 1).plusMonths(month.toLong() - 1).plusDays(day.toLong() - 1), zone)
    }
    .getOrNull()
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
private val DAY_MON_YEAR = Regex("""^(\d{1,2})[-\s]+([A-Za-z]{3,})\.?[-\s,]+(\d{4})$""")
private val MON_DAY_YEAR = Regex("""^([A-Za-z]{3,})\.?\s+(\d{1,2}),?\s+(\d{4})$""")

/**
 * `new Date(text)` for the shapes bank statements use: "2025-03-31" (UTC), "31-Mar-2025",
 * "31 Mar 2025" and "Mar 31, 2025" (local midnight). Anything else is null.
 * shortcut: V8's parser accepts far more formats; add one when a real statement needs it.
 */
internal fun jsDateParse(text: String, zone: ZoneId): JsDate? {
  val t = text.trim()
  fun monthOf(name: String) = MONTHS.indexOf(name.take(3).lowercase()).takeIf { it >= 0 }?.plus(1)
  fun local(year: Int, month: Int, day: Int) =
    runCatching { localMidnight(LocalDate.of(year, month, 1).plusDays(day.toLong() - 1), zone) }.getOrNull()

  ISO_DATE.matchEntire(t)?.let { m ->
    val (y, mo, d) = m.destructured
    return runCatching { utcMidnight(LocalDate.of(y.toInt(), mo.toInt(), d.toInt())) }.getOrNull()
  }
  DAY_MON_YEAR.matchEntire(t)?.let { m ->
    val (d, mon, y) = m.destructured
    return monthOf(mon)?.let { local(y.toInt(), it, d.toInt()) }
  }
  MON_DAY_YEAR.matchEntire(t)?.let { m ->
    val (mon, d, y) = m.destructured
    return monthOf(mon)?.let { local(y.toInt(), it, d.toInt()) }
  }
  return null
}
