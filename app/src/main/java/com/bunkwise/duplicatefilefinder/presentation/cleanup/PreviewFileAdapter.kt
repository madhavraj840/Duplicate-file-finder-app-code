package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.view.LayoutInflater
import android.view.ViewGroup
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
import java.io.File

/**
 * Rows in the Preview overlay: one per copy in a duplicate group, with a keeper
 * badge and a checkbox. Tapping a row marks/unmarks it for deletion; tapping the
 * thumbnail opens the file so the user can verify it with their own eyes.
 */
class PreviewFileAdapter(
    private val onToggle: (FileItem) -> Unit,
    private val onOpen: (FileItem) -> Unit
) : ListAdapter<PreviewFileAdapter.Row, PreviewFileAdapter.VH>(DIFF) {

    data class Row(val file: FileItem, val isKeeper: Boolean, val selected: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPreviewFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemPreviewFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row) {
            val ctx = b.root.context
            val file = row.file
            b.fileName.text = file.displayName
            val folder = File(file.path).parentFile?.name ?: ""
            b.fileMeta.text = "${Formatters.bytes(file.sizeBytes)} • $folder"
            b.keeperBadge.isVisible = row.isKeeper

            if (CategoryUi.isVisualMedia(file.category)) {
                b.thumb.clearColorFilter()
                Glide.with(b.thumb).load(File(file.path)).centerCrop()
                    .placeholder(R.drawable.bg_thumb).into(b.thumb)
            } else {
                Glide.with(b.thumb).clear(b.thumb)
                b.thumb.setImageResource(CategoryUi.icon(file.category))
                b.thumb.setColorFilter(ContextCompat.getColor(ctx, R.color.text_tertiary))
            }

            if (row.selected) {
                b.check.setImageResource(R.drawable.ic_check_box)
                b.check.background = null
            } else {
                b.check.setImageDrawable(null)
                b.check.setBackgroundResource(R.drawable.bg_checkbox_empty)
            }

            b.root.setOnClickListener { onToggle(file) }
            b.thumb.setOnClickListener { onOpen(file) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = a.file.id == b.file.id
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
