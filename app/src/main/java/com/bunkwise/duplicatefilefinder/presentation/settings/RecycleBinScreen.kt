package com.bunkwise.duplicatefilefinder.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.ui.Df
import com.bunkwise.duplicatefilefinder.ui.DfCheckbox
import com.bunkwise.duplicatefilefinder.ui.EmptyState
import com.bunkwise.duplicatefilefinder.ui.InfoNote
import com.bunkwise.duplicatefilefinder.ui.PrimaryButton
import com.bunkwise.duplicatefilefinder.ui.Radius
import com.bunkwise.duplicatefilefinder.ui.RowCard
import com.bunkwise.duplicatefilefinder.ui.RowDivider
import com.bunkwise.duplicatefilefinder.ui.ScreenHeader
import com.bunkwise.duplicatefilefinder.ui.Spacing
import com.bunkwise.duplicatefilefinder.ui.Thumb
import com.bunkwise.duplicatefilefinder.ui.TonalButton
import com.bunkwise.duplicatefilefinder.ui.glassBackdrop
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import java.io.File

data class BinRow(val entry: BinEntry, val filePath: String, val selected: Boolean)

@Composable
fun RecycleBinScreen(
    rows: List<BinRow>,
    selectionMode: Boolean,
    allSelected: Boolean,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onOpen: (BinEntry) -> Unit,
    onHeaderAction: () -> Unit,
    onRestore: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val n = Df.c
    Box(Modifier.fillMaxSize().glassBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            val empty = rows.isEmpty()
            ScreenHeader(
                stringResource(R.string.recycle_bin),
                onBack = onBack,
                subtitle = if (empty) null else pluralStringResource(
                    R.plurals.bin_summary, rows.size, rows.size, Formatters.bytes(rows.sumOf { it.entry.sizeBytes }),
                ),
            ) {
                if (!empty) {
                    Text(
                        stringResource(
                            when {
                                !selectionMode -> R.string.empty_bin
                                allSelected -> R.string.clear_selection
                                else -> R.string.select_all
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectionMode) n.acInk else n.danger,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(Radius.pill)
                            .clickable(onClick = onHeaderAction)
                            .padding(horizontal = Spacing.md, vertical = 14.dp),
                    )
                }
            }
            if (empty) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(AppIcons.Bin, stringResource(R.string.bin_empty_title), stringResource(R.string.bin_empty_body))
                }
                return@Column
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { InfoNote(AppIcons.Info, stringResource(R.string.bin_note)) }
                item {
                    RowCard {
                        rows.forEachIndexed { i, row ->
                            if (i > 0) RowDivider()
                            BinItem(row, selectionMode, onToggle, onOpen)
                        }
                    }
                }
            }
            if (selectionMode) {
                Column(Modifier.padding(Spacing.gutter)) {
                    Text(
                        stringResource(R.string.n_selected, rows.count { it.selected }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = n.mu,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TonalButton(stringResource(R.string.delete_permanently), onClick = onDeleteSelected, modifier = Modifier.weight(1f))
                        PrimaryButton(stringResource(R.string.restore), onClick = onRestore, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinItem(row: BinRow, selectionMode: Boolean, onToggle: (Long) -> Unit, onOpen: (BinEntry) -> Unit) {
    val n = Df.c
    val entry = row.entry
    val days = entry.daysLeft(System.currentTimeMillis())
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggle(entry.id) else onOpen(entry) },
                onLongClick = { onToggle(entry.id) },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(row.filePath, binCategory(entry.displayName), 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(entry.displayName, style = MaterialTheme.typography.titleSmall, color = n.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(File(entry.originalPath).parent ?: "", style = MaterialTheme.typography.bodySmall, color = n.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.file_meta, Formatters.bytes(entry.sizeBytes), pluralStringResource(R.plurals.days_left, days, days)),
                style = MaterialTheme.typography.bodySmall,
                color = n.mu,
            )
        }
        DfCheckbox(row.selected, { onToggle(entry.id) }, contentDescription = entry.displayName)
    }
}

/** Bin files keep their name but not their MediaStore type; guess it from the extension. */
private fun binCategory(name: String): FileCategory = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> FileCategory.IMAGES
    "mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v" -> FileCategory.VIDEOS
    "mp3", "wav", "aac", "ogg", "flac", "m4a" -> FileCategory.AUDIO
    "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "md" -> FileCategory.DOCUMENTS
    else -> FileCategory.OTHER
}
