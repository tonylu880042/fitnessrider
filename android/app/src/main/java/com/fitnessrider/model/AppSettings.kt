package com.fitnessrider.model

import android.content.Context
import android.content.SharedPreferences

class AppSettings internal constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fitness_rider_settings", Context.MODE_PRIVATE)

    var isCountdownBeepEnabled: Boolean
        get() = prefs.getBoolean("is_countdown_beep_enabled", true)
        set(value) = prefs.edit().putBoolean("is_countdown_beep_enabled", value).apply()

    var isHapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean("is_haptic_feedback_enabled", true)
        set(value) = prefs.edit().putBoolean("is_haptic_feedback_enabled", value).apply()

    var isAutoPauseBetweenSegmentsEnabled: Boolean
        get() = prefs.getBoolean("is_auto_pause_enabled", false)
        set(value) = prefs.edit().putBoolean("is_auto_pause_enabled", value).apply()

    var crossfadeDurationSeconds: Double
        get() = prefs.getFloat("crossfade_duration_seconds", 2.0f).toDouble()
        set(value) = prefs.edit().putFloat("crossfade_duration_seconds", value.toFloat()).apply()

    var keepScreenAwakeInHUD: Boolean
        get() = prefs.getBoolean("keep_screen_awake", true)
        set(value) = prefs.edit().putBoolean("keep_screen_awake", value).apply()

    companion object {
        @Volatile
        private var INSTANCE: AppSettings? = null

        val CROSSFADE_OPTIONS_SECONDS: List<Double> = listOf(0.0, 1.0, 2.0, 3.0, 5.0, 8.0)

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
