package com.arcontractor.estimator.data.repository

import com.arcontractor.estimator.data.local.CutoutEntity
import com.arcontractor.estimator.data.local.MaterialCatalogDao
import com.arcontractor.estimator.data.local.MaterialSpecEntity
import com.arcontractor.estimator.data.local.ProjectDao
import com.arcontractor.estimator.data.local.ProjectEntity
import com.arcontractor.estimator.data.local.SurfaceDao
import com.arcontractor.estimator.data.local.SurfaceEntity
import com.arcontractor.estimator.data.local.SurfaceWithCutouts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProjectDao : ProjectDao {
    private val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    override fun observeAll(): Flow<List<ProjectEntity>> = projects

    override fun observeById(projectId: String): Flow<ProjectEntity?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override suspend fun getById(projectId: String): ProjectEntity? =
        projects.value.firstOrNull { it.id == projectId }

    override suspend fun getFirst(): ProjectEntity? = projects.value.firstOrNull()

    override suspend fun upsert(project: ProjectEntity) {
        projects.value = projects.value.filterNot { it.id == project.id } + project
    }

    override suspend fun updateMetadata(
        projectId: String,
        name: String,
        clientName: String,
        contractorName: String,
        contractorContact: String,
        contractorLicense: String,
        updatedAtEpochMillis: Long
    ) {
        projects.value = projects.value.map { project ->
            if (project.id == projectId) {
                project.copy(
                    name = name,
                    clientName = clientName,
                    contractorName = contractorName,
                    contractorContact = contractorContact,
                    contractorLicense = contractorLicense,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            } else {
                project
            }
        }
    }

    override suspend fun updateTaxSettings(
        projectId: String,
        taxRate: Double,
        taxPolicy: String,
        updatedAtEpochMillis: Long
    ) {
        projects.value = projects.value.map { project ->
            if (project.id == projectId) {
                project.copy(
                    taxRate = taxRate,
                    taxPolicy = taxPolicy,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            } else {
                project
            }
        }
    }

    override suspend fun delete(projectId: String) {
        projects.value = projects.value.filterNot { it.id == projectId }
    }
}

class FakeSurfaceDao : SurfaceDao {
    private val surfaces = MutableStateFlow<List<SurfaceWithCutouts>>(emptyList())

    val currentSurfaces: List<SurfaceWithCutouts>
        get() = surfaces.value

    fun corruptWastePreset(surfaceId: String, presetName: String) {
        surfaces.value = surfaces.value.map { stored ->
            if (stored.surface.id == surfaceId) {
                stored.copy(surface = stored.surface.copy(wastePresetOverride = presetName))
            } else {
                stored
            }
        }
    }

    override fun observeForProject(projectId: String): Flow<List<SurfaceWithCutouts>> =
        surfaces.map { list ->
            list.filter { it.surface.projectId == projectId }
                .sortedBy { it.surface.createdAtEpochMillis }
        }

    override suspend fun getById(surfaceId: String): SurfaceEntity? =
        surfaces.value.firstOrNull { it.surface.id == surfaceId }?.surface

    override suspend fun countForMaterial(materialId: String): Int =
        surfaces.value.count { it.surface.materialId == materialId }

    override suspend fun upsertSurface(surface: SurfaceEntity) {
        surfaces.value = surfaces.value.filterNot { it.surface.id == surface.id } +
            SurfaceWithCutouts(
                surface = surface,
                cutouts = surfaces.value.firstOrNull { it.surface.id == surface.id }?.cutouts.orEmpty()
            )
    }

    override suspend fun upsertCutouts(cutouts: List<CutoutEntity>) {
        val bySurface = cutouts.groupBy(CutoutEntity::surfaceId)
        surfaces.value = surfaces.value.map { stored ->
            val replacements = bySurface[stored.surface.id] ?: return@map stored
            stored.copy(
                cutouts = stored.cutouts.filterNot { old ->
                    replacements.any { replacement -> replacement.id == old.id }
                } + replacements
            )
        }
    }

    override suspend fun deleteCutouts(surfaceId: String) {
        surfaces.value = surfaces.value.map { stored ->
            if (stored.surface.id == surfaceId) stored.copy(cutouts = emptyList()) else stored
        }
    }

    override suspend fun updatePricing(
        surfaceId: String,
        materialId: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?,
        updatedAtEpochMillis: Long
    ) {
        surfaces.value = surfaces.value.map { stored ->
            if (stored.surface.id != surfaceId) {
                stored
            } else {
                stored.copy(
                    surface = stored.surface.copy(
                        materialId = materialId,
                        wastePresetOverride = wastePresetOverride,
                        customWasteFactorOverride = customWasteFactorOverride,
                        laborRateOverride = laborRateOverride,
                        updatedAtEpochMillis = updatedAtEpochMillis
                    )
                )
            }
        }
    }

    override suspend fun rename(surfaceId: String, name: String, updatedAtEpochMillis: Long) {
        surfaces.value = surfaces.value.map { stored ->
            if (stored.surface.id == surfaceId) {
                stored.copy(
                    surface = stored.surface.copy(
                        name = name,
                        updatedAtEpochMillis = updatedAtEpochMillis
                    )
                )
            } else {
                stored
            }
        }
    }

    override suspend fun delete(surfaceId: String) {
        surfaces.value = surfaces.value.filterNot { it.surface.id == surfaceId }
    }
}

class FakeMaterialCatalogDao : MaterialCatalogDao {
    private val materials = MutableStateFlow<List<MaterialSpecEntity>>(emptyList())

    val currentMaterials: List<MaterialSpecEntity>
        get() = materials.value

    override fun observeAll(): Flow<List<MaterialSpecEntity>> = materials

    override suspend fun getById(materialId: String): MaterialSpecEntity? =
        materials.value.firstOrNull { it.id == materialId }

    override suspend fun getFirst(): MaterialSpecEntity? = materials.value.firstOrNull()

    override suspend fun count(): Int = materials.value.size

    override suspend fun insertMissing(materials: List<MaterialSpecEntity>) {
        val existingIds = this.materials.value.mapTo(HashSet(), MaterialSpecEntity::id)
        this.materials.value += materials.filterNot { it.id in existingIds }
    }

    override suspend fun upsert(material: MaterialSpecEntity) {
        materials.value = materials.value.filterNot { it.id == material.id } + material
    }

    override suspend fun delete(materialId: String) {
        materials.value = materials.value.filterNot { it.id == materialId }
    }
}
