package com.bunkwise.duplicatefilefinder

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bunkwise.duplicatefilefinder.di.AppContainer
import com.bunkwise.duplicatefilefinder.presentation.common.ThemePrefs
import com.google.android.gms.ads.MobileAds
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

        // Initialize the Ads SDK once, then warm the first rewarded ad so the file-limit
        // boost is ready by the time a user hits the quota sheet. initialize() is async
        // and does its own IO; we only preload after it reports ready.
        MobileAds.initialize(this) { container.rewardedAds.preload() }

        // Apply the saved per-app language (ARCHITECTURE §8 / OTHER §4).
        appScope.launch {
            val settings = container.settingsRepository.current()
            if (settings.languageTag.isNotBlank()) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(settings.languageTag)
                )
            }
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
