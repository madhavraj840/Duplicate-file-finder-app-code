package com.bunkwise.duplicatefilefinder.core.data

import android.os.Environment
import android.os.StatFs
import com.bunkwise.duplicatefilefinder.core.domain.model.StorageInfo
import com.bunkwise.duplicatefilefinder.core.domain.repository.StorageStatsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads device internal-storage totals via StatFs (no permission required). */
class StorageStatsProviderImpl : StorageStatsProvider {
    override suspend fun current(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            StorageInfo(totalBytes = total, usedBytes = (total - available).coerceAtLeast(0))
        } catch (t: Throwable) {
            StorageInfo(0L, 0L)
        }
    }
}
