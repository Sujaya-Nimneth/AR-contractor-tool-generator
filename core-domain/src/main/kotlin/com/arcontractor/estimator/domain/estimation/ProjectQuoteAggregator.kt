package com.arcontractor.estimator.domain.estimation

import com.arcontractor.estimator.domain.geometry.SurfaceGeometryMetrics
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Input pairing a named room surface (e.g., "Master Bath North Wall") with its measured geometry and material spec.
 */
data class ProjectSurfaceInput(
    val surfaceId: String,
    val surfaceName: String,
    val geometry: SurfaceGeometryMetrics,
    val materialSpec: MaterialSpec
)

data class EvaluatedProjectSurface(
    val surfaceId: String,
    val surfaceName: String,
    val estimate: SurfaceEstimate
)

/**
 * Aggregated multi-room / multi-surface contractor quote ready for UI display and Phase 4 PDF rendering.
 */
data class ProjectQuoteSummary(
    val projectName: String,
    val surfaces: List<EvaluatedProjectSurface>,
    val totalGrossAreaSqMeters: Double,
    val totalCutoutAreaSqMeters: Double,
    val totalNetAreaSqMeters: Double,
    val materialSubtotal: BigDecimal,
    val consumablesSubtotal: BigDecimal,
    val laborSubtotal: BigDecimal,
    val subtotalBeforeTax: BigDecimal,
    val taxRate: Double,
    val taxPolicy: TaxPolicy,
    val taxAmount: BigDecimal,
    val grandTotal: BigDecimal
)

/**
 * Aggregates multiple measured surfaces into a unified project-level financial quote.
 */
object ProjectQuoteAggregator {

    fun aggregateProject(
        projectName: String,
        surfaces: List<ProjectSurfaceInput>,
        taxRate: Double = 0.0,
        taxPolicy: TaxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL
    ): ProjectQuoteSummary {
        require(taxRate in 0.0..1.0) { "taxRate must be in [0.0, 1.0], got $taxRate" }

        val evaluated = surfaces.map { input ->
            EvaluatedProjectSurface(
                surfaceId = input.surfaceId,
                surfaceName = input.surfaceName,
                estimate = MaterialEstimationEngine.estimateSurface(
                    geometry = input.geometry,
                    materialSpec = input.materialSpec,
                    taxRate = taxRate,
                    taxPolicy = taxPolicy
                )
            )
        }

        val totalGross = evaluated.sumOf { it.estimate.grossAreaSqMeters }
        val totalCutout = evaluated.sumOf { it.estimate.cutoutAreaSqMeters }
        val totalNet = evaluated.sumOf { it.estimate.netAreaSqMeters }

        val matSub = evaluated.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.materialSubtotal }
            .setScale(2, RoundingMode.HALF_UP)
        val consSub = evaluated.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.consumablesSubtotal }
            .setScale(2, RoundingMode.HALF_UP)
        val labSub = evaluated.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.laborSubtotal }
            .setScale(2, RoundingMode.HALF_UP)

        val subtotal = matSub.add(consSub).add(labSub).setScale(2, RoundingMode.HALF_UP)

        val taxableBase = when (taxPolicy) {
            TaxPolicy.TAX_TOTAL_SUBTOTAL -> subtotal
            TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY -> matSub.add(consSub).setScale(2, RoundingMode.HALF_UP)
        }
        val taxAmount = taxableBase.multiply(BigDecimal.valueOf(taxRate)).setScale(2, RoundingMode.HALF_UP)
        val grandTotal = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP)

        return ProjectQuoteSummary(
            projectName = projectName,
            surfaces = evaluated,
            totalGrossAreaSqMeters = totalGross,
            totalCutoutAreaSqMeters = totalCutout,
            totalNetAreaSqMeters = totalNet,
            materialSubtotal = matSub,
            consumablesSubtotal = consSub,
            laborSubtotal = labSub,
            subtotalBeforeTax = subtotal,
            taxRate = taxRate,
            taxPolicy = taxPolicy,
            taxAmount = taxAmount,
            grandTotal = grandTotal
        )
    }
}

/**
 * Curated offline starter catalog of contractor material specifications across all 4 trades.
 * Reused in Phase 3 to seed the Room SQLite database on first launch.
 */
object DefaultMaterialCatalog {

    val porcelainTile300x600Straight = MaterialSpec(
        id = "tile-porcelain-300x600",
        name = "Matte Porcelain Field Tile 300×600mm",
        sku = "TL-POR-3060",
        tradeType = TradeType.TILING,
        primaryUnitType = PackageUnitType.BOX,
        boxCoverageSqMeters = 1.44, // 8 tiles per box
        unitCost = 46.80,
        wastePreset = WastePreset.STRAIGHT_LAY,
        laborRatePerUnitArea = 55.00,
        pricingAreaUnit = PricingAreaUnit.SQ_METER,
        tradeParameters = TradeParameters.Tiling(
            tileWidthMm = 300.0,
            tileHeightMm = 600.0,
            tileThicknessMm = 9.5,
            jointWidthMm = 3.0
        )
    )

    val herringboneSubwayTile75x300 = MaterialSpec(
        id = "tile-subway-herringbone-75x300",
        name = "Artisan Ceramic Subway Tile 75×300mm",
        sku = "TL-SUB-0730",
        tradeType = TradeType.TILING,
        primaryUnitType = PackageUnitType.BOX,
        boxCoverageSqMeters = 0.99, // 44 tiles per box
        unitCost = 38.50,
        wastePreset = WastePreset.DIAGONAL_OR_HERRINGBONE,
        laborRatePerUnitArea = 72.00,
        pricingAreaUnit = PricingAreaUnit.SQ_METER,
        tradeParameters = TradeParameters.Tiling(
            tileWidthMm = 75.0,
            tileHeightMm = 300.0,
            tileThicknessMm = 8.0,
            jointWidthMm = 2.0
        )
    )

    val oakLuxuryVinylPlank = MaterialSpec(
        id = "floor-lvp-oak-20mil",
        name = "White Oak Rigid Core LVP (20 mil Wear Layer)",
        sku = "FL-LVP-OAK20",
        tradeType = TradeType.FLOORING,
        primaryUnitType = PackageUnitType.BOX,
        boxCoverageSqMeters = 2.20, // ~23.68 sq ft per box
        unitCost = 64.90,
        wastePreset = WastePreset.STRAIGHT_LAY,
        laborRatePerUnitArea = 32.00,
        pricingAreaUnit = PricingAreaUnit.SQ_METER,
        tradeParameters = TradeParameters.Flooring()
    )

    val acrylicEggshellInteriorPaint = MaterialSpec(
        id = "paint-acrylic-eggshell",
        name = "Zero-VOC Washable Acrylic Eggshell Paint",
        sku = "PT-ACR-EGG1G",
        tradeType = TradeType.PAINTING,
        primaryUnitType = PackageUnitType.GALLON,
        boxCoverageSqMeters = 32.52, // ~350 sq ft per gallon per coat
        unitCost = 54.00,
        wastePreset = WastePreset.PAINT_OR_DRYWALL,
        laborRatePerUnitArea = 14.50,
        pricingAreaUnit = PricingAreaUnit.SQ_METER,
        tradeParameters = TradeParameters.Painting(
            finishCoatCount = 2,
            primerCoatCount = 1
        )
    )

    val standardHalfInchDrywall4x8 = MaterialSpec(
        id = "drywall-gypsum-4x8-12mm",
        name = "1/2\" Lightweight Gypsum Drywall Sheet (4'×8')",
        sku = "DW-GYP-4812",
        tradeType = TradeType.DRYWALL,
        primaryUnitType = PackageUnitType.SHEET,
        boxCoverageSqMeters = 2.9729, // 1.2192m x 2.4384m
        unitCost = 16.75,
        wastePreset = WastePreset.PAINT_OR_DRYWALL,
        laborRatePerUnitArea = 28.00,
        pricingAreaUnit = PricingAreaUnit.SQ_METER,
        tradeParameters = TradeParameters.Drywall()
    )

    fun allPresets(): List<MaterialSpec> = listOf(
        porcelainTile300x600Straight,
        herringboneSubwayTile75x300,
        oakLuxuryVinylPlank,
        acrylicEggshellInteriorPaint,
        standardHalfInchDrywall4x8
    )
}
