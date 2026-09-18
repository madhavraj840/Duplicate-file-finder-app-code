package com.bunkwise.duplicatefilefinder.presentation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.databinding.FragmentOnboardingBinding
import com.bunkwise.duplicatefilefinder.presentation.common.AppLanguages

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels {
        requireContext().appContainer.viewModelFactory
    }

    private val languages = AppLanguages.entries

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.getStartedButton.setOnClickListener {
            viewModel.onGetStarted {
                findNavController().navigate(
                    R.id.homeFragment,
                    null,
                    androidx.navigation.navOptions {
                        popUpTo(R.id.onboardingFragment) { inclusive = true }
                    }
                )
            }
        }
        binding.langChip.setOnClickListener { showLanguagePicker() }
    }

    private fun showLanguagePicker() {
        val names = languages.map { it.second }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_language)
            .setItems(names) { _, which ->
                val (tag, name) = languages[which]
                binding.langLabel.text = name
                viewModel.onLanguageSelected(tag)
                AppLanguages.apply(tag)
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
