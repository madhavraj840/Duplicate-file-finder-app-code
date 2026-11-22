package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.databinding.ItemGroupCardBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel.GroupUi
import com.google.android.material.checkbox.MaterialCheckBox
import java.io.File

class GroupAdapter(
    private val onToggle: (groupId: Long) -> Unit,
    private val onOpen: (groupId: Long) -> Unit
) : ListAdapter<GroupUi, GroupAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemGroupCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemGroupCardBinding) : RecyclerView.ViewHolder(b.root) {
        private val thumbs = listOf(b.thumb0, b.thumb1, b.thumb2)

        fun bind(item: GroupUi) {
            val g = item.group
            val ctx = b.root.context
            val keeper = SharedSelectionViewModel.keeperOf(g) ?: g.files.first()

            b.groupTitle.text = keeper.displayName
            val freeable = Formatters.bytes(g.wastedBytes)
            b.groupSubtitle.text = g.evidence?.let {
                ctx.getString(R.string.group_sub_similar, g.fileCount, (it.fusedScore * 100).toInt(), freeable)
            } ?: ctx.getString(R.string.group_sub_exact, g.fileCount, freeable)

            // Checked = all extra copies marked; partial = some marked.
            b.groupCheck.checkedState = when {
                item.markedCount == 0 -> MaterialCheckBox.STATE_UNCHECKED
                item.markedCount >= g.fileCount - 1 -> MaterialCheckBox.STATE_CHECKED
                else -> MaterialCheckBox.STATE_INDETERMINATE
            }
            b.groupCheck.contentDescription = ctx.getString(R.string.cd_select_group, keeper.displayName)
            b.groupCheck.setOnClickListener { onToggle(g.id) }
            b.root.setOnClickListener { onOpen(g.id) }

            // Thumbnails only where they help tell copies apart: photos and videos.
            val visual = CategoryUi.isVisualMedia(g.category)
            b.thumbRow.isVisible = visual
            if (visual) {
                thumbs.forEachIndexed { i, view -> bindThumb(view, g.files.getOrNull(i)?.path) }
                val extra = g.fileCount - thumbs.size
                b.moreCount.isVisible = extra > 0
                b.moreCount.text = ctx.getString(R.string.plus_more, extra)
            }
        }

        private fun bindThumb(view: ImageView, path: String?) {
            view.isVisible = path != null
            if (path == null) Glide.with(view).clear(view)
            else Glide.with(view).load(File(path)).centerCrop().into(view)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<GroupUi>() {
            override fun areItemsTheSame(a: GroupUi, b: GroupUi) = a.group.id == b.group.id
            override fun areContentsTheSame(a: GroupUi, b: GroupUi) = a == b
        }
    }
}
