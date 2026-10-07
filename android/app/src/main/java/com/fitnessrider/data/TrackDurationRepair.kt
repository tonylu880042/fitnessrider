package com.fitnessrider.data

import android.content.Context
import com.fitnessrider.audio.WaveformAnalyzer
import com.fitnessrider.model.WorkoutSegment
import com.fitnessrider.model.segmentsNeedingDurationRepair
import com.fitnessrider.ui.editor.segmentWithAnalyzedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object TrackDurationRepair {
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    fun start(context: Context, segments: List<WorkoutSegment>) {
        val targets = segmentsNeedingDurationRepair(segments)
        if (targets.isEmpty() || !running.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val analyzer = WaveformAnalyzer.getInstance(appContext)
                val repository = ClassRepository(appContext)
                targets.forEachIndexed { index, segment ->
                    _progress.value = (index + 1) to targets.size
                    runCatching {
                        val result = analyzer.analyzeWaveform(segment.musicFileName)
                        val updated = segmentWithAnalyzedTrack(segment, result.durationMs, result.bpm)
                        if (updated != segment) {
                            repository.updateSegmentTrack(
                                segment.id,
                                updated.durationMs,
                                updated.baseBpm.takeIf { it != segment.baseBpm }
                            )
                        }
                    }
                }
            } finally {
                _progress.value = null
                running.set(false)
            }
        }
    }
}
