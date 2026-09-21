package com.fitnessrider.audio

import android.media.AudioManager
import android.media.ToneGenerator

object CueCountdownBeepPlayer {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCountdownBeep(secondsLeft: Int) {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    fun playActionStartBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
    }
}
