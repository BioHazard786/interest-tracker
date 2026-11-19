package com.biohazard786.interesttracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biohazard786.interesttracker.App

private enum class Tab(val title: String, val subtitle: String, val icon: ImageVector) {
  Dashboard("Dashboard", "Interest and donations at a glance", Icons.Filled.Home),
  Transactions("Transactions", "Every interest credit and its status", Icons.AutoMirrored.Filled.List),
}

@Composable
fun AppRoot() {
  val app = LocalContext.current.applicationContext as App
  val token by app.tokens.token.collectAsState()
  val authVm = viewModel { AuthViewModel(app.api) }

  // Crossfade on the signed-in flag so sign-out (or a 401) swaps screens automatically.
  Crossfade(targetState = token != null, label = "auth") { signedIn ->
    if (signedIn) MainScaffold(onSignOut = authVm::signOut) else SignInScreen(authVm)
  }
}

@Composable
private fun MainScaffold(onSignOut: () -> Unit) {
  val app = LocalContext.current.applicationContext as App
  val dashboardVm = viewModel { DashboardViewModel(app.api) }
  val transactionsVm = viewModel { TransactionsViewModel(app.api) }

  var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
  var menuOpen by remember { mutableStateOf(false) }
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  // Donations change statuses server-side, so refresh the list whenever its tab opens.
  LaunchedEffect(tab) { if (tab == Tab.Transactions) transactionsVm.reload() }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(tab.title) },
        subtitle = { Text(tab.subtitle) },
        actions = {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Account menu")
          }
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
      Tab.Dashboard -> DashboardScreen(dashboardVm, Modifier.padding(padding))
      Tab.Transactions -> TransactionsScreen(transactionsVm, Modifier.padding(padding))
    }
  }
}
