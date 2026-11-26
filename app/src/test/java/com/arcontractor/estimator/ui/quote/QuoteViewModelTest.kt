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

    @Test
    fun `manual metric wall with door becomes a priced quote surface`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.OpenManualEntry())
        val draft = viewModel.uiState.value.manualSurfaceDraft ?: error("Missing manual draft")
        viewModel.onAction(
            QuoteAction.UpdateManualSurfaceDraft(
                draft.copy(
                    name = "Manual north wall",
                    widthText = "4",
                    heightText = "2.7",
                    cutouts = listOf(
                        ManualCutoutDraft(
                            kind = com.arcontractor.estimator.domain.geometry.CutoutKind.DOOR,
                            offsetHorizontalText = "0.4",
                            offsetVerticalText = "0",
                            widthText = "0.9",
                            heightText = "2.1"
                        )
                    )
                )
            )
        )
        viewModel.onAction(QuoteAction.SaveManualSurface)

        val state = viewModel.uiState.value
        assertFalse(state.isManualEntryOpen)
        assertFalse(state.isSavingMeasurement)
        val surface = state.selectedSurface ?: error("Missing saved surface")
        assertEquals("Manual north wall", surface.surfaceName)
        assertEquals(8.91, surface.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(0.9, surface.analysis?.doorOpeningWidthMeters ?: -1.0, 1e-6)
        assertTrue((surface.estimate?.grandTotal?.toDouble() ?: 0.0) > 0.0)
    }

    @Test
    fun `manual entry in feet converts dimensions through UnitConverter`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.OpenManualEntry())
        val draft = viewModel.uiState.value.manualSurfaceDraft ?: error("Missing manual draft")
        viewModel.onAction(
            QuoteAction.UpdateManualSurfaceDraft(
                draft.copy(
                    orientation = com.arcontractor.estimator.domain.geometry.ManualSurfaceOrientation.FLOOR,
                    unit = ManualLengthUnit.FEET,
                    widthText = "10",
                    heightText = "12"
                )
            )
        )
        viewModel.onAction(QuoteAction.SaveManualSurface)

        val surface = viewModel.uiState.value.selectedSurface ?: error("Missing saved surface")
        // 10 ft x 12 ft = 3.048 m x 3.6576 m = 11.1483648 m²
        assertEquals(11.1483648, surface.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
    }

    @Test
    fun `manual re-entry replaces the existing surface and keeps its pricing`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList(),
                    name = "Original wall"
                )
            )
        )
        val original = viewModel.uiState.value.selectedSurface ?: error("Missing surface")
        viewModel.onAction(QuoteAction.SetSurfaceLaborRate(original.surfaceId, 70.0))

        viewModel.onAction(QuoteAction.OpenManualEntry(original.surfaceId))
        val draft = viewModel.uiState.value.manualSurfaceDraft ?: error("Missing manual draft")
        assertEquals(original.surfaceId, draft.replaceSurfaceId)
        viewModel.onAction(
            QuoteAction.UpdateManualSurfaceDraft(
                draft.copy(name = "Corrected wall", widthText = "5", heightText = "2.5")
            )
        )
        viewModel.onAction(QuoteAction.SaveManualSurface)

        val state = viewModel.uiState.value
        assertEquals(1, state.workspace?.surfaces?.size)
        val replaced = state.selectedSurface ?: error("Missing replaced surface")
        assertEquals(original.surfaceId, replaced.surfaceId)
        assertEquals("Corrected wall", replaced.surfaceName)
        assertEquals(12.5, replaced.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(70.0, replaced.effectiveMaterial.laborRatePerUnitArea, 1e-9)
        assertFalse(state.isManualEntryOpen)
    }

    @Test
    fun `invalid manual dimensions surface an error and keep the dialog open`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.OpenManualEntry())
        val draft = viewModel.uiState.value.manualSurfaceDraft ?: error("Missing manual draft")
        viewModel.onAction(
            QuoteAction.UpdateManualSurfaceDraft(draft.copy(widthText = "abc", heightText = "2.4"))
        )
        viewModel.onAction(QuoteAction.SaveManualSurface)

        val state = viewModel.uiState.value
        assertTrue(state.isManualEntryOpen)
        assertTrue(state.message?.isError == true)
        assertTrue(state.workspace?.surfaces.isNullOrEmpty())
    }

    @Test
    fun `AR capture targeting an existing surface replaces it in place`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList(),
                    name = "AR original"
                )
            )
        )
        val original = viewModel.uiState.value.selectedSurface ?: error("Missing surface")
        viewModel.onAction(QuoteAction.SetSurfaceLaborRate(original.surfaceId, 65.0))

        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = listOf(
                        Vec3(0.0, 0.0, 0.0),
                        Vec3(4.0, 0.0, 0.0),
                        Vec3(4.0, 2.0, 0.0),
                        Vec3(0.0, 2.0, 0.0)
                    ),
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList(),
                    replaceSurfaceId = original.surfaceId
                )
            )
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.workspace?.surfaces?.size)
        val replaced = state.selectedSurface ?: error("Missing replaced surface")
        assertEquals(original.surfaceId, replaced.surfaceId)
        assertEquals("AR original", replaced.surfaceName)
        assertEquals(8.0, replaced.analysis?.netAreaSqMeters ?: -1.0, 1e-6)
        assertEquals(65.0, replaced.effectiveMaterial.laborRatePerUnitArea, 1e-9)
    }

    @Test
    fun `creating a project switches to an empty workspace and keeps the old one`() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(
            QuoteAction.SaveMeasurement(
                MeasurementCapture(
                    outerVertices = RECTANGLE,
                    referenceNormal = Vec3(0.0, 0.0, 1.0),
                    cutouts = emptyList(),
                    name = "Job A wall"
                )
            )
        )
        val originalProjectId = viewModel.uiState.value.projectId ?: error("Missing project")

        viewModel.onAction(QuoteAction.OpenProjectSwitcher)
        viewModel.onAction(QuoteAction.UpdateNewProjectName("Harbor Street job"))
        viewModel.onAction(QuoteAction.CreateProject)

        val state = viewModel.uiState.value
        assertEquals(2, state.projects.size)
        assertTrue(state.projectId != originalProjectId)
        assertTrue(state.workspace?.surfaces.isNullOrEmpty())
        assertEquals("Harbor Street job", state.workspace?.project?.name)
        assertFalse(state.isProjectSwitcherOpen)

        viewModel.onAction(QuoteAction.SwitchProject(originalProjectId))
        val restored = viewModel.uiState.value
        assertEquals(originalProjectId, restored.projectId)
        assertEquals(1, restored.workspace?.surfaces?.size)
        assertEquals("Job A wall", restored.workspace?.surfaces?.single()?.surfaceName)
    }

    @Test
    fun `deleting the active project falls back to a remaining project`() = runTest {
        val viewModel = createViewModel()
        val firstProjectId = viewModel.uiState.value.projectId ?: error("Missing project")
        viewModel.onAction(QuoteAction.UpdateNewProjectName("Second job"))
        viewModel.onAction(QuoteAction.CreateProject)
        val secondProjectId = viewModel.uiState.value.projectId ?: error("Missing second project")

        viewModel.onAction(QuoteAction.DeleteProject(secondProjectId))

        val state = viewModel.uiState.value
        assertEquals(1, state.projects.size)
        assertEquals(firstProjectId, state.projectId)
        assertNotNull(state.workspace)
    }

    @Test
    fun `the last remaining project cannot be deleted`() = runTest {
        val viewModel = createViewModel()
        val projectId = viewModel.uiState.value.projectId ?: error("Missing project")

        viewModel.onAction(QuoteAction.DeleteProject(projectId))

        val state = viewModel.uiState.value
        assertEquals(1, state.projects.size)
        assertTrue(state.message?.isError == true)
    }

    @Test
    fun `project currency changes flow into the workspace`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.OpenProjectDetails)
        val draft = viewModel.uiState.value.projectDetailsDraft ?: error("Missing draft")
        viewModel.onAction(
            QuoteAction.UpdateProjectDetails(draft.copy(currencyCode = "LKR"))
        )
        viewModel.onAction(QuoteAction.SaveProjectDetails)

        assertEquals("LKR", viewModel.uiState.value.workspace?.project?.currencyCode)
    }

    @Test
    fun `display unit toggle switches between metric and imperial`() = runTest {
        val viewModel = createViewModel()
        assertEquals(DisplayUnitSystem.METRIC, viewModel.uiState.value.displayUnits)

        viewModel.onAction(QuoteAction.SetDisplayUnits(DisplayUnitSystem.IMPERIAL))
        assertEquals(DisplayUnitSystem.IMPERIAL, viewModel.uiState.value.displayUnits)

        viewModel.onAction(QuoteAction.SetDisplayUnits(DisplayUnitSystem.METRIC))
        assertEquals(DisplayUnitSystem.METRIC, viewModel.uiState.value.displayUnits)
    }

    @Test
    fun `area and length formatters convert to imperial display units`() {
        assertEquals("10.00 m²", formatArea(10.0))
        assertEquals("107.6 ft²", formatArea(10.0, DisplayUnitSystem.IMPERIAL))
        assertEquals("2.44 m", formatLength(2.4384))
        assertEquals("8' 0\"", formatLength(2.4384, DisplayUnitSystem.IMPERIAL))
    }

    @Test
    fun `saving a measurement targets the active project instead of the default project`() =
        runTest {
            val viewModel = createViewModel()
            val defaultProjectId = viewModel.uiState.value.projectId ?: error("Missing project")
            viewModel.onAction(QuoteAction.UpdateNewProjectName("Second site"))
            viewModel.onAction(QuoteAction.CreateProject)
            val secondProjectId = viewModel.uiState.value.projectId ?: error("Missing project")
            assertTrue(secondProjectId != defaultProjectId)

            viewModel.onAction(
                QuoteAction.SaveMeasurement(
                    MeasurementCapture(
                        outerVertices = RECTANGLE,
                        referenceNormal = Vec3(0.0, 0.0, 1.0),
                        cutouts = emptyList(),
                        name = "Second site wall"
                    )
                )
            )

            val state = viewModel.uiState.value
            assertEquals(1, state.workspace?.surfaces?.size)
            assertEquals("Second site wall", state.workspace?.surfaces?.single()?.surfaceName)
            assertFalse(state.message?.isError == true)

            viewModel.onAction(QuoteAction.SwitchProject(defaultProjectId))
            assertTrue(viewModel.uiState.value.workspace?.surfaces.isNullOrEmpty())
        }

    @Test
    fun `replacing a measurement in a non-default project keeps that project selected`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onAction(QuoteAction.UpdateNewProjectName("Second site"))
            viewModel.onAction(QuoteAction.CreateProject)
            val secondProjectId = viewModel.uiState.value.projectId ?: error("Missing project")
            viewModel.onAction(
                QuoteAction.SaveMeasurement(
                    MeasurementCapture(
                        outerVertices = RECTANGLE,
                        referenceNormal = Vec3(0.0, 0.0, 1.0),
                        cutouts = emptyList(),
                        name = "Second site wall"
                    )
                )
            )
            val surfaceId = viewModel.uiState.value.selectedSurfaceId
                ?: error("Missing surface")

            viewModel.onAction(
                QuoteAction.SaveMeasurement(
                    MeasurementCapture(
                        outerVertices = listOf(
                            Vec3(0.0, 0.0, 0.0),
                            Vec3(4.0, 0.0, 0.0),
                            Vec3(4.0, 3.0, 0.0),
                            Vec3(0.0, 3.0, 0.0)
                        ),
                        referenceNormal = Vec3(0.0, 0.0, 1.0),
                        cutouts = emptyList(),
                        replaceSurfaceId = surfaceId
                    )
                )
            )

            val state = viewModel.uiState.value
            assertFalse(state.message?.isError == true)
            assertEquals(secondProjectId, state.projectId)
            assertEquals(1, state.workspace?.surfaces?.size)
            assertEquals(
                12.0,
                state.workspace?.surfaces?.single()?.analysis?.netAreaSqMeters ?: -1.0,
                1e-6
            )
        }

    @Test
    fun `creating a project keeps its confirmation message`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(QuoteAction.UpdateNewProjectName("Harbor Street job"))
        viewModel.onAction(QuoteAction.CreateProject)

        val state = viewModel.uiState.value
        assertEquals("Harbor Street job created.", state.message?.text)
        assertFalse(state.message?.isError == true)
        assertFalse(state.isLoading)
    }

    @Test
    fun `deleting the active project reports success without a transient lookup error`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onAction(QuoteAction.UpdateNewProjectName("Second job"))
            viewModel.onAction(QuoteAction.CreateProject)
            val secondProjectId = viewModel.uiState.value.projectId ?: error("Missing project")

            viewModel.onAction(QuoteAction.DeleteProject(secondProjectId))

            val state = viewModel.uiState.value
            assertEquals("Project deleted.", state.message?.text)
            assertFalse(state.message?.isError == true)
            assertTrue(state.workspace?.project?.id != secondProjectId)
        }

    private fun createViewModel(
        exporter: QuotePdfExporter = RecordingQuotePdfExporter()
    ): QuoteViewModel {
        val projectDao = FakeProjectDao()
        val surfaceDao = FakeSurfaceDao()
        val materialDao = FakeMaterialCatalogDao()
        var surfaceSequence = 0
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
            idGenerator = { "surface-${++surfaceSequence}" }
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
