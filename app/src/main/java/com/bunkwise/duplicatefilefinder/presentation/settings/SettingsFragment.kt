package com.bunkwise.duplicatefilefinder.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bunkwise.duplicatefilefinder.ui.Tab
import com.bunkwise.duplicatefilefinder.ui.tabScreen
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks
import androidx.compose.ui.graphics.Color
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.common.Formatters
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.presentation.common.AppLanguages
import com.bunkwise.duplicatefilefinder.presentation.common.ThemePrefs
import com.bunkwise.duplicatefilefinder.presentation.common.TreeUris
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    enum class Key { SCAN_FOLDER, EXCLUDE, SCHEDULE, BIN, ARCHIVE, THEME, LANGUAGE, PREMIUM, RATE, ABOUT }

    /** Rows that only work with Premium (or a 12-hour ad unlock). */
    private val premiumOnly = mapOf(
        Key.SCAN_FOLDER to ProFeature.SCAN_FOLDER,
        Key.EXCLUDE to ProFeature.EXCLUDE,
        Key.SCHEDULE to ProFeature.SCHEDULE,
        Key.ARCHIVE to ProFeature.ARCHIVE,
    )

    private fun locked(key: Key, premium: Boolean) =
        premiumOnly[key]?.let { !Unlocks.unlocked(premium, it) } ?: false

    private val sections = listOf(
        R.string.section_scanning to listOf(Key.SCAN_FOLDER, Key.EXCLUDE, Key.SCHEDULE),
        R.string.section_removing to listOf(Key.BIN, Key.ARCHIVE),
        R.string.section_app to listOf(Key.THEME, Key.LANGUAGE),
        R.string.section_about to listOf(Key.RATE, Key.ABOUT),
    )

    private fun title(key: Key) = when (key) {
        Key.SCAN_FOLDER -> R.string.set_scan_folder
        Key.EXCLUDE -> R.string.set_exclude
        Key.SCHEDULE -> R.string.set_schedule
        Key.BIN -> R.string.recycle_bin
        Key.ARCHIVE -> R.string.set_archive
        Key.THEME -> R.string.set_theme
        Key.LANGUAGE -> R.string.language
        Key.PREMIUM -> R.string.premium
        Key.RATE -> R.string.rate
        Key.ABOUT -> R.string.about
    }

    private fun icon(key: Key) = when (key) {
        Key.SCAN_FOLDER -> AppIcons.Folder
        Key.EXCLUDE -> AppIcons.FolderOff
        Key.SCHEDULE -> AppIcons.Schedule
        Key.BIN -> AppIcons.Bin
        Key.ARCHIVE -> AppIcons.Archive
        Key.THEME -> AppIcons.Theme
        Key.LANGUAGE -> AppIcons.Language
        Key.PREMIUM -> AppIcons.Pro
        Key.RATE -> AppIcons.Star
        Key.ABOUT -> AppIcons.Info
    }

    /** Each row's icon colour, so the list scans by colour before text. */
    private fun tint(key: Key) = when (key) {
        Key.SCAN_FOLDER -> Color(0xFF4A90E2)
        Key.EXCLUDE -> Color(0xFFE8873A)
        Key.SCHEDULE -> Color(0xFF8E6CE0)
        Key.BIN -> Color(0xFFE5534B)
        Key.ARCHIVE -> Color(0xFF2FA59A)
        Key.THEME -> Color(0xFF5C6BC0)
        Key.LANGUAGE -> Color(0xFF3FA75E)
        Key.PREMIUM, Key.RATE -> Color(0xFFF2B01E)
        Key.ABOUT -> Color(0xFF7D8394)
    }

    private enum class FolderTarget { SCAN, EXCLUDE, ARCHIVE }
    private var pendingFolderTarget: FolderTarget? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val target = pendingFolderTarget
        pendingFolderTarget = null
        if (uri == null) {
            if (target == FolderTarget.ARCHIVE) toast(R.string.archive_folder_needed)
            return@registerForActivityResult
        }
        onFolderPicked(target, uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        tabScreen(Tab.SETTINGS) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            Unlocks.version.collectAsStateWithLifecycle().value // re-read the locks after an ad
            SettingsScreen(
                premium = state.settings.isPremium,
                sections = sections.map { (label, keys) -> getString(label) to keys.map { row(it, state) } },
                onRow = ::onRowClicked,
                onWatchAd = { key -> premiumOnly[key]?.let { (activity as? MainActivity)?.watchAd(it) } },
                onPremium = { findNavController().navigate(R.id.premiumFragment) },
            )
        }

    private fun row(key: Key, state: SettingsViewModel.State): SettingRow<Key> {
        val s = state.settings
        val premium = s.isPremium
        val archiveOn = !locked(Key.ARCHIVE, premium) && s.archiveMode && s.archiveTreeUri != null
        val subtitle = when (key) {
            Key.SCAN_FOLDER -> s.scanFolders.firstOrNull() ?: getString(R.string.scan_folder_all)
            Key.EXCLUDE -> if (s.excludeFolders.isEmpty()) getString(R.string.exclude_none)
            else resources.getQuantityString(R.plurals.exclude_count, s.excludeFolders.size, s.excludeFolders.size)
            Key.SCHEDULE -> if (s.scanSchedule == ScanSchedule.OFF) getString(R.string.schedule_off)
            else getString(R.string.schedule_note, getString(scheduleLabel(s.scanSchedule)))
            Key.BIN -> if (state.bin.isEmpty()) getString(R.string.bin_row_empty)
            else resources.getQuantityString(R.plurals.bin_row_summary, state.bin.size, state.bin.size, Formatters.bytes(state.bin.sumOf { it.sizeBytes }))
            Key.ARCHIVE -> if (archiveOn) getString(R.string.archive_dest, TreeUris.displayName(s.archiveTreeUri!!) ?: "")
            else getString(R.string.archive_desc)
            Key.THEME -> getString(themeLabel(ThemePrefs.current(requireContext())))
            Key.LANGUAGE -> AppLanguages.displayName(s.languageTag)
            Key.PREMIUM -> getString(if (premium) R.string.premium_active else R.string.premium_inactive)
            Key.RATE, Key.ABOUT -> null
        }
        return SettingRow(
            key = key,
            title = getString(title(key)),
            subtitle = subtitle,
            icon = icon(key),
            tint = tint(key),
            locked = locked(key, premium),
            switch = if (key == Key.ARCHIVE && !locked(key, premium)) archiveOn else null,
        )
    }

    private fun onRowClicked(key: Key) {
        val s = viewModel.state.value.settings
        if (locked(key, s.isPremium)) {
            (activity as? MainActivity)?.requestUnlock(premiumOnly.getValue(key))
            return
        }
        when (key) {
            Key.SCAN_FOLDER -> onScanFolderClicked(s.scanFolders.firstOrNull())
            Key.EXCLUDE -> onExcludeClicked(s.excludeFolders)
            Key.SCHEDULE -> onScheduleClicked(s.scanSchedule)
            Key.BIN -> findNavController().navigate(R.id.recycleBinFragment)
            // Turning archiving on always asks for the folder, which is also how it's changed.
            Key.ARCHIVE -> if (s.archiveMode && s.archiveTreeUri != null) viewModel.setArchiveMode(false)
            else launchFolderPicker(FolderTarget.ARCHIVE)
            Key.THEME -> showThemePicker()
            Key.LANGUAGE -> showLanguagePicker(s.languageTag)
            Key.PREMIUM -> findNavController().navigate(R.id.premiumFragment)
            Key.RATE -> openPlayStore()
            Key.ABOUT -> showAbout()
        }
    }

    // ---- folders ----

    private fun onScanFolderClicked(current: String?) {
        if (current == null) { launchFolderPicker(FolderTarget.SCAN); return }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_scan_folder)
            .setMessage(current)
            .setPositiveButton(R.string.folder_change) { _, _ -> launchFolderPicker(FolderTarget.SCAN) }
            .setNeutralButton(R.string.folder_clear) { _, _ -> viewModel.setScanFolders(emptySet()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Lists excluded folders; tick the ones to remove, or add another. */
    private fun onExcludeClicked(current: Set<String>) {
        if (current.isEmpty()) { launchFolderPicker(FolderTarget.EXCLUDE); return }
        val folders = current.sorted()
        val checked = BooleanArray(folders.size)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_exclude)
            .setMultiChoiceItems(folders.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.remove_selected) { _, _ ->
                viewModel.setExcludeFolders(folders.filterIndexed { i, _ -> !checked[i] }.toSet())
            }
            .setNeutralButton(R.string.folder_add) { _, _ -> launchFolderPicker(FolderTarget.EXCLUDE) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchFolderPicker(target: FolderTarget) {
        pendingFolderTarget = target
        try {
            folderPicker.launch(null)
        } catch (e: ActivityNotFoundException) {
            pendingFolderTarget = null
            toast(R.string.folder_picker_missing)
        }
    }

    private fun onFolderPicked(target: FolderTarget?, uri: Uri) {
        when (target) {
            FolderTarget.SCAN -> TreeUris.toPath(uri)?.let { viewModel.setScanFolders(setOf(it)) } ?: toast(R.string.folder_unsupported)
            FolderTarget.EXCLUDE -> TreeUris.toPath(uri)?.let {
                viewModel.setExcludeFolders(viewModel.state.value.settings.excludeFolders + it)
            } ?: toast(R.string.folder_unsupported)
            FolderTarget.ARCHIVE -> {
                // Only the archive folder is written through SAF, so only it needs a
                // persisted grant; release the previous one instead of leaking it.
                val resolver = requireContext().contentResolver
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                viewModel.state.value.settings.archiveTreeUri?.takeIf { it != uri.toString() }?.let { old ->
                    runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
                }
                val granted = runCatching { resolver.takePersistableUriPermission(uri, flags) }.isSuccess
                if (granted) viewModel.setArchive(uri.toString()) else toast(R.string.folder_unsupported)
            }
            null -> Unit
        }
    }

    private fun onScheduleClicked(current: ScanSchedule) {
        val options = ScanSchedule.entries
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_schedule)
            .setSingleChoiceItems(options.map { getString(scheduleLabel(it)) }.toTypedArray(), options.indexOf(current)) { dialog, which ->
                viewModel.setScanSchedule(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun scheduleLabel(schedule: ScanSchedule): Int = when (schedule) {
        ScanSchedule.OFF -> R.string.schedule_off
        ScanSchedule.DAILY -> R.string.schedule_daily
        ScanSchedule.WEEKLY -> R.string.schedule_weekly
        ScanSchedule.MONTHLY -> R.string.schedule_monthly
    }

    // ---- app ----

    private fun themeLabel(mode: String): Int = when (mode) {
        ThemePrefs.LIGHT -> R.string.theme_light
        ThemePrefs.DARK -> R.string.theme_dark
        else -> R.string.theme_system
    }

    private fun showThemePicker() {
        val modes = listOf(ThemePrefs.SYSTEM, ThemePrefs.LIGHT, ThemePrefs.DARK)
        val checked = modes.indexOf(ThemePrefs.current(requireContext())).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_theme)
            .setSingleChoiceItems(modes.map { getString(themeLabel(it)) }.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                ThemePrefs.set(requireContext(), modes[which]) // recreates the activity
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguagePicker(currentTag: String) {
        val langs = AppLanguages.entries
        val current = langs.indexOfFirst { it.first == AppLanguages.effectiveTag(currentTag) }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language)
            .setSingleChoiceItems(langs.map { it.second }.toTypedArray(), current) { dialog, which ->
                val tag = langs[which].first
                viewModel.setLanguage(tag)
                AppLanguages.apply(tag)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPlayStore() {
        val pkg = requireContext().packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        runCatching { startActivity(market) }.onFailure { runCatching { startActivity(web) } }
    }

    private fun showAbout() {
        val ctx = requireContext()
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_body, version))
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_LONG).show()

}
