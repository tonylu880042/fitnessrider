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

class AudioEngineManager(private val context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val repository = ClassRepository(context)
    private val settings = AppSettings.getInstance(context)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressTracking() else stopProgressTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    handleTrackCompletion()
                }
            }
        })
    }

    fun loadClass(workoutClass: WorkoutClass, startIndex: Int = 0) {
        currentClass = workoutClass
        _currentSegmentIndex.value = startIndex.coerceIn(0, (workoutClass.segments.size - 1).coerceAtLeast(0))
        loadCurrentSegment()
    }

    private fun loadCurrentSegment() {
        val segment = currentSegment ?: return
        setRate(segment.playbackRate)

        // Layer 3：segment.musicFileName 可能是 Music/ 目錄下的檔名，也可能是外部資料夾的
        // content Uri，一律交給 MusicSource 判斷來源與存在性（授權被撤銷/檔案被搬走都當作
        // 「找不到檔案」退回模擬播放，行為與原本本機檔案不存在時一致）。
        if (segment.musicFileName.isNotBlank() && MusicSource.exists(context, repository, segment.musicFileName)) {
            val mediaItem = MediaItem.fromUri(MusicSource.resolveUri(repository, segment.musicFileName))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _currentDurationSeconds.value = (segment.durationMs / 1000.0)
        } else {
            // Simulated playback if no local audio file
            exoPlayer.clearMediaItems()
            _currentDurationSeconds.value = (segment.durationMs / 1000.0)
        }
        _currentOffsetSeconds.value = 0.0
    }

    fun play() {
        if (currentSegment != null) {
            val musicFileName = currentSegment?.musicFileName ?: ""
            if (musicFileName.isNotBlank() && MusicSource.exists(context, repository, musicFileName)) {
                exoPlayer.play()
            } else {
                // Simulation mode
                _isPlaying.value = true
                startProgressTracking()
            }
        }
    }

    fun pause() {
        exoPlayer.pause()
        _isPlaying.value = false
        stopProgressTracking()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(seconds: Double) {
        val clamped = seconds.coerceIn(0.0, _currentDurationSeconds.value)
        _currentOffsetSeconds.value = clamped
        if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.seekTo((clamped * 1000).toLong())
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
        exoPlayer.playbackParameters = PlaybackParameters(clamped.toFloat(), 1.0f)
    }

    fun resetRate() {
        setRate(1.0)
    }

    fun nextSegment() {
        val c = currentClass ?: return
        if (_currentSegmentIndex.value < c.segments.size - 1) {
            _currentSegmentIndex.value += 1
            loadCurrentSegment()
            if (_isPlaying.value) play()
        }
    }

    fun previousSegment() {
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

    private fun handleTrackCompletion() {
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
                if (exoPlayer.mediaItemCount > 0 && exoPlayer.duration > 0) {
                    _currentOffsetSeconds.value = exoPlayer.currentPosition / 1000.0
                    _currentDurationSeconds.value = exoPlayer.duration / 1000.0
                } else {
                    // Simulation mode step
                    _currentOffsetSeconds.value += 0.1 * _currentRate.value
                    if (_currentOffsetSeconds.value >= _currentDurationSeconds.value) {
                        handleTrackCompletion()
                        break
                    }
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
        if (!settings.isCountdownBeepEnabled) return
        val seg = currentSegment ?: return
        val currentMs = (_currentOffsetSeconds.value * 1000).toInt()

        for (cue in seg.cues) {
            val diff = cue.offsetMs - currentMs
            if (diff in 1..3100) {
                val secLeft = (diff + 500) / 1000
                if (secLeft != lastBeepSecond && secLeft in 1..3) {
                    lastBeepSecond = secLeft
                    CueCountdownBeepPlayer.playCountdownBeep(secLeft)
                }
                break
            } else if (diff in -500..0) {
                if (lastBeepSecond != 0) {
                    lastBeepSecond = 0
                    CueCountdownBeepPlayer.playActionStartBeep()
                }
            }
        }
    }

    fun release() {
        stopProgressTracking()
        exoPlayer.release()
    }
}
