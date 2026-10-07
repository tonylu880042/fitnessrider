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
import java.nio.ShortBuffer
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
        val cachedResult = cached?.let { WaveformResult(samples = it.first, durationMs = it.second, bpm = it.third) }
        if (cachedResult != null && (repository?.getWaveformAnalysisVersion(fileName) ?: 0) >= ANALYSIS_VERSION) {
            return@withContext cachedResult
        }
        val fallback = { cachedResult ?: generateFallbackResult(targetPoints) }
        val tagBpm = repository?.let { repo ->
            MusicSource.openInputStream(repo.context, repo, fileName)?.use { Id3BpmReader.read(it) }
        }

        if (MusicSource.isExternalUri(fileName)) {
            val context = repository?.context
                ?: return@withContext fallback()
            return@withContext try {
                val result = extractWaveformFromUri(context, Uri.parse(fileName), targetPoints, tagBpm)
                repository.saveWaveform(fileName, result.samples, result.durationMs, result.bpm, ANALYSIS_VERSION)
                result
            } catch (e: Exception) {
                Log.e("WaveformAnalyzer", "Failed to decode external audio $fileName, using fallback", e)
                fallback()
            }
        }

        val musicDir = repository?.musicDirectory
        val file = if (musicDir != null) File(musicDir, fileName) else File(fileName)
        if (!file.exists() || file.length() == 0L) {
            return@withContext fallback()
        }

        try {
            val result = extractWaveformFromFile(file, targetPoints, tagBpm)
            repository?.saveWaveform(fileName, result.samples, result.durationMs, result.bpm, ANALYSIS_VERSION)
            result
        } catch (e: Exception) {
            Log.e("WaveformAnalyzer", "Failed to decode audio file $fileName, using fallback", e)
            fallback()
        }
    }

    private fun generateFallbackResult(targetPoints: Int) = WaveformResult(
        samples = generateSyntheticWaveform(targetPoints),
        durationMs = 300_000,
        bpm = 128.0
    )

    fun extractWaveformFromFile(file: File, targetPoints: Int = 800, tagBpm: Double? = null): WaveformResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return decodeWaveform(extractor, targetPoints, tagBpm)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun extractWaveformFromUri(context: Context, uri: Uri, targetPoints: Int = 800, tagBpm: Double? = null): WaveformResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return decodeWaveform(extractor, targetPoints, tagBpm)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeWaveform(extractor: MediaExtractor, targetPoints: Int, tagBpm: Double?): WaveformResult {
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
            var envelope: EnergyEnvelopeAccumulator? = null

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

                        val accumulator = envelope ?: codec.outputFormat.let {
                            EnergyEnvelopeAccumulator(
                                it.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            )
                        }.also { envelope = it }
                        accumulator.add(shortBuffer)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            val maxAmp = bucketPeaks.maxOrNull() ?: 1.0f
            val scale = if (maxAmp > 0.05f) 0.95f / maxAmp else 1.0f
            for (i in bucketPeaks.indices) {
                bucketPeaks[i] = (bucketPeaks[i] * scale).coerceIn(0.05f, 1.0f)
            }

            val estimatedBpm = tagBpm ?: envelope?.let { estimateBpmFromEnvelope(it.toArray(), it.hopMs) } ?: 128.0

            return WaveformResult(bucketPeaks, durationMs, estimatedBpm)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
        }
    }

    fun estimateBpmFromEnvelope(envelope: FloatArray, hopMs: Double): Double {
        val n = envelope.size
        if (n * hopMs < 10_000.0) return 128.0
        val mean = envelope.average()
        if (mean <= 0.0) return 128.0

        val logEnergy = DoubleArray(n) { ln(1.0 + 0.3 * envelope[it] / mean) }
        val onset = DoubleArray(n) { if (it == 0) 0.0 else max(0.0, logEnergy[it] - logEnergy[it - 1]) }
        val kernel = doubleArrayOf(1.0, 2.0, 3.0, 2.0, 1.0)
        val smoothed = DoubleArray(n) { i ->
            var acc = 0.0
            for (k in kernel.indices) {
                val j = i + k - 2
                if (j in 0 until n) acc += kernel[k] * onset[j]
            }
            acc
        }
        val smoothedMean = smoothed.average()
        for (i in 0 until n) smoothed[i] -= smoothedMean

        val maxLag = (4.0 * 60_000.0 / (MIN_BPM * hopMs)).toInt() + 2
        if (n <= maxLag * 2) return 128.0
        val autocorrelation = DoubleArray(maxLag + 2) { lag ->
            var acc = 0.0
            for (i in 0 until n - lag) acc += smoothed[i] * smoothed[i + lag]
            acc / (n - lag)
        }

        fun autocorrelationAt(lag: Double): Double {
            val index = lag.toInt()
            val fraction = lag - index
            return autocorrelation[index] * (1.0 - fraction) + autocorrelation[index + 1] * fraction
        }

        fun score(bpm: Double): Double {
            val lag = 60_000.0 / (bpm * hopMs)
            val periodicity = autocorrelationAt(lag) + autocorrelationAt(2 * lag) / 2.0 + autocorrelationAt(4 * lag) / 4.0
            val octaves = ln(bpm / 120.0) / ln(2.0) / 0.8
            return periodicity * exp(-0.5 * octaves * octaves)
        }

        var best = (MIN_BPM.toInt() * 2..MAX_BPM.toInt() * 2).map { it / 2.0 }.maxByOrNull { score(it) } ?: return 128.0
        best = (-5..5).map { best + it / 10.0 }.filter { it in MIN_BPM..MAX_BPM }.maxByOrNull { score(it) } ?: best
        return (best * 10.0).roundToInt() / 10.0
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
        const val ANALYSIS_VERSION = 1
        private const val MIN_BPM = 65.0
        private const val MAX_BPM = 175.0

        @Volatile
        private var INSTANCE: WaveformAnalyzer? = null

        fun getInstance(context: Context): WaveformAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WaveformAnalyzer(ClassRepository(context.applicationContext)).also { INSTANCE = it }
            }
        }
    }
}

class EnergyEnvelopeAccumulator(sampleRate: Int, private val channels: Int) {
    private val hopFrames = (sampleRate / 100).coerceAtLeast(1)
    val hopMs = hopFrames * 1000.0 / sampleRate
    private val values = ArrayList<Float>()
    private var energySum = 0.0
    private var frames = 0

    fun add(buffer: ShortBuffer) {
        while (buffer.remaining() >= channels) {
            var mixed = 0.0
            repeat(channels) { mixed += buffer.get() }
            mixed /= channels * 32768.0
            energySum += mixed * mixed
            if (++frames == hopFrames) {
                values.add((energySum / hopFrames).toFloat())
                energySum = 0.0
                frames = 0
            }
        }
    }

    fun toArray(): FloatArray = values.toFloatArray()
}
