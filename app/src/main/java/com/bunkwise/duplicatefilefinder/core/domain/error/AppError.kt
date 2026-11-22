package com.bunkwise.duplicatefilefinder.core.domain.error

/**
 * Domain error hierarchy (ARCHITECTURE §9). UI-free; each user-facing enum is
 * mapped to a localized string by the presentation layer.
 */
sealed interface AppError {

    enum class Scan : AppError {
        UNKNOWN
    }

    enum class Delete : AppError {
        FILE_CHANGED,
        PERMISSION_DENIED,
        /** Not enough free space to write the bin copy / archive. */
        BIN_FULL,
        TARGET_MISSING,
        /** Archive mode is on but no (still accessible) destination folder is set. */
        ARCHIVE_FOLDER_MISSING,
        UNKNOWN
    }
}
