package com.bunkwise.duplicatefilefinder.presentation.premium

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.databinding.FragmentPremiumBinding
import kotlinx.coroutines.launch

/**
 * What Premium adds. There is no billing yet, so release builds only describe it;
 * debug builds get a toggle to test premium features.
 */
class PremiumFragment : Fragment() {

    private var _binding: FragmentPremiumBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PremiumViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    private val isDebugBuild: Boolean
        get() = requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().popBackStack() }
        binding.debugToggle.isVisible = isDebugBuild
        binding.debugToggle.setOnClickListener { viewModel.setPremium(!viewModel.isPremium.value) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isPremium.collect { premium ->
                    binding.statusText.setText(if (premium) R.string.premium_is_active else R.string.premium_unavailable)
                    binding.debugToggle.setText(if (premium) R.string.premium_debug_off else R.string.premium_debug_on)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
