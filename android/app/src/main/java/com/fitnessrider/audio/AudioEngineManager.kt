package com.fitnessrider.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.MusicSource
import com.fitnessrider.model.AppSettings
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.model.WorkoutSegment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

// MARK: - Crossfade Calculator

object CrossfadeCalculator {
    /// Equal-Power Crossfade volumes: fadeOut = cos(t * π / 2), fadeIn = sin(t * π / 2)
    /// where progress is in [0.0, 1.0].
    /// Maintains constant acoustic power: (fadeOut^2 + fadeIn^2 = 1.0)
    fun calculateEqualPowerVolumes(progress: Double): Pair<Float, Float> {
        val t = progress.coerceIn(0.0, 1.0)
        val angle = t * (Math.PI / 2.0)
        val fadeOut = kotlin.math.cos(angle).toFloat()
        val fadeIn = kotlin.math.sin(angle).toFloat()
        return Pair(fadeOut, fadeIn)
    }

    /// Determines effective crossfade duration based on settings, remaining track time, and auto-pause.
    fun effectiveDuration(
        requestedDuration: Double,
        segmentDuration: Double,
        isAutoPauseEnabled: Boolean
    ): Double {
        if (isAutoPauseEnabled || requestedDuration <= 0.0 || segmentDuration <= 0.0) {
            return 0.0
        }
        return minOf(requestedDuration, segmentDuration * 0.5)
    }
}

// MARK: - AudioEngineManager

class AudioEngineManager(private val context: Context) {
    private val playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    private val playerB: ExoPlayer = ExoPlayer.Builder(context).build()
    private var activePlayerIndex = 0 // 0 = playerA, 1 = playerB

    private val activePlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerA else playerB

    private val incomingPlayer: ExoPlayer
        get() = if (activePlayerIndex == 0) playerB else playerA

    private val repository = ClassRepository(context)
    private val settings = AppSettings.getInstance(context)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isCrossfading = MutableStateFlow(false)
    val isCrossfading: StateFlow<Boolean> = _isCrossfading.asStateFlow()

    private val _currentRate = MutableStateFlow(1.0)
    val currentRate: StateFlow<Double> = _currentRate.asStateFlow()

    private val _currentOffsetSeconds = MutableStateFlow(0.0)
    val currentOffsetSeconds: StateFlow<Double> = _currentOffsetSeconds.asStateFlow()

    private val _currentDurationSeconds = MutableStateFlow(0.0)
    val currentDurationSeconds: StateFlow<Double> = _currentDurationSeconds.asStateFlow()

    private val _currentSegmentIndex = MutableStateFlow(0)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()

    var currentClass: WorkoutClass? = null
        private set

    val currentSegment: WorkoutSegment?
        get() {
            val c = currentClass ?: return null
            val idx = _currentSegmentIndex.value
            return if (idx in c.segments.indices) c.segments[idx] else null
        }

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastBeepSecond = -1

    init {
        setupPlayerListener(playerA, 0)
        setupPlayerListener(playerB, 1)
    }

    private fun setupPlayerListener(player: ExoPlayer, playerIndex: Int) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (playerIndex == activePlayerIndex) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressTracking()
                    } else if (!playerA.isPlaying && !playerB.isPlaying) {
                        stopProgressTracking()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playerIndex == activePlayerIndex && playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }
        })
    }

    fun loadClass(workoutClass: WorkoutClass, startIndex: Int = 0) {
        currentClass = workoutClass
        _currentSegmentIndex.value = startIndex.coerceIn(0, (workoutClass.segments.size - 1).coerceAtLeast(0))
        cancelCrossfade()
        incomingPlayer.clearMediaItems()
        loadCurrentSegment()
    }

    private fun loadCurrentSegment() {
        val segment = currentSegment ?: return
        setRate(segment.playbackRate)
        loadSegmentOnPlayer(activePlayer, segment)
        activePlayer.volume = 1.0f
        _currentDurationSeconds.value = (segment.durationMs / 1000.0)
        _currentOffsetSeconds.value = 0.0
    }

    private fun loadSegmentOnPlayer(player: ExoPlayer, segment: WorkoutSegment) {
        // Layer 3：segment.musicFileName 可能是 Music/ 目錄下的檔名，也可能是外部資料夾的
        // content Uri，一律交給 MusicSource 判斷來源與存在性（授權被撤銷/檔案被搬走都當作
        // 「找不到檔案」退回模擬播放，行為與原本本機檔案不存在時一致）。
        if (segment.musicFileName.isNotBlank() && MusicSource.exists(context, repository, segment.musicFileName)) {
            val mediaItem = MediaItem.fromUri(MusicSource.resolveUri(repository, segment.musicFileName))
            player.setMediaItem(mediaItem)
            player.prepare()
        } else {
            // Simulated playback if no local audio file
            player.clearMediaItems()
        }
    }

    fun play() {
        if (currentSegment != null) {
            val hasAudio = currentSegment?.musicFileName?.isNotBlank() == true &&
                MusicSource.exists(context, repository, currentSegment!!.musicFileName)
            if (hasAudio) {
                activePlayer.play()
            }
            if (_isCrossfading.value && incomingPlayer.mediaItemCount > 0) {
                incomingPlayer.play()
            }
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun pause() {
        activePlayer.pause()
        if (_isCrossfading.value) {
            incomingPlayer.pause()
        }
        _isPlaying.value = false
        stopProgressTracking()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(seconds: Double) {
        cancelCrossfade()
        val clamped = seconds.coerceIn(0.0, _currentDurationSeconds.value)
        _currentOffsetSeconds.value = clamped
        if (activePlayer.mediaItemCount > 0) {
            activePlayer.seekTo((clamped * 1000).toLong())
        }
    }

    fun seekBy(deltaSeconds: Double) {
        seekTo(_currentOffsetSeconds.value + deltaSeconds)
    }

    // Rate adjustment (±15% -> 0.85 ~ 1.15, ±2% steps)
    fun adjustRatePercent(deltaPercent: Double) {
        val newRate = _currentRate.value + (deltaPercent / 100.0)
        setRate(newRate)
    }

    fun setRate(rate: Double) {
        val clamped = (rate.coerceIn(0.85, 1.15) * 100).roundToInt() / 100.0
        _currentRate.value = clamped
        // ExoPlayer PlaybackParameters strictly preserves pitch when pitch = 1.0f!
        activePlayer.playbackParameters = PlaybackParameters(clamped.toFloat(), 1.0f)
    }

    fun resetRate() {
        setRate(1.0)
    }

    fun nextSegment() {
        val c = currentClass ?: return
        if (_currentSegmentIndex.value < c.segments.size - 1) {
            cancelCrossfade()
            _currentSegmentIndex.value += 1
            loadCurrentSegment()
            if (_isPlaying.value) play()
        }
    }

    fun previousSegment() {
        cancelCrossfade()
        if (_currentOffsetSeconds.value > 3.0) {
            seekTo(0.0)
        } else if (_currentSegmentIndex.value > 0) {
            _currentSegmentIndex.value -= 1
            loadCurrentSegment()
            if (_isPlaying.value) play()
        } else {
            seekTo(0.0)
        }
    }

    // MARK: - Crossfade State Machine

    private fun startCrossfade(effectiveDuration: Double) {
        val c = currentClass ?: return
        val nextIndex = _currentSegmentIndex.value + 1
        if (nextIndex >= c.segments.size) return

        _isCrossfading.value = true
        val nextSegment = c.segments[nextIndex]

        loadSegmentOnPlayer(incomingPlayer, nextSegment)
        incomingPlayer.playbackParameters = PlaybackParameters(nextSegment.playbackRate.toFloat(), 1.0f)
        incomingPlayer.volume = 0.0f

        val hasIncomingAudio = nextSegment.musicFileName.isNotBlank() &&
            MusicSource.exists(context, repository, nextSegment.musicFileName)
        if (hasIncomingAudio) {
            incomingPlayer.play()
        }

        val remaining = (_currentDurationSeconds.value - _currentOffsetSeconds.value).coerceAtLeast(0.0)
        val progress = 1.0 - (remaining / effectiveDuration)
        val (fadeOut, fadeIn) = CrossfadeCalculator.calculateEqualPowerVolumes(progress)
        activePlayer.volume = fadeOut
        incomingPlayer.volume = fadeIn
    }

    private fun updateCrossfade(effectiveDuration: Double, remaining: Double) {
        if (remaining <= 0.0) {
            finishCrossfade(effectiveDuration)
            return
        }

        val progress = 1.0 - (remaining / effectiveDuration)
        val (fadeOut, fadeIn) = CrossfadeCalculator.calculateEqualPowerVolumes(progress)
        activePlayer.volume = fadeOut
        incomingPlayer.volume = fadeIn
    }

    private fun finishCrossfade(effectiveDuration: Double) {
        if (!_isCrossfading.value) return
        _isCrossfading.value = false

        val oldPlayer = activePlayer
        oldPlayer.pause()
        oldPlayer.clearMediaItems()
        oldPlayer.volume = 1.0f

        incomingPlayer.volume = 1.0f
        activePlayerIndex = 1 - activePlayerIndex

        _currentSegmentIndex.value += 1
        val newSegment = currentSegment
        if (newSegment != null) {
            _currentRate.value = newSegment.playbackRate
            if (activePlayer.mediaItemCount > 0 && activePlayer.duration > 0) {
                _currentDurationSeconds.value = activePlayer.duration / 1000.0
                _currentOffsetSeconds.value = activePlayer.currentPosition / 1000.0
            } else {
                _currentDurationSeconds.value = newSegment.durationMs / 1000.0
                _currentOffsetSeconds.value = effectiveDuration
            }
        }
    }

    fun cancelCrossfade() {
        if (!_isCrossfading.value) return
        _isCrossfading.value = false
        incomingPlayer.pause()
        incomingPlayer.clearMediaItems()
        incomingPlayer.volume = 1.0f
        activePlayer.volume = 1.0f
    }

    private fun handleTrackEnded() {
        if (_isCrossfading.value) {
            val effectiveCrossfade = CrossfadeCalculator.effectiveDuration(
                requestedDuration = settings.crossfadeDurationSeconds,
                segmentDuration = _currentDurationSeconds.value,
                isAutoPauseEnabled = settings.isAutoPauseBetweenSegmentsEnabled
            )
            finishCrossfade(effectiveCrossfade)
            return
        }

        val c = currentClass ?: return
        if (settings.isAutoPauseBetweenSegmentsEnabled) {
            pause()
            if (_currentSegmentIndex.value < c.segments.size - 1) {
                _currentSegmentIndex.value += 1
                loadCurrentSegment()
            }
        } else {
            if (_currentSegmentIndex.value < c.segments.size - 1) {
                _currentSegmentIndex.value += 1
                loadCurrentSegment()
                play()
            } else {
                pause()
                seekTo(0.0)
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val currentSec = if (activePlayer.mediaItemCount > 0 && activePlayer.duration > 0) {
                    activePlayer.currentPosition / 1000.0
                } else {
                    _currentOffsetSeconds.value + (0.1 * _currentRate.value)
                }

                val totalSec = if (activePlayer.mediaItemCount > 0 && activePlayer.duration > 0) {
                    activePlayer.duration / 1000.0
                } else {
                    _currentDurationSeconds.value
                }

                _currentOffsetSeconds.value = currentSec
                _currentDurationSeconds.value = totalSec

                val effectiveCrossfade = CrossfadeCalculator.effectiveDuration(
                    requestedDuration = settings.crossfadeDurationSeconds,
                    segmentDuration = totalSec,
                    isAutoPauseEnabled = settings.isAutoPauseBetweenSegmentsEnabled
                )

                val remaining = totalSec - currentSec
                val c = currentClass
                val hasNext = c != null && _currentSegmentIndex.value < c.segments.size - 1

                if (!_isCrossfading.value) {
                    if (effectiveCrossfade > 0.0 && hasNext && remaining <= effectiveCrossfade) {
                        startCrossfade(effectiveCrossfade)
                    } else if (remaining <= 0.0) {
                        handleTrackEnded()
                        break
                    }
                } else {
                    updateCrossfade(effectiveCrossfade, remaining)
                }

                checkCueCountdown()
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun checkCueCountdown() {
        val seg = currentSegment ?: return
        val isBeepEnabled = settings.isCountdownBeepEnabled
        val isHapticEnabled = settings.isHapticFeedbackEnabled
        if (!isBeepEnabled && !isHapticEnabled) return

        val currentMs = (_currentOffsetSeconds.value * 1000).toInt()

        for (cue in seg.cues) {
            val diff = cue.offsetMs - currentMs
            if (diff in 1..3100) {
                val secLeft = (diff + 500) / 1000
                if (secLeft != lastBeepSecond && secLeft in 1..3) {
                    lastBeepSecond = secLeft
                    if (isBeepEnabled) {
                        CueCountdownBeepPlayer.playCountdownBeep(secLeft)
                    }
                    if (isHapticEnabled) {
                        HapticFeedbackManager.playCountdownTick(context)
                    }
                }
                break
            } else if (diff in -500..0) {
                if (lastBeepSecond != 0) {
                    lastBeepSecond = 0
                    if (isBeepEnabled) {
                        CueCountdownBeepPlayer.playActionStartBeep()
                    }
                    if (isHapticEnabled) {
                        HapticFeedbackManager.playActionStartImpact(context)
                    }
                }
            }
        }
    }

    fun release() {
        stopProgressTracking()
        playerA.release()
        playerB.release()
    }
}
