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

    suspend fun saveClass(workoutClassInput: WorkoutClass) {
        // 保險：不管呼叫端（編輯畫面、封存匯入 RiderClassArchiveService、seed 資料...）
        // 是否記得在改動 segments 後呼叫 withRecalculatedTotals()，寫入 DB 前一律用目前的
        // segments 重新推算，避免 totalDurationMs / estimatedCalories 跟 segments 兜不起來。
        val workoutClass = workoutClassInput.withRecalculatedTotals()
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
            val classId = "770E8400-E29B-41D4-A716-446655440001"
            val demo = WorkoutClass(
                id = classId,
                title = "45min 頂級教練燃脂間歇示範課 (All-Star Masterclass)",
                author = "Coach Tony",
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
                totalDurationMs = 1493000,
                estimatedCalories = 480.0,
                segments = listOf(
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 0,
                        title = "1. 暖身平路與基本體能 (Warm-Up)",
                        musicFileName = "Spring In My Step - Silent Partner.mp3",
                        durationMs = 118000,
                        baseBpm = 100.0,
                        playbackRate = 1.0,
                        intensityZone = 1,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_FLAT,
                                handPosition = HandPosition.POSITION_1,
                                targetRpm = 80,
                                resistanceLevel = "LEVEL 3",
                                message = "椅墊坐滿，肩膀放鬆，踏板順暢轉動",
                                reminders = listOf("培養基本的踩踏，建立基本體能", "眼光往前看", "身體放輕鬆")
                            ),
                            WorkoutCue(
                                offsetMs = 60000,
                                posture = PostureType.STANDING_FLAT,
                                handPosition = HandPosition.POSITION_2,
                                targetRpm = 75,
                                resistanceLevel = "LEVEL 4",
                                message = "慢速站起，手肘微彎，穩定核心懸空",
                                reminders = listOf("站姿跑步可運用到更多的核心肌群", "不要甩肩膀", "身體不要搖晃")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 1,
                        title = "2. 提速踩踏與節奏跟進 (Cadence Build)",
                        musicFileName = "Hit the Switch - Silent Partner.mp3",
                        durationMs = 93000,
                        baseBpm = 115.0,
                        playbackRate = 1.0,
                        intensityZone = 1,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_FLAT,
                                handPosition = HandPosition.POSITION_1,
                                targetRpm = 90,
                                resistanceLevel = "LEVEL 4",
                                message = "坐回椅墊，跟隨放克節拍提速至 90 RPM",
                                reminders = listOf("速度慢慢加快", "盡量跟上節拍", "用腳底板出力")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 2,
                        title = "3. 站姿跑步核心穩定 (Standing Rhythm)",
                        musicFileName = "Marvin's Dance - Silent Partner.mp3",
                        durationMs = 94000,
                        baseBpm = 124.0,
                        playbackRate = 1.0,
                        intensityZone = 2,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.STANDING_FLAT,
                                handPosition = HandPosition.POSITION_2,
                                targetRpm = 85,
                                resistanceLevel = "LEVEL 4",
                                message = "握把 2 號位，保持輕快跑步節奏",
                                reminders = listOf("此階段運動更多的核心肌群，可增加騎車的速度並鍛練耐力", "膝蓋和腳尖朝前")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 3,
                        title = "4. 坐姿重阻力爬坡 (Seated Heavy Climb)",
                        musicFileName = "Take That Back - Silent Partner.mp3",
                        durationMs = 159000,
                        baseBpm = 95.0,
                        playbackRate = 1.0,
                        intensityZone = 2,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_CLIMB,
                                handPosition = HandPosition.POSITION_1,
                                targetRpm = 65,
                                resistanceLevel = "LEVEL 6",
                                message = "右轉阻力鈕，深層挑戰臀肌與大腿後側",
                                reminders = listOf("挑戰下半身肌群，尤其是臀肌、腿後腱肌群的力量", "大腿出力", "不要駝背")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 4,
                        title = "5. 站姿重爬坡抽車 (Standing Mountain Climb)",
                        musicFileName = "Alternate - Vibe Tracks.mp3",
                        durationMs = 172000,
                        baseBpm = 128.0,
                        playbackRate = 1.0,
                        intensityZone = 2,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.STANDING_CLIMB,
                                handPosition = HandPosition.POSITION_3,
                                targetRpm = 60,
                                resistanceLevel = "LEVEL 7",
                                message = "雙手握前端牛角 3 號位，利用槓桿爬陡坡",
                                reminders = listOf("站立的姿勢來爬坡，鍛練股四頭肌的力量", "讓身體往上伸直", "身體不要搖晃")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 5,
                        title = "6. 4拍跳躍動態抽車 (Jumps & Dynamics)",
                        musicFileName = "The Itch - NEFFEX.mp3",
                        durationMs = 166000,
                        baseBpm = 130.0,
                        playbackRate = 1.0,
                        intensityZone = 3,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.JUMPS,
                                handPosition = HandPosition.POSITION_2,
                                targetRpm = 75,
                                resistanceLevel = "LEVEL 5",
                                message = "準備起身抽車，4 拍坐下、4 拍站立",
                                reminders = listOf("準備起身抽車，4拍坐下4拍站立", "流暢起伏，注意膝蓋軌跡", "核心收緊，帶動身體節奏")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 6,
                        title = "7. 高踏頻巡航耐力 (High Cadence Cruise)",
                        musicFileName = "Level Up - Quincas Moreira.mp3",
                        durationMs = 190000,
                        baseBpm = 125.0,
                        playbackRate = 1.0,
                        intensityZone = 2,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SEATED_FLAT,
                                handPosition = HandPosition.POSITION_1,
                                targetRpm = 95,
                                resistanceLevel = "LEVEL 4",
                                message = "想像面前有一大片平原，維持高速均速",
                                reminders = listOf("想像前方有一大片平原", "速度要維持，阻力不能掉", "調整呼吸")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 7,
                        title = "8. 極限大阻力對抗 (Power Hill Battle)",
                        musicFileName = "Gas Pedal - Diamond Ortiz.mp3",
                        durationMs = 206000,
                        baseBpm = 105.0,
                        playbackRate = 1.0,
                        intensityZone = 3,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.STANDING_CLIMB,
                                handPosition = HandPosition.POSITION_3,
                                targetRpm = 60,
                                resistanceLevel = "LEVEL 8",
                                message = "阻力加重至 LEVEL 8，身體向上抗阻",
                                reminders = listOf("這個訓練要持續加強阻力，模擬爬山的情境", "肚子用力收緊核心")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 8,
                        title = "9. 無氧極速全力衝刺 (All-Out Sprint)",
                        musicFileName = "You Will Never See Me Coming - NEFFEX.mp3",
                        durationMs = 132000,
                        baseBpm = 140.0,
                        playbackRate = 1.0,
                        intensityZone = 3,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.SPRINT,
                                handPosition = HandPosition.POSITION_3,
                                targetRpm = 110,
                                resistanceLevel = "LEVEL 5",
                                message = "最後一戰！全力衝刺，跟上極限節奏！",
                                reminders = listOf("全力衝刺，跟上最快節奏！", "注意呼吸，爆發踩踏！", "咬牙堅持最後幾秒鐘！")
                            )
                        )
                    ),
                    WorkoutSegment(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        orderIndex = 9,
                        title = "10. 緩和放鬆與心率回穩 (Cool-Down & Recovery)",
                        musicFileName = "Blue Skies - Silent Partner.mp3",
                        durationMs = 163000,
                        baseBpm = 90.0,
                        playbackRate = 1.0,
                        intensityZone = 1,
                        cues = listOf(
                            WorkoutCue(
                                offsetMs = 0,
                                posture = PostureType.RECOVERY,
                                handPosition = HandPosition.POSITION_1,
                                targetRpm = 70,
                                resistanceLevel = "LEVEL 2",
                                message = "阻力降到最輕，深呼吸，慢踩排乳酸",
                                reminders = listOf("深呼吸，心率慢慢降下來", "小口補充水分，肌肉放鬆", "快結束了，要進行緩和運動")
                            )
                        )
                    )
                )
            )
            saveClass(demo)
        }
    }

    // MARK: - Waveform Cache

    // Triple(samples, durationMs, bpm). durationMs 也要一併帶回去，
    // 否則命中快取時 WaveformAnalyzer 只能拿到 samples/bpm，時長會被丟棄變成 0（連帶讓 Layer 1
    // 第 1 項「寫回時長」在快取命中時失效，甚至可能把段落已知的正確時長覆寫掉）。
    suspend fun getWaveform(fileName: String): Triple<FloatArray, Int, Double>? {
        val entity = dao.getWaveform(fileName) ?: return null
        val floats = byteArrayToFloatArray(entity.samplesBlob)
        return Triple(floats, entity.durationMs, entity.calculatedBpm)
    }

    suspend fun saveWaveform(fileName: String, samples: FloatArray, durationMs: Int, bpm: Double) {
        val bytes = floatArrayToByteArray(samples)
        val entity = WaveformEntity(
            fileName = fileName,
            samplesBlob = bytes,
            sampleCount = samples.size,
            durationMs = durationMs,
            calculatedBpm = bpm
        )
        dao.insertWaveform(entity)
    }

    companion object {
        fun floatArrayToByteArray(floats: FloatArray): ByteArray {
            val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (f in floats) {
                buffer.putFloat(f)
            }
            return buffer.array()
        }

        fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                floats[i] = buffer.float
            }
            return floats
        }
    }
}
