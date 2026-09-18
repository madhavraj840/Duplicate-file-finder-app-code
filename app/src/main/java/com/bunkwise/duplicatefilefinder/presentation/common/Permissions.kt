package com.bunkwise.duplicatefilefinder.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Central permission logic (NOTIFICATION.TXT §4). Reading media for scanning works
 * with the granular READ_MEDIA_* runtime permissions (Android 13+) or
 * READ_EXTERNAL_STORAGE (below 33). Moving/deleting arbitrary files additionally
 * needs All-files access (MANAGE_EXTERNAL_STORAGE), which is a Settings toggle, not
 * a runtime dialog — so the app functions (scan-only) with media access and nudges
 * toward All-files for full cleanup power.
 */
object Permissions {

    /** Runtime permissions to request for reading the user's files. */
    fun storagePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** All-files access (Settings toggle) — required to move/delete any file. */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** True once we can read the user's media (media runtime perms OR all-files). */
    fun hasStorageAccess(context: Context): Boolean {
        if (hasAllFilesAccess()) return true
        return storagePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
