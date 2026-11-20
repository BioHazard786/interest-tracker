package com.biohazard786.interesttracker.parsing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal sealed interface Cell {
  data class Text(val value: String) : Cell
  data class Num(val value: Double) : Cell
  data class Bool(val value: Boolean) : Cell
  /** A date-formatted number cell: JS gives these as UTC midnight. */
  data class Date(val value: JsDate) : Cell
}

/**
 * Minimal .xlsx reader for the first worksheet: zip + SAX over sharedStrings, styles and the sheet.
 * Apache POI doesn't run on Android (it needs java.awt), and bank statements only need cell values.
 * Returns rows as lists of cells, null where empty.
 */
internal object Xlsx {
  private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

  fun read(bytes: ByteArray): List<List<Cell?>> {
    // shortcut: password-protected workbooks (OLE2 header) aren't decrypted, add an Office-crypto reader if banks send them.
    if (bytes.size > 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
      throw StatementException("This spreadsheet is password protected. Open it in Excel, remove the password and upload it again.")
    }
    val entries = unzip(bytes)
    val sheetPath = firstSheetPath(entries)
    val sheet = entries[sheetPath] ?: throw StatementException("This file has no worksheet. Is it a valid .xlsx?")
    val strings = entries["xl/sharedStrings.xml"]?.let { SharedStrings().also { h -> parse(it, h) }.values } ?: emptyList()
    val dateStyles = entries["xl/styles.xml"]?.let { Styles().also { h -> parse(it, h) }.dateStyleIds } ?: emptySet()
    return Sheet(strings, dateStyles).also { parse(sheet, it) }.rows
  }

  private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val wanted = Regex("""xl/(workbook\.xml|_rels/workbook\.xml\.rels|sharedStrings\.xml|styles\.xml|worksheets/[^/]+\.xml)""")
    val out = HashMap<String, ByteArray>()
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
          if (!wanted.matches(entry.name)) return@forEach
          val buffer = ByteArrayOutputStream()
          val chunk = ByteArray(16 * 1024)
          while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > MAX_ENTRY_BYTES) throw StatementException("This spreadsheet is too large to read.")
          }
          out[entry.name] = buffer.toByteArray()
        }
      }
    } catch (e: java.io.IOException) {
      throw StatementException("This isn't a readable .xlsx file.")
    }
    if (out.isEmpty()) throw StatementException("This isn't a readable .xlsx file.")
    return out
  }

  /** The first sheet in workbook order, resolved through the relationships file. */
  private fun firstSheetPath(entries: Map<String, ByteArray>): String {
    val fallback = "xl/worksheets/sheet1.xml"
    val workbook = entries["xl/workbook.xml"] ?: return fallback
    val rels = entries["xl/_rels/workbook.xml.rels"] ?: return fallback
    var firstRid: String? = null
    parse(workbook, object : DefaultHandler() {
      override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
        if (firstRid == null && qName.substringAfter(':') == "sheet") firstRid = attrs.byName("id")
      }
    })
    var target: String? = null
    parse(rels, object : DefaultHandler() {
      override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
        if (qName == "Relationship" && attrs.byName("Id") == firstRid) target = attrs.byName("Target")
      }
    })
    val path = target ?: return fallback
    return if (path.startsWith("/")) path.removePrefix("/") else "xl/$path"
  }

  private fun parse(xml: ByteArray, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance()
    // A statement spreadsheet has no business declaring a DTD; refuse entity tricks where supported.
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    try {
      factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    } catch (e: org.xml.sax.SAXException) {
      throw StatementException("This isn't a readable .xlsx file.")
    }
  }

  /** Attribute by local name, so `r:id` matches "id" whatever the prefix. */
  private fun Attributes.byName(name: String): String? {
    for (i in 0 until length) if (getQName(i).substringAfter(':') == name) return getValue(i)
    return null
  }

  private class SharedStrings : DefaultHandler() {
    val values = ArrayList<String>()
    private var current: StringBuilder? = null
    private var inText = false
    private var phonetic = 0

    override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
      when (qName.substringAfter(':')) {
        "si" -> current = StringBuilder()
        "t" -> if (phonetic == 0) inText = true
        "rPh" -> phonetic++ // furigana runs aren't part of the visible text
      }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
      if (inText) current?.append(ch, start, length)
    }

    override fun endElement(uri: String?, local: String?, qName: String) {
      when (qName.substringAfter(':')) {
        "t" -> inText = false
        "rPh" -> phonetic--
        "si" -> {
          values += current.toString()
          current = null
        }
      }
    }
  }

  /** Which cell style ids (`s="…"`) format numbers as dates. */
  private class Styles : DefaultHandler() {
    private val customFormats = HashMap<Int, String>()
    private val xfFormatIds = ArrayList<Int>()
    private var inCellXfs = false
    val dateStyleIds: Set<Int>
      get() = xfFormatIds.indices.filter { isDateFormat(xfFormatIds[it], customFormats[xfFormatIds[it]]) }.toSet()

    override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
      when (qName.substringAfter(':')) {
        "numFmt" -> {
          val id = attrs.byName("numFmtId")?.toIntOrNull()
          val code = attrs.byName("formatCode")
          if (id != null && code != null) customFormats[id] = code
        }
        "cellXfs" -> inCellXfs = true
        "xf" -> if (inCellXfs) xfFormatIds += attrs.byName("numFmtId")?.toIntOrNull() ?: 0
      }
    }

    override fun endElement(uri: String?, local: String?, qName: String) {
      if (qName.substringAfter(':') == "cellXfs") inCellXfs = false
    }

    private fun isDateFormat(id: Int, code: String?): Boolean {
      if (code == null) return id in 14..22 || id in 27..36 || id in 45..47 || id in 50..58
      // Drop quoted literals, [colour]/[locale] blocks and escaped characters before looking for d/m/y/h/s.
      val stripped = code.replace(Regex("\"[^\"]*\"|\\[[^\\]]*]|\\\\.|_.|\\*."), "").lowercase()
      return stripped.any { it in "dmyhs" } && !stripped.contains("general")
    }
  }

  private class Sheet(private val strings: List<String>, private val dateStyles: Set<Int>) : DefaultHandler() {
    val rows = ArrayList<List<Cell?>>()
    private var row: ArrayList<Cell?>? = null
    private var cellType: String? = null
    private var cellStyle = 0
    private var cellCol = 0
    private var value = StringBuilder()
    private var inline = StringBuilder()
    private var inValue = false
    private var inInlineText = false

    override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
      when (qName.substringAfter(':')) {
        "row" -> row = ArrayList()
        "c" -> {
          cellType = attrs.byName("t")
          cellStyle = attrs.byName("s")?.toIntOrNull() ?: 0
          cellCol = columnIndex(attrs.byName("r"))
          value = StringBuilder()
          inline = StringBuilder()
        }
        "v" -> inValue = true
        "t" -> inInlineText = true
      }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
      if (inValue) value.append(ch, start, length) else if (inInlineText) inline.append(ch, start, length)
    }

    override fun endElement(uri: String?, local: String?, qName: String) {
      when (qName.substringAfter(':')) {
        "v" -> inValue = false
        "t" -> inInlineText = false
        "c" -> {
          val current = row ?: return
          while (current.size < cellCol) current += null
          if (current.size == cellCol) current += toCell() else current[cellCol] = toCell()
        }
        "row" -> row?.let { rows += it }
      }
    }

    private fun toCell(): Cell? {
      val raw = value.toString()
      return when (cellType) {
        "s" -> raw.toIntOrNull()?.let { strings.getOrNull(it) }?.let { Cell.Text(it) }
        "str" -> raw.takeIf { it.isNotEmpty() }?.let { Cell.Text(it) }
        "inlineStr" -> inline.toString().takeIf { it.isNotEmpty() }?.let { Cell.Text(it) }
        "b" -> Cell.Bool(raw == "1")
        "e" -> null
        "d" -> jsDateParse(raw.take(10), ZoneOffset.UTC)?.let { Cell.Date(utcMidnight(it.day)) }
        else -> raw.toDoubleOrNull()?.let { if (cellStyle in dateStyles) Cell.Date(serialToDate(it)) else Cell.Num(it) }
      }
    }

    /** "C5" -> 2. */
    private fun columnIndex(ref: String?): Int {
      var n = 0
      for (ch in ref.orEmpty()) {
        if (ch !in 'A'..'Z') break
        n = n * 26 + (ch - 'A' + 1)
      }
      return (n - 1).coerceAtLeast(0)
    }

    /** read-excel-file: `new Date(Math.round((serial - 25569) * 86400 * 1000))`, i.e. UTC. */
    private fun serialToDate(serial: Double): JsDate {
      val instant = Instant.ofEpochMilli(Math.round((serial - 25569.0) * 86_400_000.0))
      return JsDate(instant, instant.atZone(ZoneOffset.UTC).toLocalDate())
    }
  }
}
