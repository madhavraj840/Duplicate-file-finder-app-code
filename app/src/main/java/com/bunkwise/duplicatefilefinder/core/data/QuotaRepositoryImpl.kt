package com.bunkwise.duplicatefilefinder.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.bunkwise.duplicatefilefinder.core.domain.model.QuotaInfo
import com.bunkwise.duplicatefilefinder.core.domain.repository.QuotaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily file-count quota (ARCHITECTURE §10). Free users get [DAILY_LIMIT] files
 * enumerated per local calendar day, +[AD_BONUS] per rewarded ad. Resets lazily
 * whenever the stored date != today (equality compare, no midnight alarm).
 */
class QuotaRepositoryImpl(
    private val context: Context
) : QuotaRepository {

    override fun observe(): Flow<QuotaInfo> = context.appDataStore.data.map { p ->
        val today = todayString()
        val sameDay = p[PrefKeys.QUOTA_DATE] == today
        QuotaInfo(
            limit = DAILY_LIMIT,
            used = if (sameDay) (p[PrefKeys.FILES_USED] ?: 0) else 0,
            bonus = if (sameDay) (p[PrefKeys.BONUS_FILES] ?: 0) else 0,
            isPremium = p[PrefKeys.IS_PREMIUM] ?: false
        )
    }

    override suspend fun current(): QuotaInfo = observe().first()

    override suspend fun addUsage(files: Int) {
        context.appDataStore.edit { p ->
            val today = todayString()
            val sameDay = p[PrefKeys.QUOTA_DATE] == today
            p[PrefKeys.QUOTA_DATE] = today
            p[PrefKeys.FILES_USED] = (if (sameDay) (p[PrefKeys.FILES_USED] ?: 0) else 0) + files
            if (!sameDay) p[PrefKeys.BONUS_FILES] = 0
        }
    }

    override suspend fun addBonus(files: Int) {
        context.appDataStore.edit { p ->
            val today = todayString()
            val sameDay = p[PrefKeys.QUOTA_DATE] == today
            p[PrefKeys.QUOTA_DATE] = today
            p[PrefKeys.BONUS_FILES] = (if (sameDay) (p[PrefKeys.BONUS_FILES] ?: 0) else 0) + files
            if (!sameDay) p[PrefKeys.FILES_USED] = 0
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val DAILY_LIMIT = 10_000
        const val AD_BONUS = 500
    }
}
