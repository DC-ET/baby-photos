package com.babyphotos.archive.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAnalysisDao {

    @Query("SELECT * FROM image_analysis WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): ImageAnalysisEntity?

    @Query("SELECT * FROM image_analysis WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImageAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImageAnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ImageAnalysisEntity>)

    @Query("SELECT * FROM image_analysis WHERE contains_baby = 1 ORDER BY timestamp DESC")
    fun getBabyPhotos(): Flow<List<ImageAnalysisEntity>>

    @Query("SELECT * FROM image_analysis WHERE action = 'NEEDS_CONFIRM' ORDER BY timestamp DESC")
    fun getPendingConfirmations(): Flow<List<ImageAnalysisEntity>>

    @Query("SELECT * FROM image_analysis ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ImageAnalysisEntity>>

    @Query("SELECT COUNT(*) FROM image_analysis")
    suspend fun getCount(): Int

    @Query("DELETE FROM image_analysis WHERE id = :id")
    suspend fun deleteById(id: String)
}
