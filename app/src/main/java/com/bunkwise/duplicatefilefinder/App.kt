package com.bunkwise.duplicatefilefinder

import android.app.Application
import com.bunkwise.duplicatefilefinder.di.AppContainer
import com.bunkwise.duplicatefilefinder.presentation.common.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Must run before the first Activity inflates so it starts in the right theme.
        ThemePrefs.apply(this)
        container = AppContainer(this)
        container.notifications.ensureChannels()

        // The ads SDK is deliberately NOT initialised here: RewardedAdManager does it
        // lazily (after consent) the first time the quota sheet needs an ad.

        // The per-app language is NOT re-applied here: AppCompat (autoStoreLocales, API
        // <33) and the system (33+) already persist what AppLanguages.apply() set.
        // Re-applying from a background thread could recreate an Activity off the main
        // thread and crash, and a default tag forced English over the phone's language.
        appScope.launch {
            // Lazy recycle-bin purge on launch (replaces the WorkManager job in v1).
            container.recycleBin.purgeExpired()
        }

        // Keep the premium scheduled-scan job in sync with settings (Schedule Scan /
        // Auto Cleanup). Self-heals on every launch and reacts to toggles live.
        appScope.launch {
            container.settingsRepository.settings.collect { container.scanScheduler.sync(it) }
        }
    }
}

/** Convenience accessor for the container from any Context. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as App).container
