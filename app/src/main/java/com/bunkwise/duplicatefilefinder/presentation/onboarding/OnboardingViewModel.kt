package com.bunkwise.duplicatefilefinder.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settings: SettingsRepository
) : ViewModel() {

    fun onGetStarted(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }

    fun onLanguageSelected(tag: String) {
        viewModelScope.launch { settings.setLanguage(tag) }
    }
}
