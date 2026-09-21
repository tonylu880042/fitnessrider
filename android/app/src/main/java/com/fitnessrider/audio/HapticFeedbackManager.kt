package com.fitnessrider.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fitnessrider.model.AppSettings

object HapticFeedbackManager {
    private var vibrator: Vibrator? = null

    private fun getVibrator(context: Context): Vibrator? {
        if (vibrator != null) return vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator
    }

    fun playCountdownTick(context: Context) {
        val settings = AppSettings.getInstance(context)
        if (!settings.isHapticFeedbackEnabled) return
        val vib = getVibrator(context) ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(45L, 120))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(45L)
            }
        } catch (e: Exception) {
            // Graceful fallback if device restricts vibration
        }
    }

    fun playActionStartImpact(context: Context) {
        val settings = AppSettings.getInstance(context)
        if (!settings.isHapticFeedbackEnabled) return
        val vib = getVibrator(context) ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(180L, 255))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(180L)
            }
        } catch (e: Exception) {
            // Graceful fallback if device restricts vibration
        }
    }
}
