package com.fitnessrider

import com.fitnessrider.model.PostureType
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.model.WorkoutCue
import com.fitnessrider.model.WorkoutSegment
import org.junit.Assert.assertEquals
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
}
