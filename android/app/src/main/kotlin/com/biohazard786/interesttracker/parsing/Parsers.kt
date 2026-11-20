package com.biohazard786.interesttracker.parsing

import java.time.ZoneId

// Ports of the web parsers in src/lib/banking/parsers (one per bank). Keep them line-for-line
// comparable: the transaction hash (`hashData`) is built exactly as the web builds it, because uploads
// from both clients are deduplicated against each other by that hash. fixtures/expected.json pins it.

private val NEWLINES = Regex("""\r\n|\n""")

private fun txn(
  hashData: String,
  id: String?,
  date: JsDate,
  description: String,
  amount: Double,
  type: String,
  balance: Double,
) = ParsedTxn(id, sha256Hex(hashData), date.day, description, amount, type, balance)

private fun typeOf(amount: Double) = if (amount >= 0) "credit" else "debit"

/** `parseFloat(s.replace(/,/g, ""))` */
private fun parseAmount(text: String) = jsParseFloat(text.replace(",", ""))

// ---------------------------------------------------------------------------------------------
// PNB (csv)
// ---------------------------------------------------------------------------------------------

object PnbParser {
  private val INTEREST = Regex("""int\.pd|interest""", RegexOption.IGNORE_CASE)
  private val RANGE =
    Regex("""int\.\s*\.?pd[:\s]*([0-9]{2}-[0-9]{2}-[0-9]{4})\s*(?:to|-)\s*([0-9]{2}-[0-9]{2}-[0-9]{4})""", RegexOption.IGNORE_CASE)
  private val BALANCE_SUFFIX = Regex(""" (Cr|Dr)\.$""")
  private val HUMAN_DATE = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)
  private val DDMMYYYY = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")

  /** `formatPNBDescription`: the readable form the web stores in `description`. */
  fun humanDescription(description: String): String {
    val (from, to) = RANGE.find(description)?.destructured ?: return description
    return runCatching {
        "Interest received from the bank from ${java.time.LocalDate.parse(from, DDMMYYYY).format(HUMAN_DATE)} to " +
          java.time.LocalDate.parse(to, DDMMYYYY).format(HUMAN_DATE)
      }
      .getOrDefault(description)
  }

  fun parse(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val lines = String(bytes, Charsets.UTF_8).removePrefix("﻿").split(NEWLINES)
    val headerIndex = lines.indexOfFirst { it.contains("Txn No.") && it.contains("Txn Date") }
    if (headerIndex == -1) throw StatementException("Could not find the transaction header row (Txn No., Txn Date). Is this a PNB statement?")

    val records = Csv.parse(lines.drop(headerIndex).joinToString("\n"))
    val header = records.firstOrNull() ?: return emptyList()
    fun List<String>.col(name: String) = header.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it) }

    return records.drop(1).mapNotNull { row ->
      val description = row.col("Description").orEmpty()
      if (!INTEREST.containsMatchIn(description)) return@mapNotNull null

      val dateText = row.col("Txn Date").orEmpty()
      val date = parseDDMMYYYY(dateText, zone) ?: return@mapNotNull null

      val id = row.col("Txn No.")?.takeIf { it.isNotEmpty() }
      val debit = jsParseFloat(row.col("Dr Amount")?.takeIf { it.isNotEmpty() } ?: "0")
      val credit = jsParseFloat(row.col("Cr Amount")?.takeIf { it.isNotEmpty() } ?: "0")
      val amount = credit - debit
      val balance = jsParseFloat((row.col("Balance")?.takeIf { it.isNotEmpty() } ?: "0").replace(BALANCE_SUFFIX, "").trim())
      // The web would fail validation on NaN and drop the whole upload; skip just the bad row.
      if (amount.isNaN() || balance.isNaN()) return@mapNotNull null

      val hashData =
        "$dateText|$description|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId|${id.orEmpty()}"
      txn(hashData, id, date, humanDescription(description), amount, typeOf(amount), balance)
    }
  }
}

/** RFC 4180 CSV (quotes, escaped quotes, newlines in quoted fields), skipping empty lines like Papa Parse. */
internal object Csv {
  fun parse(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0

    fun endRow() {
      row += field.toString()
      field.clear()
      // Papa's skipEmptyLines: a row that is a single empty field is dropped.
      if (!(row.size == 1 && row[0].isEmpty())) rows += row
      row = ArrayList()
    }

    while (i < text.length) {
      val c = text[i]
      when {
        quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
          field.append('"')
          i++
        }
        c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
        !quoted && c == ',' -> {
          row += field.toString()
          field.clear()
        }
        !quoted && c == '\n' -> endRow()
        !quoted && c == '\r' -> Unit
        else -> field.append(c)
      }
      i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
  }
}

// ---------------------------------------------------------------------------------------------
// Shared by the spreadsheet parsers (SBI, IDFC)
// ---------------------------------------------------------------------------------------------

private fun normalized(description: String) =
  description.replace(Regex("""[\r\n]+"""), " ").replace(Regex("""\s+"""), " ").trim().lowercase()

/** Schema `type: String`. */
private fun Cell?.asString(): String? =
  when (this) {
    is Cell.Text -> value
    is Cell.Num -> jsNumberToString(value)
    is Cell.Bool -> value.toString()
    else -> null
  }

/** Schema `type` for Debit/Credit/Balance: numbers as-is, strings via parseFloat, otherwise 0. */
private fun Cell?.asAmount(): Double =
  when (this) {
    is Cell.Num -> value
    is Cell.Text -> jsParseFloat(value.replace(",", "")).let { if (it.isNaN()) 0.0 else it }
    else -> 0.0
  }

private class Sheet(rows: List<List<Cell?>>, headerIndex: Int) {
  val header = rows[headerIndex].map { (it as? Cell.Text)?.value?.trim() }
  val body = rows.drop(headerIndex + 1)

  fun col(row: List<Cell?>, name: String): Cell? = header.indexOf(name).takeIf { it >= 0 }?.let { row.getOrNull(it) }
}

/** `findHeaderAndSlice`: first row where every needle appears in some string cell. */
private fun headerIndex(rows: List<List<Cell?>>, vararg needles: String): Int =
  rows.indexOfFirst { row ->
    needles.all { needle -> row.any { it is Cell.Text && it.value.lowercase().contains(needle) } }
  }

// ---------------------------------------------------------------------------------------------
// SBI (xlsx)
// ---------------------------------------------------------------------------------------------

object SbiParser {
  fun parse(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val rows = Xlsx.read(bytes)
    val index = headerIndex(rows, "debit", "credit")
    if (index == -1) throw StatementException("Could not find the transaction header row in this SBI statement.")
    val sheet = Sheet(rows, index)

    return sheet.body.mapNotNull { row ->
      val date =
        when (val cell = sheet.col(row, "Date")) {
          is Cell.Date -> cell.value
          is Cell.Text -> parseDDMMYYYY(cell.value, zone)
          else -> null
        } ?: return@mapNotNull null
      val details = sheet.col(row, "Details").asString()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      if (!normalized(details).contains("interes t credit")) return@mapNotNull null

      val amount = sheet.col(row, "Credit").asAmount() - sheet.col(row, "Debit").asAmount()
      val balance = sheet.col(row, "Balance").asAmount()

      val hashData = "${date.iso}|$details|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId"
      txn(hashData, null, date, details, amount, typeOf(amount), balance)
    }
  }
}

// ---------------------------------------------------------------------------------------------
// IDFC (xlsx + pdf)
// ---------------------------------------------------------------------------------------------

object IdfcParser {
  private const val INTEREST = "monthly savings interest credit"
  private val INTEREST_LINE = Regex("""MONTHLY SAVINGS INTEREST CREDIT""", RegexOption.IGNORE_CASE)
  private val DATE_LINE =
    Regex("""^\s*(\d{2}-\w{3}-\d{4})\s+\d{2}-\w{3}-\d{4}\s+(MONTHLY SAVINGS INTEREST CREDIT)\s*$""", RegexOption.IGNORE_CASE)
  // PDFBox keeps the amounts on the description's line and wraps "CREDIT" onto the next one.
  private val ONE_LINE =
    Regex("""^\s*(\d{2}-\w{3}-\d{4})\s+\d{2}-\w{3}-\d{4}\s+MONTHLY SAVINGS INTEREST(?:\s+CREDIT)?\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$""", RegexOption.IGNORE_CASE)
  // The web's pdf2md turns the amounts row into a "###" heading; PDFBox returns it as plain text.
  private val AMOUNT_LINE = Regex("""^(?:#{1,6}\s+)?([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$""")

  fun parseXlsx(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val rows = Xlsx.read(bytes)
    val index = headerIndex(rows, "transaction date", "particulars")
    if (index == -1) throw StatementException("Could not find the transaction header row in this IDFC statement.")
    val sheet = Sheet(rows, index)

    return sheet.body.mapNotNull { row ->
      val date =
        when (val cell = sheet.col(row, "Transaction Date")) {
          is Cell.Date -> cell.value
          is Cell.Text -> jsDateParse(cell.value, zone)
          else -> null
        } ?: return@mapNotNull null
      val particulars = sheet.col(row, "Particulars").asString()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      if (!normalized(particulars).contains(INTEREST)) return@mapNotNull null

      val amount = sheet.col(row, "Credit").asAmount() - sheet.col(row, "Debit").asAmount()
      val balance = sheet.col(row, "Balance").asAmount()

      val hashData = "${date.iso}|$particulars|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId"
      txn(hashData, null, date, particulars, amount, typeOf(amount), balance)
    }
  }

  /** `lines` are the text lines of the PDF, from PDFBox on device (pdf2md markdown in the web app). */
  fun parsePdfLines(lines: List<String>, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val result = ArrayList<ParsedTxn>()
    var i = 0
    while (i < lines.size) {
      val line = lines[i]
      val oneLine = ONE_LINE.find(line)
      if (oneLine != null) {
        val (dateText, amountText, balanceText) = oneLine.destructured
        val date = jsDateParse(dateText, zone)
        if (date != null) {
          val amount = parseAmount(amountText)
          val balance = parseAmount(balanceText)
          val description = "MONTHLY SAVINGS INTEREST CREDIT" // as the web's markdown reads it
          val hashData = "${date.iso}|$description|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId"
          result += txn(hashData, null, date, description, amount, "credit", balance)
        }
        i++
        continue
      }
      val dateMatch = if (INTEREST_LINE.containsMatchIn(line)) DATE_LINE.find(line) else null
      if (dateMatch == null) {
        i++
        continue
      }
      val (dateText, description) = dateMatch.destructured

      // The amounts are on one of the next few non-empty lines.
      var amountMatch: MatchResult? = null
      var amountIndex = -1
      for (j in i + 1 until minOf(i + 5, lines.size)) {
        if (lines[j].isBlank()) continue
        amountMatch = AMOUNT_LINE.find(lines[j])
        if (amountMatch != null) {
          amountIndex = j
          break
        }
      }
      if (amountMatch == null) {
        i++
        continue
      }

      val (amountText, balanceText) = amountMatch.destructured
      val date = jsDateParse(dateText, zone)
      if (date == null) {
        i++
        continue
      }
      val amount = parseAmount(amountText)
      val balance = parseAmount(balanceText)

      val hashData = "${date.iso}|$description|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId"
      result += txn(hashData, null, date, description.trim(), amount, "credit", balance)
      i = amountIndex + 1 // skip past the amounts line we consumed
    }
    return result
  }

  fun parsePdf(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()) =
    parsePdfLines(PdfText.lines(bytes), userId, zone)
}

// ---------------------------------------------------------------------------------------------
// Kotak (pdf)
// ---------------------------------------------------------------------------------------------

object KotakParser {
  private val INTEREST = Regex("""Int\.Pd:""", RegexOption.IGNORE_CASE)
  // " 131 31 Mar 2025 Int.Pd:1234567890:01-01-2025 to 31-03-2025 12.00 2,704.39"  =>  # DATE DESCRIPTION AMOUNT BALANCE
  private val LINE =
    Regex(
      """^\s*(\d+)\s+(\d{1,2}\s+\w{3}\s+\d{4})\s+(Int\.Pd:[^\s]+(?:\s+to\s+\d{2}-\d{2}-\d{4})?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$""",
      RegexOption.IGNORE_CASE,
    )
  // The description the PDF and the CSV share; both hash it, so either format is stored once.
  private val DESCRIPTION = Regex("""Int\.Pd:[^\s]+(?:\s+to\s+\d{2}-\d{2}-\d{4})?""", RegexOption.IGNORE_CASE)

  /** Like the web, the hash uses the date exactly as printed. Interest is always a credit. */
  private fun build(dateText: String, description: String, amountText: String, balanceText: String, userId: String, zone: ZoneId): ParsedTxn? {
    val date = jsDateParse(dateText.replace(Regex("""\s+"""), " "), zone) ?: return null
    val amount = parseAmount(amountText)
    val balance = parseAmount(balanceText)
    if (amount.isNaN() || balance.isNaN()) return null
    val hashData = "$dateText|$description|${jsNumberToString(amount)}|${jsNumberToString(balance)}|$userId"
    return txn(hashData, null, date, description.trim(), amount, "credit", balance)
  }

  fun parseLines(lines: List<String>, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> =
    lines.mapNotNull { line ->
      if (!INTEREST.containsMatchIn(line)) return@mapNotNull null
      val match = LINE.find(line) ?: return@mapNotNull null
      val (_, dateText, description, amountText, balanceText) = match.destructured
      build(dateText, description, amountText, balanceText, userId, zone)
    }

  fun parse(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()) =
    parseLines(PdfText.lines(bytes), userId, zone)

  /** Columns: #, Date, Description, Chq/Ref. No., Withdrawal (Dr.), Deposit (Cr.), Balance. */
  fun parseCsv(bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val lines = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").split(NEWLINES)
    val headerIndex = lines.indexOfFirst { it.contains("Description") && it.contains("Deposit (Cr.)") }
    if (headerIndex == -1) throw StatementException("Could not find the transaction header row (Date, Description, Deposit (Cr.)). Is this a Kotak statement?")

    val records = Csv.parse(lines.drop(headerIndex).joinToString("\n"))
    val header = records.firstOrNull() ?: return emptyList()
    fun List<String>.col(name: String) = header.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it) }.orEmpty()

    return records.drop(1).mapNotNull { row ->
      val description = DESCRIPTION.find(row.col("Description"))?.value ?: return@mapNotNull null
      build(row.col("Date"), description, row.col("Deposit (Cr.)"), row.col("Balance"), userId, zone)
    }
  }
}
