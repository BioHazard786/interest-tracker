package com.biohazard786.interesttracker.parsing

import java.time.ZoneId

/** Mirrors `banks` in src/lib/banking/registry.ts. */
class Bank(
  val id: String,
  val name: String,
  /** File formats the bank's statements come in: "csv", "xlsx", "pdf". */
  val formats: List<String>,
  private val parser: (fileName: String, bytes: ByteArray, userId: String, zone: ZoneId) -> List<ParsedTxn>,
) {
  /** Parses one statement file into interest transactions. Throws [StatementException] with a readable message. */
  fun parse(fileName: String, bytes: ByteArray, userId: String, zone: ZoneId = ZoneId.systemDefault()): List<ParsedTxn> {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension !in formats) {
      throw StatementException("$name statements come as ${formats.joinToString(" or ") { it.uppercase() }}, not .$extension")
    }
    return parser(fileName, bytes, userId, zone)
  }
}

val Banks =
  listOf(
    Bank("in-pnb", "Punjab National Bank", listOf("csv")) { _, b, u, z -> PnbParser.parse(b, u, z) },
    Bank("in-kotak", "Kotak Mahindra Bank", listOf("pdf", "csv")) { name, b, u, z ->
      if (name.lowercase().endsWith(".csv")) KotakParser.parseCsv(b, u, z) else KotakParser.parse(b, u, z)
    },
    Bank("in-sbi", "State Bank of India", listOf("xlsx")) { _, b, u, z -> SbiParser.parse(b, u, z) },
    Bank("in-idfc", "IDFC First Bank", listOf("xlsx", "pdf")) { name, b, u, z ->
      if (name.lowercase().endsWith(".pdf")) IdfcParser.parsePdf(b, u, z) else IdfcParser.parseXlsx(b, u, z)
    },
  )

/** MIME types for the system file picker. Some file managers report CSV as octet-stream, so allow it. */
fun Bank.pickerMimeTypes(): Array<String> =
  formats
    .flatMap {
      when (it) {
        "csv" -> listOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream")
        "xlsx" -> listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")
        else -> listOf("application/pdf")
      }
    }
    .distinct()
    .toTypedArray()
