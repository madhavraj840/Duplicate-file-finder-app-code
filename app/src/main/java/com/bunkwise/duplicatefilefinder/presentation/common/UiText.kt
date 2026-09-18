package com.bunkwise.duplicatefilefinder.presentation.common

import android.content.Context
import androidx.annotation.StringRes

/** Small helper so ViewModels can carry localizable text without a Context. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
        is Raw -> value
    }
}
