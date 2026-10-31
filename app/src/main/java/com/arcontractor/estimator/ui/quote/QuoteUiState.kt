package com.arcontractor.estimator.ui.quote

import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
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

data class QuoteUiState(
    val isLoading: Boolean = true,
    val isSavingMeasurement: Boolean = false,
    val isSavingCatalog: Boolean = false,
    val projectId: String? = null,
    val workspace: ProjectWorkspace? = null,
    val selectedSurfaceId: String? = null,
    val isCatalogOpen: Boolean = false,
    val catalogEditor: CatalogEditorDraft? = null,
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
    data object OpenCatalog : QuoteAction
    data object CloseCatalog : QuoteAction
    data class EditCatalogMaterial(val materialId: String?) : QuoteAction
    data object CloseCatalogEditor : QuoteAction
    data class UpdateCatalogEditor(val draft: CatalogEditorDraft) : QuoteAction
    data object SaveCatalogMaterial : QuoteAction
    data object DeleteCatalogMaterial : QuoteAction
    data object DismissMessage : QuoteAction
}
