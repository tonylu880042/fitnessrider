package com.fitnessrider.data

import android.content.Context
import com.fitnessrider.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ClassRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.classDao()

    val musicDirectory: File
        get() {
            val dir = File(context.filesDir, "Music")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getAllClassesFlow(): Flow<List<WorkoutClass>> {
        return dao.getAllClasses().map { entities ->
            entities.map { entity ->
                val segments = dao.getSegmentsForClass(entity.id).map { segEntity ->
                    val cues = dao.getCuesForSegment(segEntity.id).map { cueEntity ->
                        WorkoutCue(
                            id = cueEntity.id,
                            segmentId = cueEntity.segmentId,
                            offsetMs = cueEntity.offsetMs,
                            posture = PostureType.fromRaw(cueEntity.posture),
                            targetRpm = cueEntity.targetRpm,
                            resistanceLevel = cueEntity.resistanceLevel,
                            message = cueEntity.message
                        )
                    }
                    WorkoutSegment(
                        id = segEntity.id,
                        classId = segEntity.classId,
                        orderIndex = segEntity.orderIndex,
                        title = segEntity.title,
                        musicFileName = segEntity.musicFileName,
                        durationMs = segEntity.durationMs,
                        baseBpm = segEntity.baseBpm,
                        playbackRate = segEntity.playbackRate,
                        intensityZone = segEntity.intensityZone,
                        cues = cues
                    )
                }
                WorkoutClass(
                    id = entity.id,
                    title = entity.title,
                    author = entity.author,
                    createdAt = entity.createdAt,
                    totalDurationMs = entity.totalDurationMs,
                    estimatedCalories = entity.estimatedCalories,
                    segments = segments
                )
            }
        }
    }

    suspend fun getClassById(id: String): WorkoutClass? {
        val entity = dao.getClassById(id) ?: return null
        val segments = dao.getSegmentsForClass(entity.id).map { segEntity ->
            val cues = dao.getCuesForSegment(segEntity.id).map { cueEntity ->
                val remindersList = try {
                    val arr = org.json.JSONArray(cueEntity.remindersJson)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (e: Exception) {
                    emptyList<String>()
                }
                WorkoutCue(
                    id = cueEntity.id,
                    segmentId = cueEntity.segmentId,
                    offsetMs = cueEntity.offsetMs,
                    posture = PostureType.fromRaw(cueEntity.posture),
                    targetRpm = cueEntity.targetRpm,
                    resistanceLevel = cueEntity.resistanceLevel,
                    message = cueEntity.message,
                    handPosition = HandPosition.fromValue(cueEntity.handPosition),
                    reminders = remindersList
                )
            }
            WorkoutSegment(
                id = segEntity.id,
                classId = segEntity.classId,
                orderIndex = segEntity.orderIndex,
                title = segEntity.title,
                musicFileName = segEntity.musicFileName,
                durationMs = segEntity.durationMs,
                baseBpm = segEntity.baseBpm,
                playbackRate = segEntity.playbackRate,
                intensityZone = segEntity.intensityZone,
                cues = cues
            )
        }
        return WorkoutClass(
            id = entity.id,
            title = entity.title,
            author = entity.author,
            createdAt = entity.createdAt,
            totalDurationMs = entity.totalDurationMs,
            estimatedCalories = entity.estimatedCalories,
            segments = segments
        )
    }

    suspend fun saveClass(workoutClass: WorkoutClass) {
        val classEntity = ClassEntity(
            id = workoutClass.id,
            title = workoutClass.title,
            author = workoutClass.author,
            createdAt = workoutClass.createdAt.ifEmpty {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            },
            totalDurationMs = workoutClass.totalDurationMs,
            estimatedCalories = workoutClass.estimatedCalories
        )
        dao.insertClass(classEntity)

        dao.deleteSegmentsForClass(workoutClass.id)

        val segmentEntities = workoutClass.segments.map {
            SegmentEntity(
                id = it.id,
                classId = workoutClass.id,
                orderIndex = it.orderIndex,
                title = it.title,
                musicFileName = it.musicFileName,
                durationMs = it.durationMs,
                baseBpm = it.baseBpm,
                playbackRate = it.playbackRate,
                intensityZone = it.intensityZone
            )
        }
        dao.insertSegments(segmentEntities)

        val cueEntities = workoutClass.segments.flatMap { seg ->
            seg.cues.map { cue ->
                val remindersJson = org.json.JSONArray(cue.reminders).toString()
                CueEntity(
                    id = cue.id,
                    segmentId = seg.id,
                    offsetMs = cue.offsetMs,
                    posture = cue.posture.rawValue,
                    targetRpm = cue.targetRpm,
                    resistanceLevel = cue.resistanceLevel,
                    message = cue.message,
                    handPosition = cue.handPosition.value,
                    remindersJson = remindersJson
                )
            }
        }
        dao.insertCues(cueEntities)
    }

    suspend fun deleteClass(id: String) {
        dao.deleteClass(id)
    }

    suspend fun seedSampleClassIfEmpty() {
        val existing = dao.getAllClassesSync()
        if (existing.isEmpty()) {
            val demo = WorkoutClass(
                id = "770E8400-E29B-41D4-A716-446655440001",
                title = "45min 燃脂間歇爬坡示範課",
                author = "Coach Tony",
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
                totalDurationMs = 2700000,
                estimatedCalories = 450.0,
                segments = listOf(
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = "770E8400-E29B-41D4-A716-446655440001",
                        orderIndex = 0,
                        title = "1. 熱身與節奏平路 (Warm-Up Flat)",
                        musicFileName = "warmup_groove.mp3",
                        durationMs = 300_000,
                        baseBpm = 120.0,
                        playbackRate = 1.0,
                        intensityZone = 2,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_FLAT,
                                targetRpm = 85,
                                resistanceLevel = "LEVEL 3",
                                message = "坐姿平路，輕鬆踩踏建立踏頻節奏",
                                handPosition = HandPosition.POSITION_1,
                                reminders = listOf("培養基本的踩踏，建立基本體能", "眼光往前看，身體放輕鬆", "膝蓋和腳尖朝前")
                            ),
                            WorkoutCue(
                                offsetMs = 120_000,
                                posture = PostureType.STANDING_FLAT,
                                targetRpm = 80,
                                resistanceLevel = "LEVEL 4",
                                message = "起立站姿平路，拉長呼吸與核心穩定",
                                handPosition = HandPosition.POSITION_2,
                                reminders = listOf("站姿跑步可運用到更多的核心肌群", "肚子用力收緊核心", "不要甩肩膀，身體不要搖晃")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = "770E8400-E29B-41D4-A716-446655440001",
                        orderIndex = 1,
                        title = "2. 連續重爬坡巡航 (Hill Climbing)",
                        musicFileName = "climb_anthem.mp3",
                        durationMs = 420_000,
                        baseBpm = 130.0,
                        playbackRate = 1.0,
                        intensityZone = 4,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_CLIMB,
                                targetRpm = 68,
                                resistanceLevel = "LEVEL 6",
                                message = "加兩圈重阻力，深沉坐姿爬坡",
                                handPosition = HandPosition.POSITION_1,
                                reminders = listOf("挑戰下半身肌群，尤其是臀肌、腿後腱肌群的力量", "開始爬斜坡", "大腿出力，用腳底板出力")
                            ),
                            WorkoutCue(
                                offsetMs = 180_000,
                                posture = PostureType.STANDING_CLIMB,
                                targetRpm = 60,
                                resistanceLevel = "LEVEL 8",
                                message = "起立站姿重爬坡，用全身重量下壓踩動！",
                                handPosition = HandPosition.POSITION_3,
                                reminders = listOf("站立的姿勢來爬坡，鍛練股四頭肌的力量", "這個訓練要持續加強阻力，模擬爬山的情境", "讓身體往上伸直")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = "770E8400-E29B-41D4-A716-446655440001",
                        orderIndex = 2,
                        title = "3. 爆發高轉速衝刺 (Sprint Interval)",
                        musicFileName = "sprint_fire.mp3",
                        durationMs = 240_000,
                        baseBpm = 140.0,
                        playbackRate = 1.0,
                        intensityZone = 5,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_FLAT,
                                targetRpm = 90,
                                resistanceLevel = "LEVEL 4",
                                message = "預備區間，呼吸吸飽加速中",
                                handPosition = HandPosition.POSITION_1,
                                reminders = listOf("深呼吸，心率慢慢降下來", "眼光往前看")
                            ),
                            WorkoutCue(
                                offsetMs = 60_000,
                                posture = PostureType.SPRINT,
                                targetRpm = 115,
                                resistanceLevel = "LEVEL 5",
                                message = "🔥 全力衝刺 30 秒！踩破 110 RPM！",
                                handPosition = HandPosition.POSITION_3,
                                reminders = listOf("全力衝刺，跟上最快節奏！", "核心收緊，骨盆保持穩定", "注意呼吸，爆發踩踏！")
                            ),
                            WorkoutCue(
                                offsetMs = 150_000,
                                posture = PostureType.RECOVERY,
                                targetRpm = 75,
                                resistanceLevel = "LEVEL 2",
                                message = "放鬆深呼吸，緩和踩踏",
                                handPosition = HandPosition.POSITION_1,
                                reminders = listOf("深呼吸，心率慢慢降下來", "小口補充水分，肌肉放鬆", "快結束了，要進行緩和運動")
                            )
                        )
                    )
                )
            )
            saveClass(demo)
        }
    }
}
