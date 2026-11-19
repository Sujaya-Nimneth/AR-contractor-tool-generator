package com.arcontractor.estimator.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arcontractor.estimator.data.repository.MaterialCatalogRepository
import com.arcontractor.estimator.data.repository.MaterialDeleteResult
import com.arcontractor.estimator.data.repository.ProjectRepository
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceMeasurementDraft
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.data.pdf.PdfExportOptions
import com.arcontractor.estimator.data.pdf.QuotePdfExporter
import com.arcontractor.estimator.domain.estimation.DefaultMaterialCatalog
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.PackageUnitType
import com.arcontractor.estimator.domain.estimation.PricingAreaUnit
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.estimation.TradeParameters
import com.arcontractor.estimator.domain.estimation.TradeType
import com.arcontractor.estimator.domain.estimation.WastePreset
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuoteViewModel(
    private val projectRepository: ProjectRepository,
    private val materialCatalogRepository: MaterialCatalogRepository,
    private val quotePdfExporter: QuotePdfExporter,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(QuoteUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<QuoteUiState> = _uiState

    private var observationJob: Job? = null

    init {
        bootstrap()
    }

    fun onAction(action: QuoteAction) {
        when (action) {
            QuoteAction.Retry -> bootstrap()
            is QuoteAction.SaveMeasurement -> saveMeasurement(action.capture)
            is QuoteAction.SelectSurface -> _uiState.update {
                it.copy(selectedSurfaceId = action.surfaceId)
            }

            is QuoteAction.SetSurfaceMaterial -> setSurfaceMaterial(action)
            is QuoteAction.SetSurfaceWastePreset -> setSurfaceWastePreset(action)
            is QuoteAction.SetSurfaceWasteFactor -> setSurfaceWasteFactor(action)
            is QuoteAction.SetSurfaceLaborRate -> setSurfaceLaborRate(action)
            is QuoteAction.RenameSurface -> renameSurface(action)
            is QuoteAction.DeleteSurface -> deleteSurface(action)
            is QuoteAction.SetProjectTaxRate -> setProjectTaxRate(action.rate)
            is QuoteAction.SetProjectTaxPolicy -> setProjectTaxPolicy(action.policy)
            QuoteAction.OpenProjectDetails -> openProjectDetails()
            QuoteAction.CloseProjectDetails -> _uiState.update {
                it.copy(
                    isProjectDetailsOpen = false,
                    projectDetailsDraft = null
                )
            }

            is QuoteAction.UpdateProjectDetails -> _uiState.update {
                it.copy(projectDetailsDraft = action.draft)
            }

            QuoteAction.SaveProjectDetails -> saveProjectDetails()
            QuoteAction.OpenExportDialog -> _uiState.update {
                it.copy(isExportDialogOpen = true, message = null)
            }

            QuoteAction.CloseExportDialog -> _uiState.update {
                if (it.isExportingPdf) it else it.copy(isExportDialogOpen = false)
            }

            is QuoteAction.SetExportPaperSize -> _uiState.update {
                it.copy(
                    exportDraft = it.exportDraft.copy(paperSize = action.paperSize)
                )
            }

            is QuoteAction.SetExportValidityDays -> _uiState.update {
                it.copy(
                    exportDraft = it.exportDraft.copy(
                        validityDaysText = action.text.filter(Char::isDigit).take(3)
                    )
                )
            }

            QuoteAction.ExportPdf -> exportPdf()
            QuoteAction.ConsumeExportedPdf -> _uiState.update { it.copy(exportedPdf = null) }
            QuoteAction.OpenCatalog -> _uiState.update {
                it.copy(isCatalogOpen = true, catalogEditor = null)
            }

            QuoteAction.CloseCatalog -> _uiState.update {
                it.copy(isCatalogOpen = false, catalogEditor = null)
            }

            is QuoteAction.EditCatalogMaterial -> openCatalogEditor(action.materialId)
            QuoteAction.CloseCatalogEditor -> _uiState.update { it.copy(catalogEditor = null) }
            is QuoteAction.UpdateCatalogEditor -> updateCatalogEditor(action.draft)
            QuoteAction.SaveCatalogMaterial -> saveCatalogMaterial()
            QuoteAction.DeleteCatalogMaterial -> deleteCatalogMaterial()
            QuoteAction.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun bootstrap() {
        observationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, message = null) }
        observationJob = viewModelScope.launch {
            runCatching {
                materialCatalogRepository.ensureSeeded()
                projectRepository.ensureDefaultProject()
            }.onSuccess { projectId ->
                _uiState.update { it.copy(projectId = projectId) }
                projectRepository.observeWorkspace(projectId)
                    .catch { error ->
                        showError(error.message ?: "The saved quote could not be loaded.")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collect { workspace ->
                        applyWorkspace(workspace)
                    }
            }.onFailure { error ->
                showError(error.message ?: "The local quote database could not be opened.")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun applyWorkspace(workspace: ProjectWorkspace?) {
        if (workspace == null) {
            showError("The project could not be found.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        _uiState.update { current ->
            val selectedId = current.selectedSurfaceId
                ?.takeIf { id -> workspace.surfaces.any { it.surfaceId == id } }
                ?: workspace.surfaces.firstOrNull()?.surfaceId
            current.copy(
                isLoading = false,
                workspace = workspace,
                projectId = workspace.project.id,
                selectedSurfaceId = selectedId
            )
        }
    }

    private fun saveMeasurement(capture: MeasurementCapture) {
        if (_uiState.value.isSavingMeasurement) return
        _uiState.update { it.copy(isSavingMeasurement = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val projectId = projectRepository.ensureDefaultProject()
                val workspace = projectRepository.observeWorkspace(projectId)
                    .filterNotNull()
                    .first()
                val selectedMaterialId = workspace.surfaces
                    .firstOrNull { it.surfaceId == _uiState.value.selectedSurfaceId }
                    ?.materialId
                    ?: workspace.materials.firstOrNull()?.id
                    ?: error("Add a catalog material before saving a measurement.")
                val surfaceName = capture.name?.takeIf(String::isNotBlank)
                    ?: "Surface ${workspace.surfaces.size + 1}"
                val surfaceId = projectRepository.addSurface(
                    projectId = projectId,
                    draft = SurfaceMeasurementDraft(
                        name = surfaceName,
                        outerVertices = capture.outerVertices,
                        referenceNormal = capture.referenceNormal,
                        cutouts = capture.cutouts,
                        materialId = selectedMaterialId
                    )
                )
                surfaceId to surfaceName
            }.onSuccess { (surfaceId, surfaceName) ->
                _uiState.update {
                    it.copy(
                        isSavingMeasurement = false,
                        selectedSurfaceId = surfaceId,
                        message = QuoteMessage("$surfaceName saved to the quote.")
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSavingMeasurement = false) }
                showError(error.message ?: "The measurement could not be saved.")
            }
        }
    }

    private fun setSurfaceMaterial(action: QuoteAction.SetSurfaceMaterial) {
        val line = surface(action.surfaceId) ?: return
        updateSurface(
            line = line,
            materialId = action.materialId,
            wastePresetOverride = null,
            customWasteFactorOverride = null,
            laborRateOverride = null
        )
    }

    private fun setSurfaceWastePreset(action: QuoteAction.SetSurfaceWastePreset) {
        val line = surface(action.surfaceId) ?: return
        val customFactor = when (action.preset) {
            WastePreset.CUSTOM -> line.customWasteFactorOverride
                ?: line.effectiveMaterial.customWasteFactor
                ?: action.preset.defaultFactor

            else -> null
        }
        updateSurface(
            line = line,
            materialId = line.materialId,
            wastePresetOverride = action.preset.name,
            customWasteFactorOverride = customFactor,
            laborRateOverride = line.laborRateOverride
        )
    }

    private fun setSurfaceWasteFactor(action: QuoteAction.SetSurfaceWasteFactor) {
        val line = surface(action.surfaceId) ?: return
        updateSurface(
            line = line,
            materialId = line.materialId,
            wastePresetOverride = WastePreset.CUSTOM.name,
            customWasteFactorOverride = action.factor.coerceIn(0.0, 0.50),
            laborRateOverride = line.laborRateOverride
        )
    }

    private fun setSurfaceLaborRate(action: QuoteAction.SetSurfaceLaborRate) {
        val line = surface(action.surfaceId) ?: return
        updateSurface(
            line = line,
            materialId = line.materialId,
            wastePresetOverride = line.wastePresetOverride,
            customWasteFactorOverride = line.customWasteFactorOverride,
            laborRateOverride = action.rate.coerceAtLeast(0.0)
        )
    }

    private fun updateSurface(
        line: SurfaceQuoteLine,
        materialId: String,
        wastePresetOverride: String?,
        customWasteFactorOverride: Double?,
        laborRateOverride: Double?
    ) {
        viewModelScope.launch {
            runCatching {
                projectRepository.updateSurfaceSettings(
                    surfaceId = line.surfaceId,
                    materialId = materialId,
                    wastePresetOverride = wastePresetOverride,
                    customWasteFactorOverride = customWasteFactorOverride,
                    laborRateOverride = laborRateOverride
                )
            }.onFailure { error ->
                showError(error.message ?: "Surface pricing could not be updated.")
            }
        }
    }

    private fun renameSurface(action: QuoteAction.RenameSurface) {
        viewModelScope.launch {
            runCatching {
                projectRepository.renameSurface(action.surfaceId, action.name)
            }.onSuccess {
                showMessage("Surface renamed.")
            }.onFailure { error ->
                showError(error.message ?: "The surface could not be renamed.")
            }
        }
    }

    private fun deleteSurface(action: QuoteAction.DeleteSurface) {
        viewModelScope.launch {
            runCatching {
                projectRepository.deleteSurface(action.surfaceId)
            }.onSuccess {
                _uiState.update { it.copy(selectedSurfaceId = null) }
                showMessage("Surface removed from the quote.")
            }.onFailure { error ->
                showError(error.message ?: "The surface could not be removed.")
            }
        }
    }

    private fun setProjectTaxRate(rate: Double) {
        val projectId = _uiState.value.projectId ?: return
        if (rate !in 0.0..1.0) {
            showError("Tax rate must be between 0% and 100%.")
            return
        }
        val policy = _uiState.value.taxPolicy
        viewModelScope.launch {
            runCatching {
                projectRepository.updateProjectTaxSettings(
                    projectId = projectId,
                    taxRate = rate,
                    taxPolicy = policy
                )
            }.onFailure { error ->
                showError(error.message ?: "The tax rate could not be updated.")
            }
        }
    }

    private fun setProjectTaxPolicy(policy: TaxPolicy) {
        val projectId = _uiState.value.projectId ?: return
        val rate = _uiState.value.workspace?.project?.taxRate ?: 0.0
        viewModelScope.launch {
            runCatching {
                projectRepository.updateProjectTaxSettings(
                    projectId = projectId,
                    taxRate = rate,
                    taxPolicy = policy
                )
            }.onFailure { error ->
                showError(error.message ?: "The tax policy could not be updated.")
            }
        }
    }

    private fun openProjectDetails() {
        val project = _uiState.value.workspace?.project ?: return
        _uiState.update {
            it.copy(
                isProjectDetailsOpen = true,
                projectDetailsDraft = ProjectMetadataDraft(
                    name = project.name,
                    clientName = project.clientName,
                    contractorName = project.contractorName,
                    contractorContact = project.contractorContact,
                    contractorLicense = project.contractorLicense
                ),
                message = null
            )
        }
    }

    private fun saveProjectDetails() {
        val projectId = _uiState.value.projectId ?: return
        val draft = _uiState.value.projectDetailsDraft ?: return
        if (_uiState.value.isSavingProjectDetails) return
        if (draft.name.isBlank()) {
            showError("Project name cannot be blank.")
            return
        }

        _uiState.update { it.copy(isSavingProjectDetails = true, message = null) }
        viewModelScope.launch {
            runCatching {
                projectRepository.updateProjectMetadata(
                    projectId = projectId,
                    name = draft.name,
                    clientName = draft.clientName,
                    contractorName = draft.contractorName,
                    contractorContact = draft.contractorContact,
                    contractorLicense = draft.contractorLicense
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingProjectDetails = false,
                        isProjectDetailsOpen = false,
                        projectDetailsDraft = null,
                        message = QuoteMessage("Project and contractor details saved.")
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSavingProjectDetails = false) }
                showError(error.message ?: "Project details could not be saved.")
            }
        }
    }

    private fun exportPdf() {
        if (_uiState.value.isExportingPdf) return
        val workspace = _uiState.value.workspace ?: run {
            showError("Open a project before exporting a quote.")
            return
        }
        if (workspace.surfaces.isEmpty()) {
            showError("Measure at least one surface before exporting a quote.")
            return
        }
        if (workspace.surfaces.any { it.analysis == null || it.estimate == null }) {
            showError("Fix saved surfaces with missing geometry before exporting the quote.")
            return
        }
        val validityDays = _uiState.value.exportDraft.validityDays ?: run {
            showError("Quote validity must be a whole number from 1 to 365 days.")
            return
        }
        val options = PdfExportOptions(
            paperSize = _uiState.value.exportDraft.paperSize,
            validityDays = validityDays
        )

        _uiState.update { it.copy(isExportingPdf = true, message = null) }
        viewModelScope.launch {
            runCatching {
                quotePdfExporter.export(
                    workspace = workspace,
                    options = options
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isExportingPdf = false,
                        isExportDialogOpen = false,
                        exportedPdf = result,
                        message = QuoteMessage(
                            "PDF ready: ${result.pageCount} page" +
                                if (result.pageCount == 1) "." else "s."
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isExportingPdf = false) }
                showError(error.message ?: "The quote PDF could not be generated.")
            }
        }
    }

    private fun openCatalogEditor(materialId: String?) {
        val material = materialId?.let { id ->
            _uiState.value.workspace?.materials?.firstOrNull { it.id == id }
        }
        _uiState.update {
            it.copy(
                catalogEditor = material?.toCatalogEditorDraft()
                    ?: newCatalogEditorDraft(),
                message = null
            )
        }
    }

    private fun updateCatalogEditor(draft: CatalogEditorDraft) {
        val previous = _uiState.value.catalogEditor
        val normalized = if (previous != null && previous.tradeType != draft.tradeType) {
            draft.copy(
                primaryUnitType = defaultUnitType(draft.tradeType),
                wastePreset = defaultWastePreset(draft.tradeType),
                tradeParameters = defaultTradeParameters(draft.tradeType)
            )
        } else {
            draft
        }
        _uiState.update { it.copy(catalogEditor = normalized) }
    }

    private fun saveCatalogMaterial() {
        val draft = _uiState.value.catalogEditor ?: return
        val material = runCatching { draft.toMaterialSpec() }.getOrElse { error ->
            showError(error.message ?: "Check the catalog fields.")
            return
        }
        _uiState.update { it.copy(isSavingCatalog = true, message = null) }
        viewModelScope.launch {
            runCatching { materialCatalogRepository.save(material) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingCatalog = false,
                            catalogEditor = null,
                            message = QuoteMessage("${material.name} saved to the catalog.")
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSavingCatalog = false) }
                    showError(error.message ?: "The catalog material could not be saved.")
                }
        }
    }

    private fun deleteCatalogMaterial() {
        val draft = _uiState.value.catalogEditor ?: return
        if (draft.isNew) {
            _uiState.update { it.copy(catalogEditor = null) }
            return
        }
        _uiState.update { it.copy(isSavingCatalog = true, message = null) }
        viewModelScope.launch {
            runCatching { materialCatalogRepository.delete(draft.id) }
                .onSuccess { result ->
                    when (result) {
                        MaterialDeleteResult.Deleted -> _uiState.update {
                            it.copy(
                                isSavingCatalog = false,
                                catalogEditor = null,
                                message = QuoteMessage("${draft.name} removed from the catalog.")
                            )
                        }

                        is MaterialDeleteResult.InUse -> {
                            _uiState.update { it.copy(isSavingCatalog = false) }
                            showError(
                                "${draft.name} is assigned to ${result.surfaceCount} surface" +
                                    if (result.surfaceCount == 1) "." else "s."
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSavingCatalog = false) }
                    showError(error.message ?: "The catalog material could not be deleted.")
                }
        }
    }

    private fun surface(surfaceId: String): SurfaceQuoteLine? =
        _uiState.value.workspace?.surfaces?.firstOrNull { it.surfaceId == surfaceId }

    private fun newCatalogEditorDraft(): CatalogEditorDraft {
        val source = DefaultMaterialCatalog.porcelainTile300x600Straight
        return CatalogEditorDraft(
            id = "material-${idGenerator()}",
            name = "New material",
            sku = "",
            tradeType = source.tradeType,
            primaryUnitType = source.primaryUnitType,
            coverageText = source.boxCoverageSqMeters.toString(),
            unitCostText = "0.00",
            wastePreset = source.wastePreset,
            customWastePercentText = "10",
            laborRateText = "0.00",
            pricingAreaUnit = source.pricingAreaUnit,
            tradeParameters = source.tradeParameters,
            isNew = true
        )
    }

    private fun MaterialSpec.toCatalogEditorDraft() = CatalogEditorDraft(
        id = id,
        name = name,
        sku = sku,
        tradeType = tradeType,
        primaryUnitType = primaryUnitType,
        coverageText = boxCoverageSqMeters.toString(),
        unitCostText = unitCost.toString(),
        wastePreset = wastePreset,
        customWastePercentText = ((customWasteFactor ?: 0.10) * 100.0).toString(),
        laborRateText = laborRatePerUnitArea.toString(),
        pricingAreaUnit = pricingAreaUnit,
        tradeParameters = tradeParameters,
        isNew = false
    )

    private fun CatalogEditorDraft.toMaterialSpec(): MaterialSpec {
        val coverage = coverageText.toDoubleOrNull()
            ?: error("Coverage must be a number.")
        val unitCost = unitCostText.toDoubleOrNull()
            ?: error("Package cost must be a number.")
        val laborRate = laborRateText.toDoubleOrNull()
            ?: error("Labor rate must be a number.")
        val customWaste = if (wastePreset == WastePreset.CUSTOM) {
            val percent = customWastePercentText.toDoubleOrNull()
                ?: error("Custom waste must be a percentage.")
            percent / 100.0
        } else {
            null
        }
        return MaterialSpec(
            id = id,
            name = name.trim(),
            sku = sku.trim(),
            tradeType = tradeType,
            primaryUnitType = primaryUnitType,
            boxCoverageSqMeters = coverage,
            unitCost = unitCost,
            wastePreset = wastePreset,
            customWasteFactor = customWaste,
            laborRatePerUnitArea = laborRate,
            pricingAreaUnit = pricingAreaUnit,
            tradeParameters = tradeParameters
        )
    }

    private fun defaultUnitType(tradeType: TradeType): PackageUnitType = when (tradeType) {
        TradeType.TILING,
        TradeType.FLOORING -> PackageUnitType.BOX

        TradeType.PAINTING -> PackageUnitType.GALLON
        TradeType.DRYWALL -> PackageUnitType.SHEET
    }

    private fun defaultWastePreset(tradeType: TradeType): WastePreset = when (tradeType) {
        TradeType.TILING -> WastePreset.STRAIGHT_LAY
        TradeType.FLOORING -> WastePreset.STRAIGHT_LAY
        TradeType.PAINTING,
        TradeType.DRYWALL -> WastePreset.PAINT_OR_DRYWALL
    }

    private fun defaultTradeParameters(tradeType: TradeType): TradeParameters = when (tradeType) {
        TradeType.TILING -> TradeParameters.Tiling(tileWidthMm = 300.0, tileHeightMm = 600.0)
        TradeType.FLOORING -> TradeParameters.Flooring()
        TradeType.PAINTING -> TradeParameters.Painting()
        TradeType.DRYWALL -> TradeParameters.Drywall()
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = QuoteMessage(text)) }
    }

    private fun showError(text: String) {
        _uiState.update { it.copy(message = QuoteMessage(text, isError = true)) }
    }

    class Factory(
        private val projectRepository: ProjectRepository,
        private val materialCatalogRepository: MaterialCatalogRepository,
        private val quotePdfExporter: QuotePdfExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(QuoteViewModel::class.java))
            return QuoteViewModel(
                projectRepository = projectRepository,
                materialCatalogRepository = materialCatalogRepository,
                quotePdfExporter = quotePdfExporter
            ) as T
        }
    }
}
