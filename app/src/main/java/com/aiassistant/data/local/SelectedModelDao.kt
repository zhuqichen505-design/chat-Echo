package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.SelectedModel
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedModelDao {
    @Query("SELECT * FROM selected_models WHERE apiConfigId = :apiConfigId ORDER BY sortOrder ASC, modelName ASC")
    fun getModelsByConfig(apiConfigId: Long): Flow<List<SelectedModel>>

    @Query("SELECT * FROM selected_models WHERE apiConfigId = :apiConfigId AND isEnabled = 1 ORDER BY sortOrder ASC")
    fun getEnabledModelsByConfig(apiConfigId: Long): Flow<List<SelectedModel>>

    @Query("SELECT * FROM selected_models WHERE id = :id")
    suspend fun getModelById(id: Long): SelectedModel?

    @Query("SELECT * FROM selected_models WHERE apiConfigId = :apiConfigId AND modelName = :modelName LIMIT 1")
    suspend fun getModelByName(apiConfigId: Long, modelName: String): SelectedModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: SelectedModel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<SelectedModel>)

    @Update
    suspend fun updateModel(model: SelectedModel)

    @Delete
    suspend fun deleteModel(model: SelectedModel)

    @Query("DELETE FROM selected_models WHERE apiConfigId = :apiConfigId")
    suspend fun deleteModelsByConfig(apiConfigId: Long)

    @Query("UPDATE selected_models SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setModelEnabled(id: Long, isEnabled: Boolean)
}
