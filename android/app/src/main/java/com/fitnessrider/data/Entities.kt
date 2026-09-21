package com.fitnessrider.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val createdAt: String,
    val totalDurationMs: Int,
    val estimatedCalories: Double
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = ClassEntity::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classId")]
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val classId: String,
    val orderIndex: Int,
    val title: String,
    val musicFileName: String,
    val durationMs: Int,
    val baseBpm: Double,
    val playbackRate: Double,
    val intensityZone: Int
)

@Entity(
    tableName = "cues",
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("segmentId")]
)
data class CueEntity(
    @PrimaryKey val id: String,
    val segmentId: String,
    val offsetMs: Int,
    val posture: String,
    val targetRpm: Int,
    val resistanceLevel: String,
    val message: String,
    val handPosition: Int = 1,
    val remindersJson: String = "[]"
)

@Entity(tableName = "waveform_cache")
data class WaveformEntity(
    @PrimaryKey val fileName: String,
    val samplesBlob: ByteArray,
    val sampleCount: Int,
    val durationMs: Int,
    val calculatedBpm: Double
)
