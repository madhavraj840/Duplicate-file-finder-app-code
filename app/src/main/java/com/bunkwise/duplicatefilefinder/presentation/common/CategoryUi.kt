package com.bunkwise.duplicatefilefinder.presentation.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.ui.icons.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector

/** Central mapping of file type -> icon/label so every screen names types the same way. */
object CategoryUi {

    /** Placeholder icon for files that have no thumbnail (documents, audio, other). */
    @DrawableRes
    fun icon(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.drawable.ic_image
        FileCategory.VIDEOS -> R.drawable.ic_video
        FileCategory.AUDIO -> R.drawable.ic_audio
        FileCategory.DOCUMENTS, FileCategory.ALL -> R.drawable.ic_doc
        FileCategory.OTHER -> R.drawable.ic_other
    }

    /** The Compose icon for a type (same mapping as [icon]). */
    fun vector(category: FileCategory): ImageVector = when (category) {
        FileCategory.IMAGES -> AppIcons.Image
        FileCategory.VIDEOS -> AppIcons.Video
        FileCategory.AUDIO -> AppIcons.Audio
        FileCategory.DOCUMENTS, FileCategory.ALL -> AppIcons.Document
        FileCategory.OTHER -> AppIcons.OtherFile
    }

    @StringRes
    fun label(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.string.cat_images
        FileCategory.VIDEOS -> R.string.cat_videos
        FileCategory.DOCUMENTS -> R.string.cat_documents
        FileCategory.AUDIO -> R.string.cat_audio
        FileCategory.OTHER -> R.string.cat_other
        FileCategory.ALL -> R.string.cat_all
    }

    fun isVisualMedia(category: FileCategory): Boolean =
        category == FileCategory.IMAGES || category == FileCategory.VIDEOS
}
