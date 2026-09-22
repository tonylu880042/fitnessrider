package com.fitnessrider

import com.fitnessrider.audio.TapTempoDetector
import com.fitnessrider.audio.WaveformAnalyzer
import com.fitnessrider.data.ClassRepository
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WaveformAnalyzerTest {

    @Test
    fun testSyntheticWaveformGeneration() {
        val testFloats = floatArrayOf(0.12f, 0.45f, 0.89f, 0.05f)
        val bytes = ClassRepository.floatArrayToByteArray(testFloats)
        assertEquals(testFloats.size * 4, bytes.size)
        val decoded = ClassRepository.byteArrayToFloatArray(bytes)
        assertEquals(testFloats.size, decoded.size)
        for (i in testFloats.indices) {
            assertEquals(testFloats[i], decoded[i], 0.0001f)
        }

        val analyzer = WaveformAnalyzer()
        val sampleWaveform = analyzer.generateSyntheticWaveform(800)

        assertEquals(800, sampleWaveform.size)
        for (sample in sampleWaveform) {
            assertTrue("Sample $sample should be in [0.0, 1.0]", sample in 0.0f..1.0f)
        }
    }

    @Test
    fun testTapTempoCalculationAccuracy() {
        val detector = TapTempoDetector()

        assertNull(detector.calculateCurrentBpm())
        assertEquals(0, detector.tapCount)

        val startTime = 1000000L
        detector.recordTap(startTime)
        assertNull(detector.calculateCurrentBpm())

        detector.recordTap(startTime + 500)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)

        detector.recordTap(startTime + 1000)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)

        detector.recordTap(startTime + 1500)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)
        assertEquals(4, detector.tapCount)

        detector.reset()
        assertEquals(0, detector.tapCount)
        var t = startTime
        detector.recordTap(t)
        for (i in 1..4) {
            t += 429
            detector.recordTap(t)
        }
        val calculated140 = detector.calculateCurrentBpm()
        assertNotNull(calculated140)
        assertEquals(140.0, calculated140!!, 1.0)

        detector.recordTap(t + 3000)
        assertEquals(1, detector.tapCount)
        assertNull(detector.calculateCurrentBpm())
    }

    @Test
    fun testTapTempoCompanionCalculation() {
        val bpm120 = TapTempoDetector.calculateBpmFromIntervals(listOf(500L, 500L, 500L))
        assertEquals(120.0, bpm120!!, 0.1)

        val bpm150 = TapTempoDetector.calculateBpmFromIntervals(listOf(400L, 400L, 400L))
        assertEquals(150.0, bpm150!!, 0.1)

        assertNull(TapTempoDetector.calculateBpmFromIntervals(emptyList()))
    }

    @Test
    fun testBpmEstimationFromWaveformEnvelope() {
        val durationMs = 100_000
        val sampleCount = 800
        val beatsPerSec = 128.0 / 60.0
        val totalBeats = (beatsPerSec * (durationMs / 1000.0)).toInt()

        val envelope = FloatArray(sampleCount) { 0.2f }
        for (b in 0 until totalBeats) {
            val beatTimeSec = b / beatsPerSec
            val sampleIdx = ((beatTimeSec / (durationMs / 1000.0)) * sampleCount).toInt()
            if (sampleIdx in envelope.indices) {
                envelope[sampleIdx] = 0.9f
            }
        }

        val durationSec = durationMs / 1000.0
        val peakIndices = mutableListOf<Int>()
        for (i in 1 until envelope.size - 1) {
            if (envelope[i] > 0.5f && envelope[i] >= envelope[i - 1] && envelope[i] >= envelope[i + 1]) {
                peakIndices.add(i)
            }
        }
        assertTrue("Should detect rhythmic beat peaks", peakIndices.size >= 100)

        val intervals = mutableListOf<Double>()
        for (i in 1 until peakIndices.size) {
            val diff = peakIndices[i] - peakIndices[i - 1]
            val timeDiff = (diff.toDouble() / envelope.size) * durationSec
            if (timeDiff in 0.27..1.1) {
                intervals.add(timeDiff)
            }
        }
        val avgInterval = intervals.average()
        val estimatedBpm = 60.0 / avgInterval
        assertEquals(128.0, estimatedBpm, 2.0)
    }
}
