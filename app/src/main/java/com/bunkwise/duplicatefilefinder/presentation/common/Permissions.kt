package com.bunkwise.duplicatefilefinder.presentation.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Central permission logic (NOTIFICATION.TXT §4). Reading media for scanning works
 * with the granular READ_MEDIA_* runtime permissions (Android 13+) or
 * READ_EXTERNAL_STORAGE (below 33). Moving/deleting arbitrary files additionally
 * needs All-files access (MANAGE_EXTERNAL_STORAGE), which is a Settings toggle, not
 * a runtime dialog — so the app functions (scan-only) with media access and asks
 * for All-files at the point where files are removed.
 */
object Permissions {

    /** Runtime permissions to request for reading the user's files. */
    fun storagePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        // Android 10 and below: moving/deleting files needs the WRITE runtime grant too.
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** All-files access (Settings toggle) — required to move/delete any file. */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** True when files other apps created can be moved/deleted on this Android version. */
    fun canRemoveFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * True once we can read the user's media (media runtime perms OR all-files). On 13+
     * any one media type is enough to scan; denying e.g. audio must not lock the app.
     */
    fun hasStorageAccess(context: Context): Boolean {
        if (hasAllFilesAccess()) return true
        val granted = { p: String -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) storagePermissions().any(granted)
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Documents/other files are only visible to MediaStore queries with All-files access (11+). */
    fun canSeeNonMediaFiles(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens the system "All files access" toggle for this app. */
    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(app) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }
}
