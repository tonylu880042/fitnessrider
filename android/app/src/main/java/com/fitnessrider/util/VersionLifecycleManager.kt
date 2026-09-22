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

    /**
     * 只接受「當年度」的推廣代碼。過去年度已過期；未來年度雖然格式相符，
     * 但尚未生效，一律視為無效 —— 否則像 99FR-NR 這種還沒到來的年份代碼會被誤判為
     * 永遠不過期而永久放行（見 spec 項目 G，backend/src/lib/db.ts 的
     * checkPromoCodeStatus 有相同修正）。
     */
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

    /**
     * 用伺服器回傳的試用起算時間校正本機錨點，取「較早」的一個 —— 這樣 Android 重灌
     * （SharedPreferences 被清空）後，只要伺服器還記得這個 ANDROID_ID 的原始試用起算時間，
     * 試用就不會被重置（spec 項目 D）。離線時完全不呼叫這個方法，直接沿用本機值即可。
     */
    fun reconcileFirstLaunchAnchor(context: Context, serverAnchorMs: Long) {
        if (serverAnchorMs <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val localAnchor = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (localAnchor == 0L || serverAnchorMs < localAnchor) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, serverAnchorMs).apply()
        }
    }

    /**
     * Check if device has an active VIP license.
     *
     * A missing/zero `expires` value must NOT be treated as "no expiry" (that used to let a
     * license row with a lost/never-set expiry date grant unlimited VIP access forever).
     * Missing expiry now means "not VIP" (spec 項目 C).
     */
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

    /**
     * Check if current version / trial has exceeded its duration or clock has been rolled back.
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

            // 3. Expiration date check (trial duration from first launch)
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
     * Activate app via license code or promotional code (Offline algorithmic check + persistence).
     *
     * 推廣代碼：延長一次到「總共 [BuildConfig.PROMO_TOTAL_TRIAL_DAYS] 天」，不是在現在的時間
     * 上再加 30 天，因此到期時間 = 裝置試用起算時間 + PROMO_TOTAL_TRIAL_DAYS（與後端
     * activateLicenseWithCode 的計算方式一致）。
     * 付費 VIP：改用 [VipSerialVerifier] 做 P-256 驗簽，天數由序號內容決定，
     * 不再有任何寫死序號或前綴規則。
     */
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
        // testVipPublicKeyOverride 只給測試用（見 FitnessRiderAndroidTest），讓測試可以用自己
        // 產生的金鑰對走完整條開通流程，不必碰正式私鑰；正式呼叫端一律不傳這個參數。
        val vipSerialInfo = if (!isPromo) {
            if (testVipPublicKeyOverride != null) VipSerialVerifier.verify(code, testVipPublicKeyOverride)
            else VipSerialVerifier.verify(code)
        } else null

        if (!isPromo && vipSerialInfo == null) {
            return Pair(false, "無效的授權序號或推廣代碼")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (isPromo) {
            val redeemedSet = prefs.getStringSet(KEY_REDEEMED_PROMOS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (redeemedSet.contains(code)) {
                return Pair(false, "本設備已兌換過此年度推廣代碼（$code），無法重複領取。")
            }

            val expiresMs = getFirstLaunchTimeMs(context) + (BuildConfig.PROMO_TOTAL_TRIAL_DAYS * MS_PER_DAY)
            if (expiresMs <= now) {
                return Pair(false, "此推廣代碼體驗期限為首次啟用起算 ${BuildConfig.PROMO_TOTAL_TRIAL_DAYS} 天。本設備首次啟用已超過 30 天，無法再使用此代碼，請升級專業年繳版。")
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

    /**
     * 信任伺服器已驗證過身分的授權結果，直接寫入本機 VIP 狀態
     * （帳號密碼換機、線上開通推廣代碼或已在伺服器驗過簽章的序號換機成功後呼叫）。
     */
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

    /**
     * 將推廣代碼寫入本地已兌換清單，防止單機重複兌換（與 iOS 對齊，spec 項目 5）。
     */
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

    /**
     * 取得目前授權的方案名稱，若為推廣代碼則正確顯示體驗版名稱（spec 項目 4）。
     */
    fun getVipPlanName(context: Context): String {
        val code = getVipCode(context) ?: ""
        return if (isPromoCode(code) || code == "promo_verified") {
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
