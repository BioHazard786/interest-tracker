package com.biohazard786.interesttracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biohazard786.interesttracker.App

private enum class Tab(val title: String, val icon: ImageVector) {
  Dashboard("Overview", Icons.Filled.Home),
  Transactions("Interest", Icons.AutoMirrored.Filled.List),
  Upload("Upload", UploadIcon),
}

@Composable
fun AppRoot() {
  val app = LocalContext.current.applicationContext as App
  val token by app.tokens.token.collectAsStateWithLifecycle()
  val authVm = viewModel { AuthViewModel(app) }

  // Keyed on the signed-in flag, so sign-out (or an expired token) swaps screens on its own.
  Crossfade(targetState = token != null, label = "auth") { signedIn ->
    if (signedIn) SignedIn(onSignOut = authVm::signOut) else SignInScreen(authVm)
  }
}

/** The signed-in app: three tabs (Overview, Interest, Upload). */
@Composable
private fun SignedIn(onSignOut: () -> Unit) {
  val app = LocalContext.current.applicationContext as App
  val dashboardVm = viewModel { DashboardViewModel(app.api) }
  val transactionsVm = viewModel { TransactionsViewModel(app.api) }
  val uploadVm = viewModel { UploadViewModel(app) }
  Home(dashboardVm, transactionsVm, uploadVm, onSignOut)
}

@Composable
private fun Home(
  dashboardVm: DashboardViewModel,
  transactionsVm: TransactionsViewModel,
  uploadVm: UploadViewModel,
  onSignOut: () -> Unit,
) {
  val txState by transactionsVm.state.collectAsStateWithLifecycle()
  val uploadState by uploadVm.state.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
  var menuOpen by remember { mutableStateOf(false) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val title = if (tab == Tab.Upload && uploadState.preview != null) "Review" else tab.title

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(title) },
        actions = {
          if (tab == Tab.Transactions) {
            IconButton(onClick = transactionsVm::toggleSort) {
              Icon(
                if (txState.descending) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (txState.descending) "Newest first, tap for oldest first" else "Oldest first, tap for newest first",
              )
            }
          }
          IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.AccountCircle, contentDescription = "Account") }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Sign out") },
              onClick = {
                menuOpen = false
                onSignOut()
              },
            )
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    bottomBar = {
      ShortNavigationBar {
        Tab.entries.forEach { entry ->
          ShortNavigationBarItem(
            selected = tab == entry,
            onClick = { tab = entry },
            icon = { Icon(entry.icon, contentDescription = null) },
            label = { Text(entry.title) },
          )
        }
      }
    },
  ) { padding ->
    when (tab) {
      // The server reconciles before answering a donation, so the list can refresh straight away.
      Tab.Dashboard -> DashboardScreen(dashboardVm, onDonated = transactionsVm::reload, modifier = Modifier.padding(padding))
      Tab.Transactions -> TransactionsScreen(transactionsVm, Modifier.padding(padding))
      Tab.Upload ->
        UploadScreen(
          uploadVm,
          onSynced = {
            tab = Tab.Dashboard
            dashboardVm.refresh()
            transactionsVm.reload()
          },
          modifier = Modifier.padding(padding),
        )
    }
  }
}
