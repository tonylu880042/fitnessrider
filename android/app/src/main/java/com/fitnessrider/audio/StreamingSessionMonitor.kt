package com.fitnessrider.audio

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.fitnessrider.model.WorkoutSegment
import com.fitnessrider.model.isStreamingSegment
import com.fitnessrider.model.streamingDurationMs
import kotlin.math.abs

class StreamingSessionListenerService : NotificationListenerService()

const val RESYNC_THRESHOLD_SECONDS = 1.5
const val RESYNC_COOLDOWN_MS = 3000L
const val NOW_PLAYING_AUTH_CHECK_MS = 2000L

data class StreamingSessionCandidate(val packageName: String, val isPlaying: Boolean)

enum class StreamingPreviousAction { SEEK_TO_START, SKIP_TO_PREVIOUS }

data class StreamingNowPlaying(
    val packageName: String,
    val title: String?,
    val durationMs: Long,
    val isPlaying: Boolean,
    val positionMs: Long,
    val lastUpdateElapsedMs: Long,
    val speed: Float
) {
    fun estimatedPositionMs(nowElapsedMs: Long): Long? =
        estimatedPlaybackPositionMs(positionMs, lastUpdateElapsedMs, speed, isPlaying, nowElapsedMs)
}

fun selectStreamingSessionIndex(candidates: List<StreamingSessionCandidate>, ownPackage: String): Int? {
    val others = candidates.withIndex().filter { it.value.packageName != ownPackage }
    return (others.firstOrNull { it.value.isPlaying } ?: others.firstOrNull())?.index
}

fun normalizedTrackTitle(raw: CharSequence?): String? = raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }

fun shouldFollowTrackChange(previousTitle: String?, currentTitle: String?): Boolean {
    val previous = normalizedTrackTitle(previousTitle) ?: return false
    val current = normalizedTrackTitle(currentTitle) ?: return false
    return !previous.equals(current, ignoreCase = true)
}

fun segmentIndexForNowPlaying(title: String?, segments: List<WorkoutSegment>, currentIndex: Int): Int? {
    val key = normalizedTrackTitle(title) ?: return null
    val matches = segments.indices.filter {
        isStreamingSegment(segments[it]) && normalizedTrackTitle(segments[it].title).equals(key, ignoreCase = true)
    }
    if (currentIndex in matches) return currentIndex
    return matches.firstOrNull { it > currentIndex } ?: matches.lastOrNull { it < currentIndex }
}

fun shouldResyncClock(clockSeconds: Double, playerPositionSeconds: Double): Boolean =
    abs(clockSeconds - playerPositionSeconds) > RESYNC_THRESHOLD_SECONDS

fun shouldResyncToPlayerPosition(clockSeconds: Double, playerPositionSeconds: Double?, durationSeconds: Double): Boolean {
    if (playerPositionSeconds == null || playerPositionSeconds < 0.0 || playerPositionSeconds > durationSeconds) return false
    return shouldResyncClock(clockSeconds, playerPositionSeconds)
}

fun estimatedPlaybackPositionMs(
    positionMs: Long,
    lastUpdateElapsedMs: Long,
    speed: Float,
    isPlaying: Boolean,
    nowElapsedMs: Long
): Long? {
    if (positionMs < 0) return null
    if (!isPlaying || lastUpdateElapsedMs <= 0) return positionMs
    val effectiveSpeed = if (speed > 0f) speed else 1f
    val elapsed = (nowElapsedMs - lastUpdateElapsedMs).coerceAtLeast(0)
    return positionMs + (elapsed * effectiveSpeed).toLong()
}

fun sanitizedStreamingDurationMs(rawMs: Long): Int? {
    if (rawMs !in 1..3_600_500) return null
    val seconds = ((rawMs + 500) / 1000).toInt()
    return streamingDurationMs(seconds / 60, seconds % 60)
}

fun streamingPreviousAction(offsetSeconds: Double, currentIndex: Int): StreamingPreviousAction =
    if (offsetSeconds > 3.0 || currentIndex <= 0) StreamingPreviousAction.SEEK_TO_START
    else StreamingPreviousAction.SKIP_TO_PREVIOUS

class StreamingSessionMonitor(private val context: Context) {

    private inner class Tracked(val controller: MediaController) {
        val packageName: String = controller.packageName
        var title: String? = null
        var durationMs: Long = 0L
        var state: PlaybackState? = null

        private val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = readMetadata(metadata)
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                this@Tracked.state = state
            }
        }

        init {
            readMetadata(controller.metadata)
            state = controller.playbackState
            controller.registerCallback(callback)
        }

        val isPlaying: Boolean
            get() = state?.state == PlaybackState.STATE_PLAYING

        fun release() {
            try {
                controller.unregisterCallback(callback)
            } catch (e: RuntimeException) {
            }
        }

        private fun readMetadata(metadata: MediaMetadata?) {
            title = normalizedTrackTitle(
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            )
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        }
    }

    private val component = ComponentName(context, StreamingSessionListenerService::class.java)
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var tracked: List<Tracked> = emptyList()

    val isActive: Boolean
        get() = listener != null

    fun start(): Boolean {
        if (listener != null) return true
        if (!isAuthorized(context)) return false
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val newListener = MediaSessionManager.OnActiveSessionsChangedListener { track(it) }
            manager.addOnActiveSessionsChangedListener(newListener, component)
            listener = newListener
            track(manager.getActiveSessions(component))
            true
        } catch (e: RuntimeException) {
            stop()
            false
        }
    }

    fun stop() {
        val current = listener
        listener = null
        if (current != null) {
            try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                manager.removeOnActiveSessionsChangedListener(current)
            } catch (e: RuntimeException) {
            }
        }
        tracked.forEach { it.release() }
        tracked = emptyList()
    }

    fun current(): StreamingNowPlaying? {
        val session = selectedSession() ?: return null
        val state = session.state
        return StreamingNowPlaying(
            packageName = session.packageName,
            title = session.title,
            durationMs = session.durationMs,
            isPlaying = session.isPlaying,
            positionMs = state?.position ?: -1L,
            lastUpdateElapsedMs = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed ?: 1f
        )
    }

    fun sendCommand(command: (MediaController.TransportControls) -> Unit) {
        val session = selectedSession() ?: return
        try {
            command(session.controller.transportControls)
        } catch (e: RuntimeException) {
        }
    }

    private fun selectedSession(): Tracked? {
        if (listener == null) return null
        val index = selectStreamingSessionIndex(
            tracked.map { StreamingSessionCandidate(it.packageName, it.isPlaying) },
            context.packageName
        ) ?: return null
        return tracked[index]
    }

    private fun track(controllers: List<MediaController>?) {
        tracked.forEach { it.release() }
        tracked = try {
            controllers.orEmpty().map { Tracked(it) }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    companion object {
        fun isAuthorized(context: Context): Boolean =
            try {
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            } catch (e: RuntimeException) {
                false
            }

        fun readNowPlayingOnce(context: Context): StreamingNowPlaying? {
            val monitor = StreamingSessionMonitor(context)
            return try {
                if (monitor.start()) monitor.current() else null
            } finally {
                monitor.stop()
            }
        }
    }
}
