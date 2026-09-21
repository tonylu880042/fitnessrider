package com.fitnessrider.util

import android.content.Context
import com.fitnessrider.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

object VersionLifecycleManager {
    private const val PREFS_NAME = "fitness_rider_lifecycle_prefs"
    private const val KEY_LAST_LAUNCH_TIME = "last_launch_timestamp"
    private const val MS_PER_DAY = 86_400_000L

    val buildTimeMs: Long get() = BuildConfig.BUILD_TIME_MS
    val lifecycleDays: Int get() = BuildConfig.LIFECYCLE_DAYS
    val updateUrl: String get() = BuildConfig.UPDATE_URL
    val versionName: String get() = BuildConfig.VERSION_NAME

    val expirationTimeMs: Long
        get() = buildTimeMs + (lifecycleDays * MS_PER_DAY)

    /**
     * Check if current version has exceeded its 30-day lifecycle or clock has been rolled back.
     */
    fun isExpired(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()

        // 1. Check expiration date (30-day lifecycle)
        if (now >= expirationTimeMs) {
            return true
        }

        // 2. Anti-clock rollback check
        if (context != null && overrideCurrentTimeMs == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastRecordedTime = prefs.getLong(KEY_LAST_LAUNCH_TIME, 0L)

            // If time went backwards by more than 1 hour (allowing minor timezone/NTP shifts)
            if (lastRecordedTime > 0L && now < (lastRecordedTime - 3600_000L)) {
                return true // Clock rollback detected
            }

            // If last recorded time was already past expiration, stay expired
            if (lastRecordedTime >= expirationTimeMs) {
                return true
            }

            // Update last launch time
            if (now > lastRecordedTime) {
                prefs.edit().putLong(KEY_LAST_LAUNCH_TIME, now).apply()
            }
        }

        return false
    }

    /**
     * Remaining days of validity (0 if expired).
     */
    fun getRemainingDays(overrideCurrentTimeMs: Long? = null): Int {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
        val remainingMs = expirationTimeMs - now
        return if (remainingMs <= 0) 0 else Math.ceil(remainingMs.toDouble() / MS_PER_DAY).toInt()
    }

    fun getFormattedBuildDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(buildTimeMs))
    }

    fun getFormattedExpirationDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(expirationTimeMs))
    }
}
