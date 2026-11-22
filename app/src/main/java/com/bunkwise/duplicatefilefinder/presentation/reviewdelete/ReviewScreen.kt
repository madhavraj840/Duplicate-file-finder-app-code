package com.bunkwise.duplicatefilefinder.presentation.reviewdelete

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.IconBox
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.Mono
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.Thumb
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import java.io.File

/** What the Review screen shows; built by the Fragment from the shared selection. */
data class ReviewUi(
    val files: List<FileItem>,
    val archive: Boolean,
    val headline: String,
    val note: String,
    val kept: String,
    val confirm: String,
    /** A reason every file would fail, or null. */
    val blocker: String?,
    val blockerFixable: Boolean,
)

@Composable
fun ReviewScreen(ui: ReviewUi, onBack: () -> Unit, onFixBlocker: () -> Unit, onConfirm: () -> Unit) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.review_title), onBack = onBack)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBox(if (ui.archive) AppIcons.Archive else AppIcons.Bin, active = true)
                            Text(
                                ui.headline,
                                style = MaterialTheme.typography.headlineSmall,
                                color = n.tx,
                                modifier = Modifier.padding(start = Spacing.md),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Text(ui.note, style = MaterialTheme.typography.bodyMedium, color = n.mu)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(ui.kept, style = MaterialTheme.typography.bodyMedium, color = n.ok)
                    }
                }
                if (ui.blocker != null) {
                    item {
                        Column {
                            InfoNote(AppIcons.Warning, ui.blocker, accent = true)
                            if (ui.blockerFixable) {
                                Spacer(Modifier.height(Spacing.sm))
                                TonalButton(stringResource(R.string.open_settings), onClick = onFixBlocker)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.sm)) }
                items(ui.files, key = { it.id }) { f -> FileRow(f) }
            }
            PrimaryButton(
                ui.confirm,
                onClick = onConfirm,
                enabled = ui.blocker == null,
                modifier = Modifier.fillMaxWidth().padding(Spacing.gutter),
            )
        }
    }
}

@Composable
private fun FileRow(f: FileItem) {
    val n = Df.c
    AppCard(contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(f.path, f.category, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(f.displayName, style = MaterialTheme.typography.titleSmall, color = n.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(File(f.path).parent ?: "", style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Formatters.bytes(f.sizeBytes), style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = n.mu)
        }
    }
}
