package com.bunkwise.duplicatefilefinder.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.bunkwise.duplicatefilefinder.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything Premium-only. Each can also be unlocked alone for [Unlocks.PASS_MS] by one rewarded ad. */
enum class ProFeature(@param:StringRes val label: Int) {
    SCAN_FOLDER(R.string.set_scan_folder),
    EXCLUDE(R.string.set_exclude),
    SCHEDULE(R.string.set_schedule),
    ARCHIVE(R.string.set_archive),
    SELECT_ALL(R.string.premium_f_select_all),
}

/**
 * Premium without paying (Night Mode's model): a rewarded ad unlocks the one feature it was watched
 * for, for 12 hours, and every ad also counts toward [ADS_FOR_PRO] - that many inside [PASS_MS]
 * unlocks all of Premium (including no daily limit) for 12 hours. Bought Premium lives in DataStore,
 * so callers combine: `isPremium || Unlocks.has(f)`. [version] changes on every unlock so flows and
 * Compose re-read.
 */
object Unlocks {
    const val PASS_MS = 12L * 60 * 60 * 1000
    const val ADS_FOR_PRO = 3

    private const val K_PASS_UNTIL = "pass_until"
    private const val K_AD_TIMES = "ad_times"

    private lateinit var prefs: SharedPreferences
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun init(context: Context) {
        prefs = context.getSharedPreferences("unlocks", Context.MODE_PRIVATE)
    }

    private fun untilKey(f: ProFeature) = "unlock_${f.name}"
    private val now get() = System.currentTimeMillis()

    /** The 3-ad pass is running: every Premium feature, daily limit included. */
    fun passActive(): Boolean = prefs.getLong(K_PASS_UNTIL, 0) > now

    /** Unlocked by an ad (alone or by the pass). Does NOT include bought Premium - see [unlocked]. */
    fun has(f: ProFeature): Boolean = passActive() || prefs.getLong(untilKey(f), 0) > now

    fun unlocked(isPremium: Boolean, f: ProFeature): Boolean = isPremium || has(f)

    /** Ads watched in the current 12h window, toward the pass (0 while a pass is running). */
    fun adsTowardPro(): Int = if (passActive()) 0 else adTimes().count { now - it < PASS_MS }

    private fun adTimes(): List<Long> =
        prefs.getString(K_AD_TIMES, "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    /** What a finished ad did, for the toast. */
    sealed interface Reward {
        data object Pass : Reward
        data class Feature(val feature: ProFeature?, val toward: Int) : Reward
    }

    /** A rewarded ad was watched through: [feature] (null = the daily-limit ad) for 12h, maybe all of Premium. */
    fun onAdWatched(feature: ProFeature?): Reward {
        val t = now
        val edit = prefs.edit()
        feature?.let { edit.putLong(untilKey(it), t + PASS_MS) }
        val recent = adTimes().filter { t - it < PASS_MS } + t
        val reward = if (recent.size >= ADS_FOR_PRO) {
            edit.putLong(K_PASS_UNTIL, t + PASS_MS).putString(K_AD_TIMES, "")
            Reward.Pass
        } else {
            edit.putString(K_AD_TIMES, recent.joinToString(","))
            Reward.Feature(feature, recent.size)
        }
        edit.apply()
        _version.value++
        return reward
    }
}
