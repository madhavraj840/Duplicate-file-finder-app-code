package com.bunkwise.duplicatefilefinder.core.domain.error

/**
 * Domain error hierarchy (ARCHITECTURE §9). UI-free; each user-facing enum is
 * mapped to a localized string by the presentation layer.
 */
sealed interface AppError {

    enum class Scan : AppError {
        PERMISSION_LOST,
        STORAGE_UNAVAILABLE,
        QUOTA_EXCEEDED,
        CANCELLED,
        UNKNOWN
    }

    enum class Delete : AppError {
        FILE_CHANGED,
        FILE_IN_USE,
        PERMISSION_DENIED,
        BIN_FULL,
        TARGET_MISSING,
        UNKNOWN
    }

    enum class Local : AppError {
        DISK_FULL,
        DB_ERROR,
        UNKNOWN
    }
}
