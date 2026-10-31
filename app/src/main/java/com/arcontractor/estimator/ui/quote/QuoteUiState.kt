package com.arcontractor.estimator.ui.quote

import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.data.pdf.PdfExportResult
import com.arcontractor.estimator.data.pdf.PdfPaperSize
import com.arcontractor.estimator.domain.estimation.PackageUnitType
import com.arcontractor.estimator.domain.estimation.PricingAreaUnit
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.estimation.TradeParameters
import com.arcontractor.estimator.domain.estimation.TradeType
import com.arcontractor.estimator.domain.estimation.WastePreset
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.Vec3

data class MeasurementCapture(
    val outerVertices: List<Vec3>,
    val referenceNormal: Vec3?,
    val cutouts: List<CutoutPolygonInput>,
    val name: String? = null
)

data class QuoteMessage(
    val text: String,
    val isError: Boolean = false
)

data class CatalogEditorDraft(
    val id: String,
    val name: String,
    val sku: String,
    val tradeType: TradeType,
    val primaryUnitType: PackageUnitType,
    val coverageText: String,
    val unitCostText: String,
    val wastePreset: WastePreset,
    val customWastePercentText: String,
    val laborRateText: String,
    val pricingAreaUnit: PricingAreaUnit,
    val tradeParameters: TradeParameters,
    val isNew: Boolean
)

data class ProjectMetadataDraft(
    val name: String,
    val clientName: String,
    val contractorName: String,
    val contractorContact: String,
    val contractorLicense: String
)

data class QuoteExportDraft(
    val paperSize: PdfPaperSize = PdfPaperSize.A4,
    val validityDaysText: String = "30"
) {
    val validityDays: Int?
        get() = validityDaysText.toIntOrNull()?.takeIf { it in 1..365 }
}

data class QuoteUiState(
    val isLoading: Boolean = true,
    val isSavingMeasurement: Boolean = false,
    val isSavingCatalog: Boolean = false,
    val isSavingProjectDetails: Boolean = false,
    val isExportingPdf: Boolean = false,
    val projectId: String? = null,
    val workspace: ProjectWorkspace? = null,
    val selectedSurfaceId: String? = null,
    val isCatalogOpen: Boolean = false,
    val catalogEditor: CatalogEditorDraft? = null,
    val isProjectDetailsOpen: Boolean = false,
    val projectDetailsDraft: ProjectMetadataDraft? = null,
    val isExportDialogOpen: Boolean = false,
    val exportDraft: QuoteExportDraft = QuoteExportDraft(),
    val exportedPdf: PdfExportResult? = null,
    val message: QuoteMessage? = null
) {
    val selectedSurface: SurfaceQuoteLine?
        get() = workspace?.surfaces?.firstOrNull { it.surfaceId == selectedSurfaceId }

    val taxPolicy: TaxPolicy
        get() = workspace?.quote?.taxPolicy ?: TaxPolicy.TAX_TOTAL_SUBTOTAL
}

sealed interface QuoteAction {
    data object Retry : QuoteAction
    data class SaveMeasurement(val capture: MeasurementCapture) : QuoteAction
    data class SelectSurface(val surfaceId: String) : QuoteAction
    data class SetSurfaceMaterial(val surfaceId: String, val materialId: String) : QuoteAction
    data class SetSurfaceWastePreset(val surfaceId: String, val preset: WastePreset) : QuoteAction
    data class SetSurfaceWasteFactor(val surfaceId: String, val factor: Double) : QuoteAction
    data class SetSurfaceLaborRate(val surfaceId: String, val rate: Double) : QuoteAction
    data class RenameSurface(val surfaceId: String, val name: String) : QuoteAction
    data class DeleteSurface(val surfaceId: String) : QuoteAction
    data class SetProjectTaxRate(val rate: Double) : QuoteAction
    data class SetProjectTaxPolicy(val policy: TaxPolicy) : QuoteAction
    data object OpenProjectDetails : QuoteAction
    data object CloseProjectDetails : QuoteAction
    data class UpdateProjectDetails(val draft: ProjectMetadataDraft) : QuoteAction
    data object SaveProjectDetails : QuoteAction
    data object OpenExportDialog : QuoteAction
    data object CloseExportDialog : QuoteAction
    data class SetExportPaperSize(val paperSize: PdfPaperSize) : QuoteAction
    data class SetExportValidityDays(val text: String) : QuoteAction
    data object ExportPdf : QuoteAction
    data object ConsumeExportedPdf : QuoteAction
    data object OpenCatalog : QuoteAction
    data object CloseCatalog : QuoteAction
    data class EditCatalogMaterial(val materialId: String?) : QuoteAction
    data object CloseCatalogEditor : QuoteAction
    data class UpdateCatalogEditor(val draft: CatalogEditorDraft) : QuoteAction
    data object SaveCatalogMaterial : QuoteAction
    data object DeleteCatalogMaterial : QuoteAction
    data object DismissMessage : QuoteAction
}
