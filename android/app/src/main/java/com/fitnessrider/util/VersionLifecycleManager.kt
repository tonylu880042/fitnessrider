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
    const val KEY_REDEEMED_PROMOS = "fitness_rider_redeemed_promos"
    private const val MS_PER_DAY = 86_400_000L

    val buildTimeMs: Long get() = BuildConfig.BUILD_TIME_MS
    val lifecycleDays: Int get() = BuildConfig.LIFECYCLE_DAYS
    val promoTotalTrialDays: Int get() = BuildConfig.PROMO_TOTAL_TRIAL_DAYS
    val updateUrl: String get() = BuildConfig.UPDATE_URL
    val versionName: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE

    fun isPromoCode(rawCode: String): Boolean {
        val code = rawCode.trim().uppercase()
        val regex = Regex("^(\\d{2})FR-NR$")
        val match = regex.find(code) ?: return false
        val codeYear = match.groupValues[1].toIntOrNull() ?: return false
        val currentYear = (Calendar.getInstance().get(Calendar.YEAR)) % 100
        return codeYear == currentYear
    }

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

    fun reconcileFirstLaunchAnchor(context: Context, serverAnchorMs: Long) {
        if (serverAnchorMs <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val localAnchor = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (localAnchor == 0L || serverAnchorMs < localAnchor) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, serverAnchorMs).apply()
        }
    }

    fun isVipActive(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        if (context == null) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_VIP_ACTIVE, false)) {
            val expires = prefs.getLong(KEY_VIP_EXPIRES, 0L)
            val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
            if (expires > now) {
                return true
            }
        }
        return false
    }

    fun isExpired(context: Context? = null, overrideCurrentTimeMs: Long? = null): Boolean {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()

        if (context != null) {
            if (isVipActive(context, overrideCurrentTimeMs)) {
                return false
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (prefs.getBoolean(KEY_IS_EXPIRED, false)) {
                return true
            }

            val lastRecordedTime = prefs.getLong(KEY_LAST_LAUNCH_TIME, 0L)

            if (lastRecordedTime > 0L && now < (lastRecordedTime - 3600_000L)) {
                prefs.edit()
                    .putBoolean(KEY_IS_EXPIRED, true)
                    .apply()
                return true
            }

            val expiration = getExpirationTimeMs(context, overrideCurrentTimeMs)
            if (now >= expiration) {
                prefs.edit()
                    .putBoolean(KEY_IS_EXPIRED, true)
                    .putLong(KEY_LAST_LAUNCH_TIME, maxOf(now, lastRecordedTime))
                    .apply()
                return true
            }

            if (now > lastRecordedTime) {
                prefs.edit().putLong(KEY_LAST_LAUNCH_TIME, now).apply()
            }
            return false
        }

        val expiration = buildTimeMs + (lifecycleDays * MS_PER_DAY)
        return now >= expiration
    }

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

    fun activateLicenseCode(
        context: Context,
        rawCode: String,
        testVipPublicKeyOverride: String? = null,
        overrideCurrentTimeMs: Long? = null
    ): Pair<Boolean, String> {
        val now = overrideCurrentTimeMs ?: System.currentTimeMillis()
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) {
            return Pair(false, "授權碼不能為空")
        }

        val isPromo = isPromoCode(code)
        val vipSerialInfo = if (!isPromo) {
            if (testVipPublicKeyOverride != null) VipSerialVerifier.verify(code, testVipPublicKeyOverride)
            else VipSerialVerifier.verify(code)
        } else null

        if (!isPromo && vipSerialInfo == null) {
            return Pair(false, "無效的授權序號或推廣代碼")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (isPromo) {
            promoBlockedByPaidVipMessage(context, code, overrideCurrentTimeMs)?.let { return Pair(false, it) }

            val redeemedSet = prefs.getStringSet(KEY_REDEEMED_PROMOS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (redeemedSet.contains(code)) {
                return Pair(false, "本設備已兌換過此年度推廣代碼（$code），無法重複領取。")
            }

            val expiresMs = getFirstLaunchTimeMs(context) + (BuildConfig.PROMO_TOTAL_TRIAL_DAYS * MS_PER_DAY)
            if (expiresMs <= now) {
                return Pair(false, "此推廣代碼體驗期限為首次啟用起算 ${BuildConfig.PROMO_TOTAL_TRIAL_DAYS} 天。本設備首次啟用已超過 ${BuildConfig.PROMO_TOTAL_TRIAL_DAYS} 天，無法再使用此代碼，請升級專業年繳版。")
            }

            redeemedSet.add(code)
            prefs.edit()
                .putStringSet(KEY_REDEEMED_PROMOS, redeemedSet)
                .putBoolean(KEY_VIP_ACTIVE, true)
                .putLong(KEY_VIP_EXPIRES, expiresMs)
                .putString(KEY_VIP_CODE, code)
                .putBoolean(KEY_IS_EXPIRED, false)
                .apply()

            return Pair(true, "推廣課程專屬代碼兌換成功！已為此設備啟用 ${BuildConfig.PROMO_TOTAL_TRIAL_DAYS} 天全功能免費 VIP 體驗。")
        }

        if (vipSerialInfo != null) {
            val planMs = vipSerialInfo.planDays.toLong() * MS_PER_DAY
            val expiresMs = now + planMs

            prefs.edit()
                .putBoolean(KEY_VIP_ACTIVE, true)
                .putLong(KEY_VIP_EXPIRES, expiresMs)
                .putString(KEY_VIP_CODE, code)
                .putBoolean(KEY_IS_EXPIRED, false)
                .apply()

            return Pair(true, "授權開通成功！已升級為「專業年繳版 (VIP)」")
        }

        return Pair(false, "無效的授權序號或推廣代碼")
    }

    fun activateVipFromServer(
        context: Context,
        expiresAtIso: String,
        isPromo: Boolean = false,
        code: String? = null
    ) {
        val expiresMs = try {
            java.time.Instant.parse(expiresAtIso).toEpochMilli()
        } catch (e: Exception) {
            return
        }
        val effectiveCode = code ?: if (isPromo) "promo_verified" else "server_verified"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_VIP_ACTIVE, true)
            .putLong(KEY_VIP_EXPIRES, expiresMs)
            .putString(KEY_VIP_CODE, effectiveCode)
            .putBoolean(KEY_IS_EXPIRED, false)
            .apply()
    }

    fun recordPromoRedemption(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val redeemedSet = prefs.getStringSet(KEY_REDEEMED_PROMOS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!redeemedSet.contains(code)) {
            redeemedSet.add(code)
            prefs.edit().putStringSet(KEY_REDEEMED_PROMOS, redeemedSet).apply()
        }
    }

    fun getVipCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VIP_CODE, null)
    }

    fun promoBlockedByPaidVipMessage(context: Context, code: String, overrideCurrentTimeMs: Long? = null): String? {
        if (!isPromoCode(code)) return null
        if (!isVipActive(context, overrideCurrentTimeMs)) return null
        if (isPromoVipCode(getVipCode(context))) return null
        return "此設備已有生效中的專業年繳版 VIP 授權，無需使用體驗推廣代碼。"
    }

    fun isPromoVipCode(code: String?): Boolean {
        val c = code?.trim()?.uppercase() ?: return false
        return c == "PROMO_VERIFIED" || Regex("^\\d{2}FR-NR$").matches(c)
    }

    fun getVipPlanName(context: Context): String {
        return if (isPromoVipCode(getVipCode(context))) {
            "推廣課程專屬版 (${BuildConfig.PROMO_TOTAL_TRIAL_DAYS}天免費)"
        } else {
            "專業年繳版 (VIP)"
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
