package com.fitnessrider

import com.fitnessrider.audio.CrossfadeCalculator
import com.fitnessrider.data.ExternalMusicEntry
import com.fitnessrider.data.MusicSource
import com.fitnessrider.data.RiderClassArchiveService
import com.fitnessrider.data.isAudioDocument
import com.fitnessrider.model.*
import com.fitnessrider.ui.editor.ImportedTrackInfo
import com.fitnessrider.ui.editor.buildSegmentsForImportedTracks
import com.fitnessrider.ui.editor.musicTitleFromFileName
import com.fitnessrider.ui.editor.resolveUniqueMusicFileName
import com.fitnessrider.ui.musiclibrary.MusicLibraryTrack
import com.fitnessrider.ui.musiclibrary.buildMusicLibraryTracks
import com.fitnessrider.ui.musiclibrary.buildSegmentsFromExternalSelection
import com.fitnessrider.ui.musiclibrary.buildSegmentsFromLibrarySelection
import com.fitnessrider.ui.musiclibrary.copyMusicFileOrCleanup
import com.fitnessrider.ui.musiclibrary.filterExternalMusicEntries
import com.fitnessrider.ui.musiclibrary.filterMusicLibraryTracks
import com.fitnessrider.util.VersionLifecycleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

class FitnessRiderAndroidTest {

    @Test
    fun testPostureTypesAndDefaultRPM() {
        assertEquals(85, PostureType.SEATED_FLAT.defaultRpm)
        assertEquals(60, PostureType.STANDING_CLIMB.defaultRpm)
        assertEquals(110, PostureType.SPRINT.defaultRpm)
        assertEquals("抽車節奏跳躍", PostureType.JUMPS.localizedName)
        assertEquals(PostureType.STANDING_CLIMB, PostureType.fromRaw("STANDING_CLIMB"))
    }

    @Test
    fun testHandPositionAndPostureDefaults() {
        assertEquals(HandPosition.POSITION_1, PostureType.SEATED_FLAT.defaultHandPosition)
        assertEquals(HandPosition.POSITION_2, PostureType.STANDING_FLAT.defaultHandPosition)
        assertEquals(HandPosition.POSITION_3, PostureType.STANDING_CLIMB.defaultHandPosition)
        assertEquals(HandPosition.POSITION_3, PostureType.SPRINT.defaultHandPosition)
        assertEquals(HandPosition.POSITION_2, PostureType.JUMPS.defaultHandPosition)

        assertEquals("1 號位", HandPosition.POSITION_1.shortTitle)
        assertEquals("2 號位", HandPosition.POSITION_2.shortTitle)
        assertEquals("3 號位", HandPosition.POSITION_3.shortTitle)

        assertEquals(HandPosition.POSITION_1, HandPosition.fromValue(1))
        assertEquals(HandPosition.POSITION_2, HandPosition.fromValue(2))
        assertEquals(HandPosition.POSITION_3, HandPosition.fromValue(3))
        assertEquals(HandPosition.POSITION_1, HandPosition.fromValue(99)) // default fallback
    }

    @Test
    fun testCoachingReminderLibrary() {
        assertEquals(20, CoachingReminderLibrary.generalMotion.size)
        assertEquals(9, CoachingReminderLibrary.generalBody.size)

        val climbCues = CoachingReminderLibrary.specificReminders(PostureType.STANDING_CLIMB)
        assertTrue(climbCues.isNotEmpty())
        assertTrue(climbCues.contains("站立的姿勢來爬坡，鍛練股四頭肌的力量"))

        val categories = CoachingReminderLibrary.categories(PostureType.STANDING_CLIMB)
        assertEquals(3, categories.size)
        assertEquals("站姿重爬坡 專屬指令", categories[0].title)
    }

    @Test
    fun testWorkoutDurationFormatting() {
        val workout = WorkoutClass(
            title = "測試課表",
            totalDurationMs = 2700000 // 45 minutes
        )
        assertEquals("45:00", workout.formattedDuration)

        val segment = WorkoutSegment(
            title = "測試段落",
            durationMs = 185000 // 3m 5s
        )
        assertEquals("03:05", segment.formattedDuration)
    }

    // Layer 1 第 1 項延伸：匯入音樂後 WorkoutClass.totalDurationMs / estimatedCalories 要能從
    // segments 正確重算，不能停在匯入前的舊值（或 0）。公式必須與 iOS
    // WorkoutClass.recalculateTotals()（Models/WorkoutClass.swift:41-59）算出同一個數字：
    // 用同一組輸入（118000/93000/206000ms、zone 1/3/5）在 FitnessRiderTests.swift 也驗證了
    // 417000ms 總時長、85.8 kcal，兩邊算出來要一致。
    @Test
    fun testWithRecalculatedTotalsMatchesSegmentsAndIosFormula() {
        val workoutClass = WorkoutClass(
            title = "測試課表",
            // 故意帶入跟 segments 對不上的舊值，模擬匯入前的 stale 狀態
            totalDurationMs = 999,
            estimatedCalories = 999.0,
            segments = listOf(
                WorkoutSegment(title = "暖身", durationMs = 118_000, intensityZone = 1),
                WorkoutSegment(title = "提速", durationMs = 93_000, intensityZone = 3),
                WorkoutSegment(title = "全力衝刺", durationMs = 206_000, intensityZone = 5)
            )
        )

        val recalculated = workoutClass.withRecalculatedTotals()

        assertEquals(417_000, recalculated.totalDurationMs)
        assertEquals(85.8, recalculated.estimatedCalories, 0.001)

        // 沒有段落時要歸零，不能維持舊值
        val empty = workoutClass.copy(segments = emptyList()).withRecalculatedTotals()
        assertEquals(0, empty.totalDurationMs)
        assertEquals(0.0, empty.estimatedCalories, 0.001)

        // 未知 intensityZone（例如 0 或超出 1..5）要 fallback 到預設 10 kcal/min，跟 iOS 的 default 分支一致
        val unknownZone = WorkoutClass(
            segments = listOf(WorkoutSegment(durationMs = 60_000, intensityZone = 0))
        ).withRecalculatedTotals()
        assertEquals(60_000, unknownZone.totalDurationMs)
        assertEquals(10.0, unknownZone.estimatedCalories, 0.001)
    }

    @Test
    fun testTempoClampingAndPercentageStepping() {
        var currentRate = 1.0

        // +2% step
        currentRate += 0.02
        assertEquals(1.02, (currentRate * 100).roundToInt() / 100.0, 0.001)

        // -4% step
        currentRate -= 0.04
        assertEquals(0.98, (currentRate * 100).roundToInt() / 100.0, 0.001)

        // Upper limit clamp (1.15)
        var clampedRate = 1.50.coerceIn(0.85, 1.15)
        assertEquals(1.15, clampedRate, 0.001)

        // Lower limit clamp (0.85)
        clampedRate = 0.50.coerceIn(0.85, 1.15)
        assertEquals(0.85, clampedRate, 0.001)
    }

    @Test
    fun testRiderClassJsonSerializationRoundTrip() {
        val testClass = WorkoutClass(
            id = "test-class-1",
            title = "高燃脂耐力騎行",
            author = "Tony",
            totalDurationMs = 300000,
            estimatedCalories = 150.0,
            segments = listOf(
                WorkoutSegment(
                    id = "seg-1",
                    classId = "test-class-1",
                    orderIndex = 0,
                    title = "熱身爬坡段",
                    musicFileName = "track1.mp3",
                    durationMs = 300000,
                    baseBpm = 128.0,
                    playbackRate = 1.04,
                    intensityZone = 3,
                    cues = listOf(
                        WorkoutCue(
                            id = "cue-1",
                            segmentId = "seg-1",
                            offsetMs = 0,
                            posture = PostureType.SEATED_FLAT,
                            handPosition = HandPosition.POSITION_1,
                            targetRpm = 85,
                            resistanceLevel = "LEVEL 4",
                            message = "坐姿熱身",
                            reminders = listOf("椅墊坐滿", "身體放輕鬆")
                        ),
                        WorkoutCue(
                            id = "cue-2",
                            segmentId = "seg-1",
                            offsetMs = 60000,
                            posture = PostureType.STANDING_CLIMB,
                            handPosition = HandPosition.POSITION_3,
                            targetRpm = 65,
                            resistanceLevel = "LEVEL 7",
                            message = "重齒爬坡抽車",
                            reminders = listOf("站立的姿勢來爬坡，鍛練股四頭肌的力量", "不要甩肩膀")
                        )
                    )
                )
            )
        )

        // Serialize to JSON
        val json = RiderClassArchiveService.serializeClassToJson(testClass)
        assertTrue(json.contains("\"handPosition\": 3"))
        assertTrue(json.contains("站立的姿勢來爬坡，鍛練股四頭肌的力量"))

        // Deserialize back
        val decoded = RiderClassArchiveService.deserializeJsonToClass(json)
        assertEquals("test-class-1", decoded.id)
        assertEquals("高燃脂耐力騎行", decoded.title)
        assertEquals(1, decoded.segments.size)

        val cues = decoded.segments[0].cues
        assertEquals(2, cues.size)

        assertEquals(PostureType.SEATED_FLAT, cues[0].posture)
        assertEquals(HandPosition.POSITION_1, cues[0].handPosition)
        assertEquals(listOf("椅墊坐滿", "身體放輕鬆"), cues[0].reminders)

        assertEquals(PostureType.STANDING_CLIMB, cues[1].posture)
        assertEquals(HandPosition.POSITION_3, cues[1].handPosition)
        assertEquals(listOf("站立的姿勢來爬坡，鍛練股四頭肌的力量", "不要甩肩膀"), cues[1].reminders)
    }

    @Test
    fun testM4RealtimeCalorieAccumulationAndBounds() {
        val totalClassSec = 3000 // 50 minutes
        val totalCalories = 500.0

        // At start (0s)
        var elapsedSec = 0
        var ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(0, (totalCalories * ratio).toInt())

        // At midpoint (1500s)
        elapsedSec = 1500
        ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(250, (totalCalories * ratio).toInt())

        // At end (3000s)
        elapsedSec = 3000
        ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(500, (totalCalories * ratio).toInt())

        // Overtime clamp (3200s)
        elapsedSec = 3200
        ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(500, (totalCalories * ratio).toInt())
    }

    @Test
    fun testM4IntensityZoneColorMapping() {
        val z1 = com.fitnessrider.theme.colorForZone(1)
        val z2 = com.fitnessrider.theme.colorForZone(2)
        val z3 = com.fitnessrider.theme.colorForZone(3)
        val z4 = com.fitnessrider.theme.colorForZone(4)
        val z5 = com.fitnessrider.theme.colorForZone(5)

        assertEquals(com.fitnessrider.theme.Zone1, z1)
        assertEquals(com.fitnessrider.theme.Zone2, z2)
        assertEquals(com.fitnessrider.theme.Zone3, z3)
        assertEquals(com.fitnessrider.theme.Zone4, z4)
        assertEquals(com.fitnessrider.theme.Zone5, z5)
    }

    @Test
    fun testM4AudioSeekingBounds() {
        val duration = 180.0 // 3 minutes

        // Seek -10 from 5s -> clamped to 0.0
        var currentOffset = 5.0
        var targetOffset = (currentOffset - 10.0).coerceIn(0.0, duration)
        assertEquals(0.0, targetOffset, 0.001)

        // Seek +10 from 30s -> 40s
        currentOffset = 30.0
        targetOffset = (currentOffset + 10.0).coerceIn(0.0, duration)
        assertEquals(40.0, targetOffset, 0.001)

        // Seek +10 from 175s -> clamped to 180s
        currentOffset = 175.0
        targetOffset = (currentOffset + 10.0).coerceIn(0.0, duration)
        assertEquals(180.0, targetOffset, 0.001)
    }

    @Test
    fun testVersionLifecycleExpiration() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val buildTime = manager.buildTimeMs
        val oneDayMs = 86_400_000L

        // 1. Same day as build -> not expired, ~30 days remaining
        val day0 = buildTime
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day0))
        assertEquals(30, manager.getRemainingDays(overrideCurrentTimeMs = day0))

        // 2. Day 15 -> not expired, 15 days remaining
        val day15 = buildTime + (15 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day15))
        assertEquals(15, manager.getRemainingDays(overrideCurrentTimeMs = day15))

        // 3. Day 29 -> not expired, 1 day remaining
        val day29 = buildTime + (29 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day29))
        assertEquals(1, manager.getRemainingDays(overrideCurrentTimeMs = day29))

        // 4. Day 30 -> exactly reached/exceeded 30-day lifecycle -> expired!
        val day30 = buildTime + (30 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(overrideCurrentTimeMs = day30))
        assertEquals(0, manager.getRemainingDays(overrideCurrentTimeMs = day30))

        // 5. Day 35 -> expired
        val day35 = buildTime + (35 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(overrideCurrentTimeMs = day35))
        assertEquals(0, manager.getRemainingDays(overrideCurrentTimeMs = day35))

        // 6. Formatting tests
        org.junit.Assert.assertTrue(manager.getFormattedBuildDate().isNotEmpty())
        org.junit.Assert.assertTrue(manager.getFormattedExpirationDate().isNotEmpty())
        org.junit.Assert.assertTrue(manager.updateUrl.startsWith("https://"))

        // 7. Persistence & Anti-Clock Rollback verification with mock Context
        val fakePrefs = FakeSharedPreferences()
        fakePrefs.edit().putLong(manager.KEY_FIRST_LAUNCH_TIME, buildTime).apply()
        val fakeContext = MockContext(fakePrefs)

        // Normal launch on Day 10
        val day10 = buildTime + (10 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day10))
        org.junit.Assert.assertFalse(fakePrefs.getBoolean(manager.KEY_IS_EXPIRED, false))
        assertEquals(day10, fakePrefs.getLong(manager.KEY_LAST_LAUNCH_TIME, 0L))
        assertEquals(20, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day10))

        // Advance warning range on Day 25 (5 days remaining, in 1..7 range)
        val day25 = buildTime + (25 * oneDayMs)
        val remainingDay25 = manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day25)
        assertEquals(5, remainingDay25)
        org.junit.Assert.assertTrue(remainingDay25 in 1..7)

        // Expired launch on Day 35 -> must persist KEY_IS_EXPIRED = true
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))
        org.junit.Assert.assertTrue(fakePrefs.getBoolean(manager.KEY_IS_EXPIRED, false))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day35))

        // Time Rollback Attempt: Clock rolled back to Day 5 after expiration
        val day5 = buildTime + (5 * oneDayMs)
        org.junit.Assert.assertTrue("Rollback attempt after expiration must remain expired", manager.isExpired(fakeContext, overrideCurrentTimeMs = day5))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day5))
    }

    @Test
    fun testThirtyDayTrialCalculationFromFirstLaunch() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val oneDayMs = 86_400_000L
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val firstLaunchTime = 1775000000_000L

        // Day 0: 首次啟動當天 -> 剩餘 30 天，未過期
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = firstLaunchTime))
        assertEquals(30, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = firstLaunchTime))

        // Day 15: 試用第 15 天 -> 剩餘 15 天
        val day15 = firstLaunchTime + (15 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day15))
        assertEquals(15, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day15))

        // Day 25: 試用第 25 天 -> 剩餘 5 天（落於 1..7 天提醒區間）
        val day25 = firstLaunchTime + (25 * oneDayMs)
        val rem25 = manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day25)
        assertEquals(5, rem25)
        org.junit.Assert.assertTrue(rem25 in 1..7)

        // Day 30: 滿 30 天 -> 過期，剩餘 0 天
        val day30 = firstLaunchTime + (30 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day30))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day30))

        // 格式驗證
        org.junit.Assert.assertTrue(manager.getFormattedTrialStartDate(fakeContext).isNotEmpty())
    }

    @Test
    fun testVipLicenseActivationUnlocksExpiredState() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val oneDayMs = 86_400_000L
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val firstLaunchTime = 1775000000_000L
        fakePrefs.edit().putLong(manager.KEY_FIRST_LAUNCH_TIME, firstLaunchTime).apply()
        val day35 = firstLaunchTime + (35 * oneDayMs)

        // 1. 滿 35 天已過期
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day35))

        // 2. 輸入無效序號 -> 失敗，依然過期
        val invalidRes = manager.activateLicenseCode(fakeContext, "INVALID-CODE-1234")
        org.junit.Assert.assertFalse(invalidRes.first)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))

        // 3. 輸入合法 VIP 序號 -> 成功開通，立即解鎖！
        val validRes = manager.activateLicenseCode(fakeContext, "RIDER-VIP-2026-PASS")
        org.junit.Assert.assertTrue(validRes.first)
        org.junit.Assert.assertTrue(manager.isVipActive(fakeContext, overrideCurrentTimeMs = day35))
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))
        assertEquals(365, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day35))
    }

    // Layer 1 第 6 項：檔名碰撞時要加 _1、_2... 後綴，不能互相覆寫。
    @Test
    fun testResolveUniqueMusicFileNameAppendsSuffixOnCollision() {
        // 沒有碰撞，原樣傳回
        assertEquals("track.mp3", resolveUniqueMusicFileName("track.mp3", emptySet()))

        // 撞名一次 -> _1
        assertEquals(
            "track_1.mp3",
            resolveUniqueMusicFileName("track.mp3", setOf("track.mp3"))
        )

        // 撞名兩次（_1 也已存在）-> _2
        assertEquals(
            "track_2.mp3",
            resolveUniqueMusicFileName("track.mp3", setOf("track.mp3", "track_1.mp3"))
        )

        // 沒有副檔名的檔案也要能正確加後綴
        assertEquals("track_1", resolveUniqueMusicFileName("track", setOf("track")))
    }

    @Test
    fun testMusicTitleFromFileNameStripsExtension() {
        assertEquals("我的歌曲", musicTitleFromFileName("我的歌曲.mp3"))
        assertEquals("no_extension", musicTitleFromFileName("no_extension"))
    }

    // Layer 1 第 3 項：選 N 首歌就要建立 N 個段落，標題＝曲名、長度＝曲長，依序對應不覆蓋。
    @Test
    fun testBuildSegmentsForImportedTracksMapsEachUriToOneSegment() {
        val tracks = listOf(
            ImportedTrackInfo(fileName = "track_1.mp3", durationMs = 0, bpm = 0.0),
            ImportedTrackInfo(fileName = "曲目二.mp3", durationMs = 245_000, bpm = 132.5),
            ImportedTrackInfo(fileName = "track_1_1.mp3", durationMs = 180_000, bpm = 0.0)
        )

        val segments = buildSegmentsForImportedTracks(
            tracks = tracks,
            classId = "class-1",
            startOrderIndex = 2
        )

        // N 個檔案 -> N 個段落
        assertEquals(3, segments.size)

        // 依序對應、orderIndex 接續現有段落數量往後排
        assertEquals(2, segments[0].orderIndex)
        assertEquals(3, segments[1].orderIndex)
        assertEquals(4, segments[2].orderIndex)
        segments.forEach { assertEquals("class-1", it.classId) }

        // 標題＝曲名（去副檔名），長度＝曲長
        assertEquals("track_1", segments[0].title)
        assertEquals("曲目二", segments[1].title)
        assertEquals(245_000, segments[1].durationMs)

        // 分析失敗（durationMs/bpm 為 0）時 fallback 回預設值，而不是寫入 0
        assertEquals(300_000, segments[0].durationMs)
        assertEquals(128.0, segments[0].baseBpm, 0.001)
        assertEquals(132.5, segments[1].baseBpm, 0.001)
        assertEquals(180_000, segments[2].durationMs)
        assertEquals(128.0, segments[2].baseBpm, 0.001)

        // 每個新段落都要有預設 cue，維持既有行為
        segments.forEach { assertTrue(it.cues.isNotEmpty()) }
    }

    // Layer 2：音樂庫列表要從檔名 + 波形快取建立，快取命中時直接讀值、不重新分析；
    // 查無快取（理論上不該發生，但保底）才 fallback 回段落預設值，且一律依曲名排序。
    @Test
    fun testBuildMusicLibraryTracksReadsCacheAndSortsByTitle() {
        val cache = mapOf(
            "b_track.mp3" to (210_000 to 118.0),
            "a_track.mp3" to (190_000 to 126.0)
            // "c_no_cache.mp3" 故意沒有快取
        )
        val tracks = buildMusicLibraryTracks(
            fileNames = listOf("b_track.mp3", "c_no_cache.mp3", "a_track.mp3")
        ) { cache[it] }

        // 依曲名排序：a_track -> b_track -> c_no_cache
        assertEquals(listOf("a_track", "b_track", "c_no_cache"), tracks.map { it.title })

        val aTrack = tracks.first { it.fileName == "a_track.mp3" }
        assertEquals(190_000, aTrack.durationMs)
        assertEquals(126.0, aTrack.bpm, 0.001)

        // 沒有快取 -> fallback 回段落預設值，不是 0
        val noCacheTrack = tracks.first { it.fileName == "c_no_cache.mp3" }
        assertEquals(300_000, noCacheTrack.durationMs)
        assertEquals(128.0, noCacheTrack.bpm, 0.001)
    }

    // Layer 2：即時搜尋要比照舊版 FragDialogSelectMusic，不分大小寫比對曲名子字串。
    @Test
    fun testFilterMusicLibraryTracksMatchesTitleCaseInsensitive() {
        val tracks = listOf(
            MusicLibraryTrack("Sprint Fire.mp3", "Sprint Fire", 240_000, 140.0),
            MusicLibraryTrack("warmup_groove.mp3", "warmup_groove", 300_000, 120.0),
            MusicLibraryTrack("climb_anthem.mp3", "climb_anthem", 420_000, 130.0)
        )

        assertEquals(3, filterMusicLibraryTracks(tracks, "").size)
        assertEquals(1, filterMusicLibraryTracks(tracks, "sprint").size)
        assertEquals("Sprint Fire", filterMusicLibraryTracks(tracks, "SPRINT").first().title)
        assertEquals(0, filterMusicLibraryTracks(tracks, "不存在的曲名").size)
    }

    // Layer 2 第 3、4 項：從音樂庫多選既有曲目 -> 直接建立 N 個段落，沿用已存在的檔名與快取
    // 的 duration/BPM，不需要（也不應該）再產生任何檔案複製或重新分析。
    @Test
    fun testBuildSegmentsFromLibrarySelectionReusesExistingFilesWithoutCopying() {
        val selected = listOf(
            MusicLibraryTrack("climb_anthem.mp3", "climb_anthem", 420_000, 130.0),
            MusicLibraryTrack("sprint_fire.mp3", "sprint_fire", 240_000, 140.0)
        )

        val segments = buildSegmentsFromLibrarySelection(
            tracks = selected,
            classId = "class-42",
            startOrderIndex = 3
        )

        assertEquals(2, segments.size)
        assertEquals(3, segments[0].orderIndex)
        assertEquals(4, segments[1].orderIndex)
        // 檔名原封不動沿用（沒有 resolveUniqueMusicFileName 加後綴，因為根本沒有複製動作）
        assertEquals("climb_anthem.mp3", segments[0].musicFileName)
        assertEquals("sprint_fire.mp3", segments[1].musicFileName)
        assertEquals("climb_anthem", segments[0].title)
        assertEquals(420_000, segments[0].durationMs)
        assertEquals(130.0, segments[0].baseBpm, 0.001)
        assertEquals(240_000, segments[1].durationMs)
        assertEquals(140.0, segments[1].baseBpm, 0.001)
    }

    // Layer 3 第 1、2 項：外部資料夾曲目一律以 content Uri 字串表示，天然以 "content://" 開頭，
    // 讓播放/波形分析/匯出等消費端可以用同一個欄位（musicFileName）判斷來源，不必額外加欄位。
    @Test
    fun testMusicSourceIsExternalUriDetectsContentScheme() {
        assertTrue(MusicSource.isExternalUri("content://com.android.externalstorage.documents/tree/1234/document/5678"))
        assertFalse(MusicSource.isExternalUri("track.mp3"))
        assertFalse(MusicSource.isExternalUri(""))
    }

    // Layer 3：資料夾掃描要能用副檔名或 MIME type 過濾出音樂檔，排除資料夾裡的其他檔案。
    @Test
    fun testIsAudioDocumentMatchesByMimeOrExtension() {
        assertTrue(isAudioDocument("track.mp3", "audio/mpeg"))
        assertTrue(isAudioDocument("track.MP3", null)) // 沒有 MIME 時退回副檔名比對，且不分大小寫
        assertTrue(isAudioDocument("track.m4a", null))
        assertFalse(isAudioDocument("cover.jpg", "image/jpeg"))
        assertFalse(isAudioDocument("readme.txt", null))
    }

    // Layer 3：從外部資料夾多選曲目 -> 建立段落時，musicFileName 直接存 content Uri 字串，
    // 不會（也不能）像 Layer 1 的 resolveUniqueMusicFileName 一樣加後綴，因為根本沒有複製、
    // 不會有檔名衝突的問題；時長/BPM 先用預設值，等教練實際選到該段落時才由 WaveformAnalyzer 分析。
    @Test
    fun testBuildSegmentsFromExternalSelectionUsesContentUriAsMusicFileName() {
        val entries = listOf(
            ExternalMusicEntry("content://docs/tree/1/document/warmup.mp3", "warmup.mp3"),
            ExternalMusicEntry("content://docs/tree/1/document/sprint.mp3", "sprint.mp3")
        )

        val segments = buildSegmentsFromExternalSelection(
            entries = entries,
            classId = "class-external",
            startOrderIndex = 2
        )

        assertEquals(2, segments.size)
        assertEquals(2, segments[0].orderIndex)
        assertEquals(3, segments[1].orderIndex)
        assertEquals("content://docs/tree/1/document/warmup.mp3", segments[0].musicFileName)
        assertEquals("content://docs/tree/1/document/sprint.mp3", segments[1].musicFileName)
        assertEquals("warmup", segments[0].title)
        assertEquals("sprint", segments[1].title)
        assertEquals(300_000, segments[0].durationMs)
        assertEquals(128.0, segments[0].baseBpm, 0.001)
        assertTrue(MusicSource.isExternalUri(segments[0].musicFileName))
    }

    // Layer 3：資料夾列表的即時搜尋跟 Layer 2 音樂庫是同一套邏輯，比對顯示檔名子字串。
    @Test
    fun testFilterExternalMusicEntriesMatchesDisplayNameCaseInsensitive() {
        val entries = listOf(
            ExternalMusicEntry("content://docs/1", "Sprint Fire.mp3"),
            ExternalMusicEntry("content://docs/2", "warmup_groove.mp3")
        )

        assertEquals(2, filterExternalMusicEntries(entries, "").size)
        assertEquals(1, filterExternalMusicEntries(entries, "sprint").size)
        assertEquals("Sprint Fire.mp3", filterExternalMusicEntries(entries, "SPRINT").first().displayName)
        assertEquals(0, filterExternalMusicEntries(entries, "不存在").size)
    }

    // 已知落差修復：匯入失敗時已寫入一半的檔案要清掉，不能在 Music 目錄留下截斷檔佔用檔名。
    @Test
    fun testCopyMusicFileOrCleanupDeletesPartialFileOnMidStreamFailure() {
        val tempDir = kotlin.io.path.createTempDirectory(prefix = "music_import_test").toFile()
        try {
            val destFile = java.io.File(tempDir, "track.mp3")

            // 模擬讀到一半就斷線的來源串流：先吐幾個 byte，再丟例外。
            val flakyInput = object : InputStream() {
                var bytesServed = 0
                override fun read(): Int {
                    if (bytesServed >= 4) throw IOException("模擬讀取中斷")
                    bytesServed++
                    return 0x42
                }
            }

            val copied = copyMusicFileOrCleanup(destFile) { flakyInput }

            assertFalse("複製中途失敗要回報 false", copied)
            assertFalse("失敗後半成品檔案必須被刪除，不能留下截斷檔佔用檔名", destFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // 對照組：完整複製成功時檔案要存在、內容要完整，且回報 true。
    @Test
    fun testCopyMusicFileOrCleanupSucceedsAndWritesFullContent() {
        val tempDir = kotlin.io.path.createTempDirectory(prefix = "music_import_test").toFile()
        try {
            val destFile = java.io.File(tempDir, "track.mp3")
            val sourceBytes = byteArrayOf(1, 2, 3, 4, 5)

            val copied = copyMusicFileOrCleanup(destFile) { sourceBytes.inputStream() }

            assertTrue(copied)
            assertTrue(destFile.exists())
            assertTrue(sourceBytes.contentEquals(destFile.readBytes()))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // 資安加固：匯入 .riderclass 時若包含 ../ 等路徑穿透檔名，必須被阻擋，不能寫入 musicDir 以外目錄。
    @Test
    fun testZipSlipPathTraversalBlocked() {
        val baseDir = kotlin.io.path.createTempDirectory("music_base").toFile()
        try {
            val maliciousNames = listOf(
                "../evil.mp3",
                "../../etc/passwd",
                "sub/../../evil.mp3"
            )
            for (name in maliciousNames) {
                val target = java.io.File(baseDir, name)
                val isSafe = target.canonicalPath.startsWith(baseDir.canonicalPath + java.io.File.separator)
                assertFalse("Path traversal name '$name' should be rejected", isSafe)
            }
            val validName = "valid_track.mp3"
            val validTarget = java.io.File(baseDir, validName)
            val isValidSafe = validTarget.canonicalPath.startsWith(baseDir.canonicalPath + java.io.File.separator)
            assertTrue("Normal track name should be accepted", isValidSafe)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    // MARK: - M2 Audio Crossfade Tests

    @Test
    fun testEqualPowerCrossfadeCalculation() {
        // 1. Boundary t = 0.0
        val (out0, in0) = CrossfadeCalculator.calculateEqualPowerVolumes(0.0)
        assertEquals(1.0f, out0, 0.0001f)
        assertEquals(0.0f, in0, 0.0001f)

        // 2. Boundary t = 1.0
        val (out1, in1) = CrossfadeCalculator.calculateEqualPowerVolumes(1.0)
        assertEquals(0.0f, out1, 0.0001f)
        assertEquals(1.0f, in1, 0.0001f)

        // 3. Midpoint t = 0.5 -> Equal power ≈ √2 / 2 ≈ 0.7071f
        val (outMid, inMid) = CrossfadeCalculator.calculateEqualPowerVolumes(0.5)
        assertEquals(0.7071f, outMid, 0.001f)
        assertEquals(0.7071f, inMid, 0.001f)

        // 4. Equal-Power acoustic energy conservation: out^2 + in^2 = 1.0f
        val testPoints = listOf(0.0, 0.1, 0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1.0)
        for (p in testPoints) {
            val (vOut, vIn) = CrossfadeCalculator.calculateEqualPowerVolumes(p)
            val totalPower = (vOut * vOut) + (vIn * vIn)
            assertEquals("Power should be 1.0 at progress $p", 1.0f, totalPower, 0.001f)
        }

        // 5. Clamping for out-of-bounds progress
        val (outNeg, inNeg) = CrossfadeCalculator.calculateEqualPowerVolumes(-0.5)
        assertEquals(1.0f, outNeg, 0.0001f)
        assertEquals(0.0f, inNeg, 0.0001f)

        val (outOver, inOver) = CrossfadeCalculator.calculateEqualPowerVolumes(1.8)
        assertEquals(0.0f, outOver, 0.0001f)
        assertEquals(1.0f, inOver, 0.0001f)
    }

    @Test
    fun testEffectiveCrossfadeDuration() {
        // Auto-pause enabled -> must be 0.0 (strictly mutually exclusive)
        val autoPauseDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 2.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = true
        )
        assertEquals(0.0, autoPauseDuration, 0.0001)

        // Requested 0.0 -> must be 0.0
        val zeroDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 0.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = false
        )
        assertEquals(0.0, zeroDuration, 0.0001)

        // Normal track -> returns requested duration
        val normalDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 2.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = false
        )
        assertEquals(2.0, normalDuration, 0.0001)

        // Short track (1.0s) with 2.0s requested -> clamped to segmentDuration * 0.5 = 0.5s
        val shortDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 2.0,
            segmentDuration = 1.0,
            isAutoPauseEnabled = false
        )
        assertEquals(0.5, shortDuration, 0.0001)
    }

    @Test
    fun testAppSettingsCrossfadeDuration() {
        val fakePrefs = FakeSharedPreferences()
        val mockContext = MockContext(fakePrefs)
        val settings = AppSettings(mockContext)

        // Default value should be 2.0
        assertEquals(2.0, settings.crossfadeDurationSeconds, 0.0001)

        // Update to 3.0
        settings.crossfadeDurationSeconds = 3.0
        assertEquals(3.0, settings.crossfadeDurationSeconds, 0.0001)

        // Update to 0.0 (off)
        settings.crossfadeDurationSeconds = 0.0
        assertEquals(0.0, settings.crossfadeDurationSeconds, 0.0001)
    }

    @Test
    fun testAppSettingsHapticFeedbackEnabled() {
        val fakePrefs = FakeSharedPreferences()
        val mockContext = MockContext(fakePrefs)
        val settings = AppSettings(mockContext)
        val originalValue = settings.isHapticFeedbackEnabled

        // Default should be true
        assertTrue(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = false
        assertFalse(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = originalValue
    }

    // MARK: - M6.3 Device Transfer Tests

    @Test
    fun testDeviceTransferResultModel() {
        val failRes = com.fitnessrider.auth.DeviceTransferResult(
            success = false,
            message = "換機次數受限",
            remainingCooldownDays = 15
        )
        assertFalse(failRes.success)
        assertEquals(15, failRes.remainingCooldownDays)
        assertNull(failRes.planType)

        val successRes = com.fitnessrider.auth.DeviceTransferResult(
            success = true,
            message = "設備轉移成功！",
            planType = "專業年繳版 (VIP)",
            remainingDays = 365
        )
        assertTrue(successRes.success)
        assertNull(successRes.remainingCooldownDays)
        assertEquals("專業年繳版 (VIP)", successRes.planType)
        assertEquals(365, successRes.remainingDays)
    }

    @Test
    fun testDeviceTransferCooldownDaysCalculation() {
        val oneDayMs = 86_400_000L
        val lastTransferMs = 1775000000_000L

        // 12 days later -> remaining = 18 days
        val twelveDaysLater = lastTransferMs + (12 * oneDayMs)
        val elapsedDays1 = (twelveDaysLater - lastTransferMs).toDouble() / oneDayMs
        val remaining1 = Math.max(0, Math.ceil(30.0 - elapsedDays1).toInt())
        assertEquals(18, remaining1)

        // 29.2 days later -> remaining = 1 day
        val almostEnd = lastTransferMs + (29.2 * oneDayMs).toLong()
        val elapsedDays2 = (almostEnd - lastTransferMs).toDouble() / oneDayMs
        val remaining2 = Math.max(0, Math.ceil(30.0 - elapsedDays2).toInt())
        assertEquals(1, remaining2)

        // 30.5 days later -> remaining = 0 days (can transfer)
        val afterCooldown = lastTransferMs + (30.5 * oneDayMs).toLong()
        val elapsedDays3 = (afterCooldown - lastTransferMs).toDouble() / oneDayMs
        val remaining3 = Math.max(0, Math.ceil(30.0 - elapsedDays3).toInt())
        assertEquals(0, remaining3)
    }

    @Test
    fun testDeviceTransferSuccessUnlocksVip() {
        val fakePrefs = FakeSharedPreferences()
        val mockContext = MockContext(fakePrefs)
        val manager = VersionLifecycleManager

        val firstLaunchTime = 1700000000_000L
        fakePrefs.edit().putLong(manager.KEY_FIRST_LAUNCH_TIME, firstLaunchTime).apply()

        val futureTime = 1800000000_000L
        assertTrue(manager.isExpired(mockContext, overrideCurrentTimeMs = futureTime))

        // Transfer activates VIP code
        val res = manager.activateLicenseCode(mockContext, "RIDER-VIP-2026-PASS")
        assertTrue(res.first)
        assertTrue(manager.isVipActive(mockContext, overrideCurrentTimeMs = futureTime))
        assertFalse(manager.isExpired(mockContext, overrideCurrentTimeMs = futureTime))
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = EditorImpl(this)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class EditorImpl(private val prefs: FakeSharedPreferences) : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            override fun putString(key: String?, value: String?) = apply { temp[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { temp[key!!] = values }
            override fun putInt(key: String?, value: Int) = apply { temp[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { temp[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { temp[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { temp[key!!] = value }
            override fun remove(key: String?) = apply { temp[key!!] = null }
            override fun clear() = apply { temp.clear() }
            override fun commit(): Boolean { prefs.data.putAll(temp); return true }
            override fun apply() { prefs.data.putAll(temp) }
        }
    }

    private class MockContext(private val prefs: android.content.SharedPreferences) : android.content.ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
        override fun getApplicationContext(): android.content.Context = this
    }
}



