package com.bunkwise.duplicatefilefinder.presentation.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.BinEntry
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.databinding.ItemBinEntryBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import java.io.File

/**
 * Recycle-bin rows: thumbnail (photos/videos) or a type icon, where the file came
 * from and how long it stays. Tapping opens the file unless a selection is active.
 */
class BinAdapter(
    private val onToggle: (Long) -> Unit,
    private val onOpen: (BinEntry) -> Unit,
    private val selectionMode: () -> Boolean
) : ListAdapter<BinAdapter.Row, BinAdapter.VH>(DIFF) {

    data class Row(val entry: BinEntry, val filePath: String, val selected: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemBinEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemBinEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row) {
            val ctx = b.root.context
            val entry = row.entry
            b.binName.text = entry.displayName
            b.binFolder.text = File(entry.originalPath).parent ?: ""
            val days = entry.daysLeft(System.currentTimeMillis())
            b.binMeta.text = ctx.getString(
                R.string.file_meta, Formatters.bytes(entry.sizeBytes),
                ctx.resources.getQuantityString(R.plurals.days_left, days, days)
            )

            val category = categoryOf(entry.displayName)
            if (CategoryUi.isVisualMedia(category)) {
                b.thumb.clearColorFilter()
                b.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(b.thumb).load(File(row.filePath)).centerCrop().into(b.thumb)
            } else {
                Glide.with(b.thumb).clear(b.thumb)
                b.thumb.scaleType = ImageView.ScaleType.CENTER
                b.thumb.setImageResource(CategoryUi.icon(category))
                b.thumb.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            }

            b.check.isChecked = row.selected
            b.check.contentDescription = entry.displayName
            b.check.setOnClickListener { onToggle(entry.id) }
            b.root.setOnClickListener { if (selectionMode()) onToggle(entry.id) else onOpen(entry) }
            b.root.setOnLongClickListener { onToggle(entry.id); true }
        }
    }

    private fun categoryOf(name: String): FileCategory = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE_EXT -> FileCategory.IMAGES
        in VIDEO_EXT -> FileCategory.VIDEOS
        in AUDIO_EXT -> FileCategory.AUDIO
        in DOC_EXT -> FileCategory.DOCUMENTS
        else -> FileCategory.OTHER
    }

    private companion object {
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        val VIDEO_EXT = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v")
        val AUDIO_EXT = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a")
        val DOC_EXT = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "md")

        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = a.entry.id == b.entry.id
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
