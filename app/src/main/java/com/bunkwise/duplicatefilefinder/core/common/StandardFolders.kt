package com.bunkwise.duplicatefilefinder.core.common

import android.os.Environment
import java.io.File

/**
 * Curated catalog of well-known device folders offered in Settings for the premium
 * "Scan Any Particular Folder" and "Exclude Folders" pickers. Paths are absolute and
 * match the filesystem paths the [FileEnumeratorImpl] compares against (DATA column),
 * so no fragile SAF tree-URI → path conversion is needed.
 */
object StandardFolders {

    data class Folder(val label: String, val path: String)

    fun all(): List<Folder> {
        val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val candidates = listOf(
            Folder("Camera", "$root/DCIM"),
            Folder("Pictures", "$root/Pictures"),
            Folder("Screenshots", "$root/Pictures/Screenshots"),
            Folder("Downloads", "$root/Download"),
            Folder("Documents", "$root/Documents"),
            Folder("Movies", "$root/Movies"),
            Folder("Music", "$root/Music"),
            Folder("WhatsApp", "$root/Android/media/com.whatsapp/WhatsApp/Media"),
            Folder("Telegram", "$root/Telegram")
        )
        // Prefer folders that actually exist on this device; fall back to the full
        // list if none resolve (e.g. storage permission not yet granted).
        val existing = candidates.filter { runCatching { File(it.path).isDirectory }.getOrDefault(false) }
        return existing.ifEmpty { candidates }
    }
}
