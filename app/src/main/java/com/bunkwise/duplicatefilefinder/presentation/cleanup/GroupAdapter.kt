package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.databinding.ItemGroupCardBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.GroupUi
import java.io.File

class GroupAdapter(
    private val onToggle: (groupId: Long) -> Unit,
    private val onPreview: (groupId: Long) -> Unit,
    private val onOpenFile: (FileItem) -> Unit
) : ListAdapter<GroupUi, GroupAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGroupCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemGroupCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: GroupUi) {
            val g = item.group
            val ctx = b.root.context

            val confidenceSuffix = g.evidence?.let {
                " • " + ctx.getString(R.string.match_pill, (it.fusedScore * 100).toInt())
            } ?: ""
            b.groupHeader.text = ctx.getString(
                R.string.group_header,
                g.fileCount.toString(), Formatters.bytes(g.totalBytes), g.duplicateCount.toString()
            ) + confidenceSuffix

            b.typeLabel.setText(CategoryUi.label(g.category))
            b.dupCount.text = ctx.getString(R.string.n_duplicates, g.duplicateCount.toString())
            b.typeIcon.setImageResource(CategoryUi.icon(g.category))
            b.typeIcon.setColorFilter(ContextCompat.getColor(ctx, CategoryUi.color(g.category)))

            // Thumbnails: first 3 files; the 3rd shows "+N" when the group is larger.
            // Tapping a thumbnail opens that file so users can verify it.
            val thumbs = listOf(b.thumb0, b.thumb1, b.thumb2)
            for (i in thumbs.indices) {
                val file = g.files.getOrNull(i)
                bindThumb(thumbs[i], file, g.category)
                thumbs[i].setOnClickListener { file?.let(onOpenFile) }
            }
            val extra = g.fileCount - 3
            b.moreOverlay.isVisible = extra > 0
            if (extra > 0) b.moreOverlay.text = ctx.getString(R.string.plus_more, extra)

            // Per-group selection (guarantees "keep at least one" — keeper never marked).
            if (item.selected) {
                b.groupCheck.setImageResource(R.drawable.ic_check_box)
                b.groupCheck.background = null
            } else {
                b.groupCheck.setImageDrawable(null)
                b.groupCheck.setBackgroundResource(R.drawable.bg_checkbox_empty)
            }

            b.groupCheck.setOnClickListener { onToggle(g.id) }
            b.selectAllButton.setOnClickListener { onToggle(g.id) }
            b.previewButton.setOnClickListener { onPreview(g.id) }
        }

        private fun bindThumb(view: ImageView, file: FileItem?, category: com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory) {
            if (file == null) {
                view.visibility = View.INVISIBLE
                return
            }
            view.visibility = View.VISIBLE
            if (CategoryUi.isVisualMedia(file.category) || CategoryUi.isVisualMedia(category)) {
                view.clearColorFilter()
                Glide.with(view).load(File(file.path))
                    .centerCrop().placeholder(R.drawable.bg_thumb).into(view)
            } else {
                Glide.with(view).clear(view)
                view.setImageResource(CategoryUi.icon(file.category))
                view.setColorFilter(ContextCompat.getColor(view.context, R.color.text_tertiary))
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<GroupUi>() {
            override fun areItemsTheSame(a: GroupUi, b: GroupUi) = a.group.id == b.group.id
            override fun areContentsTheSame(a: GroupUi, b: GroupUi) = a == b
        }
    }
}
