package com.biohazard786.interesttracker.parsing

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A PDF needs a password; [wrong] is true when one was given and didn't open it. */
class PasswordRequiredException(val wrong: Boolean) : Exception("This PDF is password protected.")

/**
 * Text lines of a PDF via PDFBox, in reading order. The web app uses pdf2md (pdf.js) for the same job;
 * the bank parsers only rely on each statement row coming out as one whitespace-separated line.
 * Android only: PDFBox needs PDFBoxResourceLoader.init(context) first (done in App).
 */
internal object PdfText {
  private val SPACES = Regex("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]")

  fun lines(bytes: ByteArray): List<String> =
    try {
      PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
        PDFTextStripper().apply { sortByPosition = true }.getText(document)
          .split(Regex("""\r\n|\n"""))
          // JS `\s` matches non-breaking and typographic spaces; Java's doesn't, so fold them first.
          .map { it.replace(SPACES, " ").trimEnd() }
      }
    } catch (e: InvalidPasswordException) {
      throw PasswordRequiredException(wrong = false)
    } catch (e: IOException) {
      throw StatementException("This PDF can't be read.")
    }

  /** The same PDF without its password, so the bank parsers read it like any other PDF. */
  fun unlock(bytes: ByteArray, password: String): ByteArray =
    try {
      PDDocument.load(ByteArrayInputStream(bytes), password).use { document ->
        document.setAllSecurityToBeRemoved(true)
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
      }
    } catch (e: InvalidPasswordException) {
      throw PasswordRequiredException(wrong = true)
    } catch (e: IOException) {
      throw StatementException("This PDF can't be read.")
    }
}
