package com.arcontractor.estimator.data.repository

import com.arcontractor.estimator.domain.estimation.DefaultMaterialCatalog
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.estimation.WastePreset
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.Vec3
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    @Test
    fun `catalog seeding is idempotent and preserves contractor edits`() = runTest {
        val materialDao = FakeMaterialCatalogDao()
        val repository = RoomMaterialCatalogRepository(
            materialDao = materialDao,
            surfaceDao = FakeSurfaceDao(),
            now = { 1000L }
        )

        repository.ensureSeeded()
        val edited = DefaultMaterialCatalog.oakLuxuryVinylPlank.copy(unitCost = 72.25)
        repository.save(edited)
        repository.ensureSeeded()

        assertEquals(5, materialDao.currentMaterials.size)
        assertEquals(
            72.25,
            materialDao.currentMaterials.first { it.id == edited.id }.unitCost,
            0.0
        )
    }

    @Test
    fun `surface persistence stores raw anchors and reconstructs live pricing`() = runTest {
        val fixtures = RepositoryFixtures()
        val projectId = fixtures.projectRepository.ensureDefaultProject()
        val surfaceId = fixtures.projectRepository.addSurface(
            projectId = projectId,
            draft = SurfaceMeasurementDraft(
                name = "Bathroom west wall",
                outerVertices = RECTANGLE_6_SQ_METERS,
                referenceNormal = Vec3(0.0, 0.0, 1.0),
                cutouts = listOf(DOOR_CUTOUT),
                materialId = DefaultMaterialCatalog.porcelainTile300x600Straight.id,
                wastePresetOverride = WastePreset.CUSTOM.name,
                customWasteFactorOverride = 0.20,
                laborRateOverride = 60.0
            )
        )

        val workspace = fixtures.projectRepository.observeWorkspace(projectId)
            .filterNotNull()
            .first()
        val surface = workspace.surfaces.single()

        assertEquals(surfaceId, surface.surfaceId)
        assertEquals(RECTANGLE_6_SQ_METERS, surface.outerVertices)
        assertEquals(6.0, surface.analysis?.grossAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(2.0, surface.analysis?.cutoutAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(4.0, surface.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(0.20, surface.effectiveMaterial.effectiveWasteFactor, 1e-9)
        assertEquals(60.0, surface.effectiveMaterial.laborRatePerUnitArea, 1e-9)
        assertEquals(BigDecimal("240.00"), surface.estimate?.laborSubtotal)
        assertEquals(4, surface.estimate?.boxesRequired)
        assertEquals(2.0, workspace.quote.totalCutoutAreaSqMeters, 1e-6)
        assertEquals(4.0, workspace.quote.totalNetAreaSqMeters, 1e-6)

        val stored = fixtures.surfaceDao.currentSurfaces.single()
        assertEquals(RECTANGLE_6_SQ_METERS, stored.surface.outerVertices)
        assertEquals(1, stored.cutouts.size)
        assertEquals("$surfaceId-cutout-1", stored.cutouts.single().id)
    }

    @Test
    fun `workspace recomputes totals when material and tax settings change`() = runTest {
        val fixtures = RepositoryFixtures()
        val projectId = fixtures.projectRepository.ensureDefaultProject()
        fixtures.projectRepository.addSurface(
            projectId = projectId,
            draft = SurfaceMeasurementDraft(
                name = "Hall floor",
                outerVertices = RECTANGLE_6_SQ_METERS,
                referenceNormal = Vec3(0.0, 0.0, 1.0),
                cutouts = emptyList(),
                materialId = DefaultMaterialCatalog.oakLuxuryVinylPlank.id
            )
        )

        val original = fixtures.projectRepository.observeWorkspace(projectId)
            .filterNotNull()
            .first()
        val originalMaterialTotal = original.quote.materialSubtotal
        val originalEstimate = original.surfaces.single().estimate
        assertNotNull(originalEstimate)

        val material = original.materials.first {
            it.id == DefaultMaterialCatalog.oakLuxuryVinylPlank.id
        }
        fixtures.materialRepository.save(material.copy(unitCost = material.unitCost + 10.0))
        fixtures.projectRepository.updateProjectTaxSettings(
            projectId = projectId,
            taxRate = 0.10,
            taxPolicy = TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY
        )

        val updated = fixtures.projectRepository.observeWorkspace(projectId)
            .filterNotNull()
            .first()

        assertTrue(updated.quote.materialSubtotal > originalMaterialTotal)
        assertEquals(0.10, updated.quote.taxRate, 0.0)
        assertEquals(TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY, updated.quote.taxPolicy)
        assertEquals(0.10, updated.surfaces.single().estimate?.taxRate ?: -1.0, 0.0)
        assertEquals(
            TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY,
            updated.surfaces.single().estimate?.taxPolicy
        )
        val expectedTaxable = updated.quote.materialSubtotal
            .add(updated.quote.consumablesSubtotal)
            .multiply(BigDecimal.valueOf(0.10))
            .setScale(2, RoundingMode.HALF_UP)
        assertEquals(expectedTaxable, updated.quote.taxAmount)
        assertTrue(updated.quote.taxAmount < updated.quote.subtotalBeforeTax
            .multiply(BigDecimal.valueOf(0.10))
            .setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `catalog deletion is blocked while a material is assigned`() = runTest {
        val fixtures = RepositoryFixtures()
        val projectId = fixtures.projectRepository.ensureDefaultProject()
        fixtures.projectRepository.addSurface(
            projectId = projectId,
            draft = SurfaceMeasurementDraft(
                name = "Ceiling",
                outerVertices = RECTANGLE_6_SQ_METERS,
                referenceNormal = Vec3(0.0, 0.0, 1.0),
                cutouts = emptyList(),
                materialId = DefaultMaterialCatalog.standardHalfInchDrywall4x8.id
            )
        )

        val result = fixtures.materialRepository.delete(
            DefaultMaterialCatalog.standardHalfInchDrywall4x8.id
        )
        val unusedResult = fixtures.materialRepository.delete(
            DefaultMaterialCatalog.acrylicEggshellInteriorPaint.id
        )

        assertEquals(MaterialDeleteResult.InUse(1), result)
        assertEquals(MaterialDeleteResult.Deleted, unusedResult)
        assertNull(
            fixtures.materialDao.currentMaterials.firstOrNull {
                it.id == DefaultMaterialCatalog.acrylicEggshellInteriorPaint.id
            }
        )
    }

    @Test
    fun `project metadata is trimmed and persisted for quote export`() = runTest {
        val fixtures = RepositoryFixtures()
        val projectId = fixtures.projectRepository.ensureDefaultProject()

        fixtures.projectRepository.updateProjectMetadata(
            projectId = projectId,
            name = "  Harbor Street renovation  ",
            clientName = "  Morgan & Lee  ",
            contractorName = "  Northline Build Co.  ",
            contractorContact = "  hello@northline.example | +1 555 0142  ",
            contractorLicense = "  GC-10482  "
        )

        val workspace = fixtures.projectRepository.observeWorkspace(projectId)
            .filterNotNull()
            .first()
        assertEquals("Harbor Street renovation", workspace.project.name)
        assertEquals("Morgan & Lee", workspace.project.clientName)
        assertEquals("Northline Build Co.", workspace.project.contractorName)
        assertEquals(
            "hello@northline.example | +1 555 0142",
            workspace.project.contractorContact
        )
        assertEquals("GC-10482", workspace.project.contractorLicense)
    }

    private class RepositoryFixtures {
        val projectDao = FakeProjectDao()
        val surfaceDao = FakeSurfaceDao()
        val materialDao = FakeMaterialCatalogDao()
        val materialRepository = RoomMaterialCatalogRepository(
            materialDao = materialDao,
            surfaceDao = surfaceDao,
            now = { 2000L }
        )
        val projectRepository = RoomProjectRepository(
            projectDao = projectDao,
            surfaceDao = surfaceDao,
            materialDao = materialDao,
            now = { 3000L },
            idGenerator = { "surface-1" }
        )

        init {
            kotlinx.coroutines.runBlocking {
                materialRepository.ensureSeeded()
            }
        }
    }

    private companion object {
        val RECTANGLE_6_SQ_METERS = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 2.0, 0.0),
            Vec3(0.0, 2.0, 0.0)
        )

        val DOOR_CUTOUT = CutoutPolygonInput(
            id = "cutout-1",
            label = "Door",
            vertices3D = listOf(
                Vec3(0.0, 0.0, 0.001),
                Vec3(1.0, 0.0, 0.001),
                Vec3(1.0, 2.0, 0.001),
                Vec3(0.0, 2.0, 0.001)
            ),
            kind = CutoutKind.DOOR
        )
    }
}
