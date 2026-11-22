package com.bunkwise.duplicatefilefinder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.widget.Toast
import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import com.bunkwise.duplicatefilefinder.ui.DfTheme
import com.bunkwise.duplicatefilefinder.ui.Tab
import com.bunkwise.duplicatefilefinder.ui.UnlockSheet
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.databinding.ActivityMainBinding
import com.bunkwise.duplicatefilefinder.engage.Engagement
import com.bunkwise.duplicatefilefinder.service.NotificationOrchestrator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Tab destinations; each draws the nav capsule itself, so the page blurs through it. */
    private val topLevel = mapOf(
        R.id.homeFragment to Tab.HOME,
        R.id.cleanupFragment to Tab.CLEANUP,
        R.id.settingsFragment to Tab.SETTINGS,
    )

    /** The open "Unlock X" sheet, if any. */
    private var unlockFor by mutableStateOf<ProFeature?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 forces edge-to-edge; the activity root (fitsSystemWindows)
        // applies the system-bar insets once for the whole app.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        setupOverlay()
        if (savedInstanceState == null) handleNavIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent(intent)
    }

    /** A "scan complete" notification tap opens the Duplicates tab. */
    private fun handleNavIntent(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationOrchestrator.EXTRA_NAV) ?: return
        intent.removeExtra(NotificationOrchestrator.EXTRA_NAV)
        // Don't yank the user out of a running scan/cleanup screen.
        val current = navController.currentDestination?.id
        if (current == R.id.scanFragment || current == R.id.deleteFragment) return
        if (target == NotificationOrchestrator.NAV_RESULTS) selectTab(R.id.cleanupFragment)
    }

    /** Hosts app-wide sheets over whichever Fragment is showing. */
    private fun setupOverlay() {
        binding.overlayCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.overlayCompose.setContent {
            DfTheme {
                unlockFor?.let { f ->
                    UnlockSheet(
                        feature = f,
                        onWatchAd = { unlockFor = null; watchAd(f) },
                        onGetPro = { unlockFor = null; navController.navigate(R.id.premiumFragment) },
                        onDismiss = { unlockFor = null },
                    )
                }
            }
        }
    }

    /** A locked row was tapped: offer the ad or Premium. */
    fun requestUnlock(feature: ProFeature) { unlockFor = feature }

    /**
     * Plays a rewarded ad straight away (a lock chip, the sheet's button, the daily-limit dialog).
     * [feature] null = the daily-limit ad; [onReward] runs after the unlock is stored.
     */
    fun watchAd(feature: ProFeature?, onReward: () -> Unit = {}) {
        appContainer.rewardedAds.show(this) {
            val text = when (val r = Unlocks.onAdWatched(feature)) {
                Unlocks.Reward.Pass -> getString(R.string.unlock_done_pass)
                is Unlocks.Reward.Feature -> r.feature?.let {
                    getString(R.string.unlock_done_feature, getString(it.label), r.toward, Unlocks.ADS_FOR_PRO)
                } ?: getString(R.string.unlock_progress_toast, r.toward, Unlocks.ADS_FOR_PRO)
            }
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            onReward()
        }
    }

    fun selectTab(tab: Tab) {
        topLevel.entries.firstOrNull { it.value == tab }?.let { selectTab(it.key) }
    }

    override fun onResume() {
        super.onResume()
        Performance.watch(window)
        Engagement.onAppOpened()
        appContainer.rewardedAds.onResume(this)
    }

    override fun onPause() {
        Performance.unwatch(window)
        super.onPause()
    }

    /**
     * Home is the start destination and always stays on the back stack, so it is
     * returned to by popping (its ViewModel and cached data survive) instead of
     * being rebuilt. Other tabs sit directly on top of Home.
     */
    fun selectTab(id: Int) {
        if (navController.currentDestination?.id == id) return
        if (id == R.id.homeFragment) {
            navController.popBackStack(R.id.homeFragment, false)
            return
        }
        runCatching {
            navController.navigate(id, null, navOptions {
                popUpTo(R.id.homeFragment) { inclusive = false }
                launchSingleTop = true
            })
        }
    }
}
