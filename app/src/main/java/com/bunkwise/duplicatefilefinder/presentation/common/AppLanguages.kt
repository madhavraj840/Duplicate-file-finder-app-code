package com.bunkwise.duplicatefilefinder.presentation.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The v1 language set (OTHER_RELEVANT_INFO.TXT Q3). Each entry pairs a BCP-47 tag
 * with the language's own endonym so the picker reads naturally to native speakers.
 * A matching res/values-<tag>/strings.xml supplies the translations; applying a tag
 * swaps the app locale via the per-app locales API (works to API 24 through AppCompat).
 */
object AppLanguages {

    /** tag -> endonym. Order = picker order (English first). */
    val entries: List<Pair<String, String>> = listOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "ja" to "日本語",
        "zh" to "中文",
        "ru" to "Русский",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pt" to "Português"
    )

    fun displayName(tag: String): String =
        entries.firstOrNull { it.first == tag }?.second ?: "English"

    /** Applies the locale immediately (recreates activities to re-resolve strings). */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
