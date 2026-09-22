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

object CrossfadeCalculator {
    fun calculateEqualPowerVolumes(progress: Double): Pair<Float, Float> {
        val t = progress.coerceIn(0.0, 1.0)
        val angle = t * (Math.PI / 2.0)
        val fadeOut = kotlin.math.cos(angle).toFloat()
        val fadeIn = kotlin.math.sin(angle).toFloat()
        return Pair(fadeOut, fadeIn)
    }

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

class CrossfadeFinishCoordinator(startSegmentIndex: Int = 0) {
    var activePlayerIndex: Int = 0
        private set
    var currentSegmentIndex: Int = startSegmentIndex
        private set
    var isCrossfading: Boolean = false
        private set
    private var isFinishingCrossfade = false

    fun beginNewClass(segmentIndex: Int) {
        currentSegmentIndex = segmentIndex
        isCrossfading = false
        isFinishingCrossfade = false
    }

    fun setSegmentIndex(index: Int) {
        currentSegmentIndex = index
    }

    fun startCrossfade() {
        isCrossfading = true
    }

    fun cancelCrossfade() {
        isCrossfading = false
    }

    fun finishCrossfade(tearDownOldPlayer: () -> Unit): Boolean {
        if (!isCrossfading || isFinishingCrossfade) return false
        isFinishingCrossfade = true
        try {
            activePlayerIndex = 1 - activePlayerIndex
            isCrossfading = false
            currentSegmentIndex += 1
            tearDownOldPlayer()
        } finally {
            isFinishingCrossfade = false
        }
        return true
    }

    fun shouldHandleTrackEnded(endedPlayerIndex: Int): Boolean {
        return endedPlayerIndex == activePlayerIndex && !isFinishingCrossfade
    }
}

class AudioEngineManager(private val context: Context) {
    private val playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    private val playerB: ExoPlayer = ExoPlayer.Builder(context).build()
    private val crossfadeCoordinator = CrossfadeFinishCoordinator()

    private val activePlayerIndex: Int
        get() = crossfadeCoordinator.activePlayerIndex

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
                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded(playerIndex)
                }
            }
        })
    }

    fun loadClass(workoutClass: WorkoutClass, startIndex: Int = 0) {
        currentClass = workoutClass
        val clampedIndex = startIndex.coerceIn(0, (workoutClass.segments.size - 1).coerceAtLeast(0))
        crossfadeCoordinator.beginNewClass(clampedIndex)
        _currentSegmentIndex.value = clampedIndex
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
        if (segment.musicFileName.isNotBlank() && MusicSource.exists(context, repository, segment.musicFileName)) {
            val mediaItem = MediaItem.fromUri(MusicSource.resolveUri(repository, segment.musicFileName))
            player.setMediaItem(mediaItem)
            player.prepare()
        } else {
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

    fun adjustRatePercent(deltaPercent: Double) {
        val newRate = _currentRate.value + (deltaPercent / 100.0)
        setRate(newRate)
    }

    fun setRate(rate: Double) {
        val clamped = (rate.coerceIn(0.85, 1.15) * 100).roundToInt() / 100.0
        _currentRate.value = clamped
        activePlayer.playbackParameters = PlaybackParameters(clamped.toFloat(), 1.0f)
    }

    fun resetRate() {
        setRate(1.0)
    }

    fun nextSegment() {
        val c = currentClass ?: return
        if (crossfadeCoordinator.currentSegmentIndex < c.segments.size - 1) {
            cancelCrossfade()
            crossfadeCoordinator.setSegmentIndex(crossfadeCoordinator.currentSegmentIndex + 1)
            _currentSegmentIndex.value = crossfadeCoordinator.currentSegmentIndex
            loadCurrentSegment()
            if (_isPlaying.value) play()
        }
    }

    fun previousSegment() {
        cancelCrossfade()
        if (_currentOffsetSeconds.value > 3.0) {
            seekTo(0.0)
        } else if (crossfadeCoordinator.currentSegmentIndex > 0) {
            crossfadeCoordinator.setSegmentIndex(crossfadeCoordinator.currentSegmentIndex - 1)
            _currentSegmentIndex.value = crossfadeCoordinator.currentSegmentIndex
            loadCurrentSegment()
            if (_isPlaying.value) play()
        } else {
            seekTo(0.0)
        }
    }

    private fun startCrossfade(effectiveDuration: Double) {
        val c = currentClass ?: return
        val nextIndex = crossfadeCoordinator.currentSegmentIndex + 1
        if (nextIndex >= c.segments.size) return

        crossfadeCoordinator.startCrossfade()
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
        val oldPlayer = activePlayer
        val newPlayer = incomingPlayer

        val didFinish = crossfadeCoordinator.finishCrossfade {
            oldPlayer.pause()
            oldPlayer.clearMediaItems()
            oldPlayer.volume = 1.0f
            newPlayer.volume = 1.0f
        }
        if (!didFinish) return

        _isCrossfading.value = false
        _currentSegmentIndex.value = crossfadeCoordinator.currentSegmentIndex

        val newSegment = currentSegment
        if (newSegment != null) {
            _currentRate.value = newSegment.playbackRate
            if (newPlayer.mediaItemCount > 0 && newPlayer.duration > 0) {
                _currentDurationSeconds.value = newPlayer.duration / 1000.0
                _currentOffsetSeconds.value = newPlayer.currentPosition / 1000.0
            } else {
                _currentDurationSeconds.value = newSegment.durationMs / 1000.0
                _currentOffsetSeconds.value = effectiveDuration
            }
        }
    }

    fun cancelCrossfade() {
        if (!crossfadeCoordinator.isCrossfading) return
        crossfadeCoordinator.cancelCrossfade()
        _isCrossfading.value = false
        incomingPlayer.pause()
        incomingPlayer.clearMediaItems()
        incomingPlayer.volume = 1.0f
        activePlayer.volume = 1.0f
    }

    private fun handleTrackEnded(endedPlayerIndex: Int) {
        if (!crossfadeCoordinator.shouldHandleTrackEnded(endedPlayerIndex)) return

        if (crossfadeCoordinator.isCrossfading) {
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
            if (crossfadeCoordinator.currentSegmentIndex < c.segments.size - 1) {
                crossfadeCoordinator.setSegmentIndex(crossfadeCoordinator.currentSegmentIndex + 1)
                _currentSegmentIndex.value = crossfadeCoordinator.currentSegmentIndex
                loadCurrentSegment()
            }
        } else {
            if (crossfadeCoordinator.currentSegmentIndex < c.segments.size - 1) {
                crossfadeCoordinator.setSegmentIndex(crossfadeCoordinator.currentSegmentIndex + 1)
                _currentSegmentIndex.value = crossfadeCoordinator.currentSegmentIndex
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
                        handleTrackEnded(activePlayerIndex)
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
