package com.fitnessrider

import com.fitnessrider.audio.CrossfadeCalculator
import com.fitnessrider.audio.CrossfadeFinishCoordinator
import com.fitnessrider.audio.HapticFeedbackManager
import com.fitnessrider.audio.SynchronizedOnceCache
import com.fitnessrider.data.ExternalMusicEntry
import com.fitnessrider.data.MusicSource
import com.fitnessrider.data.RiderClassArchiveService
import com.fitnessrider.data.isAudioDocument
import com.fitnessrider.model.*
import com.fitnessrider.ui.editor.ImportedTrackInfo
import com.fitnessrider.ui.editor.buildSegmentsForImportedTracks
import com.fitnessrider.ui.editor.musicTitleFromFileName
import com.fitnessrider.ui.editor.resolveUniqueMusicFileName
import com.fitnessrider.ui.editor.reindexedSegments
import com.fitnessrider.ui.editor.segmentsAfterMove
import com.fitnessrider.ui.editor.segmentsAfterRemoval
import com.fitnessrider.ui.editor.selectedIndexAfterMove
import com.fitnessrider.ui.editor.selectedIndexAfterRemoval
import com.fitnessrider.ui.hud.rateStepForSwipe
import com.fitnessrider.ui.hud.segmentStepForSwipe
import com.fitnessrider.ui.musiclibrary.MusicLibraryTrack
import com.fitnessrider.ui.musiclibrary.buildMusicLibraryTracks
import com.fitnessrider.ui.musiclibrary.buildSegmentsFromExternalSelection
import com.fitnessrider.ui.musiclibrary.buildSegmentsFromLibrarySelection
import com.fitnessrider.ui.musiclibrary.copyMusicFileOrCleanup
import com.fitnessrider.ui.musiclibrary.filterExternalMusicEntries
import com.fitnessrider.ui.musiclibrary.filterMusicLibraryTracks
import com.fitnessrider.auth.LicenseVerificationService
import com.fitnessrider.util.VersionLifecycleManager
import com.fitnessrider.util.VipSerialVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
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
        assertEquals(HandPosition.POSITION_1, HandPosition.fromValue(99))
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
            totalDurationMs = 2700000
        )
        assertEquals("45:00", workout.formattedDuration)

        val segment = WorkoutSegment(
            title = "測試段落",
            durationMs = 185000
        )
        assertEquals("03:05", segment.formattedDuration)
    }

    @Test
    fun testWithRecalculatedTotalsMatchesSegmentsAndIosFormula() {
        val workoutClass = WorkoutClass(
            title = "測試課表",
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

        val empty = workoutClass.copy(segments = emptyList()).withRecalculatedTotals()
        assertEquals(0, empty.totalDurationMs)
        assertEquals(0.0, empty.estimatedCalories, 0.001)

        val unknownZone = WorkoutClass(
            segments = listOf(WorkoutSegment(durationMs = 60_000, intensityZone = 0))
        ).withRecalculatedTotals()
        assertEquals(60_000, unknownZone.totalDurationMs)
        assertEquals(10.0, unknownZone.estimatedCalories, 0.001)
    }

    @Test
    fun testTempoClampingAndPercentageStepping() {
        var currentRate = 1.0

        currentRate += 0.02
        assertEquals(1.02, (currentRate * 100).roundToInt() / 100.0, 0.001)

        currentRate -= 0.04
        assertEquals(0.98, (currentRate * 100).roundToInt() / 100.0, 0.001)

        var clampedRate = 1.50.coerceIn(0.85, 1.15)
        assertEquals(1.15, clampedRate, 0.001)

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

        val json = RiderClassArchiveService.serializeClassToJson(testClass)
        assertTrue(json.contains("\"handPosition\": 3"))
        assertTrue(json.contains("站立的姿勢來爬坡，鍛練股四頭肌的力量"))

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
    fun testRiderClassZipEntryIsStoredWithRealSizesForIosCompat() {
        val payload = "{\"title\":\"測試課表\"}".toByteArray(Charsets.UTF_8)
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zos ->
            RiderClassArchiveService.writeStoredEntry(zos, "workout_class.json", payload)
        }
        val zip = output.toByteArray()

        assertEquals(0x50, zip[0].toInt() and 0xFF)
        assertEquals(0x4b, zip[1].toInt() and 0xFF)
        assertEquals(0x03, zip[2].toInt() and 0xFF)
        assertEquals(0x04, zip[3].toInt() and 0xFF)

        val generalPurposeFlag = (zip[6].toInt() and 0xFF) or ((zip[7].toInt() and 0xFF) shl 8)
        assertEquals("data descriptor bit (bit 3) must not be set", 0, generalPurposeFlag and 0x08)

        val method = (zip[8].toInt() and 0xFF) or ((zip[9].toInt() and 0xFF) shl 8)
        assertEquals("method must be STORED (0)", 0, method)

        val compressedSize = ((zip[18].toInt() and 0xFF)) or
            ((zip[19].toInt() and 0xFF) shl 8) or
            ((zip[20].toInt() and 0xFF) shl 16) or
            ((zip[21].toInt() and 0xFF) shl 24)
        val uncompressedSize = ((zip[22].toInt() and 0xFF)) or
            ((zip[23].toInt() and 0xFF) shl 8) or
            ((zip[24].toInt() and 0xFF) shl 16) or
            ((zip[25].toInt() and 0xFF) shl 24)

        assertEquals(payload.size, compressedSize)
        assertEquals(payload.size, uncompressedSize)
        assertTrue(compressedSize != 0)
    }

    @Test
    fun testRegenerateIdsProducesFreshIdsOnEveryImport() {
        val original = WorkoutClass(
            id = "original-class",
            title = "分享課表",
            segments = listOf(
                WorkoutSegment(
                    id = "original-seg",
                    classId = "original-class",
                    musicFileName = "track.mp3",
                    cues = listOf(
                        WorkoutCue(id = "original-cue", segmentId = "original-seg")
                    )
                )
            )
        )

        val firstImport = RiderClassArchiveService.regenerateIds(original)
        val secondImport = RiderClassArchiveService.regenerateIds(original)

        assertNotEquals("original-class", firstImport.id)
        assertNotEquals("original-class", secondImport.id)
        assertNotEquals(firstImport.id, secondImport.id)

        assertEquals(firstImport.id, firstImport.segments[0].classId)
        assertEquals(firstImport.segments[0].id, firstImport.segments[0].cues[0].segmentId)
        assertEquals(secondImport.id, secondImport.segments[0].classId)
        assertEquals(secondImport.segments[0].id, secondImport.segments[0].cues[0].segmentId)
        assertNotEquals(firstImport.segments[0].id, secondImport.segments[0].id)
        assertNotEquals(firstImport.segments[0].cues[0].id, secondImport.segments[0].cues[0].id)
    }

    @Test
    fun testResolveImportedFileNamesReusesIdenticalAndSuffixesDifferentContent() {
        val musicDir = java.nio.file.Files.createTempDirectory("musicDir").toFile()
        try {
            val existingSameContent = File(musicDir, "track.mp3")
            existingSameContent.writeBytes(byteArrayOf(1, 2, 3))

            val existingDifferentContent = File(musicDir, "collide.mp3")
            existingDifferentContent.writeBytes(byteArrayOf(9, 9, 9))

            val incomingSameContentTemp = File.createTempFile("incoming", ".tmp")
            incomingSameContentTemp.writeBytes(byteArrayOf(1, 2, 3))

            val incomingDifferentContentTemp = File.createTempFile("incoming", ".tmp")
            incomingDifferentContentTemp.writeBytes(byteArrayOf(4, 5, 6))

            val incomingNewTemp = File.createTempFile("incoming", ".tmp")
            incomingNewTemp.writeBytes(byteArrayOf(7, 8))

            val extracted = mapOf(
                "track.mp3" to incomingSameContentTemp,
                "collide.mp3" to incomingDifferentContentTemp,
                "brand_new.mp3" to incomingNewTemp
            )

            val mapping = RiderClassArchiveService.resolveImportedFileNames(extracted, musicDir)

            assertEquals("track.mp3", mapping["track.mp3"])
            assertEquals(byteArrayOf(1, 2, 3).toList(), existingSameContent.readBytes().toList())

            val renamed = mapping["collide.mp3"]!!
            assertNotEquals("collide.mp3", renamed)
            assertEquals(byteArrayOf(9, 9, 9).toList(), existingDifferentContent.readBytes().toList())
            assertEquals(byteArrayOf(4, 5, 6).toList(), File(musicDir, renamed).readBytes().toList())

            assertEquals("brand_new.mp3", mapping["brand_new.mp3"])
            assertEquals(byteArrayOf(7, 8).toList(), File(musicDir, "brand_new.mp3").readBytes().toList())
        } finally {
            musicDir.deleteRecursively()
        }
    }

    @Test
    fun testM4RealtimeCalorieAccumulationAndBounds() {
        val totalClassSec = 3000
        val totalCalories = 500.0

        var elapsedSec = 0
        var ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(0, (totalCalories * ratio).toInt())

        elapsedSec = 1500
        ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(250, (totalCalories * ratio).toInt())

        elapsedSec = 3000
        ratio = (elapsedSec.toDouble() / totalClassSec).coerceIn(0.0, 1.0)
        assertEquals(500, (totalCalories * ratio).toInt())

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
        val duration = 180.0

        var currentOffset = 5.0
        var targetOffset = (currentOffset - 10.0).coerceIn(0.0, duration)
        assertEquals(0.0, targetOffset, 0.001)

        currentOffset = 30.0
        targetOffset = (currentOffset + 10.0).coerceIn(0.0, duration)
        assertEquals(40.0, targetOffset, 0.001)

        currentOffset = 175.0
        targetOffset = (currentOffset + 10.0).coerceIn(0.0, duration)
        assertEquals(180.0, targetOffset, 0.001)
    }

    @Test
    fun testVersionLifecycleExpiration() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val buildTime = manager.buildTimeMs
        val oneDayMs = 86_400_000L

        val day0 = buildTime
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day0))
        assertEquals(7, manager.getRemainingDays(overrideCurrentTimeMs = day0))

        val day3 = buildTime + (3 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day3))
        assertEquals(4, manager.getRemainingDays(overrideCurrentTimeMs = day3))

        val day6 = buildTime + (6 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(overrideCurrentTimeMs = day6))
        assertEquals(1, manager.getRemainingDays(overrideCurrentTimeMs = day6))

        val day7 = buildTime + (7 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(overrideCurrentTimeMs = day7))
        assertEquals(0, manager.getRemainingDays(overrideCurrentTimeMs = day7))

        val day10 = buildTime + (10 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(overrideCurrentTimeMs = day10))
        assertEquals(0, manager.getRemainingDays(overrideCurrentTimeMs = day10))

        org.junit.Assert.assertTrue(manager.getFormattedBuildDate().isNotEmpty())
        org.junit.Assert.assertTrue(manager.getFormattedExpirationDate().isNotEmpty())
        org.junit.Assert.assertTrue(manager.updateUrl.startsWith("https://"))

        val fakePrefs = FakeSharedPreferences()
        fakePrefs.edit().putLong(manager.KEY_FIRST_LAUNCH_TIME, buildTime).apply()
        val fakeContext = MockContext(fakePrefs)

        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day3))
        org.junit.Assert.assertFalse(fakePrefs.getBoolean(manager.KEY_IS_EXPIRED, false))
        assertEquals(day3, fakePrefs.getLong(manager.KEY_LAST_LAUNCH_TIME, 0L))
        assertEquals(4, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day3))

        val day5 = buildTime + (5 * oneDayMs)
        val remainingDay5 = manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day5)
        assertEquals(2, remainingDay5)
        org.junit.Assert.assertTrue(remainingDay5 in 1..7)

        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day10))
        org.junit.Assert.assertTrue(fakePrefs.getBoolean(manager.KEY_IS_EXPIRED, false))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day10))

        val day2 = buildTime + (2 * oneDayMs)
        org.junit.Assert.assertTrue("Rollback attempt after expiration must remain expired", manager.isExpired(fakeContext, overrideCurrentTimeMs = day2))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day2))
    }

    @Test
    fun testSevenDayTrialCalculationFromFirstLaunch() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val oneDayMs = 86_400_000L
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val firstLaunchTime = 1775000000_000L

        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = firstLaunchTime))
        assertEquals(7, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = firstLaunchTime))

        val day3 = firstLaunchTime + (3 * oneDayMs)
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day3))
        assertEquals(4, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day3))

        val day7 = firstLaunchTime + (7 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day7))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day7))

        org.junit.Assert.assertTrue(manager.getFormattedTrialStartDate(fakeContext).isNotEmpty())
    }

    @Test
    fun testPromoCodeActivationAndAntiAbuse() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val oneDayMs = 86_400_000L
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val firstLaunchTime = 1775000000_000L
        fakePrefs.edit().putLong(manager.KEY_FIRST_LAUNCH_TIME, firstLaunchTime).apply()

        val day10 = firstLaunchTime + (10 * oneDayMs)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day10))

        val promoRes = manager.activateLicenseCode(fakeContext, "26FR-NR", overrideCurrentTimeMs = day10)
        org.junit.Assert.assertTrue(promoRes.first)
        org.junit.Assert.assertTrue(promoRes.second.contains("30 天"))
        org.junit.Assert.assertTrue(manager.isVipActive(fakeContext, overrideCurrentTimeMs = day10))
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day10))

        val duplicateRes = manager.activateLicenseCode(fakeContext, "26FR-NR", overrideCurrentTimeMs = day10)
        org.junit.Assert.assertFalse(duplicateRes.first)
        org.junit.Assert.assertTrue(duplicateRes.second.contains("無法重複領取") || duplicateRes.second.contains("已兌換過"))
    }

    private fun generateTestEcKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun buildVipPayload(serialIdHex: String, planDays: Int): ByteArray {
        val serialIdBytes = ByteArray(4) { i ->
            serialIdHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val buf = java.nio.ByteBuffer.allocate(7)
        buf.put(1)
        buf.put(serialIdBytes)
        buf.putShort(planDays.toShort())
        return buf.array()
    }

    private fun signVipSerial(privateKey: java.security.PrivateKey, serialIdHex: String, planDays: Int): String {
        val payload = buildVipPayload(serialIdHex, planDays)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payload)
        val sig = signer.sign()
        val payloadHex = payload.joinToString("") { "%02X".format(it) }
        val sigHex = sig.joinToString("") { "%02X".format(it) }
        return "FRVIP-$payloadHex-$sigHex"
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

        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))
        assertEquals(0, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day35))

        val invalidRes = manager.activateLicenseCode(fakeContext, "INVALID-CODE-1234")
        org.junit.Assert.assertFalse(invalidRes.first)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))

        org.junit.Assert.assertFalse(manager.activateLicenseCode(fakeContext, "RIDER-VIP-2026-PASS").first)
        org.junit.Assert.assertFalse(manager.activateLicenseCode(fakeContext, "RIDER-VIP-0000").first)
        org.junit.Assert.assertFalse(manager.activateLicenseCode(fakeContext, "FITNESS-PRO-ANNUAL-KEY").first)
        org.junit.Assert.assertTrue(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))

        val testKeyPair = generateTestEcKeyPair()
        val testPublicKeyB64 = Base64.getEncoder().encodeToString(testKeyPair.public.encoded)
        val validSerial = signVipSerial(testKeyPair.private, "AABBCCDD", 365)

        org.junit.Assert.assertNull(VipSerialVerifier.verify(validSerial))
        org.junit.Assert.assertFalse(manager.activateLicenseCode(fakeContext, validSerial).first)

        val validRes = manager.activateLicenseCode(fakeContext, validSerial, testVipPublicKeyOverride = testPublicKeyB64)
        org.junit.Assert.assertTrue(validRes.first)
        org.junit.Assert.assertTrue(manager.isVipActive(fakeContext, overrideCurrentTimeMs = day35))
        org.junit.Assert.assertFalse(manager.isExpired(fakeContext, overrideCurrentTimeMs = day35))
        assertEquals(365, manager.getRemainingDays(fakeContext, overrideCurrentTimeMs = day35))
    }

    @Test
    fun testVipSerialVerifierRejectsTamperedAndWrongKeySerials() {
        val keyPairA = generateTestEcKeyPair()
        val keyPairB = generateTestEcKeyPair()
        val publicKeyA = Base64.getEncoder().encodeToString(keyPairA.public.encoded)

        val serial = signVipSerial(keyPairA.private, "01020304", 30)

        val info = VipSerialVerifier.verify(serial, publicKeyA)
        assertEquals("01020304", info?.serialId)
        assertEquals(30, info?.planDays)

        val serialSignedByB = signVipSerial(keyPairB.private, "01020304", 30)
        org.junit.Assert.assertNull(VipSerialVerifier.verify(serialSignedByB, publicKeyA))

        val tamperedPayloadHex = "0101020304012C"
        val originalSigHex = serial.substringAfterLast('-')
        val tampered = "FRVIP-$tamperedPayloadHex-$originalSigHex"
        org.junit.Assert.assertNull(VipSerialVerifier.verify(tampered, publicKeyA))

        org.junit.Assert.assertNull(VipSerialVerifier.verify("RIDER-VIP-2026-PASS", publicKeyA))
        org.junit.Assert.assertNull(VipSerialVerifier.verify("RIDER-VIP-0000000000", publicKeyA))
    }

    @Test
    fun testPromoCodeOnlyAcceptsCurrentYear() {
        val currentYear = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) % 100
        val currentYearCode = String.format("%02dFR-NR", currentYear)
        val pastYearCode = String.format("%02dFR-NR", (currentYear - 1 + 100) % 100)
        val futureYearCode = String.format("%02dFR-NR", (currentYear + 1) % 100)
        val farFutureYearCode = "99FR-NR"

        org.junit.Assert.assertTrue(VersionLifecycleManager.isPromoCode(currentYearCode))
        org.junit.Assert.assertFalse(VersionLifecycleManager.isPromoCode(pastYearCode))
        org.junit.Assert.assertFalse(VersionLifecycleManager.isPromoCode(futureYearCode))
        org.junit.Assert.assertFalse(VersionLifecycleManager.isPromoCode(farFutureYearCode))
    }

    @Test
    fun testMissingOrZeroVipExpiryIsNotTreatedAsVip() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        fakePrefs.edit().putBoolean(manager.KEY_VIP_ACTIVE, true).apply()

        org.junit.Assert.assertFalse(manager.isVipActive(fakeContext))
        org.junit.Assert.assertFalse(manager.isVipActive(fakeContext, overrideCurrentTimeMs = Long.MAX_VALUE - 1))
    }

    @Test
    fun testMustUpdateNeverLocksWhenBackendUnreachableOrDisabled() {
        org.junit.Assert.assertFalse(LicenseVerificationService.computeMustUpdate(0, currentVersionCode = 1))
        org.junit.Assert.assertFalse(LicenseVerificationService.computeMustUpdate(-1, currentVersionCode = 1))
        org.junit.Assert.assertFalse(LicenseVerificationService.computeMustUpdate(5, currentVersionCode = 5))
        org.junit.Assert.assertFalse(LicenseVerificationService.computeMustUpdate(5, currentVersionCode = 6))
        org.junit.Assert.assertTrue(LicenseVerificationService.computeMustUpdate(5, currentVersionCode = 4))
    }

    @Test
    fun testBusinessConstantsMatchAcrossPlatforms() {
        val candidates = listOf(
            java.io.File("../../promo.properties"),
            java.io.File("../promo.properties"),
            java.io.File("promo.properties")
        )
        val file = candidates.firstOrNull { it.exists() }
        org.junit.Assert.assertNotNull("promo.properties must exist", file)

        val props = java.util.Properties()
        file!!.inputStream().use { props.load(it) }

        val baseTrialDays = props.getProperty("BASE_TRIAL_DAYS", "7").trim().toInt()
        val promoTrialDays = props.getProperty("TRIAL_DAYS", "30").trim().toInt()

        org.junit.Assert.assertEquals(7, baseTrialDays)
        org.junit.Assert.assertEquals(30, promoTrialDays)
        org.junit.Assert.assertEquals(baseTrialDays, BuildConfig.LIFECYCLE_DAYS)
        org.junit.Assert.assertEquals(promoTrialDays, BuildConfig.PROMO_TOTAL_TRIAL_DAYS)
    }

    @Test
    fun testPromoCodeRedemptionFailsIfDeviceOlderThan30DaysAndDoesNotBurn() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val now = System.currentTimeMillis()
        val firstLaunch35DaysAgo = now - 35 * 86_400_000L
        fakePrefs.edit()
            .putLong(manager.KEY_FIRST_LAUNCH_TIME, firstLaunch35DaysAgo)
            .apply()

        val currentYear = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) % 100
        val promoCode = String.format("%02dFR-NR", currentYear)

        val (success, message) = manager.activateLicenseCode(fakeContext, promoCode)
        org.junit.Assert.assertFalse("Device older than 30 days must not succeed in redeeming promo", success)
        org.junit.Assert.assertTrue("Error message should explain 30-day limit exceeded", message.contains("超過 30 天"))

        val redeemedSet = fakePrefs.getStringSet(manager.KEY_REDEEMED_PROMOS, null)
        org.junit.Assert.assertFalse("Promo code must NOT be burned into redeemed set", redeemedSet?.contains(promoCode) == true)
        org.junit.Assert.assertFalse("VIP must NOT be active", manager.isVipActive(fakeContext))
    }

    @Test
    fun testVipActivationRespectsOverrideCurrentTimeMs() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val keyPair = generateTestEcKeyPair()
        val publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val serial = signVipSerial(keyPair.private, "01020304", 365)

        val mockNow = 1750000000_000L
        val (success, _) = manager.activateLicenseCode(
            context = fakeContext,
            rawCode = serial,
            testVipPublicKeyOverride = publicKeyBase64,
            overrideCurrentTimeMs = mockNow
        )

        org.junit.Assert.assertTrue("VIP activation should succeed", success)
        val expectedExpiresMs = mockNow + (365L * 86_400_000L)
        val actualExpiresMs = fakePrefs.getLong(manager.KEY_VIP_EXPIRES, 0L)
        org.junit.Assert.assertEquals("VIP expiresMs must be calculated from overrideCurrentTimeMs", expectedExpiresMs, actualExpiresMs)
    }

    @Test
    fun testPromoCodeCannotDowngradeActivePaidVip() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val keyPair = generateTestEcKeyPair()
        val publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val serial = signVipSerial(keyPair.private, "0A0B0C0D", 365)

        val mockNow = 1_800_000_000_000L
        manager.getFirstLaunchTimeMs(fakeContext, overrideCurrentTimeMs = mockNow)
        org.junit.Assert.assertTrue(
            manager.activateLicenseCode(
                context = fakeContext,
                rawCode = serial,
                testVipPublicKeyOverride = publicKeyBase64,
                overrideCurrentTimeMs = mockNow
            ).first
        )
        val paidExpiresMs = fakePrefs.getLong(manager.KEY_VIP_EXPIRES, 0L)
        org.junit.Assert.assertEquals(mockNow + 365L * 86_400_000L, paidExpiresMs)

        val currentYear = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) % 100
        val promoCode = String.format("%02dFR-NR", currentYear)
        val (promoOk, promoMsg) = manager.activateLicenseCode(
            context = fakeContext,
            rawCode = promoCode,
            overrideCurrentTimeMs = mockNow
        )
        org.junit.Assert.assertFalse("Promo code must not override an active paid VIP", promoOk)
        org.junit.Assert.assertTrue(promoMsg.contains("已有生效中的專業年繳版"))

        org.junit.Assert.assertEquals(paidExpiresMs, fakePrefs.getLong(manager.KEY_VIP_EXPIRES, 0L))
        org.junit.Assert.assertEquals("專業年繳版 (VIP)", manager.getVipPlanName(fakeContext))
        org.junit.Assert.assertFalse(
            fakePrefs.getStringSet(manager.KEY_REDEEMED_PROMOS, null)?.contains(promoCode) == true
        )
    }

    @Test
    fun testPromoBlockedByPaidVipMessageOnlyBlocksPaidVip() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val prefs = FakeSharedPreferences()
        val ctx = MockContext(prefs)
        val now = 1_800_000_000_000L
        val currentYear = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) % 100
        val promoCode = String.format("%02dFR-NR", currentYear)

        org.junit.Assert.assertNull(manager.promoBlockedByPaidVipMessage(ctx, promoCode, now))

        prefs.edit()
            .putBoolean(manager.KEY_VIP_ACTIVE, true)
            .putLong(manager.KEY_VIP_EXPIRES, now + 86_400_000L)
            .putString(manager.KEY_VIP_CODE, "25FR-NR")
            .apply()
        org.junit.Assert.assertNull(manager.promoBlockedByPaidVipMessage(ctx, promoCode, now))
        prefs.edit().putString(manager.KEY_VIP_CODE, "promo_verified").apply()
        org.junit.Assert.assertNull(manager.promoBlockedByPaidVipMessage(ctx, promoCode, now))

        prefs.edit().putString(manager.KEY_VIP_CODE, "FRVIP-01020304FFFF-AABB").apply()
        org.junit.Assert.assertTrue(
            manager.promoBlockedByPaidVipMessage(ctx, promoCode, now)?.contains("已有生效中的專業年繳版") == true
        )

        org.junit.Assert.assertNull(manager.promoBlockedByPaidVipMessage(ctx, "FRVIP-01020304FFFF-AABB", now))

        prefs.edit().putLong(manager.KEY_VIP_EXPIRES, now - 1L).apply()
        org.junit.Assert.assertNull(manager.promoBlockedByPaidVipMessage(ctx, promoCode, now))
    }

    @Test
    fun testIsPromoVipCodeIsYearAgnostic() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        org.junit.Assert.assertTrue(manager.isPromoVipCode("26FR-NR"))
        org.junit.Assert.assertTrue(manager.isPromoVipCode("99FR-NR"))
        org.junit.Assert.assertTrue(manager.isPromoVipCode("promo_verified"))
        org.junit.Assert.assertFalse(manager.isPromoVipCode("server_verified"))
        org.junit.Assert.assertFalse(manager.isPromoVipCode("FRVIP-01020304FFFF-AABB"))
        org.junit.Assert.assertFalse(manager.isPromoVipCode(null))
    }

    @Test
    fun testActivateVipFromServerWithPromoLabelsCorrectly() {
        val manager = com.fitnessrider.util.VersionLifecycleManager
        val fakePrefs = FakeSharedPreferences()
        val fakeContext = MockContext(fakePrefs)

        val expiresIso = java.time.Instant.now().plusSeconds(30 * 86400L).toString()
        manager.activateVipFromServer(fakeContext, expiresIso, isPromo = true, code = "26FR-NR")
        manager.recordPromoRedemption(fakeContext, "26FR-NR")

        org.junit.Assert.assertTrue(manager.isVipActive(fakeContext))
        org.junit.Assert.assertEquals(
            "推廣課程專屬版 (${com.fitnessrider.BuildConfig.PROMO_TOTAL_TRIAL_DAYS}天免費)",
            manager.getVipPlanName(fakeContext)
        )
        val redeemed = fakePrefs.getStringSet(manager.KEY_REDEEMED_PROMOS, null)
        org.junit.Assert.assertTrue(redeemed?.contains("26FR-NR") == true)
    }

    @Test
    fun testResolveUniqueMusicFileNameAppendsSuffixOnCollision() {
        assertEquals("track.mp3", resolveUniqueMusicFileName("track.mp3", emptySet()))

        assertEquals(
            "track_1.mp3",
            resolveUniqueMusicFileName("track.mp3", setOf("track.mp3"))
        )

        assertEquals(
            "track_2.mp3",
            resolveUniqueMusicFileName("track.mp3", setOf("track.mp3", "track_1.mp3"))
        )

        assertEquals("track_1", resolveUniqueMusicFileName("track", setOf("track")))
    }

    @Test
    fun testMusicTitleFromFileNameStripsExtension() {
        assertEquals("我的歌曲", musicTitleFromFileName("我的歌曲.mp3"))
        assertEquals("no_extension", musicTitleFromFileName("no_extension"))
    }

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

        assertEquals(3, segments.size)

        assertEquals(2, segments[0].orderIndex)
        assertEquals(3, segments[1].orderIndex)
        assertEquals(4, segments[2].orderIndex)
        segments.forEach { assertEquals("class-1", it.classId) }

        assertEquals("track_1", segments[0].title)
        assertEquals("曲目二", segments[1].title)
        assertEquals(245_000, segments[1].durationMs)

        assertEquals(300_000, segments[0].durationMs)
        assertEquals(128.0, segments[0].baseBpm, 0.001)
        assertEquals(132.5, segments[1].baseBpm, 0.001)
        assertEquals(180_000, segments[2].durationMs)
        assertEquals(128.0, segments[2].baseBpm, 0.001)

        segments.forEach { assertTrue(it.cues.isNotEmpty()) }
    }

    @Test
    fun testBuildMusicLibraryTracksReadsCacheAndSortsByTitle() {
        val cache = mapOf(
            "b_track.mp3" to (210_000 to 118.0),
            "a_track.mp3" to (190_000 to 126.0)
        )
        val tracks = buildMusicLibraryTracks(
            fileNames = listOf("b_track.mp3", "c_no_cache.mp3", "a_track.mp3")
        ) { cache[it] }

        assertEquals(listOf("a_track", "b_track", "c_no_cache"), tracks.map { it.title })

        val aTrack = tracks.first { it.fileName == "a_track.mp3" }
        assertEquals(190_000, aTrack.durationMs)
        assertEquals(126.0, aTrack.bpm, 0.001)

        val noCacheTrack = tracks.first { it.fileName == "c_no_cache.mp3" }
        assertEquals(300_000, noCacheTrack.durationMs)
        assertEquals(128.0, noCacheTrack.bpm, 0.001)
    }

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
        assertEquals("climb_anthem.mp3", segments[0].musicFileName)
        assertEquals("sprint_fire.mp3", segments[1].musicFileName)
        assertEquals("climb_anthem", segments[0].title)
        assertEquals(420_000, segments[0].durationMs)
        assertEquals(130.0, segments[0].baseBpm, 0.001)
        assertEquals(240_000, segments[1].durationMs)
        assertEquals(140.0, segments[1].baseBpm, 0.001)
    }

    @Test
    fun testMusicSourceIsExternalUriDetectsContentScheme() {
        assertTrue(MusicSource.isExternalUri("content://com.android.externalstorage.documents/tree/1234/document/5678"))
        assertFalse(MusicSource.isExternalUri("track.mp3"))
        assertFalse(MusicSource.isExternalUri(""))
    }

    @Test
    fun testIsAudioDocumentMatchesByMimeOrExtension() {
        assertTrue(isAudioDocument("track.mp3", "audio/mpeg"))
        assertTrue(isAudioDocument("track.MP3", null))
        assertTrue(isAudioDocument("track.m4a", null))
        assertFalse(isAudioDocument("cover.jpg", "image/jpeg"))
        assertFalse(isAudioDocument("readme.txt", null))
    }

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

    @Test
    fun testCopyMusicFileOrCleanupDeletesPartialFileOnMidStreamFailure() {
        val tempDir = kotlin.io.path.createTempDirectory(prefix = "music_import_test").toFile()
        try {
            val destFile = java.io.File(tempDir, "track.mp3")

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

    @Test
    fun testEqualPowerCrossfadeCalculation() {
        val (out0, in0) = CrossfadeCalculator.calculateEqualPowerVolumes(0.0)
        assertEquals(1.0f, out0, 0.0001f)
        assertEquals(0.0f, in0, 0.0001f)

        val (out1, in1) = CrossfadeCalculator.calculateEqualPowerVolumes(1.0)
        assertEquals(0.0f, out1, 0.0001f)
        assertEquals(1.0f, in1, 0.0001f)

        val (outMid, inMid) = CrossfadeCalculator.calculateEqualPowerVolumes(0.5)
        assertEquals(0.7071f, outMid, 0.001f)
        assertEquals(0.7071f, inMid, 0.001f)

        val testPoints = listOf(0.0, 0.1, 0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1.0)
        for (p in testPoints) {
            val (vOut, vIn) = CrossfadeCalculator.calculateEqualPowerVolumes(p)
            val totalPower = (vOut * vOut) + (vIn * vIn)
            assertEquals("Power should be 1.0 at progress $p", 1.0f, totalPower, 0.001f)
        }

        val (outNeg, inNeg) = CrossfadeCalculator.calculateEqualPowerVolumes(-0.5)
        assertEquals(1.0f, outNeg, 0.0001f)
        assertEquals(0.0f, inNeg, 0.0001f)

        val (outOver, inOver) = CrossfadeCalculator.calculateEqualPowerVolumes(1.8)
        assertEquals(0.0f, outOver, 0.0001f)
        assertEquals(1.0f, inOver, 0.0001f)
    }

    @Test
    fun testEffectiveCrossfadeDuration() {
        val autoPauseDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 2.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = true
        )
        assertEquals(0.0, autoPauseDuration, 0.0001)

        val zeroDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 0.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = false
        )
        assertEquals(0.0, zeroDuration, 0.0001)

        val normalDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration = 2.0,
            segmentDuration = 60.0,
            isAutoPauseEnabled = false
        )
        assertEquals(2.0, normalDuration, 0.0001)

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

        assertEquals(2.0, settings.crossfadeDurationSeconds, 0.0001)

        settings.crossfadeDurationSeconds = 3.0
        assertEquals(3.0, settings.crossfadeDurationSeconds, 0.0001)

        settings.crossfadeDurationSeconds = 0.0
        assertEquals(0.0, settings.crossfadeDurationSeconds, 0.0001)
    }

    @Test
    fun testCrossfadeOptionsSecondsMatchAcrossPlatforms() {
        val options = AppSettings.CROSSFADE_OPTIONS_SECONDS
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 5.0, 8.0), options)
        org.junit.Assert.assertTrue("預設值 2 秒必須仍是合法選項之一", options.contains(2.0))
    }

    @Test
    fun testAppSettingsHapticFeedbackEnabled() {
        val fakePrefs = FakeSharedPreferences()
        val mockContext = MockContext(fakePrefs)
        val settings = AppSettings(mockContext)
        val originalValue = settings.isHapticFeedbackEnabled

        assertTrue(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = false
        assertFalse(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = originalValue
    }

    @Test
    fun testCrossfadeFinishRejectsReentrantStaleTrackEndedForOldPlayer() {
        val coordinator = CrossfadeFinishCoordinator(startSegmentIndex = 4)
        coordinator.startCrossfade()
        assertTrue(coordinator.isCrossfading)

        var reentrantGuardPassed = false
        val finished = coordinator.finishCrossfade {
            if (coordinator.shouldHandleTrackEnded(endedPlayerIndex = 0)) {
                reentrantGuardPassed = true
                coordinator.setSegmentIndex(coordinator.currentSegmentIndex + 1)
            }
        }

        assertTrue(finished)
        assertFalse("stale STATE_ENDED for the outgoing player must be rejected", reentrantGuardPassed)
        assertEquals("segment index must advance exactly once, not twice", 5, coordinator.currentSegmentIndex)
        assertEquals(1, coordinator.activePlayerIndex)
        assertFalse(coordinator.isCrossfading)

        assertTrue(coordinator.shouldHandleTrackEnded(endedPlayerIndex = 1))
    }

    @Test
    fun testCrossfadeFinishLatchRejectsDirectReentrantFinishCall() {
        val coordinator = CrossfadeFinishCoordinator(startSegmentIndex = 2)
        coordinator.startCrossfade()

        var innerTearDownRan = false
        val outerFinished = coordinator.finishCrossfade {
            val innerFinished = coordinator.finishCrossfade { innerTearDownRan = true }
            assertFalse(innerFinished)
        }

        assertTrue(outerFinished)
        assertFalse(innerTearDownRan)
        assertEquals("segment index must advance exactly once", 3, coordinator.currentSegmentIndex)
        assertEquals(1, coordinator.activePlayerIndex)
    }

    @Test
    fun testCrossfadeFinishNoOpWhenNotCrossfading() {
        val coordinator = CrossfadeFinishCoordinator(startSegmentIndex = 0)
        var tearDownRan = false

        val finished = coordinator.finishCrossfade { tearDownRan = true }

        assertFalse(finished)
        assertFalse(tearDownRan)
        assertEquals(0, coordinator.currentSegmentIndex)
        assertEquals(0, coordinator.activePlayerIndex)
    }

    @Test
    fun testHapticFeedbackManagerResolvesHostFromApplicationContextNotActivityContext() {
        val appContext = object : android.content.ContextWrapper(null) {}
        val activityLikeContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = appContext
        }

        val resolved = HapticFeedbackManager.resolveVibratorHostContext(activityLikeContext)

        assertSame(
            "must resolve the system-service host via context.applicationContext",
            appContext,
            resolved
        )
        assertNotSame(
            "must NOT resolve the system-service host as the raw (possibly Activity) context",
            activityLikeContext,
            resolved
        )
    }

    @Test
    fun testSynchronizedOnceCacheComputesExactlyOnceUnderConcurrency() {
        val cache = SynchronizedOnceCache<String>()
        val computeCallCount = java.util.concurrent.atomic.AtomicInteger(0)
        val readyCount = java.util.concurrent.atomic.AtomicInteger(0)
        val startLatch = java.util.concurrent.CountDownLatch(1)
        val results = java.util.concurrent.ConcurrentLinkedQueue<String?>()
        val threadCount = 16

        val threads = (1..threadCount).map {
            Thread {
                readyCount.incrementAndGet()
                startLatch.await()
                val value = cache.getOrCompute {
                    computeCallCount.incrementAndGet()
                    Thread.sleep(5)
                    "resolved"
                }
                results.add(value)
            }
        }
        threads.forEach { it.start() }
        while (readyCount.get() < threadCount) Thread.sleep(1)
        startLatch.countDown()
        threads.forEach { it.join(5_000) }

        assertEquals("compute() must only run once despite concurrent first access", 1, computeCallCount.get())
        assertEquals(threadCount, results.size)
        val distinctResults = results.filterNotNull().toSet()
        assertEquals("every thread must observe the same cached value", 1, distinctResults.size)
        assertEquals("resolved", distinctResults.first())
    }

    @Test
    fun testSynchronizedOnceCacheResetAllowsRecomputation() {
        val cache = SynchronizedOnceCache<String>()
        val computeCallCount = java.util.concurrent.atomic.AtomicInteger(0)

        val first = cache.getOrCompute { computeCallCount.incrementAndGet(); "a" }
        val second = cache.getOrCompute { computeCallCount.incrementAndGet(); "b" }
        cache.reset()
        val third = cache.getOrCompute { computeCallCount.incrementAndGet(); "c" }

        assertEquals("a", first)
        assertEquals("a", second)
        assertEquals("c", third)
        assertEquals(2, computeCallCount.get())
    }

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

        val twelveDaysLater = lastTransferMs + (12 * oneDayMs)
        val elapsedDays1 = (twelveDaysLater - lastTransferMs).toDouble() / oneDayMs
        val remaining1 = Math.max(0, Math.ceil(30.0 - elapsedDays1).toInt())
        assertEquals(18, remaining1)

        val almostEnd = lastTransferMs + (29.2 * oneDayMs).toLong()
        val elapsedDays2 = (almostEnd - lastTransferMs).toDouble() / oneDayMs
        val remaining2 = Math.max(0, Math.ceil(30.0 - elapsedDays2).toInt())
        assertEquals(1, remaining2)

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

        val expiresAtIso = java.time.Instant.ofEpochMilli(futureTime + 365L * 86_400_000L).toString()
        manager.activateVipFromServer(mockContext, expiresAtIso)
        assertTrue(manager.isVipActive(mockContext, overrideCurrentTimeMs = futureTime))
        assertFalse(manager.isExpired(mockContext, overrideCurrentTimeMs = futureTime))
    }

    @Test
    fun testSegmentReorderAndDeleteReindexesOrderIndex() {
        fun seg(title: String, orderIndex: Int) = WorkoutSegment(title = title, orderIndex = orderIndex)
        val segments = listOf(seg("A", 0), seg("B", 1), seg("C", 2), seg("D", 3))

        val moved = segmentsAfterMove(segments, 0, 2)
        assertEquals(listOf("B", "C", "A", "D"), moved.map { it.title })
        assertEquals(listOf(0, 1, 2, 3), moved.map { it.orderIndex })

        val removed = segmentsAfterRemoval(segments, 1)
        assertEquals(listOf("A", "C", "D"), removed.map { it.title })
        assertEquals(listOf(0, 1, 2), removed.map { it.orderIndex })

        val shuffled = listOf(seg("X", 9), seg("Y", 4))
        val reindexed = reindexedSegments(shuffled)
        assertEquals(listOf(0, 1), reindexed.map { it.orderIndex })
    }

    @Test
    fun testSegmentMoveAndRemovalOutOfRangeIsNoOp() {
        fun seg(title: String, orderIndex: Int) = WorkoutSegment(title = title, orderIndex = orderIndex)
        val segments = listOf(seg("A", 0), seg("B", 1), seg("C", 2))

        val firstUpNoOp = segmentsAfterMove(segments, 0, -1)
        assertEquals(segments, firstUpNoOp)
        val lastDownNoOp = segmentsAfterMove(segments, segments.lastIndex, 1)
        assertEquals(segments, lastDownNoOp)

        assertEquals(segments, segmentsAfterRemoval(segments, 5))
        assertEquals(segments, segmentsAfterRemoval(segments, -1))
    }

    @Test
    fun testSelectedSegmentIndexTracksMoveAndRemoval() {
        assertEquals(1, selectedIndexAfterMove(0, 0, 1))
        assertEquals(0, selectedIndexAfterMove(1, 0, 1))
        assertEquals(3, selectedIndexAfterMove(3, 0, 1))

        assertEquals(1, selectedIndexAfterRemoval(2, 0, 2))
        assertEquals(0, selectedIndexAfterRemoval(0, 2, 2))
        assertEquals(0, selectedIndexAfterRemoval(0, 0, 0))
        assertTrue(selectedIndexAfterRemoval(0, 0, 0) >= 0)
    }

    @Test
    fun testRateStepForSwipeHorizontalPastThreshold() {
        assertEquals(1, rateStepForSwipe(dx = 80f, dy = 0f, threshold = 60f))
        assertEquals(-1, rateStepForSwipe(dx = -80f, dy = 0f, threshold = 60f))
        assertEquals(1, rateStepForSwipe(dx = 500f, dy = 0f, threshold = 60f))
    }

    @Test
    fun testRateStepForSwipeBelowThresholdIsIgnored() {
        assertEquals(0, rateStepForSwipe(dx = 40f, dy = 0f, threshold = 60f))
        assertEquals(0, rateStepForSwipe(dx = -59f, dy = 0f, threshold = 60f))
        assertEquals(0, rateStepForSwipe(dx = 60f, dy = 0f, threshold = 60f))
    }

    @Test
    fun testRateStepForSwipeVerticalDominantIsIgnored() {
        assertEquals(0, rateStepForSwipe(dx = 70f, dy = 90f, threshold = 60f))
        assertEquals(0, rateStepForSwipe(dx = -70f, dy = 100f, threshold = 60f))
        assertEquals(1, rateStepForSwipe(dx = 90f, dy = 70f, threshold = 60f))
    }

    @Test
    fun testSegmentStepForSwipeVerticalPastThreshold() {
        assertEquals(1, segmentStepForSwipe(dx = 0f, dy = -100f, threshold = 80f))
        assertEquals(-1, segmentStepForSwipe(dx = 0f, dy = 100f, threshold = 80f))
        assertEquals(0, segmentStepForSwipe(dx = 0f, dy = -60f, threshold = 80f))
        assertEquals(0, segmentStepForSwipe(dx = 0f, dy = -80f, threshold = 80f))
        assertEquals(0, segmentStepForSwipe(dx = 200f, dy = -100f, threshold = 80f))
    }

    @Test
    fun testRateAndSegmentSwipesAreMutuallyExclusive() {
        val rateThreshold = 60f
        val segmentThreshold = 80f
        val swipes = listOf(
            200f to 0f,
            -200f to 0f,
            0f to 200f,
            0f to -200f,
            100f to 120f,
            -100f to 120f,
            100f to -160f,
            160f to -100f
        )
        swipes.forEach { (dx, dy) ->
            val rate = rateStepForSwipe(dx, dy, rateThreshold)
            val segment = segmentStepForSwipe(dx, dy, segmentThreshold)
            org.junit.Assert.assertFalse(
                "同一次滑動 ($dx, $dy) 同時觸發了變速與換曲",
                rate != 0 && segment != 0
            )
        }
        assertEquals(0, rateStepForSwipe(100f, 120f, rateThreshold))
        assertEquals(0, segmentStepForSwipe(100f, 120f, segmentThreshold))
    }

    @Test
    fun testPaywallPricingMatchesSpecAndYearlySavingsArithmetic() {
        assertEquals(390, PaywallPricing.MONTHLY_PRICE_TWD)
        assertEquals(890, PaywallPricing.QUARTERLY_PRICE_TWD)
        assertEquals(2390, PaywallPricing.YEARLY_PRICE_TWD)
        assertEquals(2290, PaywallPricing.YEARLY_SAVINGS_TWD)
        assertEquals(
            PaywallPricing.MONTHLY_PRICE_TWD * 12 - PaywallPricing.YEARLY_PRICE_TWD,
            PaywallPricing.YEARLY_SAVINGS_TWD
        )
        assertEquals("NT$2,390", PaywallPricing.formatTwd(PaywallPricing.YEARLY_PRICE_TWD))
        assertEquals("NT$390", PaywallPricing.formatTwd(PaywallPricing.MONTHLY_PRICE_TWD))
        org.junit.Assert.assertTrue(PaywallPricing.YEARLY_BADGE_TEXT.contains("NT$2,290"))
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

