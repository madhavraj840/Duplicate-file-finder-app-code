package com.bunkwise.duplicatefilefinder

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import com.bunkwise.duplicatefilefinder.di.AppContainer
import com.bunkwise.duplicatefilefinder.engage.Engagement
import com.bunkwise.duplicatefilefinder.presentation.common.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Coil (thumbnails) with video-frame support, so video duplicates get a preview too. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(!Performance.lite)
        .build()

    override fun onCreate() {
        super.onCreate()
        // Must run before the first Activity inflates so it starts in the right theme.
        ThemePrefs.apply(this)
        Performance.init(this)
        Unlocks.init(this)
        Engagement.init(this)
        container = AppContainer(this)
        container.notifications.ensureChannels()

        // The ads SDK is deliberately NOT initialised here: RewardedAdManager starts it (after
        // consent) the first time someone taps "Watch an ad".

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
