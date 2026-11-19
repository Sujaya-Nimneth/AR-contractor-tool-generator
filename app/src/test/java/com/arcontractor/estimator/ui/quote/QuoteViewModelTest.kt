package com.arcontractor.estimator.ui.quote

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import com.arcontractor.estimator.data.repository.FakeMaterialCatalogDao
import com.arcontractor.estimator.data.repository.FakeProjectDao
import com.arcontractor.estimator.data.repository.FakeSurfaceDao
import com.arcontractor.estimator.data.repository.RoomMaterialCatalogRepository
import com.arcontractor.estimator.data.repository.RoomProjectRepository
import com.arcontractor.estimator.data.pdf.PdfExportOptions
import com.arcontractor.estimator.data.pdf.PdfExportResult
import com.arcontractor.estimator.data.pdf.QuotePdfExporter
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.estimation.WastePreset
import com.arcontractor.estimator.domain.geometry.Vec3
import com.arcontractor.estimator.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuoteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    @Test
    fun `view model bootstraps an empty local quote workspace`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.projectId)
        assertEquals(5, state.workspace?.materials?.size)
        assertTrue(state.workspace?.surfaces.isNullOrEmpty())
        assertEquals(0.0, state.workspace?.quote?.grandTotal?.toDouble() ?: -1.0, 0.0)
    }

    @Test
    fun `saved AR measurement becomes selected quote surface`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList(),
                    name = "Kitchen north wall"
                )
            )
        )

        val state = viewModel.uiState.value
        assertFalse(state.isSavingMeasurement)
        assertEquals("surface-1", state.selectedSurfaceId)
        assertEquals(1, state.workspace?.surfaces?.size)
        assertEquals("Kitchen north wall", state.selectedSurface?.surfaceName)
        assertEquals(6.0, state.selectedSurface?.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
        assertTrue((state.workspace?.quote?.grandTotal?.toDouble() ?: 0.0) > 0.0)
    }

    @Test
    fun `surface waste labor and project tax actions recompute quote`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList()
                )
            )
        )
        val surfaceId = viewModel.uiState.value.selectedSurfaceId ?: error("Missing surface")

        viewModel.onAction(
            QuoteAction.SetSurfaceWastePreset(surfaceId, WastePreset.CUSTOM)
        )
        viewModel.onAction(QuoteAction.SetSurfaceWasteFactor(surfaceId, 0.22))
        viewModel.onAction(QuoteAction.SetSurfaceLaborRate(surfaceId, 75.0))
        viewModel.onAction(QuoteAction.SetProjectTaxRate(0.08))
        viewModel.onAction(
            QuoteAction.SetProjectTaxPolicy(TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY)
        )

        val state = viewModel.uiState.value
        assertEquals(0.22, state.selectedSurface?.effectiveMaterial?.effectiveWasteFactor ?: -1.0, 1e-9)
        assertEquals(75.0, state.selectedSurface?.effectiveMaterial?.laborRatePerUnitArea ?: -1.0, 1e-9)
        assertEquals(0.08, state.workspace?.quote?.taxRate ?: -1.0, 0.0)
        assertEquals(
            TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY,
            state.workspace?.quote?.taxPolicy
        )
        assertEquals(0.08, state.selectedSurface?.estimate?.taxRate ?: -1.0, 0.0)
    }

    @Test
    fun `catalog editor updates an existing material`() = runTest {
        val viewModel = createViewModel()
        val original = viewModel.uiState.value.workspace?.materials?.first()
            ?: error("Missing material")

        viewModel.onAction(QuoteAction.OpenCatalog)
        viewModel.onAction(QuoteAction.EditCatalogMaterial(original.id))
        val draft = viewModel.uiState.value.catalogEditor ?: error("Missing editor")
        viewModel.onAction(
            QuoteAction.UpdateCatalogEditor(
                draft.copy(
                    name = "Updated field tile",
                    unitCostText = "51.25"
                )
            )
        )
        viewModel.onAction(QuoteAction.SaveCatalogMaterial)

        val saved = viewModel.uiState.value.workspace?.materials
            ?.firstOrNull { it.id == original.id }
        assertEquals("Updated field tile", saved?.name)
        assertEquals(51.25, saved?.unitCost ?: -1.0, 0.0)
        assertEquals(null, viewModel.uiState.value.catalogEditor)
    }

    @Test
    fun `project and contractor details are saved from the quote editor`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.OpenProjectDetails)
        val draft = viewModel.uiState.value.projectDetailsDraft ?: error("Missing project editor")
        viewModel.onAction(
            QuoteAction.UpdateProjectDetails(
                draft.copy(
                    name = "  Harbor Street renovation  ",
                    clientName = "  Morgan & Lee  ",
                    contractorName = "  Northline Build Co.  ",
                    contractorContact = "  hello@northline.example  ",
                    contractorLicense = "  GC-10482  "
                )
            )
        )
        viewModel.onAction(QuoteAction.SaveProjectDetails)

        val state = viewModel.uiState.value
        val project = state.workspace?.project ?: error("Missing project")
        assertEquals("Harbor Street renovation", project.name)
        assertEquals("Morgan & Lee", project.clientName)
        assertEquals("Northline Build Co.", project.contractorName)
        assertEquals("hello@northline.example", project.contractorContact)
        assertEquals("GC-10482", project.contractorLicense)
        assertFalse(state.isProjectDetailsOpen)
        assertFalse(state.isSavingProjectDetails)
    }

    @Test
    fun `export is blocked before a surface has been measured`() = runTest {
        val exporter = RecordingQuotePdfExporter()
        val viewModel = createViewModel(exporter)

        viewModel.onAction(QuoteAction.OpenExportDialog)
        viewModel.onAction(QuoteAction.SetExportPaperSize(com.arcontractor.estimator.data.pdf.PdfPaperSize.LETTER))
        viewModel.onAction(QuoteAction.SetExportValidityDays("45"))
        viewModel.onAction(QuoteAction.ExportPdf)

        val state = viewModel.uiState.value
        assertEquals(0, exporter.callCount)
        assertFalse(state.isExportingPdf)
        assertTrue(state.message?.isError == true)
        assertEquals(com.arcontractor.estimator.data.pdf.PdfPaperSize.LETTER, state.exportDraft.paperSize)
        assertEquals(45, state.exportDraft.validityDays)
    }

    @Test
    fun `saving a measurement does not reseed an intentionally empty catalog`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        val materials = state.workspace?.materials.orEmpty()
        assertEquals(5, materials.size)

        materials.forEach { material ->
            viewModel.onAction(QuoteAction.OpenCatalog)
            viewModel.onAction(QuoteAction.EditCatalogMaterial(material.id))
            viewModel.onAction(QuoteAction.DeleteCatalogMaterial)
        }
        assertEquals(0, viewModel.uiState.value.workspace?.materials?.size)

        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList()
                )
            )
        )

        assertEquals(0, viewModel.uiState.value.workspace?.materials?.size)
        assertTrue(viewModel.uiState.value.message?.isError == true)
    }

    @Test
    fun `high tax rates above thirty percent are accepted`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList()
                )
            )
        )

        viewModel.onAction(QuoteAction.SetProjectTaxRate(0.40))

        val state = viewModel.uiState.value
        assertEquals(0.40, state.workspace?.quote?.taxRate ?: -1.0, 0.0)
        assertEquals(0.40, state.selectedSurface?.estimate?.taxRate ?: -1.0, 0.0)
    }

    @Test
    fun `out-of-range tax rates are rejected with an error instead of clamped`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(QuoteAction.SetProjectTaxRate(0.08))

        viewModel.onAction(QuoteAction.SetProjectTaxRate(1.5))

        val state = viewModel.uiState.value
        assertEquals(0.08, state.workspace?.quote?.taxRate ?: -1.0, 0.0)
        assertTrue(state.message?.isError == true)
    }

    private fun createViewModel(
        exporter: QuotePdfExporter = RecordingQuotePdfExporter()
    ): QuoteViewModel {
        val projectDao = FakeProjectDao()
        val surfaceDao = FakeSurfaceDao()
        val materialDao = FakeMaterialCatalogDao()
        val materialRepository = RoomMaterialCatalogRepository(
            materialDao = materialDao,
            surfaceDao = surfaceDao,
            now = { 1000L }
        )
        val projectRepository = RoomProjectRepository(
            projectDao = projectDao,
            surfaceDao = surfaceDao,
            materialDao = materialDao,
            now = { 2000L },
            idGenerator = { "surface-1" }
        )
        return ViewModelProvider(
            viewModelStore,
            QuoteViewModel.Factory(
                projectRepository = projectRepository,
                materialCatalogRepository = materialRepository,
                quotePdfExporter = exporter
            )
        )[QuoteViewModel::class.java]
    }

    private class RecordingQuotePdfExporter : QuotePdfExporter {
        var callCount: Int = 0
            private set

        override suspend fun export(
            workspace: ProjectWorkspace,
            options: PdfExportOptions
        ): PdfExportResult {
            callCount++
            error("Export fixture is not configured for this test.")
        }
    }

    private companion object {
        val RECTANGLE = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 2.0, 0.0),
            Vec3(0.0, 2.0, 0.0)
        )
    }
}
