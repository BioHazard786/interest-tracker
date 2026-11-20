package com.biohazard786.interesttracker.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biohazard786.interesttracker.App
import com.biohazard786.interesttracker.data.ApiException
import com.biohazard786.interesttracker.parsing.Bank
import com.biohazard786.interesttracker.parsing.ParsedTxn
import com.biohazard786.interesttracker.parsing.PasswordRequiredException
import com.biohazard786.interesttracker.parsing.PdfText
import com.biohazard786.interesttracker.parsing.StatementException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadViewModel(private val app: App) : ViewModel() {
  data class PickedFile(val uri: Uri, val name: String, val size: Long)

  data class State(
    val bank: Bank? = null,
    val files: List<PickedFile> = emptyList(),
    /** bank id -> latest stored transaction date. */
    val lastDates: Map<String, String?> = emptyMap(),
    val parsing: Boolean = false,
    val syncing: Boolean = false,
    /** Non-null once files were analysed: what would be uploaded. */
    val preview: List<ParsedTxn>? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    /** A locked PDF waiting for its password (asked one at a time); [wrongPassword] after a failed try. */
    val passwordFor: PickedFile? = null,
    val wrongPassword: Boolean = false,
    /** Set after a successful sync: how many rows the server received. */
    val syncedMessage: String? = null,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()
  private var userId: String? = null
  /** Passwords typed this session, kept in memory only. */
  private val passwords = HashMap<Uri, String>()

  /** Back to the first step (after a sync); keeps the known "last upload" dates. */
  fun reset() {
    passwords.clear()
    _state.update { State(lastDates = it.lastDates) }
    loadLastDates()
  }

  /** The "last upload" dates shown next to each bank; refreshed whenever the tab opens. */
  fun loadLastDates() {
    viewModelScope.launch {
      try {
        val dates = app.api.lastDates()
        _state.update { it.copy(lastDates = dates) }
      } catch (_: ApiException) {
        // Only decoration; the flow works without it.
      }
    }
  }

  fun selectBank(bank: Bank) = _state.update { State(bank = bank, lastDates = it.lastDates) }

  fun addFiles(uris: List<Uri>) {
    val resolver = app.contentResolver
    val added =
      uris.filter { uri -> _state.value.files.none { it.uri == uri } }.map { uri ->
        var name = uri.lastPathSegment ?: "statement"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
          if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            size = c.getLong(1)
          }
        }
        PickedFile(uri, name, size)
      }
    _state.update {
      val all = it.files + added
      it.copy(files = all.take(MAX_FILES), error = if (all.size > MAX_FILES) "You can upload up to $MAX_FILES files at a time." else null)
    }
  }

  fun removeFile(file: PickedFile) {
    passwords.remove(file.uri)
    _state.update { it.copy(files = it.files - file, error = null) }
  }

  fun submitPassword(password: String) {
    val file = _state.value.passwordFor ?: return
    passwords[file.uri] = password
    _state.update { it.copy(passwordFor = null) }
    analyze()
  }

  fun cancelPassword() {
    _state.value.passwordFor?.let(::removeFile)
    _state.update { it.copy(passwordFor = null) }
  }

  fun backToFiles() = _state.update { it.copy(preview = null, warnings = emptyList(), error = null) }

  fun analyze() {
    val s = _state.value
    val bank = s.bank ?: return
    if (s.files.isEmpty() || s.parsing) return
    _state.update { it.copy(parsing = true, error = null, warnings = emptyList()) }
    viewModelScope.launch {
      try {
        val user = userId ?: app.api.userId().also { userId = it }
        val warnings = ArrayList<String>()
        var locked: Pair<PickedFile, Boolean>? = null
        val found =
          withContext(Dispatchers.Default) {
            s.files.flatMap { file ->
              try {
                var bytes = app.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: throw StatementException("Couldn't open the file.")
                passwords[file.uri]?.let { bytes = PdfText.unlock(bytes, it) }
                bank.parse(file.name, bytes, user)
              } catch (e: PasswordRequiredException) {
                if (locked == null) locked = file to e.wrong
                emptyList()
              } catch (e: StatementException) {
                warnings += "${file.name}: ${e.message}"
                emptyList()
              } catch (e: Exception) {
                warnings += "${file.name}: couldn't be read as a ${bank.name} statement."
                emptyList()
              } catch (e: OutOfMemoryError) {
                warnings += "${file.name}: too large to read on this device."
                emptyList()
              }
            }
          }
        // Same file or overlapping statements: one row per hash, newest first (like the web).
        val unique = found.distinctBy { it.transactionHash }.sortedByDescending { it.date }
        val ask = locked
        _state.update {
          if (ask != null) it.copy(parsing = false, passwordFor = ask.first, wrongPassword = ask.second)
          else if (unique.isEmpty()) it.copy(parsing = false, warnings = warnings, error = "No interest transactions found in the uploaded files.")
          else it.copy(parsing = false, preview = unique, warnings = warnings)
        }
      } catch (e: ApiException) {
        _state.update { it.copy(parsing = false, error = e.message) }
      }
    }
  }

  fun sync() {
    val s = _state.value
    val bank = s.bank ?: return
    val rows = s.preview ?: return
    if (s.syncing) return
    _state.update { it.copy(syncing = true, error = null) }
    viewModelScope.launch {
      try {
        app.api.syncTransactions(bank.id, rows)
        _state.update { it.copy(syncing = false, syncedMessage = "Synced ${rows.size} interest transactions") }
      } catch (e: ApiException) {
        _state.update { it.copy(syncing = false, error = e.message) }
      }
    }
  }

  private companion object {
    const val MAX_FILES = 10
  }
}
