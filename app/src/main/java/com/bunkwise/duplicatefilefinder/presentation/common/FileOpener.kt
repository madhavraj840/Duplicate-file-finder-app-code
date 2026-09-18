package com.bunkwise.duplicatefilefinder.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bunkwise.duplicatefilefinder.R
import java.io.File

/**
 * Opens a file in its system viewer via a FileProvider content URI so users can
 * verify a file with their own eyes before deleting/restoring (DESIGN §3.5,
 * PSY:40 Trust). Falls back to a toast when nothing can open it.
 */
object FileOpener {

    fun open(context: Context, path: String, mime: String?) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, file.name, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_file)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, file.name, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, file.name, Toast.LENGTH_SHORT).show()
        }
    }
}
