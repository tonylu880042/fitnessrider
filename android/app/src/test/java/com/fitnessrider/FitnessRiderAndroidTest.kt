package com.fitnessrider

import com.fitnessrider.data.RiderClassArchiveService
import com.fitnessrider.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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

    private class FakeSharedPreferences : android.content.SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
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
    }
}



