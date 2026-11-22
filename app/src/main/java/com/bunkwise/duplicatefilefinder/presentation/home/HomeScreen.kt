package com.bunkwise.duplicatefilefinder.presentation.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.BottomNavClearance
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.DfCheckbox
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.ListRow
import com.bunkwise.duplicatefilefinder.ui.Mono
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.ProHeaderBadge
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.SectionLabel
import com.bunkwise.duplicatefilefinder.ui.SelectableRow
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/** VERY_SIMILAR stays in the engine (old saved results use it) but isn't offered. */
private val Modes = listOf(
    Triple(ScanMode.EXACT, R.string.mode_exact, R.string.mode_exact_desc),
    Triple(ScanMode.SIMILAR, R.string.mode_similar, R.string.mode_similar_desc),
    Triple(ScanMode.DEEP, R.string.mode_deep, R.string.mode_deep_desc),
)

@Composable
fun HomeScreen(
    state: HomeViewModel.HomeState,
    accessDenied: Boolean,
    onGrant: () -> Unit,
    onMode: (ScanMode) -> Unit,
    onType: (FileCategory, Boolean) -> Unit,
    onScan: () -> Unit,
    onOpenRunning: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenBin: () -> Unit,
    onPremium: () -> Unit,
) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.app_name), onBack = null) {
                ProHeaderBadge(isPremium = state.quota?.isPremium == true, onClick = onPremium)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter),
            ) {
                StorageCard(state)
                Spacer(Modifier.height(Spacing.md))
                RowCard {
                    val running = state.runningPercent
                    val result = state.lastResult
                    if (running != null) {
                        ListRow(
                            label = stringResource(R.string.scan_running, running),
                            icon = AppIcons.NavScan,
                            labelColor = n.acInk,
                            onClick = onOpenRunning,
                        )
                        RowDivider()
                    } else if (result != null) {
                        val ago = DateUtils.getRelativeTimeSpanString(
                            result.finishedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                        ListRow(
                            label = stringResource(R.string.last_scan),
                            caption = if (result.groupCount == 0) stringResource(R.string.last_scan_clean, ago)
                            else stringResource(
                                R.string.last_scan_summary,
                                Formatters.bytes(result.wastedBytes), Formatters.count(result.groupCount), ago,
                            ),
                            icon = AppIcons.NavDuplicates,
                            onClick = onOpenResults,
                        )
                        RowDivider()
                    }
                    ListRow(
                        label = stringResource(R.string.recycle_bin),
                        caption = if (state.bin.isEmpty()) stringResource(R.string.bin_row_empty)
                        else pluralStringResource(
                            R.plurals.bin_row_summary, state.bin.size, state.bin.size,
                            Formatters.bytes(state.bin.sumOf { it.sizeBytes }),
                        ),
                        icon = AppIcons.Bin,
                        onClick = onOpenBin,
                    )
                }

                if (!state.hasPermission) {
                    SectionLabel(stringResource(R.string.access_title))
                    AppCard {
                        Text(stringResource(R.string.access_body), style = MaterialTheme.typography.bodyMedium, color = n.mu)
                        if (accessDenied) {
                            Spacer(Modifier.height(Spacing.md))
                            InfoNote(AppIcons.Warning, stringResource(R.string.access_denied))
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        PrimaryButton(
                            stringResource(if (accessDenied) R.string.open_settings else R.string.access_allow),
                            onClick = onGrant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    SectionLabel(stringResource(R.string.section_find))
                    RowCard {
                        Modes.forEachIndexed { i, (mode, title, desc) ->
                            if (i > 0) RowDivider()
                            SelectableRow(
                                label = stringResource(title),
                                caption = stringResource(desc),
                                selected = mode == state.selectedMode,
                                onSelect = { onMode(mode) },
                            )
                        }
                    }
                    SectionLabel(stringResource(R.string.section_types))
                    RowCard {
                        HomeViewModel.SCAN_TYPES.forEachIndexed { i, type ->
                            if (i > 0) RowDivider()
                            val checked = type in state.selectedTypes
                            ListRow(
                                label = stringResource(CategoryUi.label(type)),
                                icon = CategoryUi.vector(type),
                                value = state.sizeByCategory[type]?.let(Formatters::bytes),
                                onClick = { onType(type, !checked) },
                                trailing = { DfCheckbox(checked, { onType(type, it) }) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }

            if (state.hasPermission) {
                val q = state.quota
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter)
                        .padding(top = Spacing.sm, bottom = BottomNavClearance),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val caption = when {
                        state.selectedTypes.isEmpty() -> stringResource(R.string.types_none)
                        q != null && !q.isPremium -> stringResource(
                            R.string.quota_left, Formatters.count(q.remaining), Formatters.count(q.limit + q.bonus),
                        )
                        else -> null
                    }
                    PrimaryButton(
                        stringResource(R.string.scan),
                        onClick = onScan,
                        enabled = state.selectedTypes.isNotEmpty() && state.runningPercent == null,
                        icon = AppIcons.NavScan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (caption != null) {
                        Text(
                            caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = n.mu,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(BottomNavClearance))
            }
        }
    }
}

@Composable
private fun StorageCard(state: HomeViewModel.HomeState) {
    val n = Df.c
    val s = state.storage ?: return
    AppCard {
        Text(stringResource(R.string.storage_label), style = MaterialTheme.typography.bodySmall, color = n.mu)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                Formatters.bytes(s.usedBytes),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono),
                color = n.tx,
            )
            Text(
                " / " + Formatters.bytes(s.totalBytes),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                color = n.mu,
                modifier = Modifier.padding(bottom = Spacing.xxs),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(
            progress = { s.usedPercent / 100f },
            color = if (s.usedPercent >= 90) n.offer else n.ac,
            trackColor = n.s2,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill),
        )
    }
}
