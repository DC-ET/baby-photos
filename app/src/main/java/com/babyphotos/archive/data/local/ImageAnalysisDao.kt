package com.babyphotos.archive.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAnalysisDao {

    /**
     * 同一张照片移动后 MediaStore 的 DATA 会变成新路径，但库里的 [ImageAnalysisEntity.path]
     * 仍是扫描时的旧路径；若已归档则当前磁盘路径与 [ImageAnalysisEntity.movedTo] 一致。
     */
    @Query("SELECT * FROM image_analysis WHERE path = :path OR moved_to = :path LIMIT 1")
    suspend fun getByPathOrMovedTo(path: String): ImageAnalysisEntity?

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

    @Query("DELETE FROM image_analysis WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
