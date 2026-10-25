package com.ai.pocketcore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.pocketcore.data.db.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY downloadedAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    fun getActiveModel(): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<ModelEntity>)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("UPDATE models SET isActive = CASE WHEN id = :modelId THEN 1 ELSE 0 END")
    suspend fun setActiveModel(modelId: String)

    @Query("UPDATE models SET isActive = 0")
    suspend fun clearActiveModel()

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModelById(id: String)

    @Query("DELETE FROM models")
    suspend fun deleteAllModels()

    @Query("SELECT COUNT(*) FROM models")
    fun getModelCount(): Flow<Int>
}
