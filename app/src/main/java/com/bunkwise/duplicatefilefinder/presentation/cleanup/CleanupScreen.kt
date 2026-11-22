package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.CleanupState
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.GroupUi
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.SortMode
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.BottomNavClearance
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.DfCheckbox
import com.bunkwise.duplicatefilefinder.ui.EmptyState
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.Thumb
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

private val FilterTypes = listOf(
    FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO, FileCategory.DOCUMENTS, FileCategory.OTHER,
)

private fun sortLabel(mode: SortMode) = when (mode) {
    SortMode.SIZE -> R.string.sort_size
    SortMode.NAME -> R.string.sort_name
    SortMode.COPIES -> R.string.sort_count
}

@Composable
fun CleanupScreen(
    state: CleanupState,
    onToggleGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onCategory: (FileCategory) -> Unit,
    onSort: (SortMode) -> Unit,
    onQuery: (String) -> Unit,
    onSelectAll: () -> Unit,
    onReview: () -> Unit,
    onGoScan: () -> Unit,
    onPremium: () -> Unit,
) {
    val n = Df.c
    val result = state.result
    val hasGroups = result != null && result.groupCount > 0
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val closeSearch = { searchOpen = false; onQuery("") }
    BackHandler(enabled = searchOpen, onBack = closeSearch)

    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            if (searchOpen) {
                SearchBar(state.query, onQuery, closeSearch)
            } else {
                ScreenHeader(stringResource(R.string.duplicates_title), onBack = null) {
                    if (hasGroups) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(AppIcons.Search, stringResource(R.string.cd_search), tint = n.tx)
                        }
                    }
                }
            }

            val empty: Triple<Int, String, Boolean>? = when {
                result == null -> Triple(R.string.empty_no_scan_title, stringResource(R.string.empty_no_scan_body), true)
                result.groupCount == 0 -> Triple(R.string.empty_clean_title, stringResource(R.string.empty_clean_body), true)
                state.groups.isEmpty() && state.query.isNotBlank() ->
                    Triple(0, stringResource(R.string.empty_search, state.query.trim()), false)
                state.groups.isEmpty() -> Triple(0, stringResource(R.string.empty_filter), false)
                else -> null
            }
            if (empty != null) {
                if (hasGroups) Box(Modifier.padding(horizontal = Spacing.gutter)) { Controls(state, onCategory, onSort) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = AppIcons.NavDuplicates,
                        title = if (empty.first != 0) stringResource(empty.first) else "",
                        body = empty.second,
                        actionLabel = if (empty.third) stringResource(R.string.scan) else null,
                        onAction = if (empty.third) onGoScan else null,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (!searchOpen) item(key = "savings") { SavingsCard(state) }
                    item(key = "controls") { Controls(state, onCategory, onSort) }
                    items(state.groups, key = { it.group.id }) { ui ->
                        GroupCard(ui, onToggleGroup, onOpenGroup)
                    }
                }
            }

            if (hasGroups) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter)
                        .padding(top = Spacing.sm, bottom = BottomNavClearance),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.selected_summary, Formatters.count(state.markedFiles), Formatters.bytes(state.markedBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = n.mu,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                        )
                        Text(
                            stringResource(if (state.markedFiles > 0) R.string.clear_selection else R.string.select_all),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = n.acInk,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(Radius.pill)
                                .clickable(onClick = onSelectAll)
                                .padding(horizontal = Spacing.md, vertical = 14.dp),
                        )
                    }
                    PrimaryButton(
                        stringResource(R.string.review),
                        onClick = onReview,
                        enabled = state.markedFiles > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Spacer(Modifier.height(BottomNavClearance))
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val n = Df.c
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.gutter, end = Spacing.sm, top = Spacing.md, bottom = Spacing.xs)
            .height(52.dp)
            .clip(Radius.pill)
            .background(n.glass)
            .padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, tint = n.mu, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = n.mu)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = n.tx),
                cursorBrush = SolidColor(n.ac),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(R.string.cd_close_search), tint = n.tx) }
    }
}

/**
 * The one number (what can be freed) and where it is: a bar split by file type in each type's
 * colour, so "it's mostly videos" reads before any list does.
 */
@Composable
private fun SavingsCard(state: CleanupState) {
    val n = Df.c
    val result = state.result ?: return
    val byType = remember(result) {
        result.groups.groupBy { it.category }.mapValues { (_, gs) -> gs.sumOf { it.wastedBytes } }
            .filterValues { it > 0 }.entries.sortedByDescending { it.value }
    }
    val ago = DateUtils.getRelativeTimeSpanString(result.finishedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    AppCard {
        Text(stringResource(R.string.home_last_scan_ago, ago), style = MaterialTheme.typography.bodySmall, color = n.mu)
        Text(
            Formatters.bytes(result.wastedBytes),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.6).sp),
            color = n.tx,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        Text(stringResource(R.string.home_can_free, Formatters.count(result.groupCount)), style = MaterialTheme.typography.bodySmall, color = n.mu)
        if (byType.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.lg).height(10.dp).clip(Radius.pill),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                byType.forEach { (cat, bytes) ->
                    Box(Modifier.weight(bytes.toFloat().coerceAtLeast(result.wastedBytes / 60f)).fillMaxHeight().background(CategoryUi.tint(cat)))
                }
            }
            // A scrolling Row, not FlowRow: this Compose BOM's foundation ships a FlowRow the compiler can't link.
            Row(
                Modifier.padding(top = Spacing.md).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                byType.forEach { (cat, bytes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(CategoryUi.tint(cat)))
                        Text(
                            stringResource(R.string.legend_item, stringResource(CategoryUi.label(cat)), Formatters.bytes(bytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = n.mu,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    state: CleanupState,
    onCategory: (FileCategory) -> Unit,
    onSort: (SortMode) -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    val counts = state.typeCounts
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(listOf(FileCategory.ALL) + FilterTypes.filter { (counts[it] ?: 0) > 0 }) { cat ->
                TypeChip(
                    label = if (cat == FileCategory.ALL) stringResource(CategoryUi.label(cat))
                    else stringResource(R.string.filter_item, stringResource(CategoryUi.label(cat)), counts[cat] ?: 0),
                    tint = if (cat == FileCategory.ALL) Df.c.ac else CategoryUi.tint(cat),
                    selected = state.category == cat,
                    onClick = { onCategory(cat) },
                )
            }
        }
        Box(Modifier.padding(start = Spacing.sm)) {
            Pill(stringResource(sortLabel(state.sort)), AppIcons.Sort) { sortOpen = true }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(stringResource(sortLabel(mode))) }, onClick = { sortOpen = false; onSort(mode) })
                }
            }
        }
    }
}

/** A file-type filter: its colour dot, filled with that colour when chosen. */
@Composable
private fun TypeChip(label: String, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val n = Df.c
    Row(
        Modifier
            .height(40.dp)
            .clip(Radius.pill)
            .background(if (selected) tint else n.glass)
            .border(1.dp, if (selected) tint else n.line, Radius.pill)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!selected) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.size(Spacing.xs))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else n.tx, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val n = Df.c
    Row(
        Modifier
            .height(40.dp)
            .clip(Radius.pill)
            .background(n.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md - Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = n.mu, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = n.tx, maxLines = 1)
    }
}

@Composable
private fun GroupCard(ui: GroupUi, onToggle: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val n = Df.c
    val g = ui.group
    val keeper = SharedSelectionViewModel.keeperOf(g) ?: g.files.first()
    val freeable = Formatters.bytes(g.wastedBytes)
    val visual = CategoryUi.isVisualMedia(g.category)
    AppCard(onClick = { onOpen(g.id) }, contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Thumb(keeper.path, g.category, 72.dp)
                Text(
                    stringResource(R.string.keep_tag),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .clip(Radius.pill)
                        .background(n.ok)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(keeper.displayName, style = MaterialTheme.typography.titleSmall, color = n.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    g.evidence?.let { stringResource(R.string.group_sub_similar, g.fileCount, (it.fusedScore * 100).toInt(), freeable) }
                        ?: stringResource(R.string.group_sub_exact, g.fileCount, freeable),
                    style = MaterialTheme.typography.bodySmall,
                    color = n.mu,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                if (visual) {
                    val others = g.files.filter { it.id != keeper.id }
                    Row(
                        Modifier.padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        others.take(3).forEach { Thumb(it.path, g.category, 36.dp, Modifier.alpha(0.6f)) }
                        val extra = others.size - 3
                        if (extra > 0) Text(stringResource(R.string.plus_more, extra), style = MaterialTheme.typography.bodySmall, color = n.mu)
                    }
                }
            }
            DfCheckbox(
                checked = ui.markedCount >= g.fileCount - 1 && ui.markedCount > 0,
                partial = ui.markedCount > 0,
                onCheckedChange = { onToggle(g.id) },
                contentDescription = stringResource(R.string.cd_select_group, keeper.displayName),
            )
        }
    }
}
