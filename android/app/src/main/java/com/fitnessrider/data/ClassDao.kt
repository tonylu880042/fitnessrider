package com.fitnessrider.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    suspend fun getAllClassesSync(): List<ClassEntity>

    @Query("SELECT * FROM classes WHERE id = :id LIMIT 1")
    suspend fun getClassById(id: String): ClassEntity?

    @Query("SELECT * FROM segments WHERE classId = :classId ORDER BY orderIndex ASC")
    suspend fun getSegmentsForClass(classId: String): List<SegmentEntity>

    @Query("SELECT * FROM cues WHERE segmentId = :segmentId ORDER BY offsetMs ASC")
    suspend fun getCuesForSegment(segmentId: String): List<CueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classEntity: ClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCues(cues: List<CueEntity>)

    @Query("DELETE FROM segments WHERE classId = :classId")
    suspend fun deleteSegmentsForClass(classId: String)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun deleteClass(id: String)

    // Waveform Cache
    @Query("SELECT * FROM waveform_cache WHERE fileName = :fileName LIMIT 1")
    suspend fun getWaveform(fileName: String): WaveformEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaveform(waveform: WaveformEntity)
}
