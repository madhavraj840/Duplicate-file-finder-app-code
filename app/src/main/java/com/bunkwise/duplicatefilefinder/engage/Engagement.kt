package com.bunkwise.duplicatefilefinder.engage

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Reviews and reminders, the Night Mode way: hooks only RECORD facts, one daily run decides.
 *
 * This app is used about once a month, so the reminders bring people back for the next clean-up
 * without ever feeling like spam:
 * - One check a day ([EngagementWorker], ~11am); posts only between [POST_FROM_HOUR] and [POST_UNTIL_HOUR].
 * - At most ONE reminder per run, never within [GAP_MS] of the last, and each kind has its own cooldown.
 * - Nothing for someone who never finished a scan, and nothing while a scan or clean-up runs.
 * - [MAX_IGNORED] reminders in a row without the app being opened = silence until it is opened again.
 * - A muted channel or notifications off never burns a cooldown (the post reports false).
 *
 * Review: Play's in-app review, asked only right after a clean-up that worked - after the second one,
 * or the first that freed [BIG_CLEANUP_BYTES]. Then no more than once per [REVIEW_RETRY_MS], [MAX_REVIEW_ASKS] times
 * in all (Play doesn't say whether they rated, and has its own quota on top).
 */
object Engagement {
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    const val GAP_MS = 4 * DAY_MS
    const val MAX_IGNORED = 3
    private const val POST_FROM_HOUR = 10
    private const val POST_UNTIL_HOUR = 21
    private const val CHECK_HOUR = 11

    const val BIG_CLEANUP_BYTES = 100L * 1024 * 1024
    const val REVIEW_RETRY_MS = 30 * DAY_MS
    const val MAX_REVIEW_ASKS = 3

    const val LOW_STORAGE_PERCENT = 10
    const val LEFTOVER_BYTES = 50L * 1024 * 1024
    const val NEW_MEDIA_COUNT = 150

    enum class Kind(val cooldownMs: Long) {
        /** Phone almost full: duplicates are the easiest space to get back. */
        STORAGE_LOW(7 * DAY_MS),
        /** The last scan found plenty, and nothing was cleaned since. Once per scan. */
        LEFTOVER(7 * DAY_MS),
        /** Lots of new photos and videos since the last scan. */
        NEW_MEDIA(14 * DAY_MS),
        /** A month since the last scan. */
        MONTHLY(14 * DAY_MS),
    }

    /** What the daily run knows. Times in ms, 0 = never. */
    data class Facts(
        val now: Long,
        val lastScanAt: Long,
        val lastCleanupAt: Long,
        val leftoverBytes: Long,
        val newMedia: Int,
        val freePercent: Int,
        val lastPostAt: Long,
        val ignored: Int,
        val lastByKind: Map<Kind, Long>,
        val leftoverPostedFor: Long,
        val busy: Boolean,
    )

    /** The one reminder to post now, or null. Pure, so the rules are unit-tested. */
    fun pick(f: Facts): Kind? {
        if (f.busy || f.lastScanAt == 0L || f.ignored >= MAX_IGNORED || f.now - f.lastPostAt < GAP_MS) return null
        val sinceScan = f.now - f.lastScanAt
        fun ready(k: Kind) = f.now - (f.lastByKind[k] ?: 0L) >= k.cooldownMs
        return when {
            f.freePercent < LOW_STORAGE_PERCENT && sinceScan >= 3 * DAY_MS && ready(Kind.STORAGE_LOW) -> Kind.STORAGE_LOW
            f.leftoverBytes >= LEFTOVER_BYTES && f.lastCleanupAt < f.lastScanAt && sinceScan >= 3 * DAY_MS &&
                f.leftoverPostedFor != f.lastScanAt && ready(Kind.LEFTOVER) -> Kind.LEFTOVER
            f.newMedia >= NEW_MEDIA_COUNT && sinceScan >= 7 * DAY_MS && ready(Kind.NEW_MEDIA) -> Kind.NEW_MEDIA
            sinceScan >= 30 * DAY_MS && ready(Kind.MONTHLY) -> Kind.MONTHLY
            else -> null
        }
    }

    /** Whether a clean-up that just finished is the moment to ask for a review. */
    fun reviewDue(cleanups: Int, freedNow: Long, asks: Int, lastAskAt: Long, now: Long): Boolean =
        asks < MAX_REVIEW_ASKS && now - lastAskAt >= REVIEW_RETRY_MS && (cleanups >= 2 || freedNow >= BIG_CLEANUP_BYTES)

    // ---- state ----

    private const val K_LAST_OPEN = "last_open"
    private const val K_IGNORED = "ignored"
    private const val K_LAST_POST = "last_post"
    private const val K_KIND = "last_"
    private const val K_LEFTOVER_FOR = "leftover_for"
    private const val K_CLEANUPS = "cleanups"
    private const val K_LAST_CLEANUP = "last_cleanup"
    private const val K_LAST_FREED = "last_freed"
    private const val K_REVIEW_ASKS = "review_asks"
    private const val K_REVIEW_AT = "review_at"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("engage", Context.MODE_PRIVATE)
        scheduleDaily(context)
    }

    // ---- hooks: record only ----

    /** The app came to the front: reminders worked (or weren't needed), start counting again. */
    fun onAppOpened() {
        prefs.edit().putLong(K_LAST_OPEN, System.currentTimeMillis()).putInt(K_IGNORED, 0).apply()
    }

    /** A clean-up finished and removed something. */
    fun onCleanupDone(freedBytes: Long) {
        prefs.edit()
            .putInt(K_CLEANUPS, prefs.getInt(K_CLEANUPS, 0) + 1)
            .putLong(K_LAST_CLEANUP, System.currentTimeMillis())
            .putLong(K_LAST_FREED, freedBytes)
            .apply()
    }

    /** Tapped Done on a clean-up that worked: Play's review sheet, if this is the moment. */
    fun maybeAskReview(activity: Activity) {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(K_LAST_CLEANUP, 0L) > 10 * 60_000L) return // only right after one
        if (!reviewDue(prefs.getInt(K_CLEANUPS, 0), prefs.getLong(K_LAST_FREED, 0L), prefs.getInt(K_REVIEW_ASKS, 0), prefs.getLong(K_REVIEW_AT, 0L), now)) return
        prefs.edit().putInt(K_REVIEW_ASKS, prefs.getInt(K_REVIEW_ASKS, 0) + 1).putLong(K_REVIEW_AT, now).apply()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, task.result)
        }
    }

    // ---- the daily run ----

    private fun scheduleDaily(context: Context) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, CHECK_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<EngagementWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .build()
        // KEEP: re-scheduling on every launch must not push the next run back.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("engagement", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Called by [EngagementWorker]. [post] returns whether it really showed (false = off/muted). */
    fun runDaily(context: Context, lastScanAt: Long, leftoverBytes: Long, busy: Boolean, post: (Kind, Long) -> Boolean) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in POST_FROM_HOUR until POST_UNTIL_HOUR) return
        val facts = Facts(
            now = now,
            lastScanAt = lastScanAt,
            lastCleanupAt = prefs.getLong(K_LAST_CLEANUP, 0L),
            leftoverBytes = leftoverBytes,
            newMedia = if (lastScanAt == 0L) 0 else newMediaSince(context, lastScanAt),
            freePercent = freePercent(),
            lastPostAt = prefs.getLong(K_LAST_POST, 0L),
            ignored = prefs.getInt(K_IGNORED, 0),
            lastByKind = Kind.entries.associateWith { prefs.getLong(K_KIND + it.name, 0L) },
            leftoverPostedFor = prefs.getLong(K_LEFTOVER_FOR, 0L),
            busy = busy,
        )
        val kind = pick(facts) ?: return
        if (!post(kind, if (kind == Kind.NEW_MEDIA) facts.newMedia.toLong() else leftoverBytes)) return
        prefs.edit()
            .putLong(K_LAST_POST, now)
            .putLong(K_KIND + kind.name, now)
            .putInt(K_IGNORED, facts.ignored + 1)
            .apply { if (kind == Kind.LEFTOVER) putLong(K_LEFTOVER_FOR, lastScanAt) }
            .apply()
    }

    private fun freePercent(): Int = runCatching {
        val s = StatFs(Environment.getExternalStorageDirectory().path)
        (s.availableBytes * 100 / s.totalBytes).toInt()
    }.getOrDefault(100)

    /** Photos and videos added since [sinceMs]. 0 without media access. */
    private fun newMediaSince(context: Context, sinceMs: Long): Int = runCatching {
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DATE_ADDED} > ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            arrayOf(
                (sinceMs / 1000).toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ),
            null,
        )?.use { it.count } ?: 0
    }.getOrDefault(0)
}
