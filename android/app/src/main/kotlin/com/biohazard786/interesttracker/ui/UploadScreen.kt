package com.biohazard786.interesttracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biohazard786.interesttracker.parsing.Bank
import com.biohazard786.interesttracker.parsing.Banks
import com.biohazard786.interesttracker.parsing.ParsedTxn
import com.biohazard786.interesttracker.parsing.pickerMimeTypes
import com.biohazard786.interesttracker.ui.theme.InterestTrackerTheme
import com.biohazard786.interesttracker.ui.theme.Tabular

/** The Upload tab's icon (Material "file_upload"; the extended icon set isn't a dependency). */
internal val UploadIcon: ImageVector by lazy {
  materialIcon(name = "Filled.FileUpload") {
    materialPath {
      moveTo(9f, 16f)
      horizontalLineToRelative(6f)
      verticalLineToRelative(-6f)
      horizontalLineToRelative(4f)
      lineToRelative(-7f, -7f)
      lineToRelative(-7f, 7f)
      horizontalLineToRelative(4f)
      close()
      moveTo(5f, 18f)
      horizontalLineToRelative(14f)
      verticalLineToRelative(2f)
      horizontalLineTo(5f)
      close()
    }
  }
}

/**
 * The Upload tab: pick a bank, add its statement files, review the interest rows found, sync.
 * Two steps in one place; "Back" on the review step returns to the files.
 */
@Composable
fun UploadScreen(vm: UploadViewModel, onSynced: () -> Unit, modifier: Modifier = Modifier) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }

  BackHandler(enabled = state.preview != null && !state.syncing) { vm.backToFiles() }
  LaunchedEffect(Unit) { vm.loadLastDates() }
  LaunchedEffect(state.syncedMessage) {
    state.syncedMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      vm.reset()
      onSynced()
    }
  }

  state.passwordFor?.let { PasswordDialog(it.name, state.wrongPassword, vm::submitPassword, vm::cancelPassword) }

  UploadContent(
    state = state,
    onSelectBank = vm::selectBank,
    onPickFiles = { bank -> picker.launch(bank.pickerMimeTypes()) },
    onRemoveFile = vm::removeFile,
    onAnalyze = vm::analyze,
    onSync = vm::sync,
    onBackToFiles = vm::backToFiles,
    modifier = modifier,
  )
}

@Composable
internal fun UploadContent(
  state: UploadViewModel.State,
  onSelectBank: (Bank) -> Unit,
  onPickFiles: (Bank) -> Unit,
  onRemoveFile: (UploadViewModel.PickedFile) -> Unit,
  onAnalyze: () -> Unit,
  onSync: () -> Unit,
  onBackToFiles: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val preview = state.preview
  if (preview != null) Review(preview, state, onSync, onBackToFiles, modifier)
  else Pick(state, onSelectBank, onPickFiles, onRemoveFile, onAnalyze, modifier)
}

// --- Step 1: bank + files -----------------------------------------------------------------------------------

@Composable
private fun Pick(
  state: UploadViewModel.State,
  onSelectBank: (Bank) -> Unit,
  onPickFiles: (Bank) -> Unit,
  onRemoveFile: (UploadViewModel.PickedFile) -> Unit,
  onAnalyze: () -> Unit,
  modifier: Modifier,
) {
  val bank = state.bank
  val bankShapes = listOf(MaterialShapes.Cookie9Sided.toShape(), MaterialShapes.SoftBurst.toShape(), MaterialShapes.Clover4Leaf.toShape(), MaterialShapes.Cookie12Sided.toShape())
  val fileShape = MaterialShapes.Clover4Leaf.toShape()

  Column(modifier.fillMaxSize()) {
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        "Pick your bank, add its statement, and the interest is found for you.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      SectionTitle("Bank")
      Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Banks.forEachIndexed { index, item ->
          BankRow(item, selected = bank?.id == item.id, last = state.lastDates[item.id], shape = bankShapes[index % bankShapes.size], index = index, onClick = { onSelectBank(item) })
        }
      }

      if (bank != null) {
        SectionTitle("Statements")
        if (state.files.isEmpty()) {
          Text(
            "Choose your ${bank.name} statement (${bank.formats.joinToString(" or ") { it.uppercase() }}). You can add several at once.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
          state.files.forEachIndexed { index, file ->
            SegmentedListItem(
              onClick = {},
              shapes = ListItemDefaults.segmentedShapes(index = index, count = state.files.size),
              colors = rowColors(),
              leadingContent = { Badge(fileShape, MaterialTheme.colorScheme.secondaryContainer) { Text(file.name.substringAfterLast('.', "").uppercase().take(4), style = MaterialTheme.typography.labelSmallEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer) } },
              supportingContent = { Text(formatSize(file.size)) },
              trailingContent = { IconButton(onClick = { onRemoveFile(file) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${file.name}") } },
            ) {
              Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
      Messages(state)
    }

    if (bank != null) {
      val height = ButtonDefaults.MediumContainerHeight
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.files.isEmpty()) {
          Button(onClick = { onPickFiles(bank) }, modifier = Modifier.weight(1f).heightIn(min = height), contentPadding = ButtonDefaults.contentPaddingFor(height)) {
            Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text("Choose files", style = ButtonDefaults.textStyleFor(height))
          }
        } else {
          FilledTonalButton(onClick = { onPickFiles(bank) }, enabled = !state.parsing, modifier = Modifier.heightIn(min = height), contentPadding = ButtonDefaults.contentPaddingFor(height)) {
            Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text("Add more", style = ButtonDefaults.textStyleFor(height))
          }
          Button(onClick = onAnalyze, enabled = !state.parsing, modifier = Modifier.weight(1f).heightIn(min = height), contentPadding = ButtonDefaults.contentPaddingFor(height)) {
            if (state.parsing) LoadingIndicator(Modifier.size(28.dp)) else Text("Analyze", style = ButtonDefaults.textStyleFor(height))
          }
        }
      }
    }
  }
}

@Composable
private fun BankRow(bank: Bank, selected: Boolean, last: String?, shape: Shape, index: Int, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  SegmentedListItem(
    onClick = onClick,
    shapes = ListItemDefaults.segmentedShapes(index = index, count = Banks.size),
    colors =
      if (selected) ListItemDefaults.colors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, supportingContentColor = scheme.onPrimaryContainer.copy(alpha = 0.8f))
      else rowColors(),
    leadingContent = {
      if (selected) Badge(shape, scheme.primary) { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = scheme.onPrimary, modifier = Modifier.size(22.dp)) }
      else Badge(shape, scheme.tertiaryContainer) { Text(bank.name.first().toString(), style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.onTertiaryContainer) }
    },
    supportingContent = {
      Text(bank.formats.joinToString(" · ") { it.uppercase() } + (last?.let { " · Last upload ${formatDate(it)}" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
  ) {
    Text(bank.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

// --- Step 2: review -----------------------------------------------------------------------------------------

@Composable
private fun Review(rows: List<ParsedTxn>, state: UploadViewModel.State, onSync: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
  val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
  val badge = MaterialShapes.Cookie9Sided.toShape()
  val height = ButtonDefaults.MediumContainerHeight

  Column(modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.weight(1f),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
      item(contentType = "hero") {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(bottom = 12.dp)) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text("Found in ${state.bank?.name}", style = MaterialTheme.typography.titleMedium, color = onContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            HeroAmount(formatInr(rows.sumOf { it.amount }), onContainer)
            Spacer(Modifier.height(12.dp))
            Text(
              "${rows.size} interest ${if (rows.size == 1) "credit" else "credits"} to add",
              style = MaterialTheme.typography.titleMediumEmphasized.merge(Tabular),
              color = onContainer,
            )
          }
        }
        Messages(state)
      }
      items(rows.size, key = { rows[it].transactionHash }, contentType = { "row" }) { index ->
        val txn = rows[index]
        SegmentedListItem(
          onClick = {},
          shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
          colors = rowColors(),
          leadingContent = {
            Badge(badge, MaterialTheme.colorScheme.tertiaryContainer) {
              Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
            }
          },
          supportingContent = { Text(txn.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
          trailingContent = { Text(formatInrCompact(txn.amount), style = MaterialTheme.typography.titleMediumEmphasized.merge(Tabular)) },
        ) {
          Text(formatDate(txn.date.toString()), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
      }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(onClick = onBack, enabled = !state.syncing, modifier = Modifier.heightIn(min = height), contentPadding = ButtonDefaults.contentPaddingFor(height)) {
        Text("Back", style = ButtonDefaults.textStyleFor(height))
      }
      Button(onClick = onSync, enabled = !state.syncing, modifier = Modifier.weight(1f).heightIn(min = height), contentPadding = ButtonDefaults.contentPaddingFor(height)) {
        if (state.syncing) LoadingIndicator(Modifier.size(28.dp)) else Text("Sync ${rows.size}", style = ButtonDefaults.textStyleFor(height))
      }
    }
  }
}

// --- Shared bits --------------------------------------------------------------------------------------------

/** Same grouped-card fill the Interest tab uses. */
@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** The 44dp shaped badge the Overview tiles and Interest rows lead with. */
@Composable
private fun Badge(shape: Shape, color: Color, content: @Composable () -> Unit) {
  Box(Modifier.size(44.dp).background(color, shape), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PasswordDialog(fileName: String, wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
  var password by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text("Password needed") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password") },
          singleLine = true,
          isError = wrong,
          supportingText = { if (wrong) Text("That password didn't work") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text("Unlock") } },
    dismissButton = { TextButton(onClick = onCancel) { Text("Skip file") } },
  )
}

@Composable
private fun Messages(state: UploadViewModel.State) {
  state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
  state.warnings.forEach {
    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
  }
}

private fun formatSize(bytes: Long): String =
  when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "${(bytes / 1024).coerceAtLeast(1)} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
  }

// --- Previews (rendered headlessly with `android preview render`) -----------------------------

private fun sampleFile(name: String, size: Long) = UploadViewModel.PickedFile(android.net.Uri.EMPTY, name, size)

private fun sampleTxn(hash: String, date: String, amount: Double, description: String) =
  ParsedTxn(null, hash, java.time.LocalDate.parse(date), description, amount, "credit", 0.0)

@Preview(name = "Bank + files", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PreviewFiles() =
  InterestTrackerTheme {
    UploadContent(
      state =
        UploadViewModel.State(
          bank = Banks.first(),
          files = listOf(sampleFile("pnb-jan-jun-2025.csv", 48_000), sampleFile("pnb-jul-dec-2025.csv", 51_200)),
          lastDates = mapOf("in-pnb" to "2025-12-01"),
          warnings = listOf("pnb-old.csv: Could not find the transaction header row."),
        ),
      onSelectBank = {}, onPickFiles = {}, onRemoveFile = {}, onAnalyze = {}, onSync = {}, onBackToFiles = {},
    )
  }

@Preview(name = "Review", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PreviewReview() =
  InterestTrackerTheme {
    UploadContent(
      state =
        UploadViewModel.State(
          bank = Banks.first(),
          preview =
            listOf(
              sampleTxn("a", "2025-12-02", 112.0, "Interest received from the bank from Sep 1, 2025 to Nov 30, 2025"),
              sampleTxn("b", "2025-09-04", 168.0, "Interest received from the bank from Jun 1, 2025 to Aug 31, 2025"),
              sampleTxn("c", "2024-12-01", 1182.5, "Interest received from the bank from Sep 1, 2024 to Nov 30, 2024"),
            ),
        ),
      onSelectBank = {}, onPickFiles = {}, onRemoveFile = {}, onAnalyze = {}, onSync = {}, onBackToFiles = {},
    )
  }

@Preview(name = "Nothing picked", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun PreviewEmpty() =
  InterestTrackerTheme {
    UploadContent(UploadViewModel.State(), {}, {}, {}, {}, {}, {})
  }
