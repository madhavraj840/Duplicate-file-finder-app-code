package com.bunkwise.duplicatefilefinder.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.bunkwise.duplicatefilefinder.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * The rewarded ad behind every "Watch ad" (Night Mode's model). Nothing is loaded ahead of time:
 * the tap asks for consent (UMP, first time only), starts the SDK, loads one ad and plays it the
 * moment it arrives - a preloaded ad was a request that usually never became an impression. A tap
 * waits at most [PENDING_MS] for its ad, and only plays while the app is in front ([onResume]
 * catches one that loaded while the user was away). All calls on the main thread.
 */
class RewardedAdManager(private val appContext: Context) {

    private var ad: RewardedAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var sdkReady = false

    private var pending: Pair<Activity, () -> Unit>? = null
    private var pendingAt = 0L

    /** Plays a rewarded ad; [onReward] fires after it closes, only if it was watched through. */
    fun show(activity: Activity, onReward: () -> Unit) {
        if (System.currentTimeMillis() - loadedAt > AD_FRESH_MS) ad = null // expired after an hour
        val current = ad
        if (current == null) {
            pending = activity to onReward
            pendingAt = System.currentTimeMillis()
            Toast.makeText(activity, R.string.unlock_ad_loading, Toast.LENGTH_SHORT).show()
            ensureSdk(activity) { load() }
            return
        }
        ad = null
        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            // No reload: the next tap loads its own.
            override fun onAdDismissedFullScreenContent() { if (earned) onReward() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Toast.makeText(activity, R.string.unlock_no_ad, Toast.LENGTH_LONG).show()
            }
        }
        current.show(activity) { earned = true }
    }

    /** MainActivity.onResume: an ad tapped for that loaded while the app was in the background plays now. */
    fun onResume(activity: Activity) {
        pending?.let { pending = activity to it.second; playPending(inFront = true) }
    }

    private fun ensureSdk(activity: Activity, then: () -> Unit) {
        if (sdkReady) { then(); return }
        val consent = UserMessagingPlatform.getConsentInformation(appContext)
        val start = {
            if (consent.canRequestAds()) {
                if (!sdkReady) { sdkReady = true; MobileAds.initialize(appContext) { then() } }
            } else failPending()
        }
        consent.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { start() } },
            { error -> Log.w(TAG, "Consent update failed: ${error.message}"); start() },
        )
    }

    private fun load() {
        if (ad != null) { playPending(); return }
        if (loading) return
        loading = true
        RewardedAd.load(appContext, TEST_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) {
                ad = loaded
                loadedAt = System.currentTimeMillis()
                loading = false
                playPending()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                failPending()
            }
        })
    }

    private fun failPending() {
        val (activity, _) = pending ?: return
        pending = null
        Toast.makeText(activity, R.string.unlock_no_ad, Toast.LENGTH_LONG).show()
    }

    private fun playPending(inFront: Boolean = false) {
        val (activity, onReward) = pending ?: return
        if (System.currentTimeMillis() - pendingAt > PENDING_MS) { pending = null; return }
        val front = inFront || (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        if (ad == null || !front || activity.isFinishing) return
        pending = null
        show(activity, onReward)
    }

    companion object {
        private const val TAG = "RewardedAdManager"
        private const val PENDING_MS = 60_000L
        private const val AD_FRESH_MS = 55 * 60_000L

        /** Google's public TEST rewarded unit. Swap for the real one (and the app id in the manifest) before release. */
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
