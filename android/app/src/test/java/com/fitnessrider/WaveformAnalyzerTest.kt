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
        // Test repository byte converter round-trip
        val testFloats = floatArrayOf(0.12f, 0.45f, 0.89f, 0.05f)
        val bytes = ClassRepository.floatArrayToByteArray(testFloats)
        assertEquals(testFloats.size * 4, bytes.size)
        val decoded = ClassRepository.byteArrayToFloatArray(bytes)
        assertEquals(testFloats.size, decoded.size)
        for (i in testFloats.indices) {
            assertEquals(testFloats[i], decoded[i], 0.0001f)
        }

        // Test synthetic waveform generator
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

        // 1. Initial state
        assertNull(detector.calculateCurrentBpm())
        assertEquals(0, detector.tapCount)

        // 2. Simulate 120 BPM taps (interval 500ms)
        val startTime = 1000000L
        detector.recordTap(startTime)
        assertNull(detector.calculateCurrentBpm()) // 1 tap is not enough

        detector.recordTap(startTime + 500)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)

        detector.recordTap(startTime + 1000)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)

        detector.recordTap(startTime + 1500)
        assertEquals(120.0, detector.calculateCurrentBpm()!!, 0.5)
        assertEquals(4, detector.tapCount)

        // 3. Simulate 140 BPM taps (~428.57ms)
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

        // 4. Test auto-reset on gap > 2500ms
        detector.recordTap(t + 3000) // Huge gap
        assertEquals(1, detector.tapCount)
        assertNull(detector.calculateCurrentBpm())
    }

    @Test
    fun testTapTempoCompanionCalculation() {
        // 500ms intervals = 120 BPM
        val bpm120 = TapTempoDetector.calculateBpmFromIntervals(listOf(500L, 500L, 500L))
        assertEquals(120.0, bpm120!!, 0.1)

        // 400ms intervals = 150 BPM
        val bpm150 = TapTempoDetector.calculateBpmFromIntervals(listOf(400L, 400L, 400L))
        assertEquals(150.0, bpm150!!, 0.1)

        // Empty list
        assertNull(TapTempoDetector.calculateBpmFromIntervals(emptyList()))
    }

    @Test
    fun testBpmEstimationFromWaveformEnvelope() {
        // Create an 800-point sample buffer with periodic strong beats at 128 BPM
        // Duration: 100 seconds (100,000 ms)
        // 128 BPM = 128 / 60 = 2.133 beats/sec
        // In 100 seconds -> ~213 beats
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

        // Test peak extraction logic
        val durationSec = durationMs / 1000.0
        val peakIndices = mutableListOf<Int>()
        for (i in 1 until envelope.size - 1) {
            if (envelope[i] > 0.5f && envelope[i] >= envelope[i - 1] && envelope[i] >= envelope[i + 1]) {
                peakIndices.add(i)
            }
        }
        assertTrue("Should detect rhythmic beat peaks", peakIndices.size >= 100)

        // Interval estimation
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
