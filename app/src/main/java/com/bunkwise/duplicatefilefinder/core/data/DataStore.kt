package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** Single Preferences DataStore for settings + quota + flags (ARCHITECTURE §8). */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "duplicate_finder_prefs")

object PrefKeys {
    // flags / settings
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val IS_PREMIUM = booleanPreferencesKey("is_premium")
    val ARCHIVE_MODE = booleanPreferencesKey("archive_mode")
    val AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup")
    val LANGUAGE = stringPreferencesKey("language_tag")

    // premium scan scope + schedule
    val SCAN_FOLDERS = stringSetPreferencesKey("scan_folders")
    val EXCLUDE_FOLDERS = stringSetPreferencesKey("exclude_folders")
    val SCAN_SCHEDULE = stringPreferencesKey("scan_schedule")
    val ARCHIVE_TREE_URI = stringPreferencesKey("archive_tree_uri")

    // quota
    val QUOTA_DATE = stringPreferencesKey("quota_date")
    val FILES_USED = intPreferencesKey("files_used_today")
    val BONUS_FILES = intPreferencesKey("bonus_files_today")
}
