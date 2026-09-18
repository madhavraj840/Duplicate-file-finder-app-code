package com.bunkwise.duplicatefilefinder.presentation.home

import android.Manifest
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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanMode
import com.bunkwise.duplicatefilefinder.databinding.FragmentHomeBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemCategoryChipBinding
import com.bunkwise.duplicatefilefinder.presentation.common.Permissions
import com.bunkwise.duplicatefilefinder.service.ScanForegroundService
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    private val chipBindings = mutableMapOf<FileCategory, ItemCategoryChipBinding>()

    private data class CatMeta(val category: FileCategory, val iconRes: Int, val tintRes: Int, val nameRes: Int)

    private val categories = listOf(
        CatMeta(FileCategory.ALL, R.drawable.ic_doc, R.color.primary, R.string.cat_all),
        CatMeta(FileCategory.IMAGES, R.drawable.ic_image, R.color.icon_green, R.string.cat_images),
        CatMeta(FileCategory.VIDEOS, R.drawable.ic_video, R.color.icon_purple, R.string.cat_videos),
        CatMeta(FileCategory.DOCUMENTS, R.drawable.ic_doc, R.color.icon_blue, R.string.cat_documents),
        CatMeta(FileCategory.OTHER, R.drawable.ic_other, R.color.icon_gray, R.string.cat_other)
    )

    private val modeLabels get() = listOf(binding.mode0, binding.mode1, binding.mode2, binding.mode3)

    private var pendingScan: HomeViewModel.HomeEvent.NavigateToScan? = null

    /** Runtime media/storage permission request (photos, videos, audio). */
    private val storagePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refresh()
            // Media access lets us scan; nudge toward All-files so removal works everywhere.
            if (!Permissions.hasAllFilesAccess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptAllFilesAccess()
            }
        }

    /** POST_NOTIFICATIONS is requested when a scan starts (NOTIFICATION §4 R3). */
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingScan?.let { startScan(it) }
            pendingScan = null
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildCategoryChips()

        // Scan-type slider: 0..3 map to the four ScanMode entries.
        binding.modeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.onModeSelected(value.toInt())
        }
        modeLabels.forEachIndexed { index, label ->
            label.setOnClickListener { viewModel.onModeSelected(index) }
        }

        binding.startScanButton.setOnClickListener { viewModel.onStartScan() }
        binding.grantButton.setOnClickListener { viewModel.onGrantAccess() }
        binding.crownButton.setOnClickListener { findNavController().navigate(R.id.premiumFragment) }
        binding.infoButton.setOnClickListener { showAbout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh() // re-check permission on every Home visit (NOTIFICATION §4 R2/R4)
    }

    private fun buildCategoryChips() {
        binding.categoryContainer.removeAllViews()
        chipBindings.clear()
        for (meta in categories) {
            val item = ItemCategoryChipBinding.inflate(layoutInflater, binding.categoryContainer, false)
            item.icon.setImageResource(meta.iconRes)
            item.iconTile.backgroundTintList = ContextCompat.getColorStateList(requireContext(), meta.tintRes)
            item.name.setText(meta.nameRes)
            item.root.setOnClickListener { viewModel.onCategoryToggled(meta.category) }
            binding.categoryContainer.addView(item.root)
            chipBindings[meta.category] = item
        }
    }

    private fun render(state: HomeViewModel.HomeState) {
        // Storage
        state.storage?.let { s ->
            val color = ContextCompat.getColor(requireContext(), colorForUsage(s.usedPercent))
            binding.storageRing.setProgressColor(color)
            binding.storageRing.animateTo(s.usedPercent.toFloat())
            binding.storageBar.setIndicatorColor(color)
            binding.storageBar.progress = s.usedPercent
            binding.storageUsage.text = getString(R.string.storage_of, Formatters.bytes(s.usedBytes), Formatters.bytes(s.totalBytes))
            binding.storageLeft.text = getString(
                R.string.storage_left, "${s.freePercent}%", Formatters.bytes(s.freeBytes)
            )
            binding.storageLeft.setTextColor(color)
        }

        // Permission gating
        binding.permissionCard.isVisible = !state.hasPermission
        binding.scanTypeCard.isVisible = state.hasPermission
        binding.chooseFilesCard.isVisible = state.hasPermission

        // Scan mode slider + description
        val modeIndex = ScanMode.entries.indexOf(state.selectedMode)
        if (binding.modeSlider.value.toInt() != modeIndex) {
            binding.modeSlider.value = modeIndex.toFloat()
        }
        modeLabels.forEachIndexed { i, label ->
            label.setTextColor(
                ContextCompat.getColor(requireContext(), if (i == modeIndex) R.color.primary else R.color.text_secondary)
            )
        }
        bindModeDescription(state.selectedMode)

        // Categories
        for (meta in categories) {
            val item = chipBindings[meta.category] ?: continue
            val selected = state.selectedCategories.contains(meta.category)
            item.root.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            if (selected) {
                item.check.setImageResource(R.drawable.ic_check_box)
                item.check.background = null
            } else {
                item.check.setImageDrawable(null)
                item.check.setBackgroundResource(R.drawable.bg_checkbox_empty)
            }
            val bytes = if (meta.category == FileCategory.ALL) state.sizeByCategory.values.sum()
            else state.sizeByCategory[meta.category] ?: 0L
            item.size.text = Formatters.bytes(bytes)
        }

        // Quota caption
        state.quota?.let { q ->
            binding.quotaCaption.text = if (q.isPremium) getString(R.string.quota_unlimited)
            else getString(R.string.quota_caption, Formatters.count(q.remaining), Formatters.count(q.limit))
        }
    }

    private fun bindModeDescription(mode: ScanMode) {
        val (titleRes, descRes, iconRes) = when (mode) {
            ScanMode.EXACT -> Triple(R.string.mode_exact_short, R.string.mode_exact_desc, R.drawable.ic_doc)
            ScanMode.SIMILAR -> Triple(R.string.mode_similar_short, R.string.mode_similar_desc, R.drawable.ic_image)
            ScanMode.VERY_SIMILAR -> Triple(R.string.mode_very_similar_short, R.string.mode_very_similar_desc, R.drawable.ic_image)
            ScanMode.DEEP -> Triple(R.string.mode_deep_short, R.string.mode_deep_desc, R.drawable.ic_search)
        }
        binding.modeTitle.setText(titleRes)
        binding.modeDesc.setText(descRes)
        binding.modeIcon.setImageResource(iconRes)
    }

    private fun handleEvent(event: HomeViewModel.HomeEvent) {
        when (event) {
            is HomeViewModel.HomeEvent.NavigateToScan -> ensureNotificationThenScan(event)
            is HomeViewModel.HomeEvent.RequestStorageAccess -> requestStorageAccess()
            is HomeViewModel.HomeEvent.ShowQuotaSheet -> showQuotaSheet(event)
        }
    }

    /** Ask for POST_NOTIFICATIONS when a scan starts; declining never blocks it. */
    private fun ensureNotificationThenScan(event: HomeViewModel.HomeEvent.NavigateToScan) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Permissions.hasNotificationPermission(requireContext())
        ) {
            pendingScan = event
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScan(event)
        }
    }

    private fun startScan(event: HomeViewModel.HomeEvent.NavigateToScan) {
        ScanForegroundService.start(requireContext(), event.mode, event.categories, event.budget)
        findNavController().navigate(
            R.id.scanFragment, null,
            androidx.navigation.navOptions { launchSingleTop = true }
        )
    }

    private fun requestStorageAccess() {
        if (Permissions.hasAllFilesAccess()) { viewModel.refresh(); return }
        // Request the granular media permissions (shows the photos/videos dialog).
        storagePermLauncher.launch(Permissions.storagePermissions())
    }

    /** Optional All-files access — needed to move/remove files in any folder. */
    private fun promptAllFilesAccess() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.all_files_title)
            .setMessage(R.string.all_files_body)
            .setPositiveButton(R.string.all_files_open) { _, _ -> openAllFilesSettings() }
            .setNegativeButton(R.string.all_files_later, null)
            .show()
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val uri = Uri.parse("package:${requireContext().packageName}")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    private fun showQuotaSheet(event: HomeViewModel.HomeEvent.ShowQuotaSheet) {
        val options = arrayOf(
            getString(R.string.quota_watch_ad),
            getString(R.string.quota_scan_within, Formatters.count(event.remaining)),
            getString(R.string.quota_upgrade)
        )
        // Warm the ad now so it's ready if the user picks "watch ad".
        requireContext().appContainer.rewardedAds.preload()
        // setMessage() + setItems() on the same AlertDialog is a known Android
        // conflict: the message view and the items list can't both render, and the
        // message wins, silently dropping the "watch ad" row. Fold the body into the
        // title instead so setItems() actually shows.
        val title = getString(R.string.quota_sheet_title) + "\n\n" +
            getString(R.string.quota_sheet_body, Formatters.count(event.estimate), Formatters.count(event.remaining))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRewardedAd()
                    1 -> viewModel.scanWithinLimit(event.mode, event.categories, event.remaining)
                    2 -> findNavController().navigate(R.id.premiumFragment)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Shows the rewarded interstitial (OTHER §2.1 — a REWARDED format). On a completed
     * view the reward callback ([HomeViewModel.onAdReward]) tops up the daily allowance
     * by +500 files and retries the scan. If no ad is ready we tell the user and kick
     * off a load so the next attempt succeeds.
     */
    private fun showRewardedAd() {
        val ads = requireContext().appContainer.rewardedAds
        if (!ads.isReady) {
            Toast.makeText(requireContext(), R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
            ads.preload()
            return
        }
        ads.show(
            requireActivity(),
            onReward = { viewModel.onAdReward() },
            onUnavailable = {
                Toast.makeText(requireContext(), R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showAbout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun colorForUsage(percent: Int): Int = when {
        percent >= 80 -> R.color.danger
        percent >= 60 -> R.color.warning
        else -> R.color.success
    }

    override fun onDestroyView() {
        _binding = null
        chipBindings.clear()
        super.onDestroyView()
    }
}
