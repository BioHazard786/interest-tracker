package com.biohazard786.interesttracker.parsing

import java.io.File
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares every Kotlin parser with what the real TypeScript parsers produced for the same files
 * (fixtures/generate.sh -> expected.json, recorded in Asia/Kolkata). A mismatch in `hash` means rows
 * uploaded from the phone would not be deduplicated against rows uploaded from the web.
 */
class GoldenParsersTest {
  private val fixtures = File("../fixtures")
  private val statements = File(fixtures, "statements")
  private val ist = ZoneId.of("Asia/Kolkata")
  private val expected: JsonObject = Json.parseToJsonElement(File(fixtures, "expected.json").readText()).jsonObject

  private fun case(file: String): JsonObject =
    expected["cases"]!!.jsonArray.map { it.jsonObject }.first { it["file"]!!.jsonPrimitive.content == file }

  private fun check(file: String, actual: List<ParsedTxn>) {
    val case = case(file)
    val txns = case["transactions"]!!.jsonArray
    val user = case["userId"]!!.jsonPrimitive.content
    assertTrue(user.isNotEmpty())
    assertEquals("$file: row count", txns.size, actual.size)
    txns.forEachIndexed { i, e ->
      val want = e.jsonObject
      val got = actual[i]
      val label = "$file row $i (${want["hashInput"]!!.jsonPrimitive.content})"
      assertEquals("$label: hash", want["hash"]!!.jsonPrimitive.content, got.transactionHash)
      assertEquals("$label: amount", want["amount"]!!.jsonPrimitive.double, got.amount, 0.0)
      assertEquals("$label: balance", want["balance"]!!.jsonPrimitive.double, got.balance, 0.0)
      assertEquals("$label: type", want["type"]!!.jsonPrimitive.content, got.type)
      assertEquals("$label: description", want["description"]!!.jsonPrimitive.content, got.description)
      val id = want["transactionId"]
      assertEquals("$label: transactionId", if (id == null || id is JsonNull) null else id.jsonPrimitive.content, got.transactionId)
    }
  }

  private fun user(file: String) = case(file)["userId"]!!.jsonPrimitive.content
  private fun bytes(name: String) = File(statements, name).readBytes()
  private fun lines(name: String) = File(statements, name).readText().split(Regex("""\r\n|\n"""))

  @Test fun pnb() = check("pnb.csv", PnbParser.parse(bytes("pnb.csv"), user("pnb.csv"), ist))

  @Test fun sbi() = check("sbi.xlsx", SbiParser.parse(bytes("sbi.xlsx"), user("sbi.xlsx"), ist))

  @Test fun idfcXlsx() = check("idfc.xlsx", IdfcParser.parseXlsx(bytes("idfc.xlsx"), user("idfc.xlsx"), ist))

  @Test fun idfcPdf() = check("idfc.pdf", IdfcParser.parsePdfLines(lines("idfc.md.txt"), user("idfc.pdf"), ist))

  @Test fun kotak() = check("kotak.pdf", KotakParser.parseLines(lines("kotak.md.txt"), user("kotak.pdf"), ist))

  /** PDFBox gives plain text lines, with no markdown "###" on the IDFC amounts row. */
  @Test fun idfcPdfWithoutHeadingMarkers() {
    val plain = lines("idfc.md.txt").map { it.replace(Regex("""^#+\s+"""), "") }
    check("idfc.pdf", IdfcParser.parsePdfLines(plain, user("idfc.pdf"), ist))
  }

  /**
   * The lines Apache PDFBox 2.0 (what pdfbox-android ports) extracts from the PDFs in fixtures/statements must
   * parse to the same hashes the web gets for the rows the PDFs share with the markdown fixtures.
   */
  @Test fun pdfboxLinesMatchTheWebHashes() {
    val kotak = KotakParser.parseLines(lines("kotak.pdfbox.txt"), user("kotak.pdf"), ist)
    val idfc = IdfcParser.parsePdfLines(lines("idfc.pdfbox.txt"), user("idfc.pdf"), ist)
    fun hashes(file: String) = case(file)["transactions"]!!.jsonArray.map { it.jsonObject["hash"]!!.jsonPrimitive.content }
    assertTrue("kotak: PDFBox found rows", kotak.size >= 2)
    assertEquals(hashes("kotak.pdf").take(kotak.size), kotak.map { it.transactionHash })
    assertTrue("idfc: PDFBox found rows", idfc.size >= 2)
    assertEquals(hashes("idfc.pdf").take(idfc.size), idfc.map { it.transactionHash })
  }

  // --- Altered copies of real statements (fixtures/anonymize_real.py) -------------------------------------

  @Test fun realPnb() = check("real-pnb.csv", PnbParser.parse(bytes("real-pnb.csv"), user("real-pnb.csv"), ist))

  @Test fun realKotakCsv() = check("real-kotak.csv", KotakParser.parseCsv(bytes("real-kotak.csv"), user("real-kotak.csv"), ist))

  @Test fun realIdfcXlsx() = check("real-idfc.xlsx", IdfcParser.parseXlsx(bytes("real-idfc.xlsx"), user("real-idfc.xlsx"), ist))

  @Test fun realKotakPdfFromMarkdown() = check("real-kotak.pdf", KotakParser.parseLines(lines("real-kotak.md.txt"), user("real-kotak.pdf"), ist))

  @Test fun realIdfcPdfFromMarkdown() = check("real-idfc.pdf", IdfcParser.parsePdfLines(lines("real-idfc.md.txt"), user("real-idfc.pdf"), ist))

  /** The phone's own PDF reader (PDFBox) on the real layouts must give the web's rows and hashes. */
  @Test fun realKotakPdfWithPdfbox() = check("real-kotak.pdf", KotakParser.parse(bytes("real-kotak.pdf"), user("real-kotak.pdf"), ist))

  @Test fun realIdfcPdfWithPdfbox() = check("real-idfc.pdf", IdfcParser.parsePdf(bytes("real-idfc.pdf"), user("real-idfc.pdf"), ist))

  /** real-kotak-locked.pdf is real-kotak.pdf behind the password "test-1234". */
  @Test fun lockedPdfAsksForThePasswordThenParses() {
    val locked = bytes("real-kotak-locked.pdf")
    try {
      KotakParser.parse(locked, "u", ist)
      fail("a locked PDF must ask for its password")
    } catch (e: PasswordRequiredException) {
      assertFalse(e.wrong)
    }
    try {
      PdfText.unlock(locked, "wrong")
      fail("a wrong password must be refused")
    } catch (e: PasswordRequiredException) {
      assertTrue(e.wrong)
    }
    check("real-kotak.pdf", KotakParser.parse(PdfText.unlock(locked, "test-1234"), user("real-kotak.pdf"), ist))
  }

  @Test fun passwordProtectedSpreadsheetsGetAClearMessage() {
    val ole2 = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)
    try {
      IdfcParser.parseXlsx(ole2, "u", ist)
      fail("expected a StatementException")
    } catch (e: StatementException) {
      assertTrue(e.message!!.contains("password protected"))
    }
  }

  /** Same statement as CSV and as PDF: the rows hash alike, so uploading both stores them once. */
  @Test fun kotakCsvAndPdfAreTheSameRows() {
    fun hashes(file: String) = case(file)["transactions"]!!.jsonArray.map { it.jsonObject["hash"]!!.jsonPrimitive.content }
    assertTrue(hashes("real-kotak.csv").isNotEmpty())
    assertEquals(hashes("real-kotak.pdf"), hashes("real-kotak.csv"))
  }

  @Test fun jsNumberFormatting() {
    assertEquals("812.5", jsNumberToString(812.5))
    assertEquals("49", jsNumberToString(49.0))
    assertEquals("0.1", jsNumberToString(0.1))
    assertEquals("-5.25", jsNumberToString(-5.25))
    assertEquals("0", jsNumberToString(-0.0))
    assertEquals("0.30000000000000004", jsNumberToString(0.1 + 0.2))
    assertEquals("41918.2", jsNumberToString(41918.2))
  }

  @Test fun jsParseFloatStopsAtTheComma() {
    assertEquals(1.0, jsParseFloat("1,234.50"), 0.0)
    assertEquals(1234.5, jsParseFloat("  1234.50 Cr."), 0.0)
    assertTrue(jsParseFloat("abc").isNaN())
  }

  @Test fun hashesDependOnTimezoneExactlyLikeTheWeb() {
    // SBI string dates are local midnight, so a different timezone is a different hash (as on the web).
    val utc = SbiParser.parse(bytes("sbi.xlsx"), user("sbi.xlsx"), ZoneId.of("UTC"))
    val kolkata = SbiParser.parse(bytes("sbi.xlsx"), user("sbi.xlsx"), ist)
    assertEquals("date-cell row is UTC midnight everywhere", kolkata[0].transactionHash, utc[0].transactionHash)
    assertTrue("string-date row is local midnight", kolkata[1].transactionHash != utc[1].transactionHash)
  }
}
