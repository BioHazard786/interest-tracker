package com.biohazard786.interesttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biohazard786.interesttracker.data.DonationStatus
import com.biohazard786.interesttracker.data.ProjectionItem

@Composable
fun TransactionsScreen(vm: TransactionsViewModel, modifier: Modifier = Modifier) {
  val state by vm.state.collectAsState()
  val listState = rememberLazyListState()
  val pullState = rememberPullToRefreshState()
  var selected by remember { mutableStateOf<ProjectionItem?>(null) }

  // Infinite scroll: ask for the next page when within 5 rows of the end.
  LaunchedEffect(listState, state.items.size) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .collect { last -> if (last >= state.items.size - 5) vm.loadMore() }
  }

  Box(modifier.fillMaxSize()) {
    PullToRefreshBox(
      isRefreshing = state.refreshing,
      onRefresh = vm::reload,
      state = pullState,
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
        state.items.isEmpty() && state.error != null -> ErrorState(state.error, onRetry = vm::reload)
        state.items.isEmpty() ->
          Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
              "No transactions found. Upload statements from the web app to see them here.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        else ->
          LazyColumn(
            state = listState,
            // Bottom padding keeps the last rows clear of the floating toolbar.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
          ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
              val status = DonationStatus.from(item.status)
              SegmentedListItem(
                onClick = { selected = item },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = state.items.size),
                leadingContent = { StatusBadge(status) },
                supportingContent = { Text("${formatDate(item.transactionDate)} · ${bankName(item.bankId)}") },
                trailingContent = { Text(formatInr(item.amount), style = MaterialTheme.typography.titleMedium) },
              ) {
                Text(item.description ?: "Interest credit", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
            if (state.loadingMore) {
              item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) } }
            }
          }
      }
    }

    HorizontalFloatingToolbar(
      expanded = true,
      colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
      modifier = Modifier.align(Alignment.BottomCenter).offset(y = -FloatingToolbarDefaults.ScreenOffset),
    ) {
      DonationStatus.entries.forEach { status ->
        ToggleButton(checked = status in state.statuses, onCheckedChange = { vm.toggleStatus(status) }) {
          Text(status.label)
        }
      }
      IconButton(onClick = vm::toggleSort) {
        Icon(
          if (state.descending) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
          contentDescription = if (state.descending) "Newest first" else "Oldest first",
        )
      }
    }
  }

  selected?.let { DetailSheet(it, onDismiss = { selected = null }) }
}

@Composable
private fun StatusBadge(status: DonationStatus) {
  val scheme = MaterialTheme.colorScheme
  val (shape, container, content, icon) =
    when (status) {
      DonationStatus.NotDonated -> BadgeStyle(MaterialShapes.Cookie9Sided.toShape(), scheme.errorContainer, scheme.onErrorContainer, Icons.Filled.FavoriteBorder)
      DonationStatus.PartiallyDonated -> BadgeStyle(MaterialShapes.Clover4Leaf.toShape(), scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Filled.Favorite)
      DonationStatus.FullyDonated -> BadgeStyle(MaterialShapes.SoftBurst.toShape(), scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Filled.Check)
    }
  Box(Modifier.size(40.dp).background(container, shape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = status.label, tint = content, modifier = Modifier.size(20.dp))
  }
}

private data class BadgeStyle(val shape: Shape, val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun DetailSheet(item: ProjectionItem, onDismiss: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusBadge(DonationStatus.from(item.status))
        Column {
          Text(formatInr(item.amount), style = MaterialTheme.typography.headlineSmallEmphasized)
          Text(DonationStatus.from(item.status).label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      DetailRow("Description", item.description ?: "Interest credit")
      DetailRow("Bank", bankName(item.bankId))
      DetailRow("Date", formatDate(item.transactionDate))
      DetailRow("Donated", formatInr(item.donatedAmount))
      DetailRow("Remaining", formatInr(item.remainingAmount))
      item.donationAt?.let { DetailRow("Last donation", formatDate(it)) }
      item.transactionId?.let { DetailRow("Transaction ID", it) }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, modifier = Modifier.padding(start = 24.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
  }
}
