package com.fitnessrider.util

import android.content.Context
import com.fitnessrider.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

object VersionLifecycleManager {
    private const val PREFS_NAME = "fitness_rider_lifecycle_prefs"
    const val KEY_LAST_LAUNCH_TIME = "last_launch_timestamp"
    const val KEY_IS_EXPIRED = "is_version_expired"
    private const val MS_PER_DAY = 86_400_000L

    val buildTimeMs: Long get() = BuildConfig.BUILD_TIME_MS
    val lifecycleDays: Int get() = BuildConfig.LIFECYCLE_DAYS
    val updateUrl: String get() = BuildConfig.UPDATE_URL
    val versionName: String get() = BuildConfig.VERSION_NAME

    val expirationTimeMs: Long
        get() = buildTimeMs + (lifecycleDays * MS_PER_DAY)

    /**
     * Check if current version has exceeded its 30-day lifecycle or clock has been rolled back.
     * When expired, persists KEY_IS_EXPIRED = true so subsequent clock rollbacks cannot unlock.
     */
    fun isExpired(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // 1. Permanent lock check: once marked expired, stays expired regardless of clock
            if (prefs.getBoolean(KEY_IS_EXPIRED, false)) {
                return true
            }

            val lastRecordedTime = prefs.getLong(KEY_LAST_LAUNCH_TIME, 0L)

            // 2. Anti-clock rollback check (> 1 hour backwards from last launch)
            if (lastRecordedTime > 0L && now < (lastRecordedTime - 3600_000L)) {
                prefs.edit()
                    .putBoolean(KEY_IS_EXPIRED, true)
                    .apply()
                return true
            }

            // 3. Expiration date check (30-day lifecycle)
            if (now >= expirationTimeMs) {
                prefs.edit()
                    .putBoolean(KEY_IS_EXPIRED, true)
                    .putLong(KEY_LAST_LAUNCH_TIME, maxOf(now, lastRecordedTime))
                    .apply()
                return true
            }

            // 4. Record newest launch time
            if (now > lastRecordedTime) {
                prefs.edit().putLong(KEY_LAST_LAUNCH_TIME, now).apply()
            }
            return false
        }

        // Fallback for tests without Context
        return now >= expirationTimeMs
    }

    /**
     * Remaining days of validity (0 if expired).
     */
    fun getRemainingDays(context: Context? = null, overrideCurrentTimeMs: Long? = null): Int {
        if (isExpired(context, overrideCurrentTimeMs)) {
            return 0
        }
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
