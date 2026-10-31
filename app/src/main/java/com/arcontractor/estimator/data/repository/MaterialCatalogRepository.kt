package com.arcontractor.estimator.data.repository

import com.arcontractor.estimator.data.local.MaterialCatalogDao
import com.arcontractor.estimator.data.local.SurfaceDao
import com.arcontractor.estimator.data.local.toDomain
import com.arcontractor.estimator.data.local.toEntity
import com.arcontractor.estimator.domain.estimation.DefaultMaterialCatalog
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MaterialCatalogRepository {
    val materials: Flow<List<MaterialSpec>>

    suspend fun ensureSeeded()
    suspend fun save(material: MaterialSpec)
    suspend fun delete(materialId: String): MaterialDeleteResult
}

class RoomMaterialCatalogRepository(
    private val materialDao: MaterialCatalogDao,
    private val surfaceDao: SurfaceDao,
    private val now: () -> Long = System::currentTimeMillis
) : MaterialCatalogRepository {

    override val materials: Flow<List<MaterialSpec>> =
        materialDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun ensureSeeded() {
        val timestamp = now()
        materialDao.insertMissing(
            DefaultMaterialCatalog.allPresets().map { material -> material.toEntity(timestamp) }
        )
    }

    override suspend fun save(material: MaterialSpec) {
        materialDao.upsert(material.toEntity(now()))
    }

    override suspend fun delete(materialId: String): MaterialDeleteResult {
        val surfaceCount = surfaceDao.countForMaterial(materialId)
        if (surfaceCount > 0) {
            return MaterialDeleteResult.InUse(surfaceCount)
        }
        materialDao.delete(materialId)
        return MaterialDeleteResult.Deleted
    }
}
