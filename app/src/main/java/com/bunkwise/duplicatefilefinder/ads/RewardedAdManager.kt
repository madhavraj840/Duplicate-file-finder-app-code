package com.bunkwise.duplicatefilefinder.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Owns the single rewarded-interstitial ad used to boost the daily file limit
 * (+500 files per completed view).
 *
 * Nothing ad-related runs at app start: the SDK is initialised the first time the
 * quota sheet asks for an ad, and only after the UMP consent flow (GDPR) says ads
 * may be requested. Users who never hit the limit, and all premium users, never
 * load the ads SDK, which saves network, memory and battery. All AdMob objects are
 * touched on the main thread.
 */
class RewardedAdManager(private val appContext: Context) {

    private var ad: RewardedInterstitialAd? = null
    private var loading = false
    private var sdkReady = false

    /** True when an ad is loaded and ready to [show] immediately. */
    val isReady: Boolean get() = ad != null

    /** Runs consent + SDK init once, then loads an ad if none is loaded or in flight. */
    fun preload(activity: Activity) {
        if (ad != null || loading) return
        ensureSdk(activity) { load() }
    }

    private fun ensureSdk(activity: Activity, then: () -> Unit) {
        if (sdkReady) { then(); return }
        val consent = UserMessagingPlatform.getConsentInformation(appContext)
        val initIfAllowed = {
            if (consent.canRequestAds() && !sdkReady) {
                sdkReady = true
                MobileAds.initialize(appContext) { then() }
            }
        }
        consent.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initIfAllowed() } },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                initIfAllowed() // consent from an earlier session may still allow ads
            }
        )
    }

    private fun load() {
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
            preload(activity)
            onUnavailable()
            return
        }
        // Consume this ad; a new one is loaded when the full-screen content ends.
        ad = null
        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                load()
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
