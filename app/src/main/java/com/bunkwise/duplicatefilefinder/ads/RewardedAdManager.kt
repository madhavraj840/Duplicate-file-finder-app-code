package com.bunkwise.duplicatefilefinder.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Owns the single rewarded-interstitial ad used to boost the daily file limit
 * (+500 files per completed view). Keeps one ad preloaded so the user rarely
 * waits: after every show — or any load/show failure — it eagerly reloads the
 * next one. All AdMob objects are touched on the main thread, matching the SDK's
 * threading contract; instances live for the process (held by AppContainer).
 */
class RewardedAdManager(private val appContext: Context) {

    private var ad: RewardedInterstitialAd? = null
    private var loading = false

    /** True when an ad is loaded and ready to [show] immediately. */
    val isReady: Boolean get() = ad != null

    /** Loads the next ad if one isn't already loaded or in flight. Safe to call often. */
    fun preload() {
        if (ad != null || loading) return
        loading = true
        RewardedInterstitialAd.load(
            appContext, TEST_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedInterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the preloaded ad. [onReward] fires once, after the ad is dismissed, only
     * if the user actually earned the reward. If no ad is ready (or it fails to show),
     * [onUnavailable] fires instead and a fresh load is kicked off. Must be called
     * from the main thread with a resumed [activity].
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val current = ad
        if (current == null) {
            preload()
            onUnavailable()
            return
        }
        // Consume this ad; a new one is loaded when the full-screen content ends.
        ad = null
        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload()
                onUnavailable()
            }
        }
        current.show(activity) { earned = true }
    }

    companion object {
        private const val TAG = "RewardedAdManager"

        /**
         * Google's public TEST rewarded-interstitial ad unit. Replace with the real
         * ad unit id (and the real app id in AndroidManifest.xml) before release.
         */
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    }
}
