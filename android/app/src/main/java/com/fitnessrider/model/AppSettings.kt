package com.fitnessrider.model

import android.content.Context
import android.content.SharedPreferences

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fitness_rider_settings", Context.MODE_PRIVATE)

    var isCountdownBeepEnabled: Boolean
        get() = prefs.getBoolean("is_countdown_beep_enabled", true)
        set(value) = prefs.edit().putBoolean("is_countdown_beep_enabled", value).apply()

    var isAutoPauseBetweenSegmentsEnabled: Boolean
        get() = prefs.getBoolean("is_auto_pause_enabled", false)
        set(value) = prefs.edit().putBoolean("is_auto_pause_enabled", value).apply()

    var keepScreenAwakeInHUD: Boolean
        get() = prefs.getBoolean("keep_screen_awake", true)
        set(value) = prefs.edit().putBoolean("keep_screen_awake", value).apply()

    companion object {
        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
