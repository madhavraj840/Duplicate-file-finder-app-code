package com.bunkwise.duplicatefilefinder.presentation.home

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.bunkwise.duplicatefilefinder.Performance
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.BottomNavClearance
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.ListRow
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.ProHeaderBadge
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.SectionLabel
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.TintedIcon
import com.bunkwise.duplicatefilefinder.ui.Tints
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons

/** VERY_SIMILAR stays in the engine (old saved results use it) but isn't offered. */
private val Modes = listOf(
    Triple(ScanMode.EXACT, R.string.mode_exact, R.string.mode_exact_desc),
    Triple(ScanMode.SIMILAR, R.string.mode_similar, R.string.mode_similar_desc),
    Triple(ScanMode.DEEP, R.string.mode_deep, R.string.mode_deep_desc),
)

/**
 * The Scan tab, built around one object: a ring that is your storage, with the Scan button in its
 * middle. Everything below it only tunes that one action (what to look for, which files), and the
 * last result sits right under the ring as the one number that matters - how much can be freed.
 */
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.app_name), onBack = null) {
                ProHeaderBadge(isPremium = state.quota?.isPremium == true, onClick = onPremium)
            }
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                if (!state.hasPermission) {
                    AccessCard(accessDenied, onGrant)
                } else {
                    ScanOrb(state, onScan, onOpenRunning, Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.sm))
                    OrbCaption(state)
                    ResultCard(state, onOpenResults)

                    SectionLabel(stringResource(R.string.section_find))
                    ModePicker(state.selectedMode, onMode)
                    Text(
                        stringResource(Modes.first { it.first == state.selectedMode }.third),
                        style = MaterialTheme.typography.bodySmall,
                        color = n.mu,
                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
                    )

                    SectionLabel(stringResource(R.string.section_types))
                    TypeGrid(state, onType)
                }
                Spacer(Modifier.height(Spacing.lg))
                RowCard {
                    ListRow(
                        label = stringResource(R.string.recycle_bin),
                        caption = if (state.bin.isEmpty()) stringResource(R.string.bin_row_empty)
                        else pluralStringResource(
                            R.plurals.bin_row_summary, state.bin.size, state.bin.size,
                            Formatters.bytes(state.bin.sumOf { it.sizeBytes }),
                        ),
                        icon = AppIcons.Bin,
                        iconTint = Tints.red,
                        onClick = onOpenBin,
                    )
                }
            }
            Spacer(Modifier.height(BottomNavClearance + Spacing.lg))
        }
    }
}

/** The storage ring with the Scan button inside; while a scan runs, the ring is its progress. */
@Composable
private fun ScanOrb(state: HomeViewModel.HomeState, onScan: () -> Unit, onOpenRunning: () -> Unit, modifier: Modifier = Modifier) {
    val n = Df.c
    val running = state.runningPercent
    val canScan = state.selectedTypes.isNotEmpty()
    val target = when {
        running != null -> running / 100f
        else -> (state.storage?.usedPercent ?: 0) / 100f
    }
    val sweep by animateFloatAsState(target, tween(900), label = "orb")
    val full = (state.storage?.usedPercent ?: 0) >= 90 && running == null
    val motion = running != null && !Performance.lite
    val spin = rememberInfiniteTransition(label = "scanning")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val breath by spin.animateFloat(1f, 0.94f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breath")
    Box(modifier.size(224.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 12.dp.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(n.s2, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(
                if (full) n.offer else n.ac, -90f, 360f * sweep, false, Offset(inset, inset), arcSize,
                style = Stroke(w, cap = StrokeCap.Round),
            )
            if (motion) {
                // A short comet chasing round the ring: something is happening even when the
                // percent sits still (listing files has no percent at all).
                drawArc(
                    n.acTx.copy(alpha = 0.85f), angle - 90f, 46f, false, Offset(inset, inset), arcSize,
                    style = Stroke(w * 0.5f, cap = StrokeCap.Round),
                )
            }
        }
        val enabled = running != null || canScan
        Column(
            Modifier
                .size(166.dp)
                .graphicsLayer { if (motion) { scaleX = breath; scaleY = breath } }
                .clip(CircleShape)
                .background(if (enabled) n.ac else n.s2)
                .clickable(enabled = enabled, onClick = if (running != null) onOpenRunning else onScan)
                .semantics { role = Role.Button },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val fg = if (enabled) n.acTx else n.mu
            if (running != null) {
                Text("$running%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp), color = fg)
                Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.labelLarge, color = fg)
            } else {
                Icon(AppIcons.NavScan, null, tint = fg, modifier = Modifier.size(44.dp))
                Text(
                    stringResource(R.string.scan),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
                    color = fg,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** Under the ring: how full the phone is, then today's free allowance (or why Scan is off). */
@Composable
private fun OrbCaption(state: HomeViewModel.HomeState) {
    val n = Df.c
    val q = state.quota
    val running = state.running
    if (state.runningPercent != null) {
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.md, start = Spacing.gutter, end = Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val phase = when (running?.phase) {
                ScanPhase.HASHING -> R.string.phase_hashing
                ScanPhase.COMPARING -> R.string.phase_comparing
                ScanPhase.GROUPING, ScanPhase.DONE -> R.string.phase_grouping
                else -> R.string.phase_listing
            }
            val count = running?.filesScanned ?: 0
            Text(
                if (count > 0) stringResource(R.string.home_scan_phase_count, stringResource(phase), Formatters.count(count))
                else stringResource(phase),
                style = MaterialTheme.typography.titleSmall,
                color = n.tx,
            )
            Text(
                running?.currentPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "…",
                style = MaterialTheme.typography.bodySmall,
                color = n.mu,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        state.storage?.let { s ->
            Text(
                stringResource(R.string.home_storage_used, Formatters.bytes(s.usedBytes), Formatters.bytes(s.totalBytes)),
                style = MaterialTheme.typography.titleSmall,
                color = n.tx,
            )
        }
        val caption = when {
            state.selectedTypes.isEmpty() -> stringResource(R.string.types_none)
            q != null && !q.isPremium -> stringResource(R.string.quota_left, Formatters.count(q.remaining), Formatters.count(q.limit + q.bonus))
            else -> null
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = n.mu, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.xxs))
        }
    }
}

/** The last scan as one number: what can be freed. */
@Composable
private fun ResultCard(state: HomeViewModel.HomeState, onOpenResults: () -> Unit) {
    val n = Df.c
    val result = state.lastResult ?: return
    if (state.runningPercent != null) return
    val ago = DateUtils.getRelativeTimeSpanString(result.finishedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Spacer(Modifier.height(Spacing.xl))
    if (result.groupCount == 0) {
        RowCard {
            ListRow(
                label = stringResource(R.string.last_scan),
                caption = stringResource(R.string.last_scan_clean, ago),
                icon = AppIcons.StatusOk,
                iconTint = n.ok,
            )
        }
        return
    }
    AppCard(onClick = onOpenResults) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_last_scan_ago, ago), style = MaterialTheme.typography.bodySmall, color = n.mu)
                Text(
                    Formatters.bytes(result.wastedBytes),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp),
                    color = n.tx,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
                Text(
                    stringResource(R.string.home_can_free, Formatters.count(result.groupCount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = n.mu,
                )
            }
            TonalButton(stringResource(R.string.home_review), onClick = onOpenResults)
        }
    }
}

/** Three ways to compare, as one segmented control - a choice, not a list to read. */
@Composable
private fun ModePicker(selected: ScanMode, onMode: (ScanMode) -> Unit) {
    val n = Df.c
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.pill)
            .background(n.glass)
            .border(1.dp, n.line, Radius.pill)
            .padding(4.dp),
    ) {
        Modes.forEach { (mode, title, _) ->
            val on = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Radius.pill)
                    .background(if (on) n.ac else n.glass.copy(alpha = 0f))
                    .clickable { onMode(mode) }
                    .semantics { role = Role.RadioButton; this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) n.acTx else n.mu,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

/** File types as tiles: each its own colour and how much of it is on the phone; an included type lights up in its colour. */
@Composable
private fun TypeGrid(state: HomeViewModel.HomeState, onType: (FileCategory, Boolean) -> Unit) {
    val n = Df.c
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HomeViewModel.SCAN_TYPES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { type ->
                    val on = type in state.selectedTypes
                    val tint = CategoryUi.tint(type)
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp)
                            .clip(Radius.cardShape)
                            .background(if (on) tint.copy(alpha = 0.12f) else n.glass)
                            .border(if (on) 1.5.dp else 1.dp, if (on) tint else n.line, Radius.cardShape)
                            .clickable { onType(type, !on) }
                            .semantics { role = Role.Checkbox; this.selected = on }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TintedIcon(CategoryUi.vector(type), if (on) tint else n.mu, size = 36.dp)
                        Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                            Text(stringResource(CategoryUi.label(type)), style = MaterialTheme.typography.titleSmall, color = n.tx, maxLines = 1)
                            state.sizeByCategory[type]?.let {
                                Text(Formatters.bytes(it), style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 1)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccessCard(accessDenied: Boolean, onGrant: () -> Unit) {
    val n = Df.c
    SectionLabel(stringResource(R.string.access_title), top = Spacing.sm)
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
}
