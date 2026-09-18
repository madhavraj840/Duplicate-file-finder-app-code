package com.bunkwise.duplicatefilefinder.core.domain.usecase

import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.core.domain.repository.FileEnumerator
import com.bunkwise.duplicatefilefinder.core.domain.repository.QuotaRepository

/** Decision returned before a scan starts (ARCHITECTURE §10, CHECK POINT A). */
sealed interface QuotaDecision {
    /** Enough quota remains; scan may run to completion. */
    data object Allowed : QuotaDecision
    /** Estimate exceeds remaining quota; the QuotaSheet should be shown. */
    data class Exceeds(val estimate: Int, val remaining: Int) : QuotaDecision
}

/**
 * Estimates the file count for the chosen categories and compares against the
 * remaining daily quota. Premium users are always [QuotaDecision.Allowed].
 */
class CheckScanQuotaUseCase(
    private val enumerator: FileEnumerator,
    private val quota: QuotaRepository
) {
    suspend operator fun invoke(categories: Set<FileCategory>): QuotaDecision {
        val q = quota.current()
        if (q.isPremium) return QuotaDecision.Allowed
        val estimate = enumerator.estimateCount(categories)
        return if (estimate <= q.remaining) {
            QuotaDecision.Allowed
        } else {
            QuotaDecision.Exceeds(estimate, q.remaining)
        }
    }
}
