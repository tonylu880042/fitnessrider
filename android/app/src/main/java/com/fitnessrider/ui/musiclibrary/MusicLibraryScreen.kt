package com.fitnessrider.ui.musiclibrary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fitnessrider.audio.WaveformAnalyzer
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.model.WorkoutSegment
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.TopNavBar
import com.fitnessrider.ui.editor.ImportedTrackInfo
import com.fitnessrider.ui.editor.buildSegmentsForImportedTracks
import com.fitnessrider.ui.editor.musicTitleFromFileName
import com.fitnessrider.ui.editor.resolveUniqueMusicFileName
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Layer 2「app 內音樂庫」的一筆項目：曲名、時長、BPM 一律來自 [ClassRepository.getWaveform] 的
 * 波形快取，不在這裡重新分析音檔（分析由 Layer 1 的匯入流程或 [WaveformAnalyzer] 快取命中負責）。
 */
internal data class MusicLibraryTrack(
    val fileName: String,
    val title: String,
    val durationMs: Int,
    val bpm: Double
)

/**
 * 由 `Music/` 目錄下的檔名 + 波形快取查詢建立音樂庫列表，並依曲名排序。
 * [waveformLookup] 回傳 (durationMs, bpm)；查無快取時 fallback 回段落預設值，不觸發重新分析。
 */
internal fun buildMusicLibraryTracks(
    fileNames: List<String>,
    waveformLookup: (String) -> Pair<Int, Double>?
): List<MusicLibraryTrack> {
    return fileNames.map { fileName ->
        val cached = waveformLookup(fileName)
        val durationMs = cached?.first?.takeIf { it > 0 } ?: 300_000
        val bpm = cached?.second?.takeIf { it > 0 } ?: 128.0
        MusicLibraryTrack(
            fileName = fileName,
            title = musicTitleFromFileName(fileName),
            durationMs = durationMs,
            bpm = bpm
        )
    }.sortedBy { it.title.lowercase(Locale.ROOT) }
}

/** 即時搜尋 filter，比照舊版 FragDialogSelectMusic：不分大小寫比對曲名子字串。 */
internal fun filterMusicLibraryTracks(tracks: List<MusicLibraryTrack>, query: String): List<MusicLibraryTrack> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return tracks
    val needle = trimmed.lowercase(Locale.ROOT)
    return tracks.filter { it.title.lowercase(Locale.ROOT).contains(needle) }
}

/**
 * 從音樂庫多選既有曲目 -> 直接建立 N 個段落，行為對應 Layer 1 的
 * [buildSegmentsForImportedTracks]，唯一差異是曲目已經在 `Music/` 目錄裡，不會再產生檔案複製。
 */
internal fun buildSegmentsFromLibrarySelection(
    tracks: List<MusicLibraryTrack>,
    classId: String,
    startOrderIndex: Int
): List<WorkoutSegment> {
    val importInfos = tracks.map { ImportedTrackInfo(it.fileName, it.durationMs, it.bpm) }
    return buildSegmentsForImportedTracks(importInfos, classId, startOrderIndex)
}

private fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
}

/**
 * Layer 2：app 內音樂庫。段落指定音樂時開這個列表取代直接開系統檔案選擇器；
 * SAF 只保留在畫面內「＋匯入新檔」這一個入口。選既有曲目時完全不複製檔案，
 * 直接沿用 `Music/` 目錄裡已經有的檔名（與其已經算好、存進波形快取的 duration/BPM）。
 *
 * @param onSegmentsCreated 有新段落產生時呼叫（多選既有曲目 或 匯入新檔皆會呼叫這裡），呼叫後畫面即關閉。
 * @param onImportFailed 匯入新檔部分或全部失敗時呼叫，交由呼叫端（ClassEditorScreen 既有的
 *   SnackbarHostState）顯示錯誤，不在音樂庫裡另外疊一個 SnackbarHost。
 */
@Composable
fun MusicLibraryScreen(
    classId: String,
    startOrderIndex: Int,
    onDismiss: () -> Unit,
    onSegmentsCreated: (List<WorkoutSegment>) -> Unit,
    onImportFailed: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ClassRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var allTracks by remember { mutableStateOf<List<MusicLibraryTrack>>(emptyList()) }
    var selectedFileNames by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var playingFileName by remember { mutableStateOf<String?>(null) }

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playingFileName = null
                    }
                }
            })
        }
    }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    suspend fun reload() {
        val musicDir = repository.musicDirectory
        val fileNames = musicDir.list()?.toList() ?: emptyList()
        // 不重新分析：逐一讀波形快取（durationMs/bpm），查無快取才 fallback 給預設值。
        val waveformByFileName = fileNames.associateWith { fileName ->
            repository.getWaveform(fileName)?.let { it.second to it.third }
        }
        allTracks = buildMusicLibraryTracks(fileNames) { waveformByFileName[it] }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    val filteredTracks = remember(allTracks, searchQuery) {
        filterMusicLibraryTracks(allTracks, searchQuery)
    }

    fun togglePreview(track: MusicLibraryTrack) {
        if (playingFileName == track.fileName) {
            previewPlayer.pause()
            playingFileName = null
        } else {
            val file = File(repository.musicDirectory, track.fileName)
            if (file.exists()) {
                previewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                previewPlayer.prepare()
                previewPlayer.play()
                playingFileName = track.fileName
            }
        }
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        coroutineScope.launch {
            val analyzer = WaveformAnalyzer.getInstance(context)
            val musicDir = repository.musicDirectory
            val existingNames = musicDir.list()?.toMutableSet() ?: mutableSetOf()
            val importedTracks = mutableListOf<ImportedTrackInfo>()
            val failedLabels = mutableListOf<String>()

            for (uri in uris) {
                var displayName = "imported_${System.currentTimeMillis()}_${importedTracks.size + failedLabels.size}.mp3"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                    val fileName = resolveUniqueMusicFileName(displayName, existingNames)
                    existingNames.add(fileName)
                    val destFile = File(musicDir, fileName)
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("無法讀取檔案：$displayName")
                    input.use { stream ->
                        FileOutputStream(destFile).use { output -> stream.copyTo(output) }
                    }

                    val waveform = analyzer.analyzeWaveform(fileName)
                    importedTracks.add(
                        ImportedTrackInfo(
                            fileName = fileName,
                            durationMs = waveform.durationMs,
                            bpm = waveform.bpm
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MusicLibrary", "Error importing music: $displayName", e)
                    failedLabels.add(displayName)
                }
            }

            isImporting = false
            reload()

            if (importedTracks.isNotEmpty()) {
                onSegmentsCreated(
                    buildSegmentsForImportedTracks(
                        tracks = importedTracks,
                        classId = classId,
                        startOrderIndex = startOrderIndex
                    )
                )
            }
            if (failedLabels.isNotEmpty()) {
                onImportFailed(failedLabels)
            }
            // 全部成功才自動關閉；有失敗時留在音樂庫讓教練看得到錯誤、也能重新挑選。
            if (importedTracks.isNotEmpty() && failedLabels.isEmpty()) {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasWhite)
        ) {
            TopNavBar(
                title = "",
                leading = {
                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", color = Color.White, fontSize = 16.sp)
                    }
                },
                trailing = {
                    TextButton(onClick = { musicPickerLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "匯入新檔", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                centerContent = {
                    Text(text = "音樂庫", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            )

            // 搜尋列：比照舊版 FragDialogSelectMusic 的即時 filter。
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                placeholder = { Text("搜尋曲名...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TopBarGreen)
                    }
                }
                filteredTracks.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (allTracks.isEmpty()) "尚未匯入任何音樂，點右上角「匯入新檔」開始" else "找不到符合的曲目",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredTracks, key = { it.fileName }) { track ->
                            val isSelected = selectedFileNames.contains(track.fileName)
                            val isPlaying = playingFileName == track.fileName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) TopBarGreen.copy(alpha = 0.1f) else CardBackground)
                                    .clickable {
                                        selectedFileNames = if (isSelected) {
                                            selectedFileNames - track.fileName
                                        } else {
                                            selectedFileNames + track.fileName
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedFileNames = if (isSelected) {
                                            selectedFileNames - track.fileName
                                        } else {
                                            selectedFileNames + track.fileName
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = TopBarGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${formatDuration(track.durationMs)} ・ ${track.bpm.toInt()} BPM",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(onClick = { togglePreview(track) }) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "暫停試聽" else "試聽",
                                        tint = TopBarGreenDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            HorizontalDivider(color = CardBorder)

            // 底部確認列：多選既有曲目 -> 一次建立 N 個段落，不複製任何檔案（Layer 2 第 3、4 項）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        val selectedTracks = allTracks.filter { selectedFileNames.contains(it.fileName) }
                        if (selectedTracks.isNotEmpty()) {
                            onSegmentsCreated(
                                buildSegmentsFromLibrarySelection(
                                    tracks = selectedTracks,
                                    classId = classId,
                                    startOrderIndex = startOrderIndex
                                )
                            )
                            onDismiss()
                        }
                    },
                    enabled = selectedFileNames.isNotEmpty() && !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen)
                ) {
                    Text(
                        text = if (isImporting) "匯入中..." else "加入 ${selectedFileNames.size} 首到課表",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
