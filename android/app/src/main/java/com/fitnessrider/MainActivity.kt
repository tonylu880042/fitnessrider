package com.fitnessrider

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.fitnessrider.audio.AudioEngineManager
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.RiderClassArchiveService
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.FitnessRiderTheme
import com.fitnessrider.ui.classlist.ClassListScreen
import com.fitnessrider.ui.editor.ClassEditorScreen
import com.fitnessrider.ui.expiration.ExpirationReason
import com.fitnessrider.ui.expiration.VersionExpiredScreen
import com.fitnessrider.ui.hud.WorkoutHUDScreen
import com.fitnessrider.ui.settings.SettingsScreen
import com.fitnessrider.util.VersionLifecycleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ScreenState {
    LIST,
    HUD,
    EDITOR,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: ClassRepository
    private lateinit var audioEngine: AudioEngineManager
    private lateinit var archiveService: RiderClassArchiveService
    private val pendingImportUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ClassRepository(this)
        audioEngine = AudioEngineManager(this)
        archiveService = RiderClassArchiveService(this)
        if (savedInstanceState == null) pendingImportUri.value = intent?.data

        setContent {
            FitnessRiderTheme {
                var isExpired by remember { mutableStateOf(VersionLifecycleManager.isExpired(this@MainActivity)) }
                var mustUpdate by remember { mutableStateOf(false) }
                var licenseChecked by remember { mutableStateOf(false) }
                val licenseService = remember { com.fitnessrider.auth.LicenseVerificationService(this@MainActivity) }

                LaunchedEffect(Unit) {
                    licenseService.refreshFromServer(currentVersionCode = VersionLifecycleManager.versionCode)
                    isExpired = VersionLifecycleManager.isExpired(this@MainActivity)
                    mustUpdate = licenseService.mustUpdate.value
                    licenseChecked = true
                }

                val importUri = pendingImportUri.value
                LaunchedEffect(importUri, licenseChecked) {
                    if (importUri != null && licenseChecked) {
                        pendingImportUri.value = null
                        if (!isExpired && !mustUpdate) {
                            importRiderClassFromUri(importUri)
                        }
                    }
                }

                if (mustUpdate) {
                    VersionExpiredScreen(reason = ExpirationReason.MUST_UPDATE)
                } else if (isExpired) {
                    VersionExpiredScreen(
                        reason = ExpirationReason.TRIAL_ENDED,
                        onUnlocked = { isExpired = false }
                    )
                } else {
                    val classes by repository.getAllClassesFlow().collectAsState(initial = emptyList())

                    var currentScreen by remember { mutableStateOf(ScreenState.LIST) }
                    var activeClassForHUD by remember { mutableStateOf<WorkoutClass?>(null) }
                    var activeClassForEditor by remember { mutableStateOf<WorkoutClass?>(null) }

                    when (currentScreen) {
                        ScreenState.LIST -> {
                            ClassListScreen(
                                classes = classes,
                                onClassClick = {
                                    activeClassForHUD = it
                                    currentScreen = ScreenState.HUD
                                },
                                onEditClick = {
                                    activeClassForEditor = it
                                    currentScreen = ScreenState.EDITOR
                                },
                                onNewClassClick = {
                                    val newClass = WorkoutClass(
                                        id = UUID.randomUUID().toString(),
                                        title = "新飛輪課表",
                                        author = "Coach"
                                    )
                                    lifecycleScope.launch {
                                        repository.saveClass(newClass)
                                    }
                                    activeClassForEditor = newClass
                                    currentScreen = ScreenState.EDITOR
                                },
                                onSettingsClick = {
                                    currentScreen = ScreenState.SETTINGS
                                },
                                onShareClick = {
                                    lifecycleScope.launch {
                                        val exported = withContext(Dispatchers.IO) { archiveService.exportRiderClass(it) }
                                        if (exported != null) {
                                            shareRiderClassFile(this@MainActivity, exported)
                                        } else {
                                            Toast.makeText(this@MainActivity, "課表匯出失敗", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    lifecycleScope.launch {
                                        repository.deleteClass(it.id)
                                    }
                                }
                            )
                        }

                        ScreenState.HUD -> {
                            activeClassForHUD?.let { wc ->
                                WorkoutHUDScreen(
                                    workoutClass = wc,
                                    audioManager = audioEngine,
                                    onExitClick = {
                                        audioEngine.pause()
                                        currentScreen = ScreenState.LIST
                                    }
                                )
                            } ?: run { currentScreen = ScreenState.LIST }
                        }

                        ScreenState.EDITOR -> {
                            activeClassForEditor?.let { wc ->
                                ClassEditorScreen(
                                    initialClass = wc,
                                    onSave = { updated ->
                                        lifecycleScope.launch {
                                            repository.saveClass(updated)
                                        }
                                        currentScreen = ScreenState.LIST
                                    },
                                    onCancel = {
                                        currentScreen = ScreenState.LIST
                                    }
                                )
                            } ?: run { currentScreen = ScreenState.LIST }
                        }

                        ScreenState.SETTINGS -> {
                            SettingsScreen(
                                onBackClick = { currentScreen = ScreenState.LIST }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImportUri.value = intent.data
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
    }

    private suspend fun importRiderClassFromUri(uri: Uri) {
        val imported = withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val tempFile = File(cacheDir, "incoming_${UUID.randomUUID()}.riderclass")
                    tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                    val result = archiveService.importRiderClass(tempFile)
                    tempFile.delete()
                    result
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (imported != null) {
            Toast.makeText(this, "已成功匯入課表「${imported.title}」", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "課表匯入失敗", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareRiderClassFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享課表包"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "課表匯出失敗", Toast.LENGTH_SHORT).show()
    }
}
