package com.bunkwise.duplicatefilefinder.presentation.reviewdelete

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.databinding.FragmentReviewdeleteBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemFileRowBinding
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.presentation.common.TreeUris
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.service.DeleteForegroundService
import kotlinx.coroutines.launch
import java.io.File

/** The last stop before files are removed: exactly which files, where they go, and what stays. */
class ReviewDeleteFragment : Fragment() {

    private var _binding: FragmentReviewdeleteBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private val adapter = FileRowAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewdeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.proceedButton.setOnClickListener { proceed() }
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect(::render)
            }
        }
    }

    private fun render(state: SharedSelectionViewModel.CleanupState) {
        val files = selection.selectedFiles()
        if (files.isEmpty()) { findNavController().popBackStack(); return }
        val total = Formatters.bytes(files.sumOf { it.sizeBytes })
        val folder = state.archiveTreeUri?.let(TreeUris::displayName)

        if (state.archiveMode) {
            binding.headline.text = resources.getQuantityString(R.plurals.review_head_archive, files.size, files.size)
            binding.note.text = getString(R.string.review_note_archive, total, folder ?: "")
            binding.proceedButton.setText(R.string.review_confirm_archive)
        } else {
            binding.headline.text = resources.getQuantityString(R.plurals.review_head_bin, files.size, files.size)
            binding.note.text = getString(R.string.review_note_bin, total)
            binding.proceedButton.setText(R.string.review_confirm_bin)
        }
        binding.keptNote.text = getString(R.string.review_kept, Formatters.count(selection.keptFiles().size))

        // Blockers: things that would make every file fail. Fix them here, not after.
        val blocker = when {
            !Permissions.canRemoveFiles(requireContext()) -> R.string.review_need_access
            state.archiveMode && state.archiveTreeUri == null -> R.string.review_need_folder
            // A scan finishing mid-cleanup would bring the removed files back into the list.
            requireContext().appContainer.scanController.isRunning -> R.string.review_scan_running
            else -> null
        }
        binding.blocker.isVisible = blocker != null
        binding.proceedButton.isEnabled = blocker == null
        if (blocker != null) {
            binding.blockerText.setText(blocker)
            binding.blockerAction.isVisible = blocker != R.string.review_scan_running
            binding.blockerAction.setOnClickListener {
                when {
                    blocker != R.string.review_need_access -> (activity as? MainActivity)?.selectTab(R.id.settingsFragment)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Permissions.openAllFilesSettings(requireContext())
                    // Android 10 and below: the WRITE grant lives on the app's settings page.
                    else -> runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
                    }
                }
            }
        }
        adapter.submitList(files)
    }

    override fun onResume() {
        super.onResume()
        // Returning from the All-files settings screen: re-evaluate the blocker.
        if (_binding != null) render(selection.state.value)
    }

    private fun proceed() {
        val files = selection.selectedFiles()
        if (files.isEmpty()) return
        if (!DeleteForegroundService.start(requireContext(), files, selection.state.value.archiveMode)) return
        findNavController().navigate(
            R.id.deleteFragment, null,
            navOptions { popUpTo(R.id.reviewDeleteFragment) { inclusive = true } }
        )
    }

    override fun onDestroyView() {
        binding.fileList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private class FileRowAdapter : ListAdapter<FileItem, FileRowAdapter.VH>(DIFF) {
        class VH(val b: ItemFileRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemFileRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = getItem(position)
            holder.b.name.text = f.displayName
            holder.b.folder.text = File(f.path).parent ?: ""
            holder.b.size.text = Formatters.bytes(f.sizeBytes)
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<FileItem>() {
                override fun areItemsTheSame(a: FileItem, b: FileItem) = a.id == b.id
                override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
            }
        }
    }
}
