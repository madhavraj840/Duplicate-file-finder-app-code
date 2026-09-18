package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import kotlinx.coroutines.launch

/**
 * Full-screen overlay to pick exactly which copies in a group to remove
 * (DESIGN §3.5 Preview). Selection is applied live to the shared selection store,
 * so returning to Cleanup already reflects the choices. The keeper shows a "Best
 * copy" badge and the last surviving copy can't be marked (keep-one invariant).
 */
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private lateinit var adapter: PreviewFileAdapter
    private var groupId: Long = -1L

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
        binding.doneButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Re-render whenever the shared selection changes.
                selection.state.collect { render(group) }
            }
        }
    }

    private fun render(group: DuplicateGroup) {
        // Keeper first, then the rest — makes the "kept by default" copy obvious.
        val ordered = group.files.sortedByDescending { it.id == group.keeperId }
        adapter.submitList(ordered.map {
            PreviewFileAdapter.Row(
                file = it,
                isKeeper = it.id == group.keeperId,
                selected = selection.isFileSelected(it.id)
            )
        })
        val marked = group.files.filter { selection.isFileSelected(it.id) }
        binding.markedSummary.text = getString(
            R.string.n_marked,
            Formatters.count(marked.size), Formatters.bytes(marked.sumOf { it.sizeBytes })
        )
    }

    private fun onToggle(file: FileItem) {
        if (!selection.toggleFile(groupId, file.id)) {
            Toast.makeText(requireContext(), R.string.keep_one_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFile(file: FileItem) = FileOpener.open(requireContext(), file.path, file.mime)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
