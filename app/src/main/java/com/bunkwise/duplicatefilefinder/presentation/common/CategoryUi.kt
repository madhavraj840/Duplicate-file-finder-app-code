package com.bunkwise.duplicatefilefinder.presentation.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory

/** Central mapping of category -> icon/color/label so the app stays consistent. */
object CategoryUi {

    @DrawableRes
    fun icon(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.drawable.ic_image
        FileCategory.VIDEOS -> R.drawable.ic_video
        FileCategory.DOCUMENTS -> R.drawable.ic_doc
        FileCategory.AUDIO -> R.drawable.ic_audio
        FileCategory.OTHER -> R.drawable.ic_other
        FileCategory.ALL -> R.drawable.ic_doc
    }

    @ColorRes
    fun color(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.color.cat_images
        FileCategory.VIDEOS -> R.color.cat_videos
        FileCategory.DOCUMENTS -> R.color.cat_documents
        FileCategory.AUDIO -> R.color.cat_audio
        else -> R.color.cat_others
    }

    @StringRes
    fun label(category: FileCategory): Int = when (category) {
        FileCategory.IMAGES -> R.string.cat_images
        FileCategory.VIDEOS -> R.string.cat_videos
        FileCategory.DOCUMENTS -> R.string.cat_documents
        FileCategory.AUDIO -> R.string.cat_audio
        FileCategory.OTHER -> R.string.cat_others
        FileCategory.ALL -> R.string.cat_all
    }

    fun isVisualMedia(category: FileCategory): Boolean =
        category == FileCategory.IMAGES || category == FileCategory.VIDEOS
}
