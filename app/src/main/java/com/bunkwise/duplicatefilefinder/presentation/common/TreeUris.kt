package com.bunkwise.duplicatefilefinder.presentation.common

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/** Helpers for folders picked with the system folder picker (SAF tree URIs). */
object TreeUris {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** Human-readable name of a picked folder, e.g. "Download/Archives". */
    fun displayName(treeUri: String): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        docId.substringAfter(':', docId).ifEmpty { docId.substringBefore(':') }
    }.getOrNull()

    /**
     * Absolute filesystem path for a folder picked on the phone's storage or an SD
     * card, or null for anything else (Downloads/Drive/other providers have no stable
     * path, so a folder scan there would silently find nothing).
     */
    fun toPath(uri: Uri): String? = runCatching {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val type = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val root = Environment.getExternalStorageDirectory().path
        when {
            type.equals("primary", ignoreCase = true) -> if (rel.isEmpty()) root else "$root/$rel"
            // The "Documents" root in the picker maps to the Documents folder.
            type.equals("home", ignoreCase = true) -> "$root/${Environment.DIRECTORY_DOCUMENTS}" + if (rel.isEmpty()) "" else "/$rel"
            type.isNotEmpty() && type.contains('-') -> if (rel.isEmpty()) "/storage/$type" else "/storage/$type/$rel"
            else -> null
        }
    }.getOrNull()
}
