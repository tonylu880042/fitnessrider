package com.fitnessrider.data

import android.content.Context
import com.fitnessrider.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class RiderClassArchiveService(private val context: Context) {
    private val musicDir = ClassRepository(context).musicDirectory

    fun exportRiderClass(workoutClass: WorkoutClass): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val sanitizedTitle = workoutClass.title.replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "_")
            val zipFile = File(exportDir, "$sanitizedTitle.riderclass")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val jsonBytes = serializeClassToJson(workoutClass).toByteArray(Charsets.UTF_8)
                writeStoredEntry(zos, "workout_class.json", jsonBytes)

                for (fileName in workoutClass.segments.map { it.musicFileName }.filter { it.isNotBlank() }.distinct()) {
                    val audioFile = File(musicDir, fileName)
                    if (audioFile.exists()) {
                        writeStoredEntry(zos, fileName, audioFile)
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importRiderClass(zipFile: File): WorkoutClass? {
        return try {
            var jsonString: String? = null

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "workout_class.json") {
                        val buffer = ByteArrayOutputStream()
                        zis.copyTo(buffer)
                        jsonString = buffer.toString(Charsets.UTF_8.name())
                    } else if (!entry.isDirectory) {
                        val destFile = File(musicDir, entry.name)
                        val canonicalDest = destFile.canonicalPath
                        val canonicalDir = musicDir.canonicalPath
                        if (canonicalDest.startsWith(canonicalDir + File.separator)) {
                            if (!destFile.exists()) {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (jsonString != null) {
                val workoutClass = deserializeJsonToClass(jsonString)
                ClassRepository(context).saveClass(workoutClass)
                workoutClass
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        internal fun writeStoredEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
            val crc = CRC32()
            crc.update(bytes)
            val entry = ZipEntry(name)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = crc.value
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }

        private fun writeStoredEntry(zos: ZipOutputStream, name: String, file: File) {
            val crc = CRC32()
            var size = 0L
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read = fis.read(buffer)
                while (read >= 0) {
                    crc.update(buffer, 0, read)
                    size += read
                    read = fis.read(buffer)
                }
            }
            val entry = ZipEntry(name)
            entry.method = ZipEntry.STORED
            entry.size = size
            entry.compressedSize = size
            entry.crc = crc.value
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis -> fis.copyTo(zos) }
            zos.closeEntry()
        }

        fun serializeClassToJson(wc: WorkoutClass): String {
            val root = JSONObject()
            root.put("id", wc.id)
            root.put("title", wc.title)
            root.put("author", wc.author)
            root.put("createdAt", wc.createdAt)
            root.put("totalDurationMs", wc.totalDurationMs)
            root.put("estimatedCalories", wc.estimatedCalories)

            val segArray = JSONArray()
            for (seg in wc.segments) {
                val segObj = JSONObject()
                segObj.put("id", seg.id)
                segObj.put("classId", seg.classId)
                segObj.put("orderIndex", seg.orderIndex)
                segObj.put("title", seg.title)
                segObj.put("musicFileName", seg.musicFileName)
                segObj.put("durationMs", seg.durationMs)
                segObj.put("baseBpm", seg.baseBpm)
                segObj.put("playbackRate", seg.playbackRate)
                segObj.put("intensityZone", seg.intensityZone)

                val cueArray = JSONArray()
                for (cue in seg.cues) {
                    val cueObj = JSONObject()
                    cueObj.put("id", cue.id)
                    cueObj.put("segmentId", cue.segmentId)
                    cueObj.put("offsetMs", cue.offsetMs)
                    cueObj.put("posture", cue.posture.rawValue)
                    cueObj.put("targetRpm", cue.targetRpm)
                    cueObj.put("resistanceLevel", cue.resistanceLevel)
                    cueObj.put("message", cue.message)
                    cueObj.put("handPosition", cue.handPosition.value)
                    cueObj.put("reminders", JSONArray(cue.reminders))
                    cueArray.put(cueObj)
                }
                segObj.put("cues", cueArray)
                segArray.put(segObj)
            }
            root.put("segments", segArray)
            return root.toString(2)
        }

        fun deserializeJsonToClass(jsonStr: String): WorkoutClass {
            val root = JSONObject(jsonStr)
            val id = root.optString("id")
            val title = root.optString("title")
            val author = root.optString("author")
            val createdAt = root.optString("createdAt")
            val totalDurationMs = root.optInt("totalDurationMs")
            val estimatedCalories = root.optDouble("estimatedCalories")

            val segArray = root.optJSONArray("segments") ?: JSONArray()
            val segments = mutableListOf<WorkoutSegment>()

            for (i in 0 until segArray.length()) {
                val sObj = segArray.getJSONObject(i)
                val cues = mutableListOf<WorkoutCue>()
                val cArray = sObj.optJSONArray("cues") ?: JSONArray()
                for (j in 0 until cArray.length()) {
                    val cObj = cArray.getJSONObject(j)
                    val posture = PostureType.fromRaw(cObj.optString("posture"))
                    val handPos = if (cObj.has("handPosition")) {
                        HandPosition.fromValue(cObj.optInt("handPosition"))
                    } else {
                        posture.defaultHandPosition
                    }
                    val remList = mutableListOf<String>()
                    val rArray = cObj.optJSONArray("reminders")
                    if (rArray != null) {
                        for (k in 0 until rArray.length()) {
                            remList.add(rArray.getString(k))
                        }
                    }
                    cues.add(
                        WorkoutCue(
                            id = cObj.optString("id"),
                            segmentId = cObj.optString("segmentId"),
                            offsetMs = cObj.optInt("offsetMs"),
                            posture = posture,
                            targetRpm = cObj.optInt("targetRpm"),
                            resistanceLevel = cObj.optString("resistanceLevel"),
                            message = cObj.optString("message"),
                            handPosition = handPos,
                            reminders = remList
                        )
                    )
                }

                segments.add(
                    WorkoutSegment(
                        id = sObj.optString("id"),
                        classId = sObj.optString("classId"),
                        orderIndex = sObj.optInt("orderIndex"),
                        title = sObj.optString("title"),
                        musicFileName = sObj.optString("musicFileName"),
                        durationMs = sObj.optInt("durationMs"),
                        baseBpm = sObj.optDouble("baseBpm"),
                        playbackRate = sObj.optDouble("playbackRate", 1.0),
                        intensityZone = sObj.optInt("intensityZone", 2),
                        cues = cues
                    )
                )
            }

            return WorkoutClass(
                id = id,
                title = title,
                author = author,
                createdAt = createdAt,
                totalDurationMs = totalDurationMs,
                estimatedCalories = estimatedCalories,
                segments = segments
            )
        }
    }
}
