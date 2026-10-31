package com.arcontractor.estimator.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeById(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY createdAtEpochMillis ASC LIMIT 1")
    suspend fun getFirst(): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun delete(projectId: String)
}

@Dao
interface SurfaceDao {
    @Transaction
    @Query("SELECT * FROM surfaces WHERE projectId = :projectId ORDER BY createdAtEpochMillis ASC")
    fun observeForProject(projectId: String): Flow<List<SurfaceWithCutouts>>

    @Query("SELECT * FROM surfaces WHERE id = :surfaceId")
    suspend fun getById(surfaceId: String): SurfaceEntity?

    @Query("SELECT COUNT(*) FROM surfaces WHERE materialId = :materialId")
    suspend fun countForMaterial(materialId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurface(surface: SurfaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCutouts(cutouts: List<CutoutEntity>)

    @Query("DELETE FROM cutouts WHERE surfaceId = :surfaceId")
    suspend fun deleteCutouts(surfaceId: String)

    @Transaction
    suspend fun replaceSurface(surface: SurfaceEntity, cutouts: List<CutoutEntity>) {
        upsertSurface(surface)
        deleteCutouts(surface.id)
        if (cutouts.isNotEmpty()) {
            upsertCutouts(cutouts)
        }
    }

    @Query(
        """
        UPDATE surfaces
        SET materialId = :materialId,
            wastePresetOverride = :wastePresetOverride,
            customWasteFactorOverride = :customWasteFactorOverride,
            laborRateOverride = :laborRateOverride,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :surfaceId
        """
    )
    suspend fun updatePricing(
        surfaceId: String,
        materialId: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?,
        updatedAtEpochMillis: Long
    )

    @Query(
        """
        UPDATE surfaces
        SET name = :name,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :surfaceId
        """
    )
    suspend fun rename(surfaceId: String, name: String, updatedAtEpochMillis: Long)

    @Query("DELETE FROM surfaces WHERE id = :surfaceId")
    suspend fun delete(surfaceId: String)
}

@Dao
interface MaterialCatalogDao {
    @Query("SELECT * FROM material_specs ORDER BY tradeType ASC, name ASC")
    fun observeAll(): Flow<List<MaterialSpecEntity>>

    @Query("SELECT * FROM material_specs WHERE id = :materialId")
    suspend fun getById(materialId: String): MaterialSpecEntity?

    @Query("SELECT * FROM material_specs ORDER BY name ASC LIMIT 1")
    suspend fun getFirst(): MaterialSpecEntity?

    @Query("SELECT COUNT(*) FROM material_specs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(materials: List<MaterialSpecEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(material: MaterialSpecEntity)

    @Query("DELETE FROM material_specs WHERE id = :materialId")
    suspend fun delete(materialId: String)
}
