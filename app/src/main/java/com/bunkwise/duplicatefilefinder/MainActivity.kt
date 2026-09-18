package com.bunkwise.duplicatefilefinder

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.databinding.ActivityMainBinding
import com.bunkwise.duplicatefilefinder.service.NotificationOrchestrator
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null

    /** Top-level destinations that show the bottom navigation bar. */
    private val topLevel = setOf(
        R.id.homeFragment, R.id.resultsFragment, R.id.cleanupFragment, R.id.settingsFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Go edge-to-edge on every API level. targetSdk 36 (Android 16) force-enables
        // edge-to-edge and ignores windowOptOutEdgeToEdgeEnforcement, so the only robust
        // path is to handle insets ourselves: the decor stops fitting system windows and
        // the activity root (fitsSystemWindows="true") applies them as padding once, for
        // the whole app. This is what stops the OS from panning the window up under
        // button navigation (which was hiding the top bar and "expanding" the bottom nav).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController
        this.navController = navController

        // Choose start destination based on the one-time onboarding flag.
        val onboardingDone = runBlocking { appContainer.settingsRepository.current().onboardingDone }
        val graph = navController.navInflater.inflate(R.navigation.nav_main)
        graph.setStartDestination(if (onboardingDone) R.id.homeFragment else R.id.onboardingFragment)
        navController.graph = graph

        setupBottomNav(navController)
        // A notification tap may carry a deep-link target (e.g. Results).
        if (onboardingDone) handleNavIntent(intent)

        // Hide the bottom nav on overlays (Scan/Review/Delete/Premium/Onboarding)
        // and keep the selected tab in sync with the current destination.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id in topLevel) View.VISIBLE else View.GONE
            applyStatusBarStyle(destination.id == R.id.onboardingFragment)
            val menu = binding.bottomNav.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.itemId == destination.id) item.isChecked = true
            }
        }
    }

    /**
     * Onboarding is the one screen with a fixed dark background, but as a fragment
     * it can't carry Theme.DuplicateFileFinder.Onboarding itself — so the status
     * bar is swapped here per destination and restored to the theme default after.
     */
    private fun applyStatusBarStyle(onboarding: Boolean) {
        window.statusBarColor = ContextCompat.getColor(
            this, if (onboarding) R.color.onboarding_bg else R.color.surface
        )
        val nightMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !onboarding && !nightMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent(intent)
    }

    /** Routes a notification-tap deep-link (currently only "results") to its screen. */
    private fun handleNavIntent(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationOrchestrator.EXTRA_NAV) ?: return
        // Consume it so a later config change / re-delivery doesn't navigate again.
        intent.removeExtra(NotificationOrchestrator.EXTRA_NAV)
        val nav = navController ?: return
        if (target == NotificationOrchestrator.NAV_RESULTS &&
            nav.currentDestination?.id != R.id.resultsFragment
        ) {
            runCatching {
                nav.navigate(
                    R.id.resultsFragment, null,
                    navOptions {
                        popUpTo(R.id.homeFragment) { inclusive = false }
                        launchSingleTop = true
                    }
                )
            }
        }
    }

    /**
     * Manual bottom-nav wiring. `setupWithNavController` relies on save/restore
     * state that gets corrupted once destinations are also reached via plain
     * `navigate(id)` calls elsewhere — the symptom was tab taps doing nothing.
     * Popping to Home first guarantees every tap lands on a clean, single-anchor
     * back stack, so the selected tab is always shown immediately.
     */
    private fun setupBottomNav(navController: NavController) {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id == item.itemId) return@setOnItemSelectedListener true
            val options = navOptions {
                popUpTo(R.id.homeFragment) { inclusive = item.itemId == R.id.homeFragment }
                launchSingleTop = true
            }
            runCatching { navController.navigate(item.itemId, null, options) }.isSuccess
        }
        // Re-selecting the active tab shouldn't pop its own inner state (none here).
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }
    }
}
