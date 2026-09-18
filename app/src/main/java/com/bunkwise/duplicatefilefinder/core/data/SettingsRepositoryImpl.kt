package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.repository.AppSettings
import com.bunkwise.duplicatefilefinder.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.appDataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[PrefKeys.ONBOARDING_DONE] ?: false,
            isPremium = p[PrefKeys.IS_PREMIUM] ?: false,
            archiveMode = p[PrefKeys.ARCHIVE_MODE] ?: false,
            autoCleanup = p[PrefKeys.AUTO_CLEANUP] ?: false,
            languageTag = p[PrefKeys.LANGUAGE] ?: "en",
            scanFolders = p[PrefKeys.SCAN_FOLDERS] ?: emptySet(),
            excludeFolders = p[PrefKeys.EXCLUDE_FOLDERS] ?: emptySet(),
            scanSchedule = p[PrefKeys.SCAN_SCHEDULE]
                ?.let { runCatching { ScanSchedule.valueOf(it) }.getOrNull() }
                ?: ScanSchedule.OFF,
            archiveTreeUri = p[PrefKeys.ARCHIVE_TREE_URI]
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setOnboardingDone(done: Boolean) {
        context.appDataStore.edit { it[PrefKeys.ONBOARDING_DONE] = done }
    }

    override suspend fun setPremium(premium: Boolean) {
        context.appDataStore.edit { it[PrefKeys.IS_PREMIUM] = premium }
    }

    override suspend fun setArchiveMode(enabled: Boolean) {
        context.appDataStore.edit { it[PrefKeys.ARCHIVE_MODE] = enabled }
    }

    override suspend fun setAutoCleanup(enabled: Boolean) {
        context.appDataStore.edit { it[PrefKeys.AUTO_CLEANUP] = enabled }
    }

    override suspend fun setLanguage(tag: String) {
        context.appDataStore.edit { it[PrefKeys.LANGUAGE] = tag }
    }

    override suspend fun setScanFolders(paths: Set<String>) {
        context.appDataStore.edit { it[PrefKeys.SCAN_FOLDERS] = paths }
    }

    override suspend fun setExcludeFolders(paths: Set<String>) {
        context.appDataStore.edit { it[PrefKeys.EXCLUDE_FOLDERS] = paths }
    }

    override suspend fun setScanSchedule(schedule: ScanSchedule) {
        context.appDataStore.edit { it[PrefKeys.SCAN_SCHEDULE] = schedule.name }
    }

    override suspend fun setArchiveTreeUri(uri: String?) {
        context.appDataStore.edit { p ->
            if (uri == null) p.remove(PrefKeys.ARCHIVE_TREE_URI) else p[PrefKeys.ARCHIVE_TREE_URI] = uri
        }
    }
}
