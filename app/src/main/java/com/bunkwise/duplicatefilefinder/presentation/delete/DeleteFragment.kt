package com.bunkwise.duplicatefilefinder.presentation.delete

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.databinding.FragmentDeleteBinding
import com.bunkwise.duplicatefilefinder.service.DeleteController
import com.bunkwise.duplicatefilefinder.service.DeleteProgress
import com.bunkwise.duplicatefilefinder.service.DeleteStatus
import kotlinx.coroutines.launch

class DeleteFragment : Fragment() {

    private var _binding: FragmentDeleteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeleteViewModel by viewModels { requireContext().appContainer.viewModelFactory }
    private var freedText: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.deleteRing.setProgressColor(resources.getColor(R.color.danger, requireContext().theme))

        // Back is disabled while deletion is in flight (files in flight, DESIGN §2).
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.status.value !is DeleteStatus.Running) goHome()
                }
            }
        )

        binding.backHomeButton.setOnClickListener { goHome() }
        binding.shareButton.setOnClickListener { share() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.progress.collect(::renderProgress) }
                launch { viewModel.status.collect(::handleStatus) }
            }
        }
    }

    private fun renderProgress(p: DeleteProgress) {
        val percent = if (p.total == 0) 0 else (p.deleted * 100 / p.total)
        binding.deleteRing.progress = percent.toFloat()
        binding.filesDeleted.text = Formatters.count(p.deleted)
        binding.spaceFreed.text = Formatters.bytes(p.freedBytes)
        binding.deleteElapsed.text = Formatters.duration(p.elapsedMs)
        if (p.currentName.isNotEmpty()) binding.currentName.text = p.currentName
    }

    private fun handleStatus(status: DeleteStatus) {
        when (status) {
            is DeleteStatus.Completed -> showComplete(status)
            is DeleteStatus.Failed -> { binding.deleteTitle.setText(R.string.scan_failed) }
            else -> Unit
        }
    }

    private fun showComplete(status: DeleteStatus.Completed) {
        val outcome = status.outcome
        freedText = Formatters.bytes(outcome.freedBytes)
        binding.progressContainer.isVisible = false
        binding.completeContainer.isVisible = true
        binding.confetti.start()
        binding.freedTotal.text = freedText
        binding.completeFiles.text = Formatters.count(outcome.deleted)
        binding.completeElapsed.text = Formatters.duration(viewModel.progress.value.elapsedMs)
        if (outcome.skipped.isNotEmpty()) {
            binding.skippedSummary.isVisible = true
            binding.skippedSummary.text = getString(
                R.string.skipped_summary,
                Formatters.count(outcome.deleted), Formatters.count(outcome.skipped.size)
            )
        }
    }

    private fun goHome() {
        viewModel.consume()
        findNavController().navigate(
            R.id.homeFragment, null,
            navOptions { popUpTo(R.id.homeFragment) { inclusive = true } }
        )
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, freedText))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
