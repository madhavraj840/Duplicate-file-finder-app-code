package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.ui.AppCard
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.DfCheckbox
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.Tag
import com.bunkwise.duplicatefilefinder.ui.Thumb
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import java.io.File
import java.util.Date

data class PreviewRow(val file: FileItem, val isKeeper: Boolean, val selected: Boolean, val exists: Boolean)

@Composable
fun PreviewScreen(
    title: String,
    rows: List<PreviewRow>,
    markedSummary: String,
    onBack: () -> Unit,
    onToggle: (FileItem) -> Unit,
    onOpen: (FileItem) -> Unit,
) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, onBack = onBack, subtitle = markedSummary)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { InfoNote(AppIcons.Info, stringResource(R.string.group_hint)) }
                items(rows, key = { it.file.id }) { row -> FileCard(row, onToggle, onOpen) }
            }
        }
    }
}

@Composable
private fun FileCard(row: PreviewRow, onToggle: (FileItem) -> Unit, onOpen: (FileItem) -> Unit) {
    val n = Df.c
    val context = LocalContext.current
    val file = row.file
    AppCard(
        onClick = if (row.exists) ({ onToggle(file) }) else null,
        contentPadding = Spacing.md,
        modifier = Modifier.alpha(if (row.exists) 1f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val openLabel = stringResource(R.string.cd_open_file, file.displayName)
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = row.exists) { onOpen(file) }
                    .semantics { onClick(label = openLabel) { onOpen(file); true } },
            ) {
                Thumb(file.path, if (row.exists) file.category else com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory.OTHER, 72.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                if (row.isKeeper) {
                    Tag(stringResource(R.string.recommended).uppercase(), n.okSoft, n.ok, Modifier.padding(bottom = Spacing.xs))
                }
                Text(file.displayName, style = MaterialTheme.typography.titleSmall, color = n.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(File(file.path).parent ?: "", style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (row.exists) stringResource(
                        R.string.file_meta, Formatters.bytes(file.sizeBytes),
                        DateFormat.getMediumDateFormat(context).format(Date(file.modifiedMs)),
                    ) else stringResource(R.string.file_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.exists) n.mu else n.offer,
                )
            }
            if (row.exists) DfCheckbox(row.selected, { onToggle(file) })
        }
    }
}
