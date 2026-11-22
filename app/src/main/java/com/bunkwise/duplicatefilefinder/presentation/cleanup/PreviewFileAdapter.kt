package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.databinding.ItemPreviewFileBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.common.announceAsCheckable
import java.io.File
import java.util.Date

/** Rows in the group detail screen: one per copy, with where it lives and when it changed. */
class PreviewFileAdapter(
    private val onToggle: (FileItem) -> Unit,
    private val onOpen: (FileItem) -> Unit
) : ListAdapter<PreviewFileAdapter.Row, PreviewFileAdapter.VH>(DIFF) {

    data class Row(val file: FileItem, val isKeeper: Boolean, val selected: Boolean, val exists: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPreviewFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemPreviewFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row) {
            val ctx = b.root.context
            val file = row.file
            b.fileName.text = file.displayName
            b.folder.text = File(file.path).parent ?: ""
            b.fileMeta.text = if (row.exists) {
                ctx.getString(
                    R.string.file_meta, Formatters.bytes(file.sizeBytes),
                    DateFormat.getMediumDateFormat(ctx).format(Date(file.modifiedMs))
                )
            } else ctx.getString(R.string.file_missing)
            b.fileMeta.setTextColor(
                ContextCompat.getColor(ctx, if (row.exists) R.color.text_secondary else R.color.warning)
            )
            b.recommended.isVisible = row.isKeeper

            if (CategoryUi.isVisualMedia(file.category) && row.exists) {
                b.thumb.clearColorFilter()
                b.thumb.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(b.thumb).load(File(file.path)).centerCrop().into(b.thumb)
            } else {
                Glide.with(b.thumb).clear(b.thumb)
                b.thumb.scaleType = android.widget.ImageView.ScaleType.CENTER
                b.thumb.setImageResource(CategoryUi.icon(file.category))
                b.thumb.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            b.thumb.contentDescription = ctx.getString(R.string.cd_open_file, file.displayName)

            b.check.isChecked = row.selected
            b.check.isEnabled = row.exists
            b.root.isEnabled = row.exists
            b.root.announceAsCheckable(CheckBox::class.java.name, row.selected)
            b.root.setOnClickListener { onToggle(file) }
            b.thumb.setOnClickListener { if (row.exists) onOpen(file) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = a.file.id == b.file.id
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
