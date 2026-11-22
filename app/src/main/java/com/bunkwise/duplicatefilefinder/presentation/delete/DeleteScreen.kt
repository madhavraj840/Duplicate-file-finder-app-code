package com.bunkwise.duplicatefilefinder.presentation.delete

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.Mono
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop

/** Either a running cleanup or its outcome; all copy is resolved by the Fragment. */
sealed interface DeleteUi {
    val title: String
    data class Running(override val title: String, val percent: Int, val count: String, val current: String, val note: String) : DeleteUi
    data class Done(
        override val title: String,
        val body: String,
        val undoLabel: String?,
        val skipped: String?,
        val doneLabel: String,
        val busy: Boolean,
    ) : DeleteUi
}

@Composable
fun DeleteScreen(ui: DeleteUi, onUndo: () -> Unit, onDone: () -> Unit) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize().padding(Spacing.gutter)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(Spacing.xxl))
                Text(ui.title, style = MaterialTheme.typography.headlineMedium, color = n.tx)
                Spacer(Modifier.height(Spacing.lg))
                when (ui) {
                    is DeleteUi.Running -> {
                        Text("${ui.percent}%", style = MaterialTheme.typography.displayMedium.copy(fontFamily = Mono), color = n.tx)
                        Spacer(Modifier.height(Spacing.md))
                        LinearProgressIndicator(
                            progress = { ui.percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = n.ac,
                            trackColor = n.s2,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(ui.count, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono), color = n.mu)
                        Text(ui.current, style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(Spacing.xl))
                        Text(ui.note, style = MaterialTheme.typography.bodyMedium, color = n.mu)
                    }
                    is DeleteUi.Done -> {
                        Text(ui.body, style = MaterialTheme.typography.bodyLarge, color = n.mu)
                        if (ui.skipped != null) {
                            Spacer(Modifier.height(Spacing.lg))
                            AppCard { Text(ui.skipped, style = MaterialTheme.typography.bodyMedium, color = n.tx) }
                        }
                    }
                }
            }
            if (ui is DeleteUi.Done) {
                if (ui.undoLabel != null) {
                    TonalButton(ui.undoLabel, onClick = onUndo, enabled = !ui.busy, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.sm))
                }
                PrimaryButton(ui.doneLabel, onClick = onDone, enabled = !ui.busy, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
