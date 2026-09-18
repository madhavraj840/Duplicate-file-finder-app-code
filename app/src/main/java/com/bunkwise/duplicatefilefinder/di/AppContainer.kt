package com.bunkwise.duplicatefilefinder.di

import android.content.Context
import com.bunkwise.duplicatefilefinder.core.common.DefaultDispatcherProvider
import com.bunkwise.duplicatefilefinder.core.common.DispatcherProvider
import com.bunkwise.duplicatefilefinder.core.data.DeletionEngineImpl
import com.bunkwise.duplicatefilefinder.core.data.FileEnumeratorImpl
import com.bunkwise.duplicatefilefinder.core.data.QuotaRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.data.RecycleBinRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.data.ScanResultRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.data.SettingsRepositoryImpl
import com.bunkwise.duplicatefilefinder.core.data.StorageStatsProviderImpl
import com.bunkwise.duplicatefilefinder.core.domain.engine.DuplicateEngine
import com.bunkwise.duplicatefilefinder.core.domain.repository.DeletionEngine
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.QuotaRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.ScanResultRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import com.bunkwise.duplicatefilefinder.core.domain.repository.StorageStatsProvider
import com.bunkwise.duplicatefilefinder.core.domain.usecase.CheckScanQuotaUseCase
import com.bunkwise.duplicatefilefinder.ads.RewardedAdManager
import com.bunkwise.duplicatefilefinder.core.engine.DuplicateEngineImpl
import com.bunkwise.duplicatefilefinder.core.engine.ImageFeatureExtractor
import com.bunkwise.duplicatefilefinder.service.AutoScanScheduler
import com.bunkwise.duplicatefilefinder.service.DeleteController
import com.bunkwise.duplicatefilefinder.service.NotificationOrchestrator
import com.bunkwise.duplicatefilefinder.service.ScanController

/**
 * Manual dependency container (ARCHITECTURE §3, DIP honoured without an
 * annotation processor). One instance lives on the Application; everything is a
 * lazily-created singleton. Hilt is the production drop-in behind these same
 * abstractions — this class is the single composition root.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    // Data
    val fileEnumerator: FileEnumerator by lazy { FileEnumeratorImpl(appContext, settingsRepository) }
    val storageStats: StorageStatsProvider by lazy { StorageStatsProviderImpl() }
    val scanResultRepository: ScanResultRepository by lazy { ScanResultRepositoryImpl(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }
    val quotaRepository: QuotaRepository by lazy { QuotaRepositoryImpl(appContext) }
    val recycleBin: RecycleBinRepositoryImpl by lazy { RecycleBinRepositoryImpl(appContext) }
    val deletionEngine: DeletionEngine by lazy { DeletionEngineImpl(appContext, recycleBin) }

    // Engine
    private val imageFeatures by lazy { ImageFeatureExtractor() }
    val duplicateEngine: DuplicateEngine by lazy {
        DuplicateEngineImpl(fileEnumerator, imageFeatures, dispatchers)
    }

    // Use cases
    val checkScanQuota by lazy { CheckScanQuotaUseCase(fileEnumerator, quotaRepository) }

    // Presentation composition root
    val viewModelFactory by lazy { AppViewModelFactory(this, appContext) }

    // Service bridges + notifications (singletons)
    val scanController = ScanController()
    val deleteController = DeleteController()
    val notifications by lazy { NotificationOrchestrator(appContext) }
    val scanScheduler by lazy { AutoScanScheduler(appContext) }

    // Rewarded-interstitial ad used to top up the daily file limit (+500 per view).
    val rewardedAds by lazy { RewardedAdManager(appContext) }

    /**
     * Staging area for a confirmed deletion (the file list is too large to pass
     * through an Intent). Set by Review & Delete just before starting the service.
     */
    @Volatile
    var pendingDeletionFiles: List<com.bunkwise.duplicatefilefinder.core.domain.model.FileItem> = emptyList()

    @Volatile
    var pendingArchive: Boolean = false

    /**
     * One-shot flag: scan just completed, so the Results screen should play its
     * confetti burst once. Consumed (reset) by ResultsFragment on first show, so
     * revisiting Results from Home does not re-trigger it.
     */
    @Volatile
    var pendingScanCelebration: Boolean = false
}
