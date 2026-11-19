package com.biohazard786.interesttracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(vm: DashboardViewModel, modifier: Modifier = Modifier) {
  val state by vm.state.collectAsState()
  val stats = state.stats
  val pullState = rememberPullToRefreshState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var sheetOpen by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }

  PullToRefreshBox(
    isRefreshing = state.refreshing,
    onRefresh = vm::refresh,
    state = pullState,
    modifier = modifier.fillMaxSize(),
    indicator = {
      PullToRefreshDefaults.LoadingIndicator(
        state = pullState,
        isRefreshing = state.refreshing,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    },
  ) {
    when {
      state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
      stats == null -> ErrorState(state.error, onRetry = vm::refresh)
      else ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          val fraction = if (stats.totalInterest > 0) (stats.totalDonated / stats.totalInterest).toFloat().coerceIn(0f, 1f) else 0f

          Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          ) {
            Column(Modifier.padding(24.dp)) {
              Text("Left to donate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
              Text(
                formatInr(stats.amountLeft),
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(Modifier.size(20.dp))
              LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
              Spacer(Modifier.size(8.dp))
              Text(
                "${formatInr(stats.totalDonated)} of ${formatInr(stats.totalInterest)} donated",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total interest", formatInr(stats.totalInterest), Icons.Filled.Star, MaterialShapes.Cookie9Sided.toShape(), Modifier.weight(1f))
            StatCard("Donated", formatInr(stats.totalDonated), Icons.Filled.Favorite, MaterialShapes.SoftBurst.toShape(), Modifier.weight(1f))
          }

          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SplitButtonLayout(
              leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = { sheetOpen = true }) {
                  Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                  Spacer(Modifier.size(ButtonSpacing))
                  Text("Donate")
                }
              },
              trailingButton = {
                SplitButtonDefaults.TrailingButton(
                  checked = menuOpen,
                  onCheckedChange = { menuOpen = it },
                  modifier = Modifier.semantics { contentDescription = "More donation options" },
                ) {
                  Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).rotate(if (menuOpen) 180f else 0f),
                  )
                }
              },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                text = { Text("Donate full balance") },
                enabled = stats.amountLeft > 0.005,
                onClick = {
                  menuOpen = false
                  // Round to paise so we never send a float tail like 12.340000000000002.
                  val amount = (stats.amountLeft * 100).roundToLong() / 100.0
                  scope.launch { vm.donate(amount)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
                },
              )
            }
          }

          state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
  }

  if (sheetOpen && stats != null) {
    DonateSheet(
      amountLeft = stats.amountLeft,
      onDismiss = { sheetOpen = false },
      onDonate = { amount -> vm.donate(amount).also { if (it == null) sheetOpen = false } },
    )
  }
}

private val ButtonSpacing = 8.dp

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, shape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
      }
      Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
      Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
  }
}

@Composable
private fun DonateSheet(amountLeft: Double, onDismiss: () -> Unit, onDonate: suspend (Double) -> String?) {
  val scope = rememberCoroutineScope()
  var text by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Add donation", style = MaterialTheme.typography.headlineSmallEmphasized)
      Text("Up to ${formatInr(amountLeft)} is available to mark as donated.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(
        value = text,
        onValueChange = {
          text = it
          error = null
        },
        label = { Text("Amount (INR)") },
        prefix = { Text("₹") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        onClick = {
          val amount = text.trim().toDoubleOrNull()
          error =
            when {
              amount == null || amount <= 0 -> "Please enter a valid amount"
              (amount * 100).roundToLong() > (amountLeft * 100).roundToLong() -> "Amount cannot exceed available balance"
              else -> null
            }
          if (error == null && amount != null) {
            busy = true
            scope.launch {
              error = onDonate(amount)
              busy = false
            }
          }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (busy) LoadingIndicator(Modifier.size(24.dp)) else Text("Donate")
      }
    }
  }
}

@Composable
fun ErrorState(message: String?, onRetry: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(message ?: "Something went wrong", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.size(16.dp))
    Button(onClick = onRetry) { Text("Retry") }
  }
}
