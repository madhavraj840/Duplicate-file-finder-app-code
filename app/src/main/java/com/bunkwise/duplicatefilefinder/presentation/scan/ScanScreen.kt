package com.bunkwise.duplicatefilefinder.presentation.scan

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.Performance
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanPhase
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanProgress
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.Mono
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop

@Composable
fun ScanScreen(
    progress: ScanProgress,
    scope: String,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    val n = Df.c
    val listing = progress.phase == ScanPhase.ENUMERATING && progress.percent <= 5
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.scanning), onBack = onBack, subtitle = scope)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.gutter)) {
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    if (listing) "—" else "${progress.percent}%",
                    style = MaterialTheme.typography.displayLarge,
                    color = n.tx,
                )
                Text(
                    stringResource(
                        when (progress.phase) {
                            ScanPhase.ENUMERATING -> R.string.phase_listing
                            ScanPhase.HASHING -> R.string.phase_hashing
                            ScanPhase.COMPARING -> R.string.phase_comparing
                            ScanPhase.GROUPING, ScanPhase.DONE -> R.string.phase_grouping
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = n.mu,
                )
                Spacer(Modifier.height(Spacing.lg))
                Steps(progress.phase)
                Spacer(Modifier.height(Spacing.lg))
                val bar = Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill)
                if (listing && !Performance.lite) {
                    LinearProgressIndicator(modifier = bar, color = n.ac, trackColor = n.s2)
                } else {
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = bar,
                        color = n.ac,
                        trackColor = n.s2,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                AppCard(contentPadding = Spacing.lg) {
                    Figure(stringResource(R.string.files_checked), Formatters.count(progress.filesScanned))
                    RowDivider(start = 0.dp)
                    Figure(stringResource(R.string.duplicates_found), Formatters.count(progress.duplicatesFound))
                    RowDivider(start = 0.dp)
                    Figure(stringResource(R.string.time_elapsed), Formatters.duration(progress.elapsedMs))
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    progress.currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = n.mu,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PrimaryButton(stringResource(R.string.run_in_background), onClick = onBack, modifier = Modifier.fillMaxWidth())
                TonalButton(
                    stringResource(R.string.stop),
                    onClick = onStop,
                    height = 52.dp,
                    contentColor = n.danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The scan's three steps: done ones ticked, the running one spinning, the rest waiting. */
@Composable
private fun Steps(phase: ScanPhase) {
    val n = Df.c
    val current = when (phase) {
        ScanPhase.ENUMERATING -> 0
        ScanPhase.HASHING, ScanPhase.COMPARING -> 1
        ScanPhase.GROUPING -> 2
        ScanPhase.DONE -> 3
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(R.string.step_find, R.string.step_compare, R.string.step_group).forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when {
                        i < current -> Icon(AppIcons.StatusOk, null, tint = n.ok, modifier = Modifier.size(22.dp))
                        i == current && !Performance.lite ->
                            CircularProgressIndicator(Modifier.size(18.dp), color = n.ac, strokeWidth = 2.dp)
                        else -> Box(
                            Modifier.size(14.dp).border(2.dp, if (i == current) n.ac else n.line, CircleShape),
                        )
                    }
                }
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i > current) n.mu else n.tx,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    val n = Df.c
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = n.mu, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono), color = n.tx)
    }
}
