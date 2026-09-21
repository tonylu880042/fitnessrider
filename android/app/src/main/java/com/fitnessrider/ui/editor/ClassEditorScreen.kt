package com.fitnessrider.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fitnessrider.audio.WaveformAnalyzer
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.model.*
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.HandPositionBadge
import com.fitnessrider.ui.components.TopNavBar
import com.fitnessrider.ui.components.WaveformCanvas
import com.fitnessrider.ui.musiclibrary.MusicLibraryScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 匯入單一音樂檔後、建立段落前所需的資料（檔名、實際曲長、偵測 BPM）。
 * 拆成獨立資料類別與純函式是為了讓「檔名碰撞後綴」與「N 個檔案 -> N 個段落」
 * 這兩段非顯而易見的邏輯可以脫離 Compose/Context 被單元測試覆蓋。
 */
internal data class ImportedTrackInfo(
    val fileName: String,
    val durationMs: Int,
    val bpm: Double
)

/**
 * 檔名碰撞處理：若 [desiredName] 已存在於 [existingNames]，在副檔名前加上 `_1`、`_2`... 直到唯一。
 * 對應 CLAUDE.md Layer 1 第 6 項：避免不同曲目的同名檔案互相覆寫。
 */
internal fun resolveUniqueMusicFileName(desiredName: String, existingNames: Set<String>): String {
    if (!existingNames.contains(desiredName)) return desiredName
    val dotIndex = desiredName.lastIndexOf('.')
    val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
    val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
    var suffix = 1
    var candidate: String
    do {
        candidate = "${base}_$suffix$ext"
        suffix++
    } while (existingNames.contains(candidate))
    return candidate
}

/** 段落標題帶入曲名：去除副檔名。對應 Layer 1 第 2 項。 */
internal fun musicTitleFromFileName(fileName: String): String {
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
}

/**
 * 多選匯入 -> 逐一建立段落。對應 Layer 1 第 3 項（沿用舊版 ActivityClassEditor.java:1188 的行為）：
 * 選 N 首歌就建立 N 個段落，段落標題＝曲名、長度＝曲長。
 */
internal fun buildSegmentsForImportedTracks(
    tracks: List<ImportedTrackInfo>,
    classId: String,
    startOrderIndex: Int
): List<WorkoutSegment> {
    return tracks.mapIndexed { index, track ->
        WorkoutSegment(
            id = UUID.randomUUID().toString(),
            classId = classId,
            orderIndex = startOrderIndex + index,
            title = musicTitleFromFileName(track.fileName),
            musicFileName = track.fileName,
            durationMs = if (track.durationMs > 0) track.durationMs else 300_000,
            baseBpm = if (track.bpm > 0) track.bpm else 128.0,
            cues = listOf(
                WorkoutCue(
                    id = UUID.randomUUID().toString(),
                    offsetMs = 0,
                    posture = PostureType.SEATED_FLAT,
                    targetRpm = 85,
                    resistanceLevel = "LEVEL 4",
                    message = "坐姿平路巡航"
                )
            )
        )
    }
}

@Composable
fun ClassEditorScreen(
    initialClass: WorkoutClass,
    onSave: (WorkoutClass) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ClassRepository(context) }
    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(previewPlayer) {
        onDispose {
            previewPlayer.release()
        }
    }

    var workoutClass by remember { mutableStateOf(initialClass) }
    var selectedSegmentIndex by remember { mutableStateOf(0) }
    var isEditingCueDialogVisible by remember { mutableStateOf(false) }
    var currentEditingCue by remember { mutableStateOf<WorkoutCue?>(null) }
    var isBpmDialogVisible by remember { mutableStateOf(false) }

    var waveformSamples by remember { mutableStateOf(FloatArray(0)) }
    var previewPlayheadMs by remember { mutableStateOf(0) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeSegment = if (workoutClass.segments.isNotEmpty() && selectedSegmentIndex in workoutClass.segments.indices) {
        workoutClass.segments[selectedSegmentIndex]
    } else null

    // Layer 2：段落指定音樂一律先開 app 內音樂庫，不再直接開系統檔案選擇器；
    // SAF／OpenMultipleDocuments 只留在 MusicLibraryScreen 內「＋匯入新檔」一個入口。
    var isMusicLibraryVisible by remember { mutableStateOf(false) }

    fun appendSegmentsFromLibrary(newSegments: List<WorkoutSegment>) {
        if (newSegments.isEmpty()) return
        // 匯入/選曲後立刻重算總時長/預估消耗，不然編輯畫面頂端的「⏱ 總時長」在存檔前都還是舊值（Layer 1 第 1 項）。
        workoutClass = workoutClass.copy(segments = workoutClass.segments + newSegments).withRecalculatedTotals()
        selectedSegmentIndex = workoutClass.segments.size - 1
    }

    LaunchedEffect(activeSegment?.id, activeSegment?.musicFileName) {
        if (activeSegment != null) {
            previewPlayheadMs = 0
            isPreviewPlaying = false
            previewPlayer.pause()
            if (activeSegment.musicFileName.isNotBlank()) {
                val file = File(repository.musicDirectory, activeSegment.musicFileName)
                if (file.exists()) {
                    previewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    previewPlayer.prepare()
                }
            }
            val analyzer = WaveformAnalyzer.getInstance(context)
            val result = analyzer.analyzeWaveform(activeSegment.musicFileName)
            waveformSamples = result.samples
            val needsBpmUpdate = activeSegment.baseBpm == 128.0 && result.bpm != 128.0
            // 寫回時長：WaveformAnalyzer 算出的 durationMs 之前被丟棄，段落永遠停在預設 300_000（Layer 1 第 1 項）。
            val needsDurationUpdate = result.durationMs > 0 && result.durationMs != activeSegment.durationMs
            if (needsBpmUpdate || needsDurationUpdate) {
                val updatedSeg = activeSegment.copy(
                    baseBpm = if (needsBpmUpdate) result.bpm else activeSegment.baseBpm,
                    durationMs = if (needsDurationUpdate) result.durationMs else activeSegment.durationMs
                )
                val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                // 段落時長變了，總時長/預估消耗也要跟著重算，否則頭部「⏱ 總時長」跟 segments 兜不起來。
                workoutClass = workoutClass.copy(segments = updatedSegs).withRecalculatedTotals()
            }
        } else {
            waveformSamples = FloatArray(0)
            previewPlayer.stop()
        }
    }

    LaunchedEffect(isPreviewPlaying, activeSegment?.playbackRate) {
        if (activeSegment != null) {
            if (isPreviewPlaying) {
                previewPlayer.playbackParameters = PlaybackParameters(activeSegment.playbackRate.toFloat())
                previewPlayer.seekTo(previewPlayheadMs.toLong())
                previewPlayer.play()
                while (isActive && isPreviewPlaying) {
                    delay(50)
                    val pos = previewPlayer.currentPosition.toInt()
                    if (previewPlayer.playbackState == Player.STATE_ENDED || pos >= activeSegment.durationMs) {
                        previewPlayheadMs = 0
                        isPreviewPlaying = false
                        previewPlayer.pause()
                        previewPlayer.seekTo(0)
                    } else {
                        previewPlayheadMs = pos
                    }
                }
            } else {
                previewPlayer.pause()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        // TopBar (#84BF09)
        TopNavBar(
            title = "",
            leading = {
                TextButton(onClick = {
                    previewPlayer.stop()
                    onCancel()
                }) {
                    Text(text = "取消", color = Color.White, fontSize = 16.sp)
                }
            },
            trailing = {
                TextButton(onClick = {
                    previewPlayer.stop()
                    // 存檔前的最後一道保險：跟 iOS 儲存按鈕一樣，在呼叫 onSave 前重算一次
                    // （ClassRepository.saveClass 寫入 DB 前也會再算一次，這裡是給呼叫端拿到的 workoutClass 也一致）。
                    onSave(workoutClass.withRecalculatedTotals())
                }) {
                    Text(text = "儲存", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            centerContent = {
                TextField(
                    value = workoutClass.title,
                    onValueChange = { workoutClass = workoutClass.copy(title = it) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        )

        // Sub Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardHeaderBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⏱ 總時長: ${workoutClass.formattedDuration}", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "🔥 預估消耗: ${workoutClass.estimatedCalories.toInt()} kcal", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { isMusicLibraryVisible = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TopBarGreen.copy(alpha = 0.15f),
                    contentColor = TopBarGreenDark
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "匯入音樂檔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = CardBorder)

        // Horizontal Segment Cards
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(workoutClass.segments) { index, segment ->
                val isSelected = index == selectedSegmentIndex
                Column(
                    modifier = Modifier
                        .width(170.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) TopBarGreen.copy(alpha = 0.1f) else CardBackground)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) TopBarGreen else CardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedSegmentIndex = index }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier
                                .background(if (isSelected) TopBarGreen else CardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = segment.formattedDuration, fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = segment.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row {
                        Text(text = "${segment.effectiveBpm.toInt()} BPM", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${segment.cues.size} Cues", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Add Segment Card
            item {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, TopBarGreen, RoundedCornerShape(10.dp))
                        .clickable {
                            // 空段落對教練沒有意義，直接開音樂庫，選好曲目才建立段落（Layer 1 第 4 項、Layer 2 音樂庫）。
                            isMusicLibraryVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TopBarGreen)
                        Text(text = "新增段落", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TopBarGreen)
                    }
                }
            }
        }

        HorizontalDivider(color = CardBorder)

        // Waveform & Cue Section
        if (activeSegment != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Waveform & Preview Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    // Header: Track title & Timecode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeSegment.musicFileName.ifBlank { activeSegment.title },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        val curSec = previewPlayheadMs / 1000
                        val totSec = activeSegment.durationMs / 1000
                        Text(
                            text = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TopBarGreenDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Waveform Canvas with interactive drag seeking
                    WaveformCanvas(
                        samples = waveformSamples,
                        cues = activeSegment.cues,
                        durationMs = activeSegment.durationMs,
                        currentOffsetMs = previewPlayheadMs,
                        onSeek = {
                            previewPlayheadMs = it
                            previewPlayer.seekTo(it.toLong())
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Control Bar: Play/Pause, ±2%, BPM Tap-Tempo, Mark Cue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / Pause Button
                        IconButton(
                            onClick = { isPreviewPlaying = !isPreviewPlaying },
                            modifier = Modifier
                                .size(40.dp)
                                .background(TopBarGreen, CircleShape)
                        ) {
                            Icon(
                                if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPreviewPlaying) "暫停" else "試聽",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Rate buttons: -2%, 100%, +2%
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val newRate = ((activeSegment.playbackRate - 0.02) * 100).roundToInt() / 100.0
                                    val updatedSeg = activeSegment.copy(playbackRate = newRate.coerceIn(0.85, 1.15))
                                    val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                                    workoutClass = workoutClass.copy(segments = updatedSegs)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("-2%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val updatedSeg = activeSegment.copy(playbackRate = 1.0)
                                    val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                                    workoutClass = workoutClass.copy(segments = updatedSegs)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("100%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newRate = ((activeSegment.playbackRate + 0.02) * 100).roundToInt() / 100.0
                                    val updatedSeg = activeSegment.copy(playbackRate = newRate.coerceIn(0.85, 1.15))
                                    val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                                    workoutClass = workoutClass.copy(segments = updatedSegs)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+2%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Live BPM readout & Tap-Tempo Calibration button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CardBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.clickable { isBpmDialogVisible = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = TopBarGreenDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.0f%% (%.1f BPM)", activeSegment.playbackRate * 100, activeSegment.effectiveBpm),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "校正",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TopBarGreenDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Mark Cue Pin Button
                        Button(
                            onClick = {
                                val newOffset = previewPlayheadMs
                                currentEditingCue = WorkoutCue(
                                    id = UUID.randomUUID().toString(),
                                    segmentId = activeSegment.id,
                                    offsetMs = newOffset,
                                    posture = PostureType.STANDING_CLIMB,
                                    handPosition = PostureType.STANDING_CLIMB.defaultHandPosition,
                                    targetRpm = 65,
                                    resistanceLevel = "LEVEL 6",
                                    message = "起立站姿爬坡",
                                    reminders = emptyList()
                                )
                                isEditingCueDialogVisible = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "標記 Cue 點", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cue Section Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "動作設定提示 (${activeSegment.cues.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(activeSegment.cues) { _, cue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable {
                                    currentEditingCue = cue
                                    isEditingCueDialogVisible = true
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hand Position Badge
                            HandPositionBadge(
                                position = cue.handPosition,
                                isCompact = true,
                                showTitle = false
                            )
                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cue.posture.localizedName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${cue.targetRpm} RPM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(TopBarGreen, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = cue.resistanceLevel, fontSize = 12.sp, color = TextSecondary)
                                }
                                if (cue.reminders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "💬 " + cue.reminders.joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = TopBarGreenDark,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                if (cue.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = cue.message, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                }
                            }

                            val sec = cue.offsetMs / 1000
                            Text(
                                text = String.format("%02d:%02d", sec / 60, sec % 60),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )

                            IconButton(onClick = {
                                val updatedCues = activeSegment.cues.filter { it.id != cue.id }
                                val updatedSeg = activeSegment.copy(cues = updatedCues)
                                val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                                workoutClass = workoutClass.copy(segments = updatedSegs)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = AccentRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

        // 匯入失敗要看得見：改用 Snackbar 取代原本只有 Log.e 的無聲失敗（Layer 1 第 5 項）。
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (isEditingCueDialogVisible && currentEditingCue != null) {
        CueEditorDialog(
            cue = currentEditingCue!!,
            onDismiss = {
                isEditingCueDialogVisible = false
                currentEditingCue = null
            },
            onSave = { savedCue ->
                if (activeSegment != null) {
                    val existingIndex = activeSegment.cues.indexOfFirst { it.id == savedCue.id }
                    val updatedCues = if (existingIndex >= 0) {
                        activeSegment.cues.toMutableList().also { it[existingIndex] = savedCue }
                    } else {
                        (activeSegment.cues + savedCue).sortedBy { it.offsetMs }
                    }
                    val updatedSeg = activeSegment.copy(cues = updatedCues)
                    val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                    workoutClass = workoutClass.copy(segments = updatedSegs)
                }
                isEditingCueDialogVisible = false
                currentEditingCue = null
            }
        )
    }

    if (isBpmDialogVisible && activeSegment != null) {
        BpmCalibrationDialog(
            initialBpm = activeSegment.baseBpm,
            onDismiss = { isBpmDialogVisible = false },
            onSave = { newBpm ->
                val updatedSeg = activeSegment.copy(baseBpm = newBpm)
                val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                workoutClass = workoutClass.copy(segments = updatedSegs)
                isBpmDialogVisible = false
            }
        )
    }

    if (isMusicLibraryVisible) {
        MusicLibraryScreen(
            classId = workoutClass.id,
            startOrderIndex = workoutClass.segments.size,
            onDismiss = { isMusicLibraryVisible = false },
            onSegmentsCreated = { newSegments -> appendSegmentsFromLibrary(newSegments) },
            onImportFailed = { failedLabels ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("匯入失敗：${failedLabels.joinToString("、")}")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CueEditorDialog(
    cue: WorkoutCue,
    onDismiss: () -> Unit,
    onSave: (WorkoutCue) -> Unit
) {
    var editingCue by remember { mutableStateOf(cue) }
    var isShowingRemindersPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "編輯動作提示 (Cue)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Time code
                val sec = editingCue.offsetMs / 1000
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("時間點", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = String.format("%02d:%02d (%d 秒)", sec / 60, sec % 60, sec),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TopBarGreenDark,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Posture Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("騎乘姿勢", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PostureType.entries.forEach { posture ->
                            val isSelected = posture == editingCue.posture
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) TopBarGreenDark else CardBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) TopBarGreenDark else CardBorder
                                ),
                                modifier = Modifier.clickable {
                                    editingCue = editingCue.copy(
                                        posture = posture,
                                        handPosition = posture.defaultHandPosition
                                    )
                                }
                            ) {
                                Text(
                                    text = posture.localizedName,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = editingCue.posture.trainingGoalDescription,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Hand Position Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("握把把位指引", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HandPosition.entries.forEach { pos ->
                            val isSelected = pos == editingCue.handPosition
                            Button(
                                onClick = { editingCue = editingCue.copy(handPosition = pos) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) TopBarGreenDark else CardBackground,
                                    contentColor = if (isSelected) Color.White else TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) TopBarGreenDark else CardBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(pos.shortTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HandPositionBadge(position = editingCue.handPosition, isCompact = true)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = editingCue.handPosition.gripDescription,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                HorizontalDivider(color = CardBorder)

                // RPM & Resistance
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("目標踏頻 (RPM)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (editingCue.targetRpm > 40) editingCue = editingCue.copy(targetRpm = editingCue.targetRpm - 5) }
                            ) {
                                Text("-5", fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                            }
                            Text(
                                text = "${editingCue.targetRpm}",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { if (editingCue.targetRpm < 150) editingCue = editingCue.copy(targetRpm = editingCue.targetRpm + 5) }
                            ) {
                                Text("+5", fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editingCue.resistanceLevel,
                        onValueChange = { editingCue = editingCue.copy(resistanceLevel = it) },
                        label = { Text("建議阻力") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Coaching Reminders Library Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("教練專業口訣提示 (${editingCue.reminders.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { isShowingRemindersPicker = true }) {
                            Text("選取口訣庫...", color = TopBarGreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (editingCue.reminders.isEmpty()) {
                        Text(
                            "尚未選取口訣，課堂中將預設輪播姿勢訓練目標",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            editingCue.reminders.forEach { reminder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBackground, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.FormatQuote,
                                        contentDescription = null,
                                        tint = TopBarGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = reminder,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            editingCue = editingCue.copy(reminders = editingCue.reminders - reminder)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "移除", tint = AccentRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CardBorder)

                // Message
                OutlinedTextField(
                    value = editingCue.message,
                    onValueChange = { editingCue = editingCue.copy(message = it) },
                    label = { Text("備註說明 (Message)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editingCue) },
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark)
            ) {
                Text("確定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )

    if (isShowingRemindersPicker) {
        RemindersPickerDialog(
            posture = editingCue.posture,
            initialReminders = editingCue.reminders,
            onDismiss = { isShowingRemindersPicker = false },
            onSave = { updated ->
                editingCue = editingCue.copy(reminders = updated)
                isShowingRemindersPicker = false
            }
        )
    }
}

@Composable
fun RemindersPickerDialog(
    posture: PostureType,
    initialReminders: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val selectedReminders = remember { mutableStateListOf<String>().apply { addAll(initialReminders) } }
    var customInput by remember { mutableStateOf("") }
    val categories = remember(posture) { CoachingReminderLibrary.categories(posture) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("選擇指導口訣", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Custom input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = { Text("輸入自訂口訣...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = customInput.trim()
                            if (trimmed.isNotEmpty() && !selectedReminders.contains(trimmed)) {
                                selectedReminders.add(trimmed)
                                customInput = ""
                            }
                        },
                        enabled = customInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("加入", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = CardBorder)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        item {
                            Text(
                                text = category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TopBarGreenDark,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(category.reminders.size) { idx ->
                            val reminder = category.reminders[idx]
                            val isChecked = selectedReminders.contains(reminder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            selectedReminders.remove(reminder)
                                        } else {
                                            selectedReminders.add(reminder)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedReminders.contains(reminder)) selectedReminders.add(reminder)
                                        } else {
                                            selectedReminders.remove(reminder)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = TopBarGreenDark)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reminder,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedReminders.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark)
            ) {
                Text("完成 (${selectedReminders.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

