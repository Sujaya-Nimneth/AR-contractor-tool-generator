package com.arcontractor.estimator.data.repository

import com.arcontractor.estimator.data.local.CutoutEntity
import com.arcontractor.estimator.data.local.MaterialCatalogDao
import com.arcontractor.estimator.data.local.ProjectDao
import com.arcontractor.estimator.data.local.ProjectEntity
import com.arcontractor.estimator.data.local.SurfaceDao
import com.arcontractor.estimator.data.local.SurfaceEntity
import com.arcontractor.estimator.data.local.SurfaceWithCutouts
import com.arcontractor.estimator.data.local.toDomain
import com.arcontractor.estimator.data.local.toEntity
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.MaterialEstimationEngine
import com.arcontractor.estimator.domain.estimation.ProjectQuoteAggregator
import com.arcontractor.estimator.domain.estimation.ProjectSurfaceInput
import com.arcontractor.estimator.domain.estimation.SurfaceEstimate
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.estimation.WastePreset
import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.CutoutValidationPolicy
import com.arcontractor.estimator.domain.geometry.PolygonGeometryEngine
import com.arcontractor.estimator.domain.geometry.Vec3
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface ProjectRepository {
    fun observeWorkspace(projectId: String): Flow<ProjectWorkspace?>

    suspend fun ensureDefaultProject(): String
    suspend fun addSurface(projectId: String, draft: SurfaceMeasurementDraft): String
    suspend fun updateSurfaceSettings(
        surfaceId: String,
        materialId: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?
    )

    suspend fun updateProjectTaxSettings(
        projectId: String,
        taxRate: Double,
        taxPolicy: TaxPolicy
    )

    suspend fun updateProjectMetadata(
        projectId: String,
        name: String,
        clientName: String,
        contractorName: String,
        contractorContact: String,
        contractorLicense: String
    )

    suspend fun renameSurface(surfaceId: String, name: String)
    suspend fun deleteSurface(surfaceId: String)
}

class RoomProjectRepository(
    private val projectDao: ProjectDao,
    private val surfaceDao: SurfaceDao,
    private val materialDao: MaterialCatalogDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : ProjectRepository {

    override fun observeWorkspace(projectId: String): Flow<ProjectWorkspace?> =
        combine(
            projectDao.observeById(projectId),
            surfaceDao.observeForProject(projectId),
            materialDao.observeAll()
        ) { project, storedSurfaces, materialEntities ->
            project ?: return@combine null

            val materials = materialEntities.map { it.toDomain() }
            val materialsById = materials.associateBy(MaterialSpec::id)
            val loadErrors = ArrayList<String>()
            val taxRate = project.taxRate.coerceIn(0.0, 1.0)
            val taxPolicy = runCatching { TaxPolicy.valueOf(project.taxPolicy) }
                .getOrElse {
                    loadErrors += "Unknown tax policy '${project.taxPolicy}'; totals use taxable labor."
                    TaxPolicy.TAX_TOTAL_SUBTOTAL
                }
            val surfaces = storedSurfaces.map { stored ->
                reconstructSurface(
                    stored = stored,
                    materialsById = materialsById,
                    loadErrors = loadErrors,
                    taxRate = taxRate,
                    taxPolicy = taxPolicy
                )
            }
            val quoteInputs = surfaces.mapNotNull(::quoteInput)

            val quote = ProjectQuoteAggregator.aggregateProject(
                projectName = project.name,
                surfaces = quoteInputs,
                taxRate = taxRate,
                taxPolicy = taxPolicy
            )
            val reconciledSurfaces = surfaces.map { surface ->
                val projectEstimate = quote.surfaces.firstOrNull {
                    it.surfaceId == surface.surfaceId
                }?.estimate
                if (projectEstimate != null) {
                    surface.copy(estimate = projectEstimate)
                } else {
                    surface
                }
            }
            ProjectWorkspace(
                project = project,
                materials = materials,
                surfaces = reconciledSurfaces,
                quote = quote,
                loadErrors = loadErrors
            )
        }

    override suspend fun ensureDefaultProject(): String {
        val existing = projectDao.getFirst()
        if (existing != null) return existing.id

        val timestamp = now()
        val project = ProjectEntity(
            id = DEFAULT_PROJECT_ID,
            name = DEFAULT_PROJECT_NAME,
            clientName = "",
            contractorName = "",
            contractorContact = "",
            contractorLicense = "",
            taxRate = 0.0,
            taxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL.name,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp
        )
        projectDao.upsert(project)
        return project.id
    }

    override suspend fun addSurface(
        projectId: String,
        draft: SurfaceMeasurementDraft
    ): String {
        require(draft.name.isNotBlank()) { "Surface name cannot be blank." }
        require(draft.outerVertices.size >= 3) { "A surface requires at least three anchors." }

        val materialEntity = materialDao.getById(draft.materialId)
            ?: error("Selected material no longer exists.")
        val effectiveMaterial = applyOverrides(
            material = materialEntity.toDomain(),
            wastePresetOverride = draft.wastePresetOverride,
            customWasteFactorOverride = draft.customWasteFactorOverride,
            laborRateOverride = draft.laborRateOverride
        )

        val analysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = draft.outerVertices,
            cutouts = draft.cutouts,
            referenceNormal = draft.referenceNormal,
            cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
        )
        MaterialEstimationEngine.estimateSurface(
            geometry = analysis,
            materialSpec = effectiveMaterial
        )

        val timestamp = now()
        val surfaceId = idGenerator()
        val surface = SurfaceEntity(
            id = surfaceId,
            projectId = projectId,
            name = draft.name.trim(),
            outerVertices = draft.outerVertices,
            referenceNormal = draft.referenceNormal,
            materialId = draft.materialId,
            wastePresetOverride = draft.wastePresetOverride,
            customWasteFactorOverride = draft.customWasteFactorOverride,
            laborRateOverride = draft.laborRateOverride,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp
        )
        val cutouts = draft.cutouts.mapIndexed { index, cutout ->
            cutout.toEntity(surfaceId = surfaceId, displayOrder = index)
        }
        surfaceDao.replaceSurface(surface, cutouts)
        return surfaceId
    }

    override suspend fun updateSurfaceSettings(
        surfaceId: String,
        materialId: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?
    ) {
        require(customWasteFactorOverride == null || customWasteFactorOverride in 0.0..1.0) {
            "Waste factor must be between 0% and 100%."
        }
        require(laborRateOverride == null || laborRateOverride >= 0.0) {
            "Labor rate cannot be negative."
        }
        require(materialDao.getById(materialId) != null) { "Selected material no longer exists." }
        surfaceDao.updatePricing(
            surfaceId = surfaceId,
            materialId = materialId,
            wastePresetOverride = wastePresetOverride,
            customWasteFactorOverride = customWasteFactorOverride,
            laborRateOverride = laborRateOverride,
            updatedAtEpochMillis = now()
        )
    }

    override suspend fun updateProjectTaxSettings(
        projectId: String,
        taxRate: Double,
        taxPolicy: TaxPolicy
    ) {
        require(taxRate in 0.0..1.0) { "Tax rate must be between 0% and 100%." }
        projectDao.updateTaxSettings(
            projectId = projectId,
            taxRate = taxRate,
            taxPolicy = taxPolicy.name,
            updatedAtEpochMillis = now()
        )
    }

    override suspend fun updateProjectMetadata(
        projectId: String,
        name: String,
        clientName: String,
        contractorName: String,
        contractorContact: String,
        contractorLicense: String
    ) {
        require(name.isNotBlank()) { "Project name cannot be blank." }
        require(contractorName.length <= 120) { "Contractor name is too long." }
        require(contractorContact.length <= 240) { "Contractor contact is too long." }
        require(contractorLicense.length <= 120) { "Contractor license is too long." }
        projectDao.updateMetadata(
            projectId = projectId,
            name = name.trim(),
            clientName = clientName.trim(),
            contractorName = contractorName.trim(),
            contractorContact = contractorContact.trim(),
            contractorLicense = contractorLicense.trim(),
            updatedAtEpochMillis = now()
        )
    }

    override suspend fun renameSurface(surfaceId: String, name: String) {
        require(name.isNotBlank()) { "Surface name cannot be blank." }
        surfaceDao.rename(surfaceId, name.trim(), now())
    }

    override suspend fun deleteSurface(surfaceId: String) {
        surfaceDao.delete(surfaceId)
    }

    private fun reconstructSurface(
        stored: SurfaceWithCutouts,
        materialsById: Map<String, MaterialSpec>,
        loadErrors: MutableList<String>,
        taxRate: Double,
        taxPolicy: TaxPolicy
    ): SurfaceQuoteLine {
        val entity = stored.surface
        val catalogMaterial = materialsById[entity.materialId] ?: run {
            val error = "Surface '${entity.name}' references a missing material."
            loadErrors += error
            return emptySurfaceLine(entity, error)
        }
        val overrideErrors = ArrayList<String>()
        val effectiveMaterial = applyOverridesSafely(
            material = catalogMaterial,
            surfaceName = entity.name,
            wastePresetOverride = entity.wastePresetOverride,
            customWasteFactorOverride = entity.customWasteFactorOverride,
            laborRateOverride = entity.laborRateOverride,
            loadErrors = overrideErrors
        )
        loadErrors += overrideErrors

        val cutouts = stored.cutouts
            .sortedBy(CutoutEntity::displayOrder)
            .map(CutoutEntity::toDomain)
        return runCatching {
            val analysis = PolygonGeometryEngine.analyze3DSurface(
                outerVertices3D = entity.outerVertices,
                cutouts = cutouts,
                referenceNormal = entity.referenceNormal,
                cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
            )
            SurfaceQuoteLine(
                surfaceId = entity.id,
                surfaceName = entity.name,
                materialId = entity.materialId,
                outerVertices = entity.outerVertices,
                referenceNormal = entity.referenceNormal,
                cutouts = cutouts,
                effectiveMaterial = effectiveMaterial,
                wastePresetOverride = entity.wastePresetOverride,
                customWasteFactorOverride = entity.customWasteFactorOverride,
                laborRateOverride = entity.laborRateOverride,
                analysis = analysis,
                estimate = MaterialEstimationEngine.estimateSurface(
                    geometry = analysis,
                    materialSpec = effectiveMaterial,
                    taxRate = 0.0,
                    taxPolicy = taxPolicy
                )
            )
        }.getOrElse { error ->
            val message = "Surface '${entity.name}' could not be reconstructed: " +
                (error.message ?: "invalid saved geometry")
            loadErrors += message
            SurfaceQuoteLine(
                surfaceId = entity.id,
                surfaceName = entity.name,
                materialId = entity.materialId,
                outerVertices = entity.outerVertices,
                referenceNormal = entity.referenceNormal,
                cutouts = cutouts,
                effectiveMaterial = effectiveMaterial,
                wastePresetOverride = entity.wastePresetOverride,
                customWasteFactorOverride = entity.customWasteFactorOverride,
                laborRateOverride = entity.laborRateOverride,
                analysis = null,
                estimate = null,
                loadError = message
            )
        }
    }

    private fun emptySurfaceLine(
        entity: SurfaceEntity,
        error: String
    ): SurfaceQuoteLine {
        val fallback = MaterialSpec(
            id = entity.materialId,
            name = "Unavailable material",
            tradeType = com.arcontractor.estimator.domain.estimation.TradeType.DRYWALL,
            primaryUnitType = com.arcontractor.estimator.domain.estimation.PackageUnitType.SHEET,
            boxCoverageSqMeters = 1.0,
            unitCost = 0.0,
            wastePreset = WastePreset.PAINT_OR_DRYWALL,
            laborRatePerUnitArea = 0.0,
            tradeParameters = com.arcontractor.estimator.domain.estimation.TradeParameters.Drywall()
        )
        return SurfaceQuoteLine(
            surfaceId = entity.id,
            surfaceName = entity.name,
            materialId = entity.materialId,
            outerVertices = entity.outerVertices,
            referenceNormal = entity.referenceNormal,
            cutouts = emptyList(),
            effectiveMaterial = fallback,
            wastePresetOverride = entity.wastePresetOverride,
            customWasteFactorOverride = entity.customWasteFactorOverride,
            laborRateOverride = entity.laborRateOverride,
            analysis = null,
            estimate = null,
            loadError = error
        )
    }

    private fun applyOverrides(
        material: MaterialSpec,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?
    ): MaterialSpec {
        val wastePreset = wastePresetOverride?.let(WastePreset::valueOf)
            ?: material.wastePreset
        return material.copy(
            wastePreset = wastePreset,
            customWasteFactor = customWasteFactorOverride ?: material.customWasteFactor,
            laborRatePerUnitArea = laborRateOverride ?: material.laborRatePerUnitArea
        )
    }

    private fun applyOverridesSafely(
        material: MaterialSpec,
        surfaceName: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?,
        loadErrors: MutableList<String>
    ): MaterialSpec {
        val wastePreset = wastePresetOverride?.let { name ->
            runCatching { WastePreset.valueOf(name) }.getOrElse {
                loadErrors += "Surface '$surfaceName' has unknown waste preset '$name'; " +
                    "using the material default."
                material.wastePreset
            }
        } ?: material.wastePreset
        val customFactor = customWasteFactorOverride?.takeIf { it in 0.0..1.0 }
        if (customWasteFactorOverride != null && customFactor == null) {
            loadErrors += "Surface '$surfaceName' has an out-of-range waste factor; " +
                "using the material default."
        }
        val laborRate = laborRateOverride?.takeIf { it >= 0.0 }
        if (laborRateOverride != null && laborRate == null) {
            loadErrors += "Surface '$surfaceName' has a negative labor rate; " +
                "using the material default."
        }
        return runCatching {
            material.copy(
                wastePreset = wastePreset,
                customWasteFactor = customFactor ?: material.customWasteFactor,
                laborRatePerUnitArea = laborRate ?: material.laborRatePerUnitArea
            )
        }.getOrElse {
            loadErrors += "Surface '$surfaceName' has invalid pricing overrides; " +
                "using the material defaults."
            material
        }
    }

    private fun quoteInput(surface: SurfaceQuoteLine): ProjectSurfaceInput? {
        val analysis = surface.analysis ?: return null
        val estimate = surface.estimate ?: return null
        if (estimate.netAreaSqMeters <= 0.0 && analysis.grossAreaSqMeters <= 0.0) {
            return null
        }
        return ProjectSurfaceInput(
            surfaceId = surface.surfaceId,
            surfaceName = surface.surfaceName,
            geometry = analysis,
            materialSpec = surface.effectiveMaterial
        )
    }

    private companion object {
        const val DEFAULT_PROJECT_ID = "default-project"
        const val DEFAULT_PROJECT_NAME = "Untitled contractor quote"
    }
}
