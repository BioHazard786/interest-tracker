package com.biohazard786.interesttracker.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biohazard786.interesttracker.data.DonationStatus
import com.biohazard786.interesttracker.ui.theme.HeroNumeral
import com.biohazard786.interesttracker.ui.theme.Tabular

private data class BadgeStyle(val shape: Shape, val container: Color, val content: Color, val icon: ImageVector)

/** Built once per screen: a shape + colour + icon per status, so rows don't each rebuild them. */
@Composable
private fun rememberBadgeStyles(): Map<DonationStatus, BadgeStyle> {
  val scheme = MaterialTheme.colorScheme
  val pending = MaterialShapes.Cookie9Sided.toShape()
  val partial = MaterialShapes.Clover4Leaf.toShape()
  val done = MaterialShapes.SoftBurst.toShape()
  return remember(scheme, pending, partial, done) {
    mapOf(
      DonationStatus.NotDonated to BadgeStyle(pending, scheme.errorContainer, scheme.onErrorContainer, Icons.Filled.FavoriteBorder),
      DonationStatus.PartiallyDonated to BadgeStyle(partial, scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Filled.Favorite),
      DonationStatus.FullyDonated to BadgeStyle(done, scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Filled.Check),
    )
  }
}

@Composable
fun TransactionsScreen(vm: TransactionsViewModel, modifier: Modifier = Modifier) {
  val state by vm.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val pullState = rememberPullToRefreshState()
  val badges = rememberBadgeStyles()
  // Segments need a filled container to read as one grouped card per year.
  val rowColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  var selected by remember { mutableStateOf<TxRow?>(null) }

  // derivedStateOf: only flips when crossing the threshold, not on every scroll frame.
  val nearEnd by remember {
    derivedStateOf {
      val info = listState.layoutInfo
      info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
    }
  }
  LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

  Column(modifier.fillMaxSize()) {
    StatusFilter(state.filter, vm::setFilter)
    Box(Modifier.fillMaxWidth().height(4.dp)) {
      if (state.switching) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
    PullToRefreshBox(
      isRefreshing = state.refreshing,
      onRefresh = vm::reload,
      state = pullState,
      indicator = {
        PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = state.refreshing, modifier = Modifier.align(Alignment.TopCenter))
      },
    ) {
      when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        state.groups.isEmpty() && state.error != null -> ErrorState(state.error, onRetry = vm::reload)
        state.groups.isEmpty() ->
          Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
              if (state.filter == null) "No interest yet. Add a statement in the Upload tab to see it here."
              else "Nothing ${state.filter!!.label.lowercase()} right now.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        else ->
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().alpha(if (state.switching) 0.5f else 1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
          ) {
            state.groups.forEach { group ->
              stickyHeader(key = "year-${group.year}", contentType = "header") { YearHeader(group) }
              itemsIndexed(group.rows, key = { _, row -> row.item.id }, contentType = { _, _ -> "row" }) { index, row ->
                SegmentedListItem(
                  onClick = { selected = row },
                  shapes = ListItemDefaults.segmentedShapes(index = index, count = group.rows.size),
                  colors = rowColors,
                  leadingContent = { StatusBadge(badges.getValue(row.status), row.status.label) },
                  supportingContent = { Text(row.subtitle, maxLines = 1) },
                  trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                      Text(row.amount, style = MaterialTheme.typography.titleMediumEmphasized.merge(Tabular))
                      row.remaining?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium.merge(Tabular), color = MaterialTheme.colorScheme.tertiary)
                      }
                    }
                  },
                ) {
                  Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
            if (state.loadingMore) {
              item(contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
              }
            }
          }
      }
    }
  }

  selected?.let { DetailSheet(it, badges.getValue(it.status), onDismiss = { selected = null }) }
}

/** Expressive connected button group, single-select. */
@Composable
private fun StatusFilter(filter: DonationStatus?, onFilter: (DonationStatus?) -> Unit) {
  val options = listOf<Pair<DonationStatus?, String>>(null to "All") + DonationStatus.entries.map { it to it.label }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    options.forEachIndexed { index, (status, label) ->
      ToggleButton(
        checked = filter == status,
        onCheckedChange = { onFilter(status) },
        shapes =
          when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
          },
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
      ) {
        Text(label, maxLines = 1)
      }
    }
  }
}

@Composable
private fun YearHeader(group: YearGroup) {
  Row(
    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    Text(group.year, style = MaterialTheme.typography.displaySmall.merge(Tabular), modifier = Modifier.weight(1f))
    Text(group.summary, style = MaterialTheme.typography.labelLarge.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun StatusBadge(style: BadgeStyle, label: String, size: Int = 44) {
  Box(Modifier.size(size.dp).background(style.container, style.shape), contentAlignment = Alignment.Center) {
    Icon(style.icon, contentDescription = label, tint = style.content, modifier = Modifier.size((size / 2).dp))
  }
}

@Composable
private fun DetailSheet(row: TxRow, badge: BadgeStyle, onDismiss: () -> Unit) {
  val item = row.item
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(badge, row.status.label, size = 56)
        Spacer(Modifier.size(16.dp))
        Column {
          Text(row.status.label, style = MaterialTheme.typography.titleMediumEmphasized)
          Text(row.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(20.dp))
      Text(formatInr(item.amount), style = HeroNumeral.copy(fontSize = HeroNumeral.fontSize * 0.7f, lineHeight = HeroNumeral.lineHeight * 0.7f))
      Spacer(Modifier.height(20.dp))
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow("Donated", formatInr(item.donatedAmount))
        DetailRow("Remaining", formatInr(item.remainingAmount))
        DetailRow("Credited on", formatDate(item.transactionDate))
        item.donationAt?.let { DetailRow("Last donation", formatDate(it)) }
        DetailRow("Bank", bankName(item.bankId))
        item.transactionId?.let { DetailRow("Transaction ID", it) }
        Text(
          formatDescription(item.bankId, item.description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth()) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodyLargeEmphasized.merge(Tabular), maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}
