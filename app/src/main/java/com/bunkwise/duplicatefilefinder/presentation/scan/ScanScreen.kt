package com.bunkwise.duplicatefilefinder.presentation.scan

import androidx.compose.foundation.layout.Arrangement
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

@Composable
private fun Figure(label: String, value: String) {
    val n = Df.c
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = n.mu, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Mono), color = n.tx)
    }
}
