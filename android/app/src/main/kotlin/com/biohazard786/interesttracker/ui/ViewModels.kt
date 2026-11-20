package com.biohazard786.interesttracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biohazard786.interesttracker.App
import com.biohazard786.interesttracker.data.ApiException
import com.biohazard786.interesttracker.data.DonationStatus
import com.biohazard786.interesttracker.data.GoogleAuth
import com.biohazard786.interesttracker.data.GoogleIdToken
import com.biohazard786.interesttracker.data.InterestApi
import com.biohazard786.interesttracker.data.ProjectionItem
import com.biohazard786.interesttracker.data.Stats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val app: App) : ViewModel() {
  data class State(val loading: Boolean = false, val error: String? = null)

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  /** The bottom sheet opens by itself once per app launch, not again after an explicit sign-out. */
  var autoPrompted = false

  fun startLoading() = _state.update { State(loading = true) }

  fun idle(error: String? = null) = _state.update { State(error = error) }

  fun signIn(token: GoogleIdToken) {
    viewModelScope.launch {
      try {
        app.api.signInWithGoogle(token)
        idle()
      } catch (e: ApiException) {
        idle(e.message ?: "Sign-in failed")
      }
    }
  }

  fun signOut() {
    autoPrompted = true
    viewModelScope.launch {
      app.api.signOut()
      GoogleAuth.clearState(app)
    }
  }
}

class DashboardViewModel(private val api: InterestApi) : ViewModel() {
  data class State(
    val stats: Stats? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _state.update { it.copy(loading = it.stats == null, refreshing = it.stats != null, error = null) }
      try {
        _state.value = State(stats = api.stats(), loading = false)
      } catch (e: ApiException) {
        _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
      }
    }
  }

  /** Returns an error message, or null on success. */
  suspend fun donate(amount: Double): String? =
    try {
      api.donate(amount)
      refresh()
      null
    } catch (e: ApiException) {
      e.message
    }
}

/** One list row, with every display string computed once when the page arrives. */
data class TxRow(
  val item: ProjectionItem,
  val status: DonationStatus,
  val title: String,
  val subtitle: String,
  val amount: String,
  val remaining: String?,
)

data class YearGroup(val year: String, val summary: String, val rows: List<TxRow>)

private fun List<ProjectionItem>.toGroups(): List<YearGroup> =
  groupBy { it.transactionDate.take(4) }
    .map { (year, items) ->
      val total = items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
      YearGroup(
        year = year,
        summary = "${formatInrCompact(total)} · ${items.size} ${if (items.size == 1) "credit" else "credits"}",
        rows =
          items.map {
            val status = DonationStatus.from(it.status)
            TxRow(
              item = it,
              status = status,
              title = formatTitle(it.bankId, it.description),
              subtitle = "Credited ${formatDayMonth(it.transactionDate)} · ${bankName(it.bankId)}",
              amount = formatInrCompact(it.amount.toDoubleOrNull() ?: 0.0),
              remaining =
                if (status == DonationStatus.PartiallyDonated) "${formatInrCompact(it.remainingAmount.toDoubleOrNull() ?: 0.0)} left"
                else null,
            )
          },
      )
    }

class TransactionsViewModel(private val api: InterestApi) : ViewModel() {
  data class State(
    val items: List<ProjectionItem> = emptyList(),
    val groups: List<YearGroup> = emptyList(),
    /** null = all statuses. */
    val filter: DonationStatus? = null,
    val descending: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Filter/sort changed: the old list stays on screen, dimmed, until the new one lands. */
    val switching: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()
  private var job: Job? = null

  init {
    load()
  }

  /** Pull-to-refresh, or a silent refresh after a donation. */
  fun reload() = load(refreshing = _state.value.items.isNotEmpty())

  fun setFilter(filter: DonationStatus?) {
    if (filter == _state.value.filter) return
    _state.update { it.copy(filter = filter) }
    load(switching = true)
  }

  fun toggleSort() {
    _state.update { it.copy(descending = !it.descending) }
    load(switching = true)
  }

  private fun load(refreshing: Boolean = false, switching: Boolean = false) {
    job?.cancel()
    job =
      viewModelScope.launch {
        _state.update {
          it.copy(loading = it.items.isEmpty() && !switching, refreshing = refreshing, switching = switching, loadingMore = false, error = null)
        }
        val s = _state.value
        try {
          val page = api.projection(null, s.descending, setOfNotNull(s.filter))
          _state.update {
            it.copy(
              items = page.data,
              groups = page.data.toGroups(),
              nextCursor = page.nextCursor,
              loading = false,
              refreshing = false,
              switching = false,
            )
          }
        } catch (e: ApiException) {
          _state.update { it.copy(loading = false, refreshing = false, switching = false, error = e.message) }
        }
      }
  }

  fun loadMore() {
    val s = _state.value
    val cursor = s.nextCursor ?: return
    if (s.loading || s.loadingMore || s.refreshing || s.switching) return
    job =
      viewModelScope.launch {
        _state.update { it.copy(loadingMore = true) }
        try {
          val page = api.projection(cursor, s.descending, setOfNotNull(s.filter))
          _state.update {
            val items = it.items + page.data
            it.copy(items = items, groups = items.toGroups(), nextCursor = page.nextCursor, loadingMore = false)
          }
        } catch (e: ApiException) {
          _state.update { it.copy(loadingMore = false, error = e.message) }
        }
      }
  }
}
