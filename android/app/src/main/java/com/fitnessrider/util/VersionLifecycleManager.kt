package com.fitnessrider.util

import android.content.Context
import com.fitnessrider.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

object VersionLifecycleManager {
    private const val PREFS_NAME = "fitness_rider_lifecycle_prefs"
    const val KEY_LAST_LAUNCH_TIME = "last_launch_timestamp"
    const val KEY_IS_EXPIRED = "is_version_expired"
    const val KEY_FIRST_LAUNCH_TIME = "first_launch_timestamp"
    const val KEY_VIP_ACTIVE = "fitness_rider_vip_active"
    const val KEY_VIP_EXPIRES = "fitness_rider_vip_expires"
    const val KEY_VIP_CODE = "fitness_rider_vip_code"
    private const val MS_PER_DAY = 86_400_000L

    val buildTimeMs: Long get() = BuildConfig.BUILD_TIME_MS
    val lifecycleDays: Int get() = BuildConfig.LIFECYCLE_DAYS
    val updateUrl: String get() = BuildConfig.UPDATE_URL
    val versionName: String get() = BuildConfig.VERSION_NAME

    fun getFirstLaunchTimeMs(context: Context? = null, overrideCurrentTimeMs: Long? = null): Long {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
            if (stored > 0L) return stored

            val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, now).apply()
            return now
        }
        return buildTimeMs
    }

    fun getExpirationTimeMs(context: Context? = null, overrideCurrentTimeMs: Long? = null): Long {
        val anchor = getFirstLaunchTimeMs(context, overrideCurrentTimeMs)
        return anchor + (lifecycleDays * MS_PER_DAY)
    }

    /**
     * Check if device has an active VIP license.
     */
    fun isVipActive(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        if (context == null) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_VIP_ACTIVE, false)) {
            val expires = prefs.getLong(KEY_VIP_EXPIRES, 0L)
            val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
            if (expires == 0L || expires > now) {
                return true
            }
        }
        return false
    }

    /**
     * Check if current version / 30-day trial has exceeded its duration or clock has been rolled back.
     * When expired, persists KEY_IS_EXPIRED = true so subsequent clock rollbacks cannot unlock.
     */
    fun isExpired(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()

        if (context != null) {
            // VIP license completely bypasses trial expiration
            if (isVipActive(context, overrideCurrentTimeMs)) {
                return false
            }

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

            // 3. Expiration date check (30-day trial from first launch)
            val expiration = getExpirationTimeMs(context, overrideCurrentTimeMs)
            if (now >= expiration) {
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

        // Fallback for tests without Context (based on buildTimeMs)
        val expiration = buildTimeMs + (lifecycleDays * MS_PER_DAY)
        return now >= expiration
    }

    /**
     * Remaining days of validity (0 if expired).
     */
    fun getRemainingDays(context: Context? = null, overrideCurrentTimeMs: Long? = null): Int {
        if (context != null && isVipActive(context, overrideCurrentTimeMs)) {
            return 365
        }
        if (isExpired(context, overrideCurrentTimeMs)) {
            return 0
        }
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
        val expiration = if (context != null) getExpirationTimeMs(context, overrideCurrentTimeMs) else (buildTimeMs + (lifecycleDays * MS_PER_DAY))
        val remainingMs = expiration - now
        return if (remainingMs <= 0) 0 else Math.ceil(remainingMs.toDouble() / MS_PER_DAY).toInt()
    }

    /**
     * Activate app via license code (Offline algorithmic check + persistence).
     */
    fun activateLicenseCode(context: Context, rawCode: String): Pair<Boolean, String> {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) {
            return Pair(false, "授權碼不能為空")
        }

        val isValidVIP = code == "RIDER-VIP-2026-PASS" ||
                         code == "FITNESS-PRO-ANNUAL-KEY" ||
                         (code.startsWith("RIDER-VIP-") && code.length >= 14)

        if (isValidVIP) {
            val oneYearMs = 365L * MS_PER_DAY
            val expiresMs = System.currentTimeMillis() + oneYearMs
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            prefs.edit()
                .putBoolean(KEY_VIP_ACTIVE, true)
                .putLong(KEY_VIP_EXPIRES, expiresMs)
                .putString(KEY_VIP_CODE, code)
                .putBoolean(KEY_IS_EXPIRED, false)
                .apply()

            return Pair(true, "授權開通成功！已升級為「專業年繳版 (VIP)」")
        } else {
            return Pair(false, "無效的授權序號，請檢查格式或聯絡客服")
        }
    }

    fun getFormattedBuildDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(buildTimeMs))
    }

    fun getFormattedExpirationDate(context: Context? = null): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expiration = if (context != null) getExpirationTimeMs(context) else (buildTimeMs + (lifecycleDays * MS_PER_DAY))
        return sdf.format(Date(expiration))
    }

    fun getFormattedTrialStartDate(context: Context? = null): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val start = if (context != null) getFirstLaunchTimeMs(context) else buildTimeMs
        return sdf.format(Date(start))
    }
}
