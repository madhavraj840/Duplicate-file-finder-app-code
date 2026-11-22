package com.bunkwise.duplicatefilefinder

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.databinding.ActivityMainBinding
import com.bunkwise.duplicatefilefinder.service.NotificationOrchestrator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Tab destinations; everything else is a full-screen step without the bar. */
    private val topLevel = setOf(R.id.homeFragment, R.id.cleanupFragment, R.id.settingsFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 forces edge-to-edge; the activity root (fitsSystemWindows)
        // applies the system-bar insets once for the whole app.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        setupBottomNav()
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val tab = destination.id in topLevel
            binding.bottomNav.visibility = if (tab) View.VISIBLE else View.GONE
            binding.bottomNavDivider.visibility = binding.bottomNav.visibility
            if (tab) binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
        }
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

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item -> selectTab(item.itemId); true }
        binding.bottomNav.setOnItemReselectedListener { /* tabs keep no inner state */ }
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
