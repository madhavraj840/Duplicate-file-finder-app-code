package com.bunkwise.duplicatefilefinder.core.common

import com.bunkwise.duplicatefilefinder.core.domain.error.AppError

/**
 * Typed result used across the domain/data boundary (ARCHITECTURE §9).
 * Expected failures are represented as [Error]; unexpected exceptions are
 * caught at the data/engine boundary and mapped into an [AppError].
 * CancellationException must NEVER be wrapped here — always rethrown.
 */
sealed interface Result<out D, out E : AppError> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : AppError>(val error: E) : Result<Nothing, E>
}
