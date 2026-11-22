package com.bunkwise.duplicatefilefinder.presentation.delete

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.error.AppError
import com.bunkwise.duplicatefilefinder.databinding.FragmentDeleteBinding
import com.bunkwise.duplicatefilefinder.presentation.common.TreeUris
import com.bunkwise.duplicatefilefinder.service.DeleteProgress
import com.bunkwise.duplicatefilefinder.service.DeleteStatus
import kotlinx.coroutines.launch

/**
 * Progress and outcome of a cleanup. The work runs in a foreground service, so the
 * user may leave at any time; the result is also posted as a notification.
 */
class DeleteFragment : Fragment() {

    private var _binding: FragmentDeleteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeleteViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private val archiving get() = requireContext().appContainer.pendingArchive

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Restored after the process died: nothing is running any more.
        if (viewModel.status.value is DeleteStatus.Idle) {
            findNavController().popBackStack()
            return
        }
        binding.title.setText(if (archiving) R.string.removing_archive else R.string.removing_bin)
        binding.doneButton.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.progress.collect(::renderProgress) }
                launch { viewModel.status.collect(::handleStatus) }
            }
        }
    }

    private fun renderProgress(p: DeleteProgress) {
        val percent = if (p.total == 0) 0 else p.deleted * 100 / p.total
        binding.progress.setProgressCompat(percent, true)
        binding.progressCount.text = getString(R.string.progress_count, Formatters.count(p.deleted), Formatters.count(p.total))
        binding.currentName.text = p.currentName
    }

    private fun handleStatus(status: DeleteStatus) {
        when (status) {
            is DeleteStatus.Completed -> showCompleted(status)
            is DeleteStatus.Failed -> showFailed(status.error)
            else -> Unit
        }
    }

    private fun showCompleted(status: DeleteStatus.Completed) {
        val outcome = status.outcome
        showResultLayout()
        val freed = Formatters.bytes(outcome.freedBytes)
        if (status.archived) {
            binding.title.text = resources.getQuantityString(R.plurals.done_title_archive, outcome.deleted, outcome.deleted)
            viewLifecycleOwner.lifecycleScope.launch {
                val folder = viewModel.archiveFolderUri()?.let(TreeUris::displayName) ?: ""
                _binding?.resultBody?.text = getString(R.string.done_body_archive, freed, folder)
            }
        } else {
            binding.title.text = resources.getQuantityString(R.plurals.done_title_bin, outcome.deleted, outcome.deleted)
            binding.resultBody.text = getString(R.string.done_body_bin, freed)
        }
        binding.undoButton.isVisible = !status.archived && outcome.deleted > 0
        binding.undoButton.setOnClickListener { undo(status) }

        val skipped = outcome.skipped
        binding.skippedText.isVisible = skipped.isNotEmpty()
        if (skipped.isNotEmpty()) {
            val lines = skipped.take(MAX_SKIPPED_LINES).map { getString(R.string.skipped_line, it.displayName, reason(it.reason)) }
            val more = skipped.size - MAX_SKIPPED_LINES
            binding.skippedText.text = buildString {
                append(resources.getQuantityString(R.plurals.skipped_head, skipped.size, skipped.size))
                lines.forEach { append('\n').append(it) }
                if (more > 0) append('\n').append(getString(R.string.skipped_more, more))
            }
        }
    }

    private fun showFailed(error: AppError.Delete) {
        showResultLayout()
        binding.title.setText(R.string.failed_title)
        binding.resultBody.setText(
            when (error) {
                AppError.Delete.PERMISSION_DENIED -> R.string.error_permission
                AppError.Delete.BIN_FULL -> R.string.error_space
                AppError.Delete.ARCHIVE_FOLDER_MISSING -> R.string.error_archive_folder
                else -> R.string.error_unknown
            }
        )
        binding.undoButton.isVisible = false
        binding.skippedText.isVisible = false
    }

    private fun showResultLayout() {
        binding.progressBlock.isVisible = false
        binding.resultBody.isVisible = true
        binding.bottomBar.isVisible = true
        binding.bottomDivider.isVisible = true
    }

    private fun undo(status: DeleteStatus.Completed) {
        binding.undoButton.isEnabled = false
        binding.doneButton.isEnabled = false
        val appContext = requireContext().applicationContext
        // The restore runs on the app scope, so leaving the screen can't cut it short.
        val work = viewModel.undo(status.outcome)
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = work.await()
            Toast.makeText(
                appContext, resources.getQuantityString(R.plurals.undo_result, restored, restored), Toast.LENGTH_SHORT
            ).show()
            leave()
        }
    }

    private fun reason(e: AppError.Delete): String = getString(
        when (e) {
            AppError.Delete.FILE_CHANGED -> R.string.reason_changed
            AppError.Delete.TARGET_MISSING -> R.string.reason_missing
            AppError.Delete.PERMISSION_DENIED, AppError.Delete.ARCHIVE_FOLDER_MISSING -> R.string.reason_permission
            AppError.Delete.BIN_FULL -> R.string.reason_space
            AppError.Delete.UNKNOWN -> R.string.reason_unknown
        }
    )

    /** Back to Duplicates. A running cleanup keeps going in the background. */
    private fun leave() {
        if (viewModel.status.value !is DeleteStatus.Running) viewModel.consume()
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_SKIPPED_LINES = 5
    }
}
