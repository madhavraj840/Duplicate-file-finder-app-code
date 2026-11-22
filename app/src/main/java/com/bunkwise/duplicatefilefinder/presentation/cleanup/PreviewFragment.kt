package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileItem
import com.bunkwise.duplicatefilefinder.presentation.common.FileOpener
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import android.widget.Toast
import com.bunkwise.duplicatefilefinder.ui.composeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All copies in one group. Marks apply live to the shared selection, so going back
 * to Duplicates already reflects them. The last unmarked copy can't be marked.
 */
class PreviewFragment : Fragment() {

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    private var groupId: Long = -1L
    /** Which copies still exist on disk; checked once when the screen opens. */
    private var existing by mutableStateOf<Set<Long>>(emptySet())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        groupId = selection.previewGroupId
        return composeScreen {
            // Re-read on every selection change so marks show live.
            val state by selection.state.collectAsStateWithLifecycle()
            val group = remember(state) { selection.groupById(groupId) } ?: return@composeScreen
            // Recommended copy first, so the default choice is obvious.
            val rows = group.files.sortedByDescending { it.id == group.keeperId }.map {
                PreviewRow(it, it.id == group.keeperId, selection.isFileSelected(it.id), it.id in existing)
            }
            val marked = group.files.filter { selection.isFileSelected(it.id) }
            PreviewScreen(
                title = stringResource(R.string.copies_title, group.fileCount),
                rows = rows,
                markedSummary = stringResource(
                    R.string.marked_summary, Formatters.count(marked.size), Formatters.bytes(marked.sumOf { it.sizeBytes }),
                ),
                onBack = { findNavController().popBackStack() },
                onToggle = ::onToggle,
                onOpen = ::openFile,
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val group = selection.groupById(groupId)
        if (group == null) {
            findNavController().popBackStack()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // Results can be days old; show copies that were deleted elsewhere as missing.
            existing = withContext(Dispatchers.IO) {
                group.files.filter { File(it.path).exists() }.mapTo(HashSet()) { it.id }
            }
        }
    }

    private fun onToggle(file: FileItem) {
        if (!selection.toggleFile(groupId, file.id)) {
            Toast.makeText(requireContext(), R.string.keep_one, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFile(file: FileItem) = FileOpener.open(requireContext(), file.path, file.mime)
}
