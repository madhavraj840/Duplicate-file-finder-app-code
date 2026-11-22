package com.bunkwise.duplicatefilefinder.presentation.common

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Makes a whole list row read as a checkbox/radio button to TalkBack (the visual
 * control inside it is not focusable; the row is the touch target). Call again
 * whenever [checked] changes.
 */
fun View.announceAsCheckable(className: String, checked: Boolean) {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = className
            info.isCheckable = true
            info.isChecked = checked
        }
    })
}
