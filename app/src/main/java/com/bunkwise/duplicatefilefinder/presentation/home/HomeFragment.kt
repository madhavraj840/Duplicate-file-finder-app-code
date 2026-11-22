package com.bunkwise.duplicatefilefinder.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.databinding.FragmentHomeBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemCheckRowBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemRadioRowBinding
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.presentation.common.announceAsCheckable
import com.bunkwise.duplicatefilefinder.service.ScanForegroundService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    private val modeRows = mutableMapOf<ScanMode, ItemRadioRowBinding>()
    private val typeRows = mutableMapOf<FileCategory, ItemCheckRowBinding>()

    /** VERY_SIMILAR stays in the engine (old saved results use it) but isn't offered. */
    private val modes = listOf(
        Triple(ScanMode.EXACT, R.string.mode_exact, R.string.mode_exact_desc),
        Triple(ScanMode.SIMILAR, R.string.mode_similar, R.string.mode_similar_desc),
        Triple(ScanMode.DEEP, R.string.mode_deep, R.string.mode_deep_desc)
    )

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildModeRows()
        buildTypeRows()

        binding.startScanButton.setOnClickListener { viewModel.onStartScan() }
        binding.grantButton.setOnClickListener { viewModel.onGrantAccess() }
        binding.runningRow.setOnClickListener { openScanScreen() }
        binding.lastScanRow.setOnClickListener { (activity as? MainActivity)?.selectTab(R.id.cleanupFragment) }
        binding.binRow.setOnClickListener { findNavController().navigate(R.id.recycleBinFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh() // permission may have changed in system settings
        renderAccessDenied()
    }

    private fun buildModeRows() {
        for ((mode, title, desc) in modes) {
            val row = ItemRadioRowBinding.inflate(layoutInflater, binding.modeContainer, false)
            row.title.setText(title)
            row.desc.setText(desc)
            row.root.setOnClickListener { viewModel.onModeSelected(mode) }
            binding.modeContainer.addView(row.root)
            modeRows[mode] = row
        }
    }

    private fun buildTypeRows() {
        for (type in HomeViewModel.SCAN_TYPES) {
            val row = ItemCheckRowBinding.inflate(layoutInflater, binding.typeContainer, false)
            row.title.setText(CategoryUi.label(type))
            row.root.setOnClickListener { viewModel.onTypeToggled(type, !row.check.isChecked) }
            binding.typeContainer.addView(row.root)
            typeRows[type] = row
        }
    }

    private fun render(state: HomeViewModel.HomeState) {
        state.storage?.let { s ->
            binding.storageUsage.text = getString(R.string.storage_used, Formatters.bytes(s.usedBytes), Formatters.bytes(s.totalBytes))
            binding.storageBar.progress = s.usedPercent
            // Warning color only when space is actually tight.
            binding.storageBar.setIndicatorColor(
                ContextCompat.getColor(requireContext(), if (s.usedPercent >= 90) R.color.warning else R.color.primary)
            )
        }

        binding.accessBlock.isVisible = !state.hasPermission
        binding.optionsBlock.isVisible = state.hasPermission
        binding.bottomBar.isVisible = state.hasPermission
        binding.bottomDivider.isVisible = state.hasPermission

        val running = state.runningPercent
        binding.runningRow.isVisible = running != null
        if (running != null) binding.runningText.text = getString(R.string.scan_running, running)

        val result = state.lastResult
        binding.lastScanRow.isVisible = result != null && running == null
        if (result != null) {
            val ago = DateUtils.getRelativeTimeSpanString(
                result.finishedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
            binding.lastScanText.text = if (result.groupCount == 0) getString(R.string.last_scan_clean, ago)
            else getString(
                R.string.last_scan_summary,
                Formatters.bytes(result.wastedBytes), Formatters.count(result.groupCount), ago
            )
        }

        binding.binText.text = if (state.bin.isEmpty()) getString(R.string.bin_row_empty)
        else resources.getQuantityString(
            R.plurals.bin_row_summary, state.bin.size, state.bin.size, Formatters.bytes(state.bin.sumOf { it.sizeBytes })
        )

        for ((mode, row) in modeRows) {
            val checked = mode == state.selectedMode
            row.radio.isChecked = checked
            row.root.announceAsCheckable(RadioButton::class.java.name, checked)
        }
        for ((type, row) in typeRows) {
            val checked = type in state.selectedTypes
            row.check.isChecked = checked
            row.root.announceAsCheckable(CheckBox::class.java.name, checked)
            row.value.text = state.sizeByCategory[type]?.let(Formatters::bytes) ?: ""
        }

        val canScan = state.selectedTypes.isNotEmpty() && running == null
        binding.startScanButton.isEnabled = canScan
        val q = state.quota
        binding.quotaCaption.isVisible = state.selectedTypes.isEmpty() || (q != null && !q.isPremium)
        binding.quotaCaption.text = when {
            state.selectedTypes.isEmpty() -> getString(R.string.types_none)
            q != null -> getString(R.string.quota_left, Formatters.count(q.remaining), Formatters.count(q.limit + q.bonus))
            else -> ""
        }
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
        val denied = permanentlyDenied()
        binding.accessDenied.isVisible = denied
        binding.grantButton.setText(if (denied) R.string.open_settings else R.string.access_allow)
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
        // Consent + ad SDK start only now, when an ad is actually on offer.
        requireContext().appContainer.rewardedAds.preload(requireActivity())
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
            .setNeutralButton(R.string.quota_watch_ad) { _, _ -> showRewardedAd() }
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

    private fun showRewardedAd() {
        val ads = requireContext().appContainer.rewardedAds
        if (!ads.isReady) {
            Toast.makeText(requireContext(), R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
            ads.preload(requireActivity())
            return
        }
        ads.show(
            requireActivity(),
            onReward = { viewModel.onAdReward() },
            onUnavailable = {
                // The fragment may be gone by the time the ad closes.
                context?.let { Toast.makeText(it, R.string.ad_not_ready, Toast.LENGTH_SHORT).show() }
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        modeRows.clear()
        typeRows.clear()
        super.onDestroyView()
    }

    private companion object {
        const val PREFS = "ui_prefs"
        const val KEY_ASKED = "storage_permission_asked"
    }
}
