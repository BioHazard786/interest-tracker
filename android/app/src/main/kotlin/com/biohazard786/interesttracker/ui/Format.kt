package com.biohazard786.interesttracker.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val india = Locale.Builder().setLanguage("en").setRegion("IN").build()
private val inr = NumberFormat.getCurrencyInstance(india)
private val inrWhole = NumberFormat.getCurrencyInstance(india).apply { maximumFractionDigits = 0 }
private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun formatInr(value: Double): String = inr.format(value)

fun formatInr(value: String): String = formatInr(value.toDoubleOrNull() ?: 0.0)

/** "₹112" for whole rupees, "₹112.50" otherwise (never "₹112.5"). */
fun formatInrCompact(value: Double): String = if (kotlin.math.abs(value - Math.rint(value)) < 0.005) inrWhole.format(value) else inr.format(value)

/** Accepts "YYYY-MM-DD" or a full ISO timestamp. */
fun formatDate(iso: String): String =
  runCatching { LocalDate.parse(iso.take(10)).format(dateFormat) }.getOrDefault(iso)

fun formatDayMonth(iso: String): String =
  runCatching { LocalDate.parse(iso.take(10)).format(dayMonth) }.getOrDefault(iso)

// Matches src/lib/banking/registry.ts
fun bankName(id: String): String =
  when (id) {
    "in-pnb" -> "PNB"
    "in-kotak" -> "Kotak"
    "in-sbi" -> "SBI"
    "in-idfc" -> "IDFC FIRST"
    else -> id
  }

private val pnbRange =
  Regex("""int\.\s*\.?pd[:\s]*(\d{2}-\d{2}-\d{4})\s*(?:to|-)\s*(\d{2}-\d{2}-\d{4})""", RegexOption.IGNORE_CASE)
private val ddMMyyyy = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** The interest period PNB encodes in its descriptions, if present. */
private fun pnbPeriod(bankId: String, description: String?): Pair<LocalDate, LocalDate>? {
  if (bankId != "in-pnb" || description == null) return null
  val (from, to) = pnbRange.find(description)?.destructured ?: return null
  return runCatching { LocalDate.parse(from, ddMMyyyy) to LocalDate.parse(to, ddMMyyyy) }.getOrNull()
}

/** Port of the web app's per-bank description formatters (only PNB has one). */
fun formatDescription(bankId: String, description: String?): String {
  val period = pnbPeriod(bankId, description)
    ?: return description ?: "Interest credit"
  return "Interest received from the bank from ${period.first.format(monthDayYear)} to ${period.second.format(monthDayYear)}"
}

/** Short list title: the interest period when known ("Sep 1 – Nov 30, 2025"), else the description. */
fun formatTitle(bankId: String, description: String?): String {
  val (from, to) = pnbPeriod(bankId, description) ?: return description ?: "Interest credit"
  return if (from.year == to.year) "${from.format(monthDay)} – ${to.format(monthDayYear)}"
  else "${from.format(monthDayYear)} – ${to.format(monthDayYear)}"
}
