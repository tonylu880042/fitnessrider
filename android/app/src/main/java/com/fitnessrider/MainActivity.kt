package com.fitnessrider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.fitnessrider.audio.AudioEngineManager
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.RiderClassArchiveService
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.FitnessRiderTheme
import com.fitnessrider.ui.classlist.ClassListScreen
import com.fitnessrider.ui.editor.ClassEditorScreen
import com.fitnessrider.ui.expiration.VersionExpiredScreen
import com.fitnessrider.ui.hud.WorkoutHUDScreen
import com.fitnessrider.ui.settings.SettingsScreen
import com.fitnessrider.util.VersionLifecycleManager
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ClassRepository(this)
        audioEngine = AudioEngineManager(this)
        archiveService = RiderClassArchiveService(this)

        // Handle incoming .riderclass intent if launched via file manager or AirDrop
        intent?.data?.let { uri ->
            lifecycleScope.launch {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val tempFile = File(cacheDir, "incoming.riderclass")
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        archiveService.importRiderClass(tempFile)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        setContent {
            FitnessRiderTheme {
                var isExpired by remember { mutableStateOf(VersionLifecycleManager.isExpired(this@MainActivity)) }

                if (isExpired) {
                    VersionExpiredScreen(
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
                                        archiveService.exportRiderClass(it)
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

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
    }
}
