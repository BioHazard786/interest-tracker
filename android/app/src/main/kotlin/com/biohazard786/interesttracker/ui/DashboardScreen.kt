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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biohazard786.interesttracker.data.Stats
import com.biohazard786.interesttracker.ui.theme.HeroNumeral
import com.biohazard786.interesttracker.ui.theme.Tabular
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(vm: DashboardViewModel, onDonated: () -> Unit, modifier: Modifier = Modifier) {
  val state by vm.state.collectAsStateWithLifecycle()
  val stats = state.stats
  val pullState = rememberPullToRefreshState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var sheetOpen by remember { mutableStateOf(false) }
  var confirmAll by remember { mutableStateOf(false) }

  suspend fun donate(amount: Double): String? = vm.donate(amount).also { if (it == null) onDonated() }

  PullToRefreshBox(
    isRefreshing = state.refreshing,
    onRefresh = vm::refresh,
    state = pullState,
    modifier = modifier.fillMaxSize(),
    indicator = {
      PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = state.refreshing, modifier = Modifier.align(Alignment.TopCenter))
    },
  ) {
    when {
      state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
      stats == null -> ErrorState(state.error, onRetry = vm::refresh)
      else ->
        Column(
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Hero(stats)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
              "Total interest", formatInrCompact(stats.totalInterest), Icons.Filled.Star,
              MaterialShapes.Cookie9Sided.toShape(), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary,
              Modifier.weight(1f),
            )
            StatTile(
              "Donated", formatInrCompact(stats.totalDonated), Icons.Filled.Favorite,
              MaterialShapes.SoftBurst.toShape(), MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary,
              Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            val height = ButtonDefaults.MediumContainerHeight
            Button(
              onClick = { sheetOpen = true },
              enabled = stats.amountLeft > 0.005,
              modifier = Modifier.weight(1f).heightIn(min = height),
              contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
              Icon(Icons.Filled.Favorite, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
              Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
              Text("Donate", style = ButtonDefaults.textStyleFor(height))
            }
            FilledTonalButton(
              onClick = { confirmAll = true },
              enabled = stats.amountLeft > 0.005,
              modifier = Modifier.heightIn(min = height),
              contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
              Text("Donate all", style = ButtonDefaults.textStyleFor(height))
            }
          }
          state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
          }
        }
    }
  }

  if (stats != null && sheetOpen) {
    DonateSheet(
      amountLeft = stats.amountLeft,
      onDismiss = { sheetOpen = false },
      onDonate = { amount -> donate(amount).also { if (it == null) sheetOpen = false } },
    )
  }

  if (stats != null && confirmAll) {
    // Round to paise so we never send a float tail like 12.340000000000002.
    val amount = (stats.amountLeft * 100).roundToLong() / 100.0
    AlertDialog(
      onDismissRequest = { confirmAll = false },
      title = { Text("Donate ${formatInr(amount)}?") },
      text = { Text("This marks all remaining interest as donated.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmAll = false
            scope.launch { donate(amount)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
          }
        ) {
          Text("Donate")
        }
      },
      dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun Hero(stats: Stats) {
  val fraction = if (stats.totalInterest > 0) (stats.totalDonated / stats.totalInterest).toFloat().coerceIn(0f, 1f) else 0f
  val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
  val left = formatInr(stats.amountLeft)

  Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.primaryContainer) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
      Text("Left to donate", style = MaterialTheme.typography.titleMedium, color = onContainer.copy(alpha = 0.8f))
      Spacer(Modifier.height(4.dp))
      HeroAmount(left, onContainer)
      Spacer(Modifier.height(24.dp))
      LinearWavyProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = onContainer.copy(alpha = 0.16f),
      )
      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "${formatInrCompact(stats.totalDonated)} of ${formatInrCompact(stats.totalInterest)} donated",
          style = MaterialTheme.typography.bodyMedium.merge(Tabular),
          color = onContainer,
          modifier = Modifier.weight(1f),
        )
        Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.titleMediumEmphasized.merge(Tabular), color = onContainer)
      }
    }
  }
}

/** The big rupee figure: paise in the light weight at half size, so the rupees carry the weight. */
@Composable
internal fun HeroAmount(amount: String, color: Color) {
  val split = amount.lastIndexOf('.')
  BasicText(
    buildAnnotatedString {
      append(if (split > 0) amount.substring(0, split) else amount)
      if (split > 0) withStyle(SpanStyle(fontSize = 0.45.em, fontWeight = FontWeight(400))) { append(amount.substring(split)) }
    },
    style = HeroNumeral.copy(color = color),
    maxLines = 1,
    autoSize = TextAutoSize.StepBased(minFontSize = 36.sp, maxFontSize = 72.sp),
  )
}

@Composable
private fun StatTile(
  label: String,
  value: String,
  icon: ImageVector,
  shape: Shape,
  badgeColor: Color,
  onBadge: Color,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Column(Modifier.padding(20.dp)) {
      Box(Modifier.size(44.dp).background(badgeColor, shape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = onBadge, modifier = Modifier.size(22.dp))
      }
      Spacer(Modifier.height(20.dp))
      Text(value, style = MaterialTheme.typography.headlineSmallEmphasized.merge(Tabular), maxLines = 1)
      Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
      Text("Add a donation", style = MaterialTheme.typography.headlineMediumEmphasized)
      Text("Up to ${formatInr(amountLeft)} is left to give.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(
        value = text,
        onValueChange = {
          text = it
          error = null
        },
        label = { Text("Amount") },
        prefix = { Text("₹") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.headlineSmallEmphasized.merge(Tabular),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )
      val height = ButtonDefaults.MediumContainerHeight
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
        modifier = Modifier.fillMaxWidth().heightIn(min = height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
      ) {
        if (busy) LoadingIndicator(Modifier.size(28.dp)) else Text("Donate", style = ButtonDefaults.textStyleFor(height))
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
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry) { Text("Retry") }
  }
}
