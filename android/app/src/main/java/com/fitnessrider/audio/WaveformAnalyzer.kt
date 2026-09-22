package com.fitnessrider.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.*

data class WaveformResult(
    val samples: FloatArray,
    val durationMs: Int,
    val bpm: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformResult
        return samples.contentEquals(other.samples) && durationMs == other.durationMs && bpm == other.bpm
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + durationMs
        result = 31 * result + bpm.hashCode()
        return result
    }
}

class WaveformAnalyzer(private val repository: ClassRepository? = null) {

    suspend fun analyzeWaveform(fileName: String, targetPoints: Int = 800): WaveformResult = withContext(Dispatchers.IO) {
        val cached = repository?.getWaveform(fileName)
        if (cached != null) {
            return@withContext WaveformResult(
                samples = cached.first,
                durationMs = cached.second,
                bpm = cached.third
            )
        }

        if (MusicSource.isExternalUri(fileName)) {
            val context = repository?.context
                ?: return@withContext generateFallbackResult(targetPoints)
            return@withContext try {
                val result = extractWaveformFromUri(context, Uri.parse(fileName), targetPoints)
                repository.saveWaveform(fileName, result.samples, result.durationMs, result.bpm)
                result
            } catch (e: Exception) {
                Log.e("WaveformAnalyzer", "Failed to decode external audio $fileName, using fallback", e)
                generateFallbackResult(targetPoints)
            }
        }

        val musicDir = repository?.musicDirectory
        val file = if (musicDir != null) File(musicDir, fileName) else File(fileName)
        if (!file.exists() || file.length() == 0L) {
            return@withContext generateFallbackResult(targetPoints)
        }

        try {
            val result = extractWaveformFromFile(file, targetPoints)
            repository?.saveWaveform(fileName, result.samples, result.durationMs, result.bpm)
            result
        } catch (e: Exception) {
            Log.e("WaveformAnalyzer", "Failed to decode audio file $fileName, using fallback", e)
            generateFallbackResult(targetPoints)
        }
    }

    private fun generateFallbackResult(targetPoints: Int) = WaveformResult(
        samples = generateSyntheticWaveform(targetPoints),
        durationMs = 300_000,
        bpm = 128.0
    )

    fun extractWaveformFromFile(file: File, targetPoints: Int = 800): WaveformResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return decodeWaveform(extractor, targetPoints)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun extractWaveformFromUri(context: Context, uri: Uri, targetPoints: Int = 800): WaveformResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return decodeWaveform(extractor, targetPoints)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeWaveform(extractor: MediaExtractor, targetPoints: Int): WaveformResult {
        var codec: MediaCodec? = null
        try {
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                return WaveformResult(generateSyntheticWaveform(targetPoints), 300_000, 128.0)
            }

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val durationMs = (durationUs / 1000).toInt().coerceAtLeast(1000)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bucketUs = (durationUs.toDouble() / targetPoints).coerceAtLeast(1.0)
            val bucketPeaks = FloatArray(targetPoints)
            val info = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false
            val kTimeoutUs = 5000L

            while (!isOutputEos) {
                if (!isInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(kTimeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEos = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, kTimeoutUs)
                if (outputIndex >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEos = true
                    }
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val bucketIdx = (info.presentationTimeUs / bucketUs).toInt().coerceIn(0, targetPoints - 1)
                        val shortBuffer = outputBuffer.asShortBuffer()
                        var maxPeak = bucketPeaks[bucketIdx]

                        val count = shortBuffer.remaining()
                        var i = 0
                        while (i < count) {
                            val sampleVal = abs(shortBuffer.get(i).toInt()) / 32768f
                            if (sampleVal > maxPeak) maxPeak = sampleVal
                            i += 4
                        }
                        bucketPeaks[bucketIdx] = maxPeak
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            val maxAmp = bucketPeaks.maxOrNull() ?: 1.0f
            val scale = if (maxAmp > 0.05f) 0.95f / maxAmp else 1.0f
            for (i in bucketPeaks.indices) {
                bucketPeaks[i] = (bucketPeaks[i] * scale).coerceIn(0.05f, 1.0f)
            }

            val estimatedBpm = estimateBpm(bucketPeaks, durationMs)

            return WaveformResult(bucketPeaks, durationMs, estimatedBpm)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
        }
    }

    fun estimateBpm(samples: FloatArray, durationMs: Int): Double {
        if (durationMs < 10_000 || samples.size < 100) return 128.0
        val durationSec = durationMs / 1000.0

        val maxVal = samples.maxOrNull() ?: 1.0f
        val threshold = maxVal * 0.55f

        val peakIndices = mutableListOf<Int>()
        for (i in 1 until samples.size - 1) {
            if (samples[i] > threshold && samples[i] >= samples[i - 1] && samples[i] >= samples[i + 1]) {
                peakIndices.add(i)
            }
        }

        if (peakIndices.size < 4) return 128.0

        val intervals = mutableListOf<Double>()
        for (i in 1 until peakIndices.size) {
            val sampleDiff = peakIndices[i] - peakIndices[i - 1]
            val timeDiff = (sampleDiff.toDouble() / samples.size) * durationSec
            if (timeDiff in 0.25..1.2) {
                var t = timeDiff
                while (t > 1.05) t /= 2.0
                while (t < 0.27) t *= 2.0
                intervals.add(t)
            }
        }

        if (intervals.isEmpty()) return 128.0

        val avgInterval = intervals.average()
        if (avgInterval <= 0.0) return 128.0
        var bpm = 60.0 / avgInterval
        while (bpm < 65.0) bpm *= 2.0
        while (bpm > 175.0) bpm /= 2.0
        return ((bpm * 10.0).roundToInt() / 10.0)
    }

    fun generateSyntheticWaveform(sampleCount: Int = 800): FloatArray {
        val result = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val progress = i.toDouble() / sampleCount
            val base = 0.35 + 0.3 * sin(progress * 20.0 * PI)
            val beat = if (i % 8 == 0) 0.3 else 0.0
            val noise = (Math.random() - 0.5) * 0.2
            val valFloat = (base + beat + noise).coerceIn(0.08, 0.95).toFloat()
            result[i] = valFloat
        }
        return result
    }

    companion object {
        @Volatile
        private var INSTANCE: WaveformAnalyzer? = null

        fun getInstance(context: Context): WaveformAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WaveformAnalyzer(ClassRepository(context.applicationContext)).also { INSTANCE = it }
            }
        }
    }
}
