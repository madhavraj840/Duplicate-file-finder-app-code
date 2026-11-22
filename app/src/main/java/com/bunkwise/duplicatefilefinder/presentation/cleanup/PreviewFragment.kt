package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.DuplicateGroup
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.databinding.FragmentPreviewBinding
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All copies in one group. Marks apply live to the shared selection, so going back
 * to Duplicates already reflects them. The last unmarked copy can't be marked.
 */
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private lateinit var adapter: PreviewFileAdapter
    private var groupId: Long = -1L
    /** Which copies still exist on disk; checked once when the screen opens. */
    private var existing: Set<Long> = emptySet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        groupId = selection.previewGroupId
        val group = selection.groupById(groupId)
        if (group == null) {
            findNavController().popBackStack()
            return
        }

        adapter = PreviewFileAdapter(onToggle = ::onToggle, onOpen = ::openFile)
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.title.text = getString(R.string.copies_title, group.fileCount)

        viewLifecycleOwner.lifecycleScope.launch {
            // Results can be days old; show copies that were deleted elsewhere as missing.
            existing = withContext(Dispatchers.IO) {
                group.files.filter { File(it.path).exists() }.mapTo(HashSet()) { it.id }
            }
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect { render(selection.groupById(groupId) ?: group) }
            }
        }
    }

    private fun render(group: DuplicateGroup) {
        // Recommended copy first, so the default choice is obvious.
        val ordered = group.files.sortedByDescending { it.id == group.keeperId }
        adapter.submitList(ordered.map {
            PreviewFileAdapter.Row(
                file = it,
                isKeeper = it.id == group.keeperId,
                selected = selection.isFileSelected(it.id),
                exists = it.id in existing
            )
        })
        val marked = group.files.filter { selection.isFileSelected(it.id) }
        binding.markedSummary.text = getString(
            R.string.marked_summary, Formatters.count(marked.size), Formatters.bytes(marked.sumOf { it.sizeBytes })
        )
    }

    private fun onToggle(file: FileItem) {
        if (!selection.toggleFile(groupId, file.id)) {
            Snackbar.make(binding.root, R.string.keep_one, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openFile(file: FileItem) = FileOpener.open(requireContext(), file.path, file.mime)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
