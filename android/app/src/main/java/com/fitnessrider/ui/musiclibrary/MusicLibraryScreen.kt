package com.fitnessrider.ui.musiclibrary

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fitnessrider.audio.StreamingSessionMonitor
import com.fitnessrider.audio.WaveformAnalyzer
import com.fitnessrider.audio.normalizedTrackTitle
import com.fitnessrider.audio.sanitizedStreamingDurationMs
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.ExternalMusicEntry
import com.fitnessrider.data.ExternalMusicFolderPrefs
import com.fitnessrider.data.listExternalMusicEntries
import com.fitnessrider.model.WorkoutSegment
import com.fitnessrider.model.streamingDurationMs
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.TopNavBar
import com.fitnessrider.ui.editor.ImportedTrackInfo
import com.fitnessrider.ui.editor.buildSegmentsForImportedTracks
import com.fitnessrider.ui.editor.musicTitleFromFileName
import com.fitnessrider.ui.editor.resolveUniqueMusicFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

internal const val IMPORT_PROGRESS_DELAY_MS = 500L

private const val STREAMING_TAB_INDEX = 2

internal fun importProgressFraction(done: Int, total: Int): Float {
    if (total <= 0) return 0f
    return (done.toFloat() / total).coerceIn(0f, 1f)
}

private data class ImportProgress(val done: Int, val total: Int, val currentTitle: String)

internal data class MusicLibraryTrack(
    val fileName: String,
    val title: String,
    val durationMs: Int,
    val bpm: Double
)

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

internal fun filterMusicLibraryTracks(tracks: List<MusicLibraryTrack>, query: String): List<MusicLibraryTrack> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return tracks
    val needle = trimmed.lowercase(Locale.ROOT)
    return tracks.filter { it.title.lowercase(Locale.ROOT).contains(needle) }
}

internal fun filterExternalMusicEntries(entries: List<ExternalMusicEntry>, query: String): List<ExternalMusicEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return entries
    val needle = trimmed.lowercase(Locale.ROOT)
    return entries.filter { it.displayName.lowercase(Locale.ROOT).contains(needle) }
}

internal fun buildSegmentsFromLibrarySelection(
    tracks: List<MusicLibraryTrack>,
    classId: String,
    startOrderIndex: Int
): List<WorkoutSegment> {
    val importInfos = tracks.map { ImportedTrackInfo(it.fileName, it.durationMs, it.bpm) }
    return buildSegmentsForImportedTracks(importInfos, classId, startOrderIndex)
}

internal fun buildSegmentsFromExternalSelection(
    entries: List<ExternalMusicEntry>,
    analyzed: Map<String, Pair<Int, Double>>,
    classId: String,
    startOrderIndex: Int
): List<WorkoutSegment> {
    val importInfos = entries.map {
        val result = analyzed[it.documentUriString]
        ImportedTrackInfo(
            fileName = it.documentUriString,
            durationMs = result?.first ?: 300_000,
            bpm = result?.second ?: 128.0,
            displayTitle = musicTitleFromFileName(it.displayName)
        )
    }
    return buildSegmentsForImportedTracks(importInfos, classId, startOrderIndex)
}

internal fun buildStreamingSegment(
    title: String,
    durationMs: Int,
    classId: String,
    startOrderIndex: Int
): WorkoutSegment {
    val info = ImportedTrackInfo(fileName = "", durationMs = durationMs, bpm = 128.0, displayTitle = title)
    return buildSegmentsForImportedTracks(listOf(info), classId, startOrderIndex).single()
}

internal fun streamingDurationMsFromInput(minutesText: String, secondsText: String): Int? {
    val minutes = if (minutesText.isBlank()) 0 else minutesText.trim().toIntOrNull()
    val seconds = if (secondsText.isBlank()) 0 else secondsText.trim().toIntOrNull()
    if (minutes == null || seconds == null) return null
    return streamingDurationMs(minutes, seconds)
}

internal fun copyMusicFileOrCleanup(destFile: File, openInput: () -> InputStream): Boolean {
    return try {
        openInput().use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) {
        if (destFile.exists()) destFile.delete()
        false
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
}

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

    var selectedTabIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var allTracks by remember { mutableStateOf<List<MusicLibraryTrack>>(emptyList()) }
    var selectedFileNames by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(ImportProgress(0, 0, "")) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }

    var folderUri by remember { mutableStateOf(ExternalMusicFolderPrefs.getFolderUri(context)) }
    var includeSubdirectories by remember { mutableStateOf(ExternalMusicFolderPrefs.isIncludeSubdirectories(context)) }
    var externalEntries by remember { mutableStateOf<List<ExternalMusicEntry>>(emptyList()) }
    var selectedExternalUris by remember { mutableStateOf(setOf<String>()) }
    var isLoadingExternal by remember { mutableStateOf(false) }
    var externalFolderError by remember { mutableStateOf<String?>(null) }

    var playingKey by remember { mutableStateOf<String?>(null) }

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        playingKey = null
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
        val analyzer = WaveformAnalyzer.getInstance(context)
        val trackInfoByFileName = fileNames.associateWith { fileName ->
            analyzer.readTrackInfo(fileName).let { it.durationMs to it.bpm }
        }
        allTracks = buildMusicLibraryTracks(fileNames) { trackInfoByFileName[it] }
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(folderUri, includeSubdirectories) {
        val uri = folderUri
        if (uri == null) {
            externalEntries = emptyList()
            externalFolderError = null
            return@LaunchedEffect
        }
        isLoadingExternal = true
        externalFolderError = null
        try {
            externalEntries = withContext(Dispatchers.IO) {
                listExternalMusicEntries(context, uri, includeSubdirectories)
            }
        } catch (e: Exception) {
            externalEntries = emptyList()
            externalFolderError = "資料夾存取已失效，請重新選擇資料夾"
        }
        isLoadingExternal = false
    }

    fun startImport(total: Int, work: suspend () -> Unit) {
        importProgress = ImportProgress(0, total, "")
        isImporting = true
        importJob = coroutineScope.launch {
            val reveal = launch {
                delay(IMPORT_PROGRESS_DELAY_MS)
                showImportDialog = true
            }
            try {
                work()
            } finally {
                reveal.cancel()
                showImportDialog = false
                isImporting = false
                importJob = null
            }
        }
    }

    val filteredTracks = remember(allTracks, searchQuery) {
        filterMusicLibraryTracks(allTracks, searchQuery)
    }
    val filteredExternalEntries = remember(externalEntries, searchQuery) {
        filterExternalMusicEntries(externalEntries, searchQuery)
    }

    fun togglePreview(track: MusicLibraryTrack) {
        val key = "lib:${track.fileName}"
        if (playingKey == key) {
            previewPlayer.pause()
            playingKey = null
        } else {
            val file = File(repository.musicDirectory, track.fileName)
            if (file.exists()) {
                previewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                previewPlayer.prepare()
                previewPlayer.play()
                playingKey = key
            }
        }
    }

    fun toggleExternalPreview(entry: ExternalMusicEntry) {
        val key = "ext:${entry.documentUriString}"
        if (playingKey == key) {
            previewPlayer.pause()
            playingKey = null
        } else {
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(entry.documentUriString)))
            previewPlayer.prepare()
            previewPlayer.play()
            playingKey = key
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            ExternalMusicFolderPrefs.setFolderUri(context, uri)
            folderUri = uri
            selectedExternalUris = emptySet()
        }
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        startImport(uris.size) {
            val analyzer = WaveformAnalyzer.getInstance(context)
            val musicDir = repository.musicDirectory
            val existingNames = musicDir.list()?.toMutableSet() ?: mutableSetOf()
            val importedTracks = mutableListOf<ImportedTrackInfo>()
            val failedLabels = mutableListOf<String>()

            try {
                for ((index, uri) in uris.withIndex()) {
                    var displayName = "imported_${System.currentTimeMillis()}_${importedTracks.size + failedLabels.size}.mp3"
                    withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                displayName = cursor.getString(nameIndex)
                            }
                        }
                    }
                    importProgress = ImportProgress(index, uris.size, musicTitleFromFileName(displayName))
                    val fileName = resolveUniqueMusicFileName(displayName, existingNames)
                    existingNames.add(fileName)
                    val destFile = File(musicDir, fileName)

                    val copied = withContext(Dispatchers.IO) {
                        copyMusicFileOrCleanup(destFile) {
                            context.contentResolver.openInputStream(uri)
                                ?: throw java.io.IOException("無法讀取檔案：$displayName")
                        }
                    }

                    if (!copied) {
                        android.util.Log.e("MusicLibrary", "Error importing music: $displayName")
                        failedLabels.add(displayName)
                        continue
                    }

                    val waveform = analyzer.readTrackInfo(fileName)
                    importedTracks.add(
                        ImportedTrackInfo(
                            fileName = fileName,
                            durationMs = waveform.durationMs,
                            bpm = waveform.bpm
                        )
                    )
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { reload() }
                throw e
            }

            reload()
            currentCoroutineContext().ensureActive()

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
                    TextButton(onClick = onDismiss, enabled = !isImporting) {
                        Text(text = "取消", color = Color.White, fontSize = 16.sp)
                    }
                },
                trailing = {
                    if (selectedTabIndex == 0) {
                        TextButton(onClick = { musicPickerLauncher.launch(arrayOf("audio/*")) }, enabled = !isImporting) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "匯入新檔", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedTabIndex == 1) {
                        TextButton(onClick = { folderPickerLauncher.launch(null) }, enabled = !isImporting) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (folderUri == null) "選擇資料夾" else "更換資料夾",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                centerContent = {
                    Text(text = "音樂庫", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            )

            TabRow(selectedTabIndex = selectedTabIndex, containerColor = CanvasWhite) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    enabled = !isImporting,
                    text = { Text("已匯入音樂庫", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    enabled = !isImporting,
                    text = { Text("音樂資料夾", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTabIndex == STREAMING_TAB_INDEX,
                    onClick = { selectedTabIndex = STREAMING_TAB_INDEX },
                    enabled = !isImporting,
                    text = { Text("串流音樂", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (selectedTabIndex != STREAMING_TAB_INDEX) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    singleLine = true,
                    enabled = !isImporting,
                    placeholder = { Text("搜尋曲名...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTabIndex == 0) {
                    LibraryTabContent(
                        enabled = !isImporting,
                        isLoading = isLoading,
                        allTracksEmpty = allTracks.isEmpty(),
                        filteredTracks = filteredTracks,
                        selectedFileNames = selectedFileNames,
                        playingKey = playingKey,
                        onToggleSelect = { track ->
                            selectedFileNames = if (selectedFileNames.contains(track.fileName)) {
                                selectedFileNames - track.fileName
                            } else {
                                selectedFileNames + track.fileName
                            }
                        },
                        onTogglePreview = { togglePreview(it) }
                    )
                } else if (selectedTabIndex == STREAMING_TAB_INDEX) {
                    StreamingTabContent(
                        enabled = !isImporting,
                        onAdd = { title, durationMs ->
                            onSegmentsCreated(listOf(buildStreamingSegment(title, durationMs, classId, startOrderIndex)))
                            onDismiss()
                        }
                    )
                } else {
                    ExternalFolderTabContent(
                        enabled = !isImporting,
                        folderUri = folderUri,
                        includeSubdirectories = includeSubdirectories,
                        onIncludeSubdirectoriesChange = {
                            includeSubdirectories = it
                            ExternalMusicFolderPrefs.setIncludeSubdirectories(context, it)
                        },
                        isLoading = isLoadingExternal,
                        errorMessage = externalFolderError,
                        entries = filteredExternalEntries,
                        allEntriesEmpty = externalEntries.isEmpty(),
                        selectedUris = selectedExternalUris,
                        playingKey = playingKey,
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        onToggleSelect = { entry ->
                            val key = entry.documentUriString
                            selectedExternalUris = if (selectedExternalUris.contains(key)) {
                                selectedExternalUris - key
                            } else {
                                selectedExternalUris + key
                            }
                        },
                        onTogglePreview = { toggleExternalPreview(it) }
                    )
                }
            }

            if (selectedTabIndex != STREAMING_TAB_INDEX) {
                HorizontalDivider(color = CardBorder)

                val selectionCount = if (selectedTabIndex == 0) selectedFileNames.size else selectedExternalUris.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            val analyzer = WaveformAnalyzer.getInstance(context)
                            if (selectedTabIndex == 0) {
                                val selectedTracks = allTracks.filter { selectedFileNames.contains(it.fileName) }
                                if (selectedTracks.isNotEmpty()) {
                                    startImport(selectedTracks.size) {
                                        val analyzedTracks = selectedTracks.mapIndexed { index, track ->
                                            importProgress = ImportProgress(index, selectedTracks.size, track.title)
                                            val result = analyzer.readTrackInfo(track.fileName)
                                            track.copy(durationMs = result.durationMs, bpm = result.bpm)
                                        }
                                        currentCoroutineContext().ensureActive()
                                        onSegmentsCreated(
                                            buildSegmentsFromLibrarySelection(
                                                tracks = analyzedTracks,
                                                classId = classId,
                                                startOrderIndex = startOrderIndex
                                            )
                                        )
                                        onDismiss()
                                    }
                                }
                            } else {
                                val selectedEntries = externalEntries.filter {
                                    selectedExternalUris.contains(it.documentUriString)
                                }
                                if (selectedEntries.isNotEmpty()) {
                                    startImport(selectedEntries.size) {
                                        val analyzed = mutableMapOf<String, Pair<Int, Double>>()
                                        selectedEntries.forEachIndexed { index, entry ->
                                            importProgress = ImportProgress(
                                                index,
                                                selectedEntries.size,
                                                musicTitleFromFileName(entry.displayName)
                                            )
                                            val result = analyzer.readTrackInfo(entry.documentUriString)
                                            analyzed[entry.documentUriString] = result.durationMs to result.bpm
                                        }
                                        currentCoroutineContext().ensureActive()
                                        onSegmentsCreated(
                                            buildSegmentsFromExternalSelection(
                                                entries = selectedEntries,
                                                analyzed = analyzed,
                                                classId = classId,
                                                startOrderIndex = startOrderIndex
                                            )
                                        )
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = selectionCount > 0 && !isImporting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen)
                    ) {
                        Text(
                            text = if (isImporting) "匯入中..." else "加入 $selectionCount 首到課表",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (showImportDialog) {
            ImportProgressDialog(
                progress = importProgress,
                onCancel = { importJob?.cancel() }
            )
        }
    }
}

@Composable
internal fun StreamingDurationFields(
    minutesText: String,
    secondsText: String,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = minutesText,
            onValueChange = { onMinutesChange(it.filter(Char::isDigit).take(2)) },
            label = { Text("分") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = secondsText,
            onValueChange = { onSecondsChange(it.filter(Char::isDigit).take(2)) },
            label = { Text("秒") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StreamingTabContent(enabled: Boolean, onAdd: (String, Int) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }
    val trimmedTitle = title.trim()
    val durationMs = streamingDurationMsFromInput(minutesText, secondsText)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "歌曲由 Spotify、KKBOX 等 App 播放，FitnessRider 只負責計時與提示",
            fontSize = 13.sp,
            color = TextSecondary
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("曲名") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        StreamingDurationFields(
            minutesText = minutesText,
            secondsText = secondsText,
            onMinutesChange = { minutesText = it },
            onSecondsChange = { secondsText = it }
        )
        if (remember { StreamingSessionMonitor.isAuthorized(context) }) {
            OutlinedButton(
                onClick = {
                    val nowPlaying = StreamingSessionMonitor.readNowPlayingOnce(context)
                    val nowTitle = normalizedTrackTitle(nowPlaying?.title)
                    if (nowPlaying == null || nowTitle == null) {
                        Toast.makeText(context, "目前沒有正在播放的歌曲", Toast.LENGTH_SHORT).show()
                    } else {
                        title = nowTitle
                        sanitizedStreamingDurationMs(nowPlaying.durationMs)?.let {
                            minutesText = (it / 60_000).toString()
                            secondsText = ((it / 1000) % 60).toString()
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "帶入正在播放的歌", fontWeight = FontWeight.Bold, color = TopBarGreenDark)
            }
        }
        Button(
            onClick = { if (durationMs != null) onAdd(trimmedTitle, durationMs) },
            enabled = enabled && trimmedTitle.isNotEmpty() && durationMs != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen)
        ) {
            Text(text = "加入課表", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImportProgressDialog(progress: ImportProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = CanvasWhite,
        title = { Text("正在加入歌曲", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                Text("${progress.done} / ${progress.total}", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { importProgressFraction(progress.done, progress.total) },
                    modifier = Modifier.fillMaxWidth(),
                    color = TopBarGreen,
                    trackColor = CardBorder
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress.currentTitle,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("取消", color = AccentRed, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun LibraryTabContent(
    enabled: Boolean,
    isLoading: Boolean,
    allTracksEmpty: Boolean,
    filteredTracks: List<MusicLibraryTrack>,
    selectedFileNames: Set<String>,
    playingKey: String?,
    onToggleSelect: (MusicLibraryTrack) -> Unit,
    onTogglePreview: (MusicLibraryTrack) -> Unit
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TopBarGreen)
            }
        }
        filteredTracks.isEmpty() -> {
            EmptyState(
                icon = Icons.Default.MusicNote,
                message = if (allTracksEmpty) "尚未匯入任何音樂，點右上角「匯入新檔」開始" else "找不到符合的曲目"
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTracks, key = { it.fileName }) { track ->
                    val isSelected = selectedFileNames.contains(track.fileName)
                    val isPlaying = playingKey == "lib:${track.fileName}"
                    MusicRow(
                        title = track.title,
                        subtitle = "${formatDuration(track.durationMs)} ・ ${track.bpm.toInt()} BPM",
                        isSelected = isSelected,
                        isPlaying = isPlaying,
                        enabled = enabled,
                        onClick = { onToggleSelect(track) },
                        onTogglePreview = { onTogglePreview(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalFolderTabContent(
    enabled: Boolean,
    folderUri: Uri?,
    includeSubdirectories: Boolean,
    onIncludeSubdirectoriesChange: (Boolean) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    entries: List<ExternalMusicEntry>,
    allEntriesEmpty: Boolean,
    selectedUris: Set<String>,
    playingKey: String?,
    onPickFolder: () -> Unit,
    onToggleSelect: (ExternalMusicEntry) -> Unit,
    onTogglePreview: (ExternalMusicEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (folderUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "直接讀取資料夾內容，不會複製進 App",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text("含子資料夾", fontSize = 12.sp, color = TextSecondary)
                Switch(
                    checked = includeSubdirectories,
                    onCheckedChange = onIncludeSubdirectoriesChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = TopBarGreen)
                )
            }
        }

        when {
            folderUri == null -> {
                EmptyState(
                    icon = Icons.Default.Folder,
                    message = "尚未選擇音樂資料夾",
                    actionLabel = "選擇資料夾",
                    onAction = onPickFolder,
                    actionEnabled = enabled
                )
            }
            errorMessage != null -> {
                EmptyState(
                    icon = Icons.Default.Warning,
                    message = errorMessage,
                    actionLabel = "重新選擇資料夾",
                    onAction = onPickFolder,
                    actionEnabled = enabled
                )
            }
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TopBarGreen)
                }
            }
            entries.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.MusicNote,
                    message = if (allEntriesEmpty) "這個資料夾裡沒有找到音樂檔" else "找不到符合的曲目"
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.documentUriString }) { entry ->
                        val key = entry.documentUriString
                        val isSelected = selectedUris.contains(key)
                        val isPlaying = playingKey == "ext:$key"
                        MusicRow(
                            title = musicTitleFromFileName(entry.displayName),
                            subtitle = "外部資料夾",
                            isSelected = isSelected,
                            isPlaying = isPlaying,
                            enabled = enabled,
                            onClick = { onToggleSelect(entry) },
                            onTogglePreview = { onTogglePreview(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = TextSecondary, fontSize = 14.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAction, enabled = actionEnabled, colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen)) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MusicRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) TopBarGreen.copy(alpha = 0.1f) else CardBackground)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = TopBarGreen)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        IconButton(onClick = onTogglePreview, enabled = enabled) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暫停試聽" else "試聽",
                tint = TopBarGreenDark
            )
        }
    }
}
