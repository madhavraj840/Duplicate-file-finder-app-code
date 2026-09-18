package com.bunkwise.duplicatefilefinder.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bunkwise.duplicatefilefinder.presentation.delete.DeleteViewModel
import com.bunkwise.duplicatefilefinder.presentation.home.HomeViewModel
import com.bunkwise.duplicatefilefinder.presentation.onboarding.OnboardingViewModel
import com.bunkwise.duplicatefilefinder.presentation.premium.PremiumViewModel
import com.bunkwise.duplicatefilefinder.presentation.results.ResultsViewModel
import com.bunkwise.duplicatefilefinder.presentation.scan.ScanViewModel
import com.bunkwise.duplicatefilefinder.presentation.settings.RecycleBinViewModel
import com.bunkwise.duplicatefilefinder.presentation.settings.SettingsViewModel
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel

/**
 * Single ViewModel factory backed by the manual [AppContainer]. The switch is the
 * composition root for the presentation layer; adding Hilt later replaces this
 * file without touching any ViewModel.
 */
class AppViewModelFactory(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
            OnboardingViewModel(container.settingsRepository)

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(
                appContext, container.storageStats, container.quotaRepository,
                container.fileEnumerator, container.checkScanQuota, container.deleteController
            )

        modelClass.isAssignableFrom(ScanViewModel::class.java) ->
            ScanViewModel(container.scanController)

        modelClass.isAssignableFrom(ResultsViewModel::class.java) ->
            ResultsViewModel(container.scanResultRepository, container.settingsRepository)

        modelClass.isAssignableFrom(SharedSelectionViewModel::class.java) ->
            SharedSelectionViewModel(
                container.scanResultRepository, container.settingsRepository, container.storageStats
            )

        modelClass.isAssignableFrom(DeleteViewModel::class.java) ->
            DeleteViewModel(container.deleteController)

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settingsRepository)

        modelClass.isAssignableFrom(PremiumViewModel::class.java) ->
            PremiumViewModel(container.settingsRepository)

        modelClass.isAssignableFrom(RecycleBinViewModel::class.java) ->
            RecycleBinViewModel(container.recycleBin)

        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
