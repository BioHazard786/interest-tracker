package com.biohazard786.interesttracker.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val inr = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatInr(value: Double): String = inr.format(value)

fun formatInr(value: String): String = formatInr(value.toDoubleOrNull() ?: 0.0)

/** Accepts "YYYY-MM-DD" or a full ISO timestamp. */
fun formatDate(iso: String): String =
  runCatching { LocalDate.parse(iso.take(10)).format(dateFormat) }.getOrDefault(iso)

// Matches src/lib/banking/registry.ts
fun bankName(id: String): String =
  when (id) {
    "in-pnb" -> "PNB"
    "in-kotak" -> "Kotak"
    "in-sbi" -> "SBI"
    "in-idfc" -> "IDFC FIRST"
    else -> id
  }
