package com.bunkwise.duplicatefilefinder.presentation.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Light / dark / follow-system choice. Stored in plain SharedPreferences (not
 * DataStore) so Application.onCreate can read it synchronously before the first
 * Activity inflates — a late apply would flash the wrong theme.
 */
object ThemePrefs {

    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val PREFS = "ui_prefs"
    private const val KEY = "theme_mode"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode).apply()
        applyMode(mode)
    }

    fun apply(context: Context) = applyMode(current(context))

    private fun applyMode(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
