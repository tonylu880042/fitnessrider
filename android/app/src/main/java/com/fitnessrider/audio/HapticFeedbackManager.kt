package com.fitnessrider.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fitnessrider.model.AppSettings

internal class SynchronizedOnceCache<T : Any> {
    @Volatile
    private var value: T? = null

    fun getOrCompute(compute: () -> T?): T? {
        value?.let { return it }
        synchronized(this) {
            value?.let { return it }
            val resolved = compute()
            value = resolved
            return resolved
        }
    }

    fun reset() {
        value = null
    }
}

object HapticFeedbackManager {
    private val vibratorCache = SynchronizedOnceCache<Vibrator>()

    internal fun resolveVibratorHostContext(context: Context): Context = context.applicationContext

    private fun getVibrator(context: Context): Vibrator? = vibratorCache.getOrCompute {
        val appContext = resolveVibratorHostContext(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
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
        }
    }
}
