package com.fitnessrider.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fitnessrider.model.AppSettings

/**
 * 通用、執行緒安全、只計算一次的快取：用 @Volatile + 雙重檢查鎖定
 * (double-checked locking) 避免併發初始化時的資料競賽與可見性問題。
 *
 * 抽成獨立的小型泛型類別（而不是直接把邏輯寫進 HapticFeedbackManager），是為了讓
 * unit test 能在沒有 Robolectric/Mockito 的情況下，直接對一個真正的併發情境做斷言
 * ——android.os.Vibrator 的建構子是 package-private，app 模組無法建立假的執行個體
 * 來驅動併發測試，但這個泛型快取本身跟 Vibrator 完全無關，可以直接用 String 之類
 * 的型別測試，同時仍是 HapticFeedbackManager 實際使用的那份程式碼。
 */
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

    /** Test-only hook to reset a cache between test cases. */
    fun reset() {
        value = null
    }
}

object HapticFeedbackManager {
    // 只快取「應用程式層級」的 Vibrator。呼叫端 (AudioEngineManager) 目前是拿
    // MainActivity 當 Context 建構的（見 MainActivity.kt: AudioEngineManager(this)），
    // 若直接快取用它取得的系統服務，這個 process 全域單例就會把該 Activity 一路
    // 釘住到 App 存活期間（Activity Context 洩漏）。resolveVibratorHostContext()
    // 一律透過 context.applicationContext 解析，就不會有這個問題。
    //
    // vibratorCache 的第一次初始化可能同時被「音訊進度」coroutine（讀，見
    // AudioEngineManager.checkCueCountdown）與主執行緒（寫）觸碰到，交給
    // SynchronizedOnceCache 處理執行緒安全。
    private val vibratorCache = SynchronizedOnceCache<Vibrator>()

    /**
     * 解析用來查詢系統服務的 Context。internal 可見度是為了讓 unit test 能直接驗證
     * 這裡一定回傳 `context.applicationContext`，而不是呼叫端傳進來的原始
     * （可能是 Activity 的）context。
     */
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
