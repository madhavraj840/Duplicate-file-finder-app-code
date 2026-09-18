package com.bunkwise.duplicatefilefinder.presentation.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.core.domain.model.ScanSchedule
import com.bunkwise.duplicatefilefinder.core.domain.repository.AppSettings
import com.bunkwise.duplicatefilefinder.databinding.FragmentSettingsBinding
import com.bunkwise.duplicatefilefinder.databinding.ItemSettingsRowBinding
import com.bunkwise.duplicatefilefinder.presentation.common.AppLanguages
import com.bunkwise.duplicatefilefinder.presentation.common.ThemePrefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels { requireContext().appContainer.viewModelFactory }

    private enum class RowKey { SCAN_FOLDER, EXCLUDE, RECYCLE_BIN, SCHEDULE, AUTO_CLEANUP, ARCHIVE, THEME, LANGUAGE, RATE, ABOUT }

    /** Which pending pro action a folder-tree pick should be applied to. */
    private enum class FolderTarget { SCAN, EXCLUDE, ARCHIVE }
    private var pendingFolderTarget: FolderTarget? = null

    private data class Row(val key: RowKey, val icon: Int, val label: Int, val pro: Boolean, val hasSwitch: Boolean = false)

    private val smartRows = listOf(
        Row(RowKey.SCAN_FOLDER, R.drawable.ic_folder, R.string.set_scan_folder, true),
        Row(RowKey.EXCLUDE, R.drawable.ic_folder_off, R.string.set_exclude, true),
        Row(RowKey.RECYCLE_BIN, R.drawable.ic_trash, R.string.set_recycle_bin, false),
        Row(RowKey.SCHEDULE, R.drawable.ic_calendar, R.string.set_schedule, true),
        Row(RowKey.AUTO_CLEANUP, R.drawable.ic_magic, R.string.set_auto_cleanup, true, hasSwitch = true),
        Row(RowKey.ARCHIVE, R.drawable.ic_archive, R.string.set_archive, true, hasSwitch = true)
    )
    private val generalRows = listOf(
        Row(RowKey.THEME, R.drawable.ic_theme, R.string.set_theme, false),
        Row(RowKey.LANGUAGE, R.drawable.ic_globe, R.string.language, false),
        Row(RowKey.RATE, R.drawable.ic_star, R.string.rate_us, false),
        Row(RowKey.ABOUT, R.drawable.ic_info, R.string.about_us, false)
    )

    private val rowBindings = mutableMapOf<RowKey, ItemSettingsRowBinding>()

    /** SAF folder-tree picker; the result is routed by [pendingFolderTarget]. */
    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val target = pendingFolderTarget
            pendingFolderTarget = null
            if (uri == null) return@registerForActivityResult
            onFolderPicked(target, uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildRows(smartRows, binding.smartGroup)
        buildRows(generalRows, binding.generalGroup)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun buildRows(rows: List<Row>, container: LinearLayout) {
        rows.forEachIndexed { index, row ->
            val item = ItemSettingsRowBinding.inflate(layoutInflater, container, false)
            item.rowIcon.setImageResource(row.icon)
            item.rowLabel.setText(row.label)
            item.rowDivider.isVisible = index != rows.lastIndex
            item.rowContent.setOnClickListener { onRowClicked(row) }
            container.addView(item.root)
            rowBindings[row.key] = item
        }
    }

    private fun render(settings: AppSettings) {
        for (row in smartRows) {
            val item = rowBindings[row.key] ?: continue
            val premium = settings.isPremium
            item.proPill.isVisible = row.pro && !premium
            val showSwitch = row.hasSwitch && premium
            item.rowSwitch.isVisible = showSwitch
            item.rowChevron.isVisible = !showSwitch
            if (showSwitch) {
                item.rowSwitch.setOnCheckedChangeListener(null)
                item.rowSwitch.isChecked = when (row.key) {
                    RowKey.ARCHIVE -> settings.archiveMode
                    RowKey.AUTO_CLEANUP -> settings.autoCleanup
                    else -> false
                }
                item.rowSwitch.setOnCheckedChangeListener { _, checked ->
                    when (row.key) {
                        RowKey.ARCHIVE -> onArchiveToggled(checked, settings)
                        RowKey.AUTO_CLEANUP -> viewModel.setAutoCleanup(checked)
                        else -> {}
                    }
                }
            }
            // Subtitle shows the current selection for premium users.
            item.rowSubtitle.isVisible = premium && subtitleFor(row.key, settings) != null
            subtitleFor(row.key, settings)?.let { item.rowSubtitle.text = it }
        }
        // Language row (General group) always shows the current language.
        rowBindings[RowKey.LANGUAGE]?.let { item ->
            item.rowSubtitle.isVisible = true
            item.rowSubtitle.text = AppLanguages.displayName(settings.languageTag)
        }
        rowBindings[RowKey.THEME]?.let { item ->
            item.rowSubtitle.isVisible = true
            item.rowSubtitle.text = getString(themeLabel(ThemePrefs.current(requireContext())))
        }
    }

    private fun subtitleFor(key: RowKey, s: AppSettings): String? = when (key) {
        RowKey.SCAN_FOLDER -> s.scanFolders.firstOrNull()?.let { getString(R.string.folder_scanning, it) }
            ?: getString(R.string.folder_whole_device)
        RowKey.EXCLUDE -> if (s.excludeFolders.isEmpty()) null
        else getString(R.string.folder_excluded_count, s.excludeFolders.size)
        RowKey.SCHEDULE -> getString(scheduleLabel(s.scanSchedule))
        RowKey.ARCHIVE -> if (s.archiveMode && s.archiveTreeUri != null)
            getString(R.string.archive_dest, folderName(s.archiveTreeUri)) else null
        else -> null
    }

    private fun onRowClicked(row: Row) {
        val premium = viewModel.state.value.isPremium
        if (row.pro && !premium) {
            findNavController().navigate(R.id.premiumFragment)
            return
        }
        when (row.key) {
            RowKey.SCAN_FOLDER -> onScanFolderClicked()
            RowKey.EXCLUDE -> onExcludeClicked()
            RowKey.SCHEDULE -> onScheduleClicked()
            RowKey.RECYCLE_BIN -> findNavController().navigate(R.id.recycleBinFragment)
            RowKey.THEME -> showThemePicker()
            RowKey.LANGUAGE -> showLanguagePicker()
            RowKey.ABOUT -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_title).setMessage(R.string.about_body)
                .setPositiveButton(android.R.string.ok, null).show()
            RowKey.RATE -> openPlayStore()
            else -> Unit // switch rows handled by the switch
        }
    }

    // ---- Scan / exclude folders ----

    private fun onScanFolderClicked() {
        val current = viewModel.state.value.scanFolders.firstOrNull()
        if (current == null) {
            launchFolderPicker(FolderTarget.SCAN)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_scan_folder)
                .setMessage(getString(R.string.folder_scanning, current))
                .setPositiveButton(R.string.folder_change) { _, _ -> launchFolderPicker(FolderTarget.SCAN) }
                .setNeutralButton(R.string.folder_clear) { _, _ -> viewModel.setScanFolders(emptySet()) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun onExcludeClicked() {
        val current = viewModel.state.value.excludeFolders
        if (current.isEmpty()) {
            launchFolderPicker(FolderTarget.EXCLUDE)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_exclude)
                .setMessage(current.joinToString("\n"))
                .setPositiveButton(R.string.folder_add) { _, _ -> launchFolderPicker(FolderTarget.EXCLUDE) }
                .setNeutralButton(R.string.folder_clear) { _, _ -> viewModel.setExcludeFolders(emptySet()) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun onScheduleClicked() {
        val options = ScanSchedule.entries
        val labels = options.map { getString(scheduleLabel(it)) }.toTypedArray()
        val checked = options.indexOf(viewModel.state.value.scanSchedule)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_schedule)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setScanSchedule(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onArchiveToggled(enabled: Boolean, settings: AppSettings) {
        viewModel.setArchiveMode(enabled)
        // Turning archive ON needs a destination folder for the zip.
        if (enabled && settings.archiveTreeUri == null) launchFolderPicker(FolderTarget.ARCHIVE)
    }

    private fun launchFolderPicker(target: FolderTarget) {
        pendingFolderTarget = target
        try {
            folderPicker.launch(null)
        } catch (e: ActivityNotFoundException) {
            pendingFolderTarget = null
            Toast.makeText(requireContext(), R.string.folder_picker_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onFolderPicked(target: FolderTarget?, uri: Uri) {
        // Persist access so the choice survives restarts.
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        when (target) {
            FolderTarget.SCAN -> treeUriToPath(uri)?.let { viewModel.setScanFolders(setOf(it)) }
                ?: unsupportedFolder()
            FolderTarget.EXCLUDE -> treeUriToPath(uri)?.let {
                viewModel.setExcludeFolders(viewModel.state.value.excludeFolders + it)
            } ?: unsupportedFolder()
            FolderTarget.ARCHIVE -> viewModel.setArchiveTreeUri(uri.toString())
            null -> Unit
        }
    }

    private fun unsupportedFolder() =
        Toast.makeText(requireContext(), R.string.folder_unsupported, Toast.LENGTH_LONG).show()

    /** Best-effort SAF tree URI -> absolute path (primary + named volumes). */
    private fun treeUriToPath(uri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        val type = parts[0]
        val rel = parts.getOrElse(1) { "" }
        when {
            type.equals("primary", true) -> {
                val root = Environment.getExternalStorageDirectory().path
                if (rel.isEmpty()) root else "$root/$rel"
            }
            type.isNotEmpty() -> if (rel.isEmpty()) "/storage/$type" else "/storage/$type/$rel"
            else -> null
        }
    }.getOrNull()

    private fun folderName(treeUri: String): String = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        docId.substringAfterLast('/').ifEmpty { docId.substringAfter(':', docId) }
    }.getOrDefault(getString(R.string.archive_dest_default))

    private fun scheduleLabel(schedule: ScanSchedule): Int = when (schedule) {
        ScanSchedule.OFF -> R.string.schedule_off
        ScanSchedule.DAILY -> R.string.schedule_daily
        ScanSchedule.WEEKLY -> R.string.schedule_weekly
        ScanSchedule.MONTHLY -> R.string.schedule_monthly
    }

    private fun themeLabel(mode: String): Int = when (mode) {
        ThemePrefs.LIGHT -> R.string.theme_light
        ThemePrefs.DARK -> R.string.theme_dark
        else -> R.string.theme_system
    }

    private fun showThemePicker() {
        val modes = listOf(ThemePrefs.SYSTEM, ThemePrefs.LIGHT, ThemePrefs.DARK)
        val labels = modes.map { getString(themeLabel(it)) }.toTypedArray()
        val checked = modes.indexOf(ThemePrefs.current(requireContext())).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                // Recreates started activities with the new uiMode.
                ThemePrefs.set(requireContext(), modes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguagePicker() {
        val langs = AppLanguages.entries
        val names = langs.map { it.second }.toTypedArray()
        val current = langs.indexOfFirst { it.first == viewModel.state.value.languageTag }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(names, current) { dialog, which ->
                val tag = langs[which].first
                viewModel.setLanguage(tag)
                AppLanguages.apply(tag)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.rate_us, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        rowBindings.clear()
        super.onDestroyView()
    }
}
