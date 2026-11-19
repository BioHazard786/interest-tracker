package com.biohazard786.interesttracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biohazard786.interesttracker.data.ApiException
import com.biohazard786.interesttracker.data.DonationStatus
import com.biohazard786.interesttracker.data.InterestApi
import com.biohazard786.interesttracker.data.ProjectionItem
import com.biohazard786.interesttracker.data.Stats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val api: InterestApi) : ViewModel() {
  data class State(val loading: Boolean = false, val error: String? = null)

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()

  fun fail(message: String) = _state.update { State(error = message) }

  fun signIn(idToken: String) {
    _state.value = State(loading = true)
    viewModelScope.launch {
      try {
        api.signInWithGoogle(idToken)
        _state.value = State()
      } catch (e: ApiException) {
        fail(e.message ?: "Sign-in failed")
      }
    }
  }

  fun startLoading() {
    _state.value = State(loading = true)
  }

  fun signOut() {
    viewModelScope.launch { api.signOut() }
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

class TransactionsViewModel(private val api: InterestApi) : ViewModel() {
  data class State(
    val items: List<ProjectionItem> = emptyList(),
    val statuses: Set<DonationStatus> = emptySet(),
    val descending: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
  )

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()
  private var job: Job? = null

  init {
    reload()
  }

  /** Re-fetches page one. Keeps the current list on screen when there is one. */
  fun reload() {
    job?.cancel()
    job =
      viewModelScope.launch {
        _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null) }
        val s = _state.value
        try {
          val page = api.projection(null, s.descending, s.statuses)
          _state.update {
            it.copy(items = page.data, nextCursor = page.nextCursor, loading = false, refreshing = false)
          }
        } catch (e: ApiException) {
          _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
        }
      }
  }

  fun loadMore() {
    val s = _state.value
    val cursor = s.nextCursor ?: return
    if (s.loading || s.loadingMore || s.refreshing) return
    job =
      viewModelScope.launch {
        _state.update { it.copy(loadingMore = true) }
        try {
          val page = api.projection(cursor, s.descending, s.statuses)
          _state.update {
            it.copy(items = it.items + page.data, nextCursor = page.nextCursor, loadingMore = false)
          }
        } catch (e: ApiException) {
          _state.update { it.copy(loadingMore = false, error = e.message) }
        }
      }
  }

  fun toggleStatus(status: DonationStatus) {
    _state.update { it.copy(statuses = if (status in it.statuses) it.statuses - status else it.statuses + status, items = emptyList()) }
    reload()
  }

  fun toggleSort() {
    _state.update { it.copy(descending = !it.descending, items = emptyList()) }
    reload()
  }
}
