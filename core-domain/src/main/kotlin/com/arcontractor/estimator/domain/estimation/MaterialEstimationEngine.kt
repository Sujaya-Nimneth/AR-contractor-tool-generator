package com.arcontractor.estimator.domain.estimation

import com.arcontractor.estimator.domain.geometry.SurfaceGeometryMetrics
import com.arcontractor.estimator.domain.geometry.UnitConverter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure Kotlin Material, Waste & Trade Consumables Estimation Engine.
 *
 * Computes exact package quantities, trade-specific consumables (volumetric grout, trowel-scaled thinset,
 * primer/paint coats, underlayment/baseboards, drywall mud/tape/screws/beads), and cent-accurate
 * `BigDecimal` itemized totals.
 */
object MaterialEstimationEngine {

    private const val ZERO_AREA_EPSILON = 1e-7

    /**
     * Generates a complete [SurfaceEstimate] from measured [geometry] and a contractor [materialSpec].
     *
     * @param geometry Surface geometry (Net Area, Gross Area, Outer Perimeter, Cutout Perimeter).
     * @param materialSpec Selected material catalog specification & trade parameters.
     * @param taxRate Fractional tax rate in `[0.0, 1.0]` (e.g., `0.0825` for `8.25%`).
     * @param taxPolicy Determines whether labor is taxed alongside materials and consumables.
     */
    fun estimateSurface(
        geometry: SurfaceGeometryMetrics,
        materialSpec: MaterialSpec,
        taxRate: Double = 0.0,
        taxPolicy: TaxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL
    ): SurfaceEstimate {
        require(taxRate in 0.0..1.0) { "taxRate must be in [0.0, 1.0], got $taxRate" }

        val netArea = max(0.0, geometry.netAreaSqMeters)
        val grossArea = max(netArea, geometry.grossAreaSqMeters)
        val cutoutArea = max(0.0, geometry.cutoutAreaSqMeters)
        val outerPerimeter = max(0.0, geometry.outerPerimeterMeters)
        val cutoutPerimeter = max(0.0, geometry.cutoutPerimeterMeters)
        val wasteFactor = materialSpec.effectiveWasteFactor

        if (netArea <= ZERO_AREA_EPSILON) {
            val zero = CurrencyMath.money(0.0)
            return SurfaceEstimate(
                materialSpec = materialSpec,
                grossAreaSqMeters = grossArea,
                cutoutAreaSqMeters = cutoutArea,
                netAreaSqMeters = 0.0,
                outerPerimeterMeters = outerPerimeter,
                cutoutPerimeterMeters = cutoutPerimeter,
                wasteFactor = wasteFactor,
                grossMaterialAreaSqMeters = 0.0,
                boxesRequired = 0,
                individualPiecesRequired = if (materialSpec.tradeParameters is TradeParameters.Tiling) 0 else null,
                purchasedCoverageSqMeters = 0.0,
                consumables = emptyList(),
                materialSubtotal = zero,
                consumablesSubtotal = zero,
                laborSubtotal = zero,
                subtotalBeforeTax = zero,
                taxRate = taxRate,
                taxPolicy = taxPolicy,
                taxAmount = zero,
                grandTotal = zero,
                lineItems = emptyList()
            )
        }

        // Core material waste formula: grossMaterialArea = netArea * (1.0 + wasteFactor)
        val grossMaterialArea = netArea * (1.0 + wasteFactor)

        // For multi-coat painting, boxCoverageSqMeters represents single-coat spread rate per gallon;
        // for single-layer materials (tile boxes, flooring boxes, drywall sheets), coatMultiplier == 1.
        val coatMultiplier = when (val params = materialSpec.tradeParameters) {
            is TradeParameters.Painting -> params.finishCoatCount
            else -> 1
        }
        val totalCoatedMaterialArea = grossMaterialArea * coatMultiplier.toDouble()
        val boxesRequired = ceil(totalCoatedMaterialArea / materialSpec.boxCoverageSqMeters).toInt()
        val purchasedCoverage = (boxesRequired.toDouble() * materialSpec.boxCoverageSqMeters) / coatMultiplier.toDouble()

        val individualPieces: Int? = when (val params = materialSpec.tradeParameters) {
            is TradeParameters.Tiling -> ceil(grossMaterialArea / params.singleTileAreaSqMeters).toInt()
            else -> null
        }

        val consumables = estimateConsumables(
            params = materialSpec.tradeParameters,
            netAreaSqMeters = netArea,
            grossMaterialAreaSqMeters = grossMaterialArea,
            outerPerimeterMeters = outerPerimeter,
            cutoutPerimeterMeters = cutoutPerimeter
        )

        val materialSubtotal = CurrencyMath.multiplyQty(boxesRequired, materialSpec.unitCost)
        val consumablesSubtotal = consumables.fold(BigDecimal.ZERO) { acc, item -> acc + item.totalCost }
            .setScale(2, RoundingMode.HALF_UP)

        val laborAreaInPricingUnit = when (materialSpec.pricingAreaUnit) {
            PricingAreaUnit.SQ_METER -> netArea
            PricingAreaUnit.SQ_FOOT -> UnitConverter.sqMetersToSqFeet(netArea)
        }
        val laborSubtotal = CurrencyMath.multiplyRate(laborAreaInPricingUnit, materialSpec.laborRatePerUnitArea)

        val subtotalBeforeTax = materialSubtotal
            .add(consumablesSubtotal)
            .add(laborSubtotal)
            .setScale(2, RoundingMode.HALF_UP)

        val taxableBase = when (taxPolicy) {
            TaxPolicy.TAX_TOTAL_SUBTOTAL -> subtotalBeforeTax
            TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY ->
                materialSubtotal.add(consumablesSubtotal).setScale(2, RoundingMode.HALF_UP)
        }

        val taxAmount = taxableBase
            .multiply(BigDecimal.valueOf(taxRate))
            .setScale(2, RoundingMode.HALF_UP)

        val grandTotal = subtotalBeforeTax
            .add(taxAmount)
            .setScale(2, RoundingMode.HALF_UP)

        val lineItems = buildLineItems(
            materialSpec = materialSpec,
            boxesRequired = boxesRequired,
            coatMultiplier = coatMultiplier,
            materialSubtotal = materialSubtotal,
            consumables = consumables,
            laborAreaInPricingUnit = laborAreaInPricingUnit,
            laborSubtotal = laborSubtotal
        )

        return SurfaceEstimate(
            materialSpec = materialSpec,
            grossAreaSqMeters = grossArea,
            cutoutAreaSqMeters = cutoutArea,
            netAreaSqMeters = netArea,
            outerPerimeterMeters = outerPerimeter,
            cutoutPerimeterMeters = cutoutPerimeter,
            wasteFactor = wasteFactor,
            grossMaterialAreaSqMeters = grossMaterialArea,
            boxesRequired = boxesRequired,
            individualPiecesRequired = individualPieces,
            purchasedCoverageSqMeters = purchasedCoverage,
            consumables = consumables,
            materialSubtotal = materialSubtotal,
            consumablesSubtotal = consumablesSubtotal,
            laborSubtotal = laborSubtotal,
            subtotalBeforeTax = subtotalBeforeTax,
            taxRate = taxRate,
            taxPolicy = taxPolicy,
            taxAmount = taxAmount,
            grandTotal = grandTotal,
            lineItems = lineItems
        )
    }

    private fun estimateConsumables(
        params: TradeParameters,
        netAreaSqMeters: Double,
        grossMaterialAreaSqMeters: Double,
        outerPerimeterMeters: Double,
        cutoutPerimeterMeters: Double
    ): List<ConsumableEstimate> = when (params) {
        is TradeParameters.Tiling -> estimateTilingConsumables(
            params = params,
            netAreaSqMeters = netAreaSqMeters,
            grossMaterialAreaSqMeters = grossMaterialAreaSqMeters,
            cutoutPerimeterMeters = cutoutPerimeterMeters
        )
        is TradeParameters.Flooring -> estimateFlooringConsumables(
            params = params,
            grossMaterialAreaSqMeters = grossMaterialAreaSqMeters,
            outerPerimeterMeters = outerPerimeterMeters
        )
        is TradeParameters.Painting -> estimatePaintingConsumables(
            params = params,
            grossMaterialAreaSqMeters = grossMaterialAreaSqMeters,
            outerPerimeterMeters = outerPerimeterMeters,
            cutoutPerimeterMeters = cutoutPerimeterMeters
        )
        is TradeParameters.Drywall -> estimateDrywallConsumables(
            params = params,
            grossMaterialAreaSqMeters = grossMaterialAreaSqMeters,
            outerPerimeterMeters = outerPerimeterMeters,
            cutoutPerimeterMeters = cutoutPerimeterMeters
        )
    }

    private fun estimateTilingConsumables(
        params: TradeParameters.Tiling,
        netAreaSqMeters: Double,
        grossMaterialAreaSqMeters: Double,
        cutoutPerimeterMeters: Double
    ): List<ConsumableEstimate> {
        val items = ArrayList<ConsumableEstimate>(3)

        // 1. Thinset Mortar Bags scaled by tile max dimension (trowel notch + back-butter requirement)
        val maxDimMm = max(params.tileWidthMm, params.tileHeightMm)
        val baseCoverageFor50LbBag = when {
            maxDimMm <= 200.0 -> 5.5 // Mosaic / small format (1/4" x 1/4" notch)
            maxDimMm <= 380.0 -> 4.2 // Medium format (1/4" x 3/8" notch)
            else -> 2.8              // Large format >= 15" (1/2" x 1/2" notch + back-buttering)
        }
        val effectiveThinsetCoverage = params.thinsetCoverageOverrideSqMetersPerBag
            ?: (baseCoverageFor50LbBag * (params.thinsetBagWeightKg / 22.68))

        val thinsetBags = max(1, ceil(grossMaterialAreaSqMeters / effectiveThinsetCoverage).toInt())
        items.add(
            ConsumableEstimate(
                name = String.format(Locale.US, "Polymer-Modified Thinset Mortar (%.1f kg)", params.thinsetBagWeightKg),
                calculationBasis = String.format(Locale.US, "%.2f m²/bag trowel rate", effectiveThinsetCoverage),
                quantityRequired = thinsetBags,
                unitType = PackageUnitType.UNIT,
                rawAmountEstimate = thinsetBags * params.thinsetBagWeightKg,
                rawAmountUnit = "kg",
                unitCost = CurrencyMath.money(params.thinsetBagUnitCost),
                totalCost = CurrencyMath.multiplyQty(thinsetBags, params.thinsetBagUnitCost)
            )
        )

        // 2. Volumetric Cementitious Grout Bags
        if (params.jointWidthMm > 0.0) {
            val l = params.tileHeightMm
            val w = params.tileWidthMm
            val jw = params.jointWidthMm
            val jd = params.tileThicknessMm
            val groutKgPerSqMeter = ((l + w) / ((l + jw) * (w + jw))) * jw * jd * params.groutDensityGramsPerCm3
            val totalGroutKg = netAreaSqMeters * groutKgPerSqMeter * (1.0 + params.groutMixingWasteFactor)
            val groutBags = max(1, ceil(totalGroutKg / params.groutBagWeightKg).toInt())

            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Sanded/FA Tile Grout (%.2f kg)", params.groutBagWeightKg),
                    calculationBasis = String.format(
                        Locale.US,
                        "%.0fx%.0fmm tile, %.1fmm joint (%.2f kg est.)",
                        params.tileWidthMm,
                        params.tileHeightMm,
                        params.jointWidthMm,
                        totalGroutKg
                    ),
                    quantityRequired = groutBags,
                    unitType = PackageUnitType.UNIT,
                    rawAmountEstimate = totalGroutKg,
                    rawAmountUnit = "kg",
                    unitCost = CurrencyMath.money(params.groutBagUnitCost),
                    totalCost = CurrencyMath.multiplyQty(groutBags, params.groutBagUnitCost)
                )
            )
        }

        // 3. Cutout Edge Trim / Bullnose Profiles (preserved cutout perimeter!)
        if (params.includeCutoutEdgeTrim && cutoutPerimeterMeters > 1e-4) {
            val trimMetersWithMiterWaste = cutoutPerimeterMeters * 1.10
            val trimStrips = ceil(trimMetersWithMiterWaste / params.edgeTrimStripLengthMeters).toInt()
            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Aluminum Tile Edge Trim Profile (%.2f m)", params.edgeTrimStripLengthMeters),
                    calculationBasis = String.format(Locale.US, "%.2f m cutout perimeter + 10%% miter waste", cutoutPerimeterMeters),
                    quantityRequired = trimStrips,
                    unitType = PackageUnitType.STRIP,
                    rawAmountEstimate = trimMetersWithMiterWaste,
                    rawAmountUnit = "m",
                    unitCost = CurrencyMath.money(params.edgeTrimStripUnitCost),
                    totalCost = CurrencyMath.multiplyQty(trimStrips, params.edgeTrimStripUnitCost)
                )
            )
        }

        return items
    }

    private fun estimateFlooringConsumables(
        params: TradeParameters.Flooring,
        grossMaterialAreaSqMeters: Double,
        outerPerimeterMeters: Double
    ): List<ConsumableEstimate> {
        val items = ArrayList<ConsumableEstimate>(2)

        if (params.includeUnderlayment) {
            val rolls = max(1, ceil(grossMaterialAreaSqMeters / params.underlaymentRollCoverageSqMeters).toInt())
            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Acoustic Moisture Underlayment Roll (%.2f m²)", params.underlaymentRollCoverageSqMeters),
                    calculationBasis = String.format(Locale.US, "%.2f m² gross coverage", grossMaterialAreaSqMeters),
                    quantityRequired = rolls,
                    unitType = PackageUnitType.ROLL,
                    rawAmountEstimate = grossMaterialAreaSqMeters,
                    rawAmountUnit = "m²",
                    unitCost = CurrencyMath.money(params.underlaymentRollUnitCost),
                    totalCost = CurrencyMath.multiplyQty(rolls, params.underlaymentRollUnitCost)
                )
            )
        }

        if (params.includePerimeterBaseboard && outerPerimeterMeters > 1e-4) {
            val linearMeters = outerPerimeterMeters * (1.0 + params.baseboardMiterWasteFactor)
            val pieces = ceil(linearMeters / params.baseboardPieceLengthMeters).toInt()
            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Perimeter Baseboard / Quarter-Round (%.2f m)", params.baseboardPieceLengthMeters),
                    calculationBasis = String.format(Locale.US, "%.2f m room perimeter + %.0f%% waste", outerPerimeterMeters, params.baseboardMiterWasteFactor * 100.0),
                    quantityRequired = pieces,
                    unitType = PackageUnitType.STRIP,
                    rawAmountEstimate = linearMeters,
                    rawAmountUnit = "m",
                    unitCost = CurrencyMath.money(params.baseboardPieceUnitCost),
                    totalCost = CurrencyMath.multiplyQty(pieces, params.baseboardPieceUnitCost)
                )
            )
        }

        return items
    }

    private fun estimatePaintingConsumables(
        params: TradeParameters.Painting,
        grossMaterialAreaSqMeters: Double,
        outerPerimeterMeters: Double,
        cutoutPerimeterMeters: Double
    ): List<ConsumableEstimate> {
        val items = ArrayList<ConsumableEstimate>(2)

        if (params.primerCoatCount > 0) {
            val totalPrimerArea = grossMaterialAreaSqMeters * params.primerCoatCount.toDouble()
            val primerGallons = max(1, ceil(totalPrimerArea / params.primerSpreadRateSqMetersPerGallon).toInt())
            items.add(
                ConsumableEstimate(
                    name = "High-Hide Interior Sealer Primer",
                    calculationBasis = String.format(
                        Locale.US,
                        "%d coat(s) @ %.1f m²/gal",
                        params.primerCoatCount,
                        params.primerSpreadRateSqMetersPerGallon
                    ),
                    quantityRequired = primerGallons,
                    unitType = PackageUnitType.GALLON,
                    rawAmountEstimate = totalPrimerArea / params.primerSpreadRateSqMetersPerGallon,
                    rawAmountUnit = "gal",
                    unitCost = CurrencyMath.money(params.primerGallonUnitCost),
                    totalCost = CurrencyMath.multiplyQty(primerGallons, params.primerGallonUnitCost)
                )
            )
        }

        val totalTrimPerimeter = outerPerimeterMeters + cutoutPerimeterMeters
        if (params.includePaintersTape && totalTrimPerimeter > 1e-4) {
            val tapeRolls = max(1, ceil(totalTrimPerimeter / params.tapeRollLengthMeters).toInt())
            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Pro Edge Painter's Masking Tape (%.1f m)", params.tapeRollLengthMeters),
                    calculationBasis = String.format(Locale.US, "%.2f m total outer + cutout perimeter", totalTrimPerimeter),
                    quantityRequired = tapeRolls,
                    unitType = PackageUnitType.ROLL,
                    rawAmountEstimate = totalTrimPerimeter,
                    rawAmountUnit = "m",
                    unitCost = CurrencyMath.money(params.tapeRollUnitCost),
                    totalCost = CurrencyMath.multiplyQty(tapeRolls, params.tapeRollUnitCost)
                )
            )
        }

        return items
    }

    private fun estimateDrywallConsumables(
        params: TradeParameters.Drywall,
        grossMaterialAreaSqMeters: Double,
        outerPerimeterMeters: Double,
        cutoutPerimeterMeters: Double
    ): List<ConsumableEstimate> {
        val items = ArrayList<ConsumableEstimate>(4)

        val mudBuckets = max(1, ceil(grossMaterialAreaSqMeters / params.jointCompoundBucketCoverageSqMeters).toInt())
        items.add(
            ConsumableEstimate(
                name = "All-Purpose Joint Compound Bucket (5 gal)",
                calculationBasis = String.format(Locale.US, "3-coat finish @ %.1f m²/bucket", params.jointCompoundBucketCoverageSqMeters),
                quantityRequired = mudBuckets,
                unitType = PackageUnitType.BUCKET,
                rawAmountEstimate = grossMaterialAreaSqMeters / params.jointCompoundBucketCoverageSqMeters,
                rawAmountUnit = "buckets",
                unitCost = CurrencyMath.money(params.jointCompoundBucketUnitCost),
                totalCost = CurrencyMath.multiplyQty(mudBuckets, params.jointCompoundBucketUnitCost)
            )
        )

        val seamLengthMeters = grossMaterialAreaSqMeters * 1.5 + outerPerimeterMeters * 0.5
        val tapeRolls = max(1, ceil(seamLengthMeters / params.jointTapeRollLengthMeters).toInt())
        items.add(
            ConsumableEstimate(
                name = String.format(Locale.US, "Drywall Paper Joint Tape Roll (%.1f m)", params.jointTapeRollLengthMeters),
                calculationBasis = String.format(Locale.US, "%.1f m estimated board seams", seamLengthMeters),
                quantityRequired = tapeRolls,
                unitType = PackageUnitType.ROLL,
                rawAmountEstimate = seamLengthMeters,
                rawAmountUnit = "m",
                unitCost = CurrencyMath.money(params.jointTapeRollUnitCost),
                totalCost = CurrencyMath.multiplyQty(tapeRolls, params.jointTapeRollUnitCost)
            )
        )

        val totalScrews = ceil(grossMaterialAreaSqMeters * params.screwsPerSqMeter).toInt()
        val screwBoxes = max(1, ceil(totalScrews.toDouble() / params.screwsPerBox.toDouble()).toInt())
        items.add(
            ConsumableEstimate(
                name = "Coarse-Thread Drywall Screws (${params.screwsPerBox}/box)",
                calculationBasis = "$totalScrews screws estimated",
                quantityRequired = screwBoxes,
                unitType = PackageUnitType.BOX,
                rawAmountEstimate = totalScrews.toDouble(),
                rawAmountUnit = "screws",
                unitCost = CurrencyMath.money(params.screwBoxUnitCost),
                totalCost = CurrencyMath.multiplyQty(screwBoxes, params.screwBoxUnitCost)
            )
        )

        if (params.includeCutoutJTrim && cutoutPerimeterMeters > 1e-4) {
            val beadMeters = cutoutPerimeterMeters * 1.05
            val beadStrips = ceil(beadMeters / params.jTrimStripLengthMeters).toInt()
            items.add(
                ConsumableEstimate(
                    name = String.format(Locale.US, "Vinyl/Metal Cutout J-Bead Trim (%.2f m)", params.jTrimStripLengthMeters),
                    calculationBasis = String.format(Locale.US, "%.2f m opening perimeter + 5%% waste", cutoutPerimeterMeters),
                    quantityRequired = beadStrips,
                    unitType = PackageUnitType.STRIP,
                    rawAmountEstimate = beadMeters,
                    rawAmountUnit = "m",
                    unitCost = CurrencyMath.money(params.jTrimStripUnitCost),
                    totalCost = CurrencyMath.multiplyQty(beadStrips, params.jTrimStripUnitCost)
                )
            )
        }

        return items
    }

    private fun buildLineItems(
        materialSpec: MaterialSpec,
        boxesRequired: Int,
        coatMultiplier: Int,
        materialSubtotal: BigDecimal,
        consumables: List<ConsumableEstimate>,
        laborAreaInPricingUnit: Double,
        laborSubtotal: BigDecimal
    ): List<QuoteLineItem> {
        val items = ArrayList<QuoteLineItem>(consumables.size + 2)

        val unitLabel = if (boxesRequired == 1) {
            materialSpec.primaryUnitType.singular
        } else {
            materialSpec.primaryUnitType.plural
        }
        val coatNote = if (coatMultiplier > 1) " ($coatMultiplier coats)" else ""
        val wastePercent = String.format(Locale.US, "%.0f%% waste", materialSpec.effectiveWasteFactor * 100.0)

        items.add(
            QuoteLineItem(
                category = QuoteLineCategory.PRIMARY_MATERIAL,
                description = "${materialSpec.name}$coatNote",
                skuOrBasis = if (materialSpec.sku.isNotBlank()) "${materialSpec.sku} • $wastePercent" else wastePercent,
                quantity = boxesRequired.toDouble(),
                unitLabel = unitLabel,
                unitPrice = CurrencyMath.money(materialSpec.unitCost),
                lineTotal = materialSubtotal
            )
        )

        for (c in consumables) {
            val cLabel = if (c.quantityRequired == 1) c.unitType.singular else c.unitType.plural
            items.add(
                QuoteLineItem(
                    category = QuoteLineCategory.CONSUMABLE,
                    description = c.name,
                    skuOrBasis = c.calculationBasis,
                    quantity = c.quantityRequired.toDouble(),
                    unitLabel = cLabel,
                    unitPrice = c.unitCost,
                    lineTotal = c.totalCost
                )
            )
        }

        items.add(
            QuoteLineItem(
                category = QuoteLineCategory.LABOR,
                description = "${materialSpec.tradeType.displayName} Installation Labor",
                skuOrBasis = String.format(
                    Locale.US,
                    "Net surface area (%.2f %s)",
                    laborAreaInPricingUnit,
                    materialSpec.pricingAreaUnit.symbol
                ),
                quantity = laborAreaInPricingUnit,
                unitLabel = materialSpec.pricingAreaUnit.symbol,
                unitPrice = CurrencyMath.money(materialSpec.laborRatePerUnitArea),
                lineTotal = laborSubtotal
            )
        )

        return items
    }
}
