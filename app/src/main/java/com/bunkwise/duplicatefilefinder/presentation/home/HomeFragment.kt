package com.bunkwise.duplicatefilefinder.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bunkwise.duplicatefilefinder.ui.Tab
import com.bunkwise.duplicatefilefinder.ui.tabScreen
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.service.ScanForegroundService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    /** "Don't ask again" was hit: the grant button opens app settings instead. */
    private var accessDenied by mutableStateOf(false)

    private val storagePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.refresh()
            renderAccessDenied()
            // Media access is enough to scan; removing other apps' files needs All files.
            if (result.values.all { it } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Permissions.hasAllFilesAccess()
            ) promptAllFilesAccess()
        }

    /** POST_NOTIFICATIONS is asked when a scan starts; declining never blocks it. */
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.pendingScan?.let(::startScan)
            viewModel.pendingScan = null
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        tabScreen(Tab.HOME) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                accessDenied = accessDenied,
                onGrant = viewModel::onGrantAccess,
                onMode = viewModel::onModeSelected,
                onType = { type, checked -> viewModel.onTypeToggled(type, checked) },
                onScan = viewModel::onStartScan,
                onOpenRunning = ::openScanScreen,
                onOpenResults = { (activity as? MainActivity)?.selectTab(R.id.cleanupFragment) },
                onOpenBin = { findNavController().navigate(R.id.recycleBinFragment) },
                onPremium = { findNavController().navigate(R.id.premiumFragment) },
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh() // permission may have changed in system settings
        renderAccessDenied()
    }

    private fun handleEvent(event: HomeViewModel.HomeEvent) {
        when (event) {
            is HomeViewModel.HomeEvent.NavigateToScan -> ensureNotificationThenScan(event)
            is HomeViewModel.HomeEvent.RequestStorageAccess -> requestStorageAccess()
            is HomeViewModel.HomeEvent.ShowQuotaDialog -> showQuotaDialog(event)
            is HomeViewModel.HomeEvent.ScanFailed ->
                Toast.makeText(requireContext(), R.string.scan_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureNotificationThenScan(event: HomeViewModel.HomeEvent.NavigateToScan, docsChecked: Boolean = false) {
        val wantsDocs = event.categories.any {
            it == FileCategory.ALL || it == FileCategory.DOCUMENTS || it == FileCategory.OTHER
        }
        if (!docsChecked && wantsDocs && !Permissions.canSeeNonMediaFiles()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.all_files_title)
                .setMessage(R.string.docs_need_all_files)
                .setPositiveButton(R.string.open_settings) { _, _ -> Permissions.openAllFilesSettings(requireContext()) }
                .setNegativeButton(R.string.scan_anyway) { _, _ -> ensureNotificationThenScan(event, docsChecked = true) }
                .show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Permissions.hasNotificationPermission(requireContext())
        ) {
            viewModel.pendingScan = event
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScan(event)
        }
    }

    private fun startScan(event: HomeViewModel.HomeEvent.NavigateToScan) {
        val ctx = context ?: return
        if (ScanForegroundService.start(ctx, event.mode, event.categories, event.budget)) openScanScreen()
    }

    private fun openScanScreen() {
        findNavController().navigate(R.id.scanFragment, null, androidx.navigation.navOptions { launchSingleTop = true })
    }

    // ---- permissions ----

    private val prefs get() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Android stops showing the permission dialog after the user denies it (twice,
     * or with "don't ask again"). Detect that and send them to the app's settings
     * instead of a button that silently does nothing.
     */
    private fun permanentlyDenied(): Boolean =
        prefs.getBoolean(KEY_ASKED, false) && !viewModel.hasStoragePermission() &&
            Permissions.storagePermissions().none { shouldShowRequestPermissionRationale(it) }

    private fun renderAccessDenied() {
        accessDenied = permanentlyDenied()
    }

    private fun requestStorageAccess() {
        if (Permissions.hasAllFilesAccess()) { viewModel.refresh(); return }
        if (permanentlyDenied()) { openAppSettings(); return }
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        storagePermLauncher.launch(Permissions.storagePermissions())
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
        }
    }

    private fun promptAllFilesAccess() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.all_files_title)
            .setMessage(R.string.all_files_body)
            .setPositiveButton(R.string.open_settings) { _, _ -> Permissions.openAllFilesSettings(requireContext()) }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    // ---- daily limit ----

    private fun showQuotaDialog(event: HomeViewModel.HomeEvent.ShowQuotaDialog) {
        val q = viewModel.state.value.quota
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.quota_title)
            .setMessage(
                getString(
                    R.string.quota_body,
                    Formatters.count(event.estimate),
                    Formatters.count(q?.limit ?: 0),
                    Formatters.count(event.remaining)
                )
            )
            // The ad loads only now, on the tap.
            .setNeutralButton(R.string.quota_watch_ad) { _, _ -> (activity as? MainActivity)?.watchAd(null) { viewModel.onAdReward() } }
            .setNegativeButton(R.string.cancel, null)
        if (event.remaining > 0) {
            builder.setPositiveButton(getString(R.string.quota_scan_within, Formatters.count(event.remaining))) { _, _ ->
                viewModel.scanWithinLimit(event.mode, event.categories, event.remaining)
            }
        } else {
            builder.setPositiveButton(R.string.quota_premium) { _, _ -> findNavController().navigate(R.id.premiumFragment) }
        }
        builder.show()
    }

    private companion object {
        const val PREFS = "ui_prefs"
        const val KEY_ASKED = "storage_permission_asked"
    }
}
