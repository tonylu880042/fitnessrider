package com.fitnessrider.audio

import kotlin.math.roundToInt

class TapTempoDetector(
    private val maxHistory: Int = 8,
    private val resetThresholdMs: Long = 2500L
) {
    private val tapTimestamps = mutableListOf<Long>()

    val tapCount: Int
        get() = tapTimestamps.size

    /**
     * Record a tap at the given timestamp (defaults to current system time).
     * Returns the calculated BPM if at least 2 taps are recorded, or null if insufficient taps.
     */
    fun recordTap(now: Long = System.currentTimeMillis()): Double? {
        if (tapTimestamps.isNotEmpty()) {
            val elapsedSinceLast = now - tapTimestamps.last()
            if (elapsedSinceLast > resetThresholdMs) {
                tapTimestamps.clear()
            }
        }

        tapTimestamps.add(now)
        if (tapTimestamps.size > maxHistory) {
            tapTimestamps.removeAt(0)
        }

        return calculateCurrentBpm()
    }

    /**
     * Calculates the rolling average BPM from the recorded timestamps.
     */
    fun calculateCurrentBpm(): Double? {
        if (tapTimestamps.size < 2) return null

        val intervals = mutableListOf<Long>()
        for (i in 1 until tapTimestamps.size) {
            val delta = tapTimestamps[i] - tapTimestamps[i - 1]
            if (delta in 200..2000) { // Valid interval between 30 BPM and 300 BPM
                intervals.add(delta)
            }
        }

        if (intervals.isEmpty()) return null

        val avgIntervalMs = intervals.average()
        if (avgIntervalMs <= 0.0) return null

        val rawBpm = 60_000.0 / avgIntervalMs
        // Round to 1 decimal place
        return ((rawBpm * 10.0).roundToInt() / 10.0).coerceIn(40.0, 240.0)
    }

    fun reset() {
        tapTimestamps.clear()
    }

    companion object {
        fun calculateBpmFromIntervals(intervalsMs: List<Long>): Double? {
            if (intervalsMs.isEmpty()) return null
            val valid = intervalsMs.filter { it in 200..2000 }
            if (valid.isEmpty()) return null
            val avg = valid.average()
            val rawBpm = 60_000.0 / avg
            return ((rawBpm * 10.0).roundToInt() / 10.0).coerceIn(40.0, 240.0)
        }
    }
}
