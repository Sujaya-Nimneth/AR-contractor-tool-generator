package com.arcontractor.estimator.domain.estimation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Supported contractor trades.
 */
enum class TradeType(val displayName: String) {
    TILING("Tile & Stone"),
    FLOORING("Hardwood / LVP / Laminate Flooring"),
    PAINTING("Painting & Coatings"),
    DRYWALL("Drywall & Gypsum Board")
}

/**
 * Industry-standard material waste percentage presets.
 */
enum class WastePreset(
    val defaultFactor: Double,
    val displayName: String
) {
    STRAIGHT_LAY(0.10, "Straight / Grid Lay (10%)"),
    DIAGONAL_OR_HERRINGBONE(0.15, "Diagonal / Herringbone (15%)"),
    COMPLEX_PATTERN(0.18, "Complex / Large-Format Cut-Heavy (18%)"),
    PAINT_OR_DRYWALL(0.05, "Paint / Standard Drywall (5%)"),
    DRYWALL_MULTI_CUTOUT(0.10, "Drywall Multi-Opening (10%)"),
    CUSTOM(0.10, "Custom Waste Factor")
}

/**
 * Area unit used for labor pricing and quote display.
 */
enum class PricingAreaUnit(val symbol: String) {
    SQ_METER("m²"),
    SQ_FOOT("sq ft")
}

/**
 * Packaging unit of the primary material SKU.
 */
enum class PackageUnitType(val singular: String, val plural: String) {
    BOX("Box", "Boxes"),
    GALLON("Gallon", "Gallons"),
    SHEET("Sheet", "Sheets"),
    ROLL("Roll", "Rolls"),
    BUCKET("Bucket", "Buckets"),
    STRIP("Strip", "Strips"),
    UNIT("Unit", "Units")
}

/**
 * Controls which subtotals are subject to sales/VAT tax.
 */
enum class TaxPolicy {
    /** Applies tax rate to Materials + Consumables + Labor. */
    TAX_TOTAL_SUBTOTAL,

    /** Applies tax rate only to tangible goods (Materials + Consumables), exempting Labor. */
    TAX_MATERIALS_AND_CONSUMABLES_ONLY
}

/**
 * Trade-specific physical parameters driving consumables estimation.
 */
sealed interface TradeParameters {

    /**
     * Tiling parameters for thinset mortar, volumetric grout, individual tile count, and cutout edge trim.
     */
    data class Tiling(
        val tileWidthMm: Double,
        val tileHeightMm: Double,
        val tileThicknessMm: Double = 9.0,
        val jointWidthMm: Double = 3.0,
        val thinsetBagWeightKg: Double = 22.68,
        val thinsetBagUnitCost: Double = 24.50,
        val thinsetCoverageOverrideSqMetersPerBag: Double? = null,
        val groutBagWeightKg: Double = 4.54,
        val groutBagUnitCost: Double = 21.00,
        val groutDensityGramsPerCm3: Double = 1.65,
        val groutMixingWasteFactor: Double = 0.10,
        val includeCutoutEdgeTrim: Boolean = true,
        val edgeTrimStripLengthMeters: Double = 2.50,
        val edgeTrimStripUnitCost: Double = 16.50
    ) : TradeParameters {
        init {
            require(tileWidthMm > 0.0 && tileHeightMm > 0.0) { "Tile dimensions must be positive" }
            require(tileThicknessMm > 0.0) { "Tile thickness must be positive" }
            require(jointWidthMm >= 0.0) { "Joint width must be non-negative" }
            require(thinsetBagWeightKg > 0.0) { "Thinset bag weight must be positive" }
            require(groutBagWeightKg > 0.0) { "Grout bag weight must be positive" }
            require(edgeTrimStripLengthMeters > 0.0) { "Edge trim strip length must be positive" }
        }

        val singleTileAreaSqMeters: Double
            get() = (tileWidthMm / 1000.0) * (tileHeightMm / 1000.0)
    }

    /**
     * Flooring parameters for acoustic/moisture underlayment rolls and perimeter baseboard/quarter-round trim.
     */
    data class Flooring(
        val includeUnderlayment: Boolean = true,
        val underlaymentRollCoverageSqMeters: Double = 9.29,
        val underlaymentRollUnitCost: Double = 34.00,
        val includePerimeterBaseboard: Boolean = true,
        val baseboardPieceLengthMeters: Double = 2.44,
        val baseboardMiterWasteFactor: Double = 0.10,
        val baseboardPieceUnitCost: Double = 12.50
    ) : TradeParameters {
        init {
            require(underlaymentRollCoverageSqMeters > 0.0) { "Underlayment coverage must be positive" }
            require(baseboardPieceLengthMeters > 0.0) { "Baseboard piece length must be positive" }
            require(baseboardMiterWasteFactor >= 0.0) { "Baseboard waste factor must be non-negative" }
        }
    }

    /**
     * Painting parameters for multi-coat finish coverage, primer coats, and perimeter masking tape.
     */
    data class Painting(
        val finishCoatCount: Int = 2,
        val primerCoatCount: Int = 1,
        val primerSpreadRateSqMetersPerGallon: Double = 27.87,
        val primerGallonUnitCost: Double = 32.00,
        val includePaintersTape: Boolean = true,
        val tapeRollLengthMeters: Double = 54.86,
        val tapeRollUnitCost: Double = 7.50
    ) : TradeParameters {
        init {
            require(finishCoatCount >= 1) { "finishCoatCount must be >= 1" }
            require(primerCoatCount >= 0) { "primerCoatCount must be >= 0" }
            require(primerSpreadRateSqMetersPerGallon > 0.0) { "Primer spread rate must be positive" }
            require(tapeRollLengthMeters > 0.0) { "Tape roll length must be positive" }
        }
    }

    /**
     * Drywall parameters for joint compound buckets, paper seam tape, drywall screws, and cutout J-bead trim.
     */
    data class Drywall(
        val sheetWidthMeters: Double = 1.2192,
        val sheetHeightMeters: Double = 2.4384,
        val jointCompoundBucketCoverageSqMeters: Double = 37.16,
        val jointCompoundBucketUnitCost: Double = 22.00,
        val jointTapeRollLengthMeters: Double = 76.20,
        val jointTapeRollUnitCost: Double = 6.50,
        val screwsPerSqMeter: Double = 12.0,
        val screwsPerBox: Int = 1000,
        val screwBoxUnitCost: Double = 14.00,
        val includeCutoutJTrim: Boolean = true,
        val jTrimStripLengthMeters: Double = 3.05,
        val jTrimStripUnitCost: Double = 5.50
    ) : TradeParameters {
        init {
            require(sheetWidthMeters > 0.0 && sheetHeightMeters > 0.0) { "Sheet dimensions must be positive" }
            require(jointCompoundBucketCoverageSqMeters > 0.0) { "Compound coverage must be positive" }
            require(jointTapeRollLengthMeters > 0.0) { "Tape roll length must be positive" }
            require(screwsPerSqMeter > 0.0 && screwsPerBox > 0) { "Screw parameters must be positive" }
            require(jTrimStripLengthMeters > 0.0) { "J-Trim length must be positive" }
        }
    }
}

/**
 * Complete material & labor specification from the contractor's catalog.
 *
 * @property boxCoverageSqMeters Coverage area in `m²` per primary package unit (e.g., per box of tile/flooring,
 *                               per sheet of drywall, or single-coat spread rate per gallon of paint).
 * @property unitCost Price per primary package unit (per box / gallon / sheet).
 * @property wastePreset Selected industry waste preset (10% straight, 15% diagonal/herringbone, 5% paint/drywall).
 * @property customWasteFactor Optional override when [wastePreset] is [WastePreset.CUSTOM] (or manually tuned).
 * @property laborRatePerUnitArea Installation labor rate per [pricingAreaUnit] (`m²` or `sq ft`).
 */
data class MaterialSpec(
    val id: String,
    val name: String,
    val sku: String = "",
    val tradeType: TradeType,
    val primaryUnitType: PackageUnitType,
    val boxCoverageSqMeters: Double,
    val unitCost: Double,
    val wastePreset: WastePreset,
    val customWasteFactor: Double? = null,
    val laborRatePerUnitArea: Double,
    val pricingAreaUnit: PricingAreaUnit = PricingAreaUnit.SQ_METER,
    val tradeParameters: TradeParameters
) {
    init {
        require(name.isNotBlank()) { "Material name cannot be blank" }
        require(boxCoverageSqMeters > 0.0) { "boxCoverageSqMeters must be > 0, got $boxCoverageSqMeters" }
        require(unitCost >= 0.0) { "unitCost must be non-negative, got $unitCost" }
        require(laborRatePerUnitArea >= 0.0) { "laborRatePerUnitArea must be non-negative" }
        if (customWasteFactor != null) {
            require(customWasteFactor in 0.0..1.0) {
                "customWasteFactor must be between 0.0 and 1.0, got $customWasteFactor"
            }
        }
    }

    val effectiveWasteFactor: Double
        get() = customWasteFactor ?: wastePreset.defaultFactor
}

enum class QuoteLineCategory {
    PRIMARY_MATERIAL,
    CONSUMABLE,
    LABOR
}

/**
 * Single itemized line entry in a surface or project quote.
 */
data class QuoteLineItem(
    val category: QuoteLineCategory,
    val description: String,
    val skuOrBasis: String,
    val quantity: Double,
    val unitLabel: String,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

/**
 * Quantified trade consumable (thinset, grout, primer, underlayment, joint compound, trim, etc.).
 */
data class ConsumableEstimate(
    val name: String,
    val calculationBasis: String,
    val quantityRequired: Int,
    val unitType: PackageUnitType,
    val rawAmountEstimate: Double,
    val rawAmountUnit: String,
    val unitCost: BigDecimal,
    val totalCost: BigDecimal
)

/**
 * Complete material, waste, consumables, labor, and financial breakdown for a single measured surface.
 */
data class SurfaceEstimate(
    val materialSpec: MaterialSpec,
    val grossAreaSqMeters: Double,
    val cutoutAreaSqMeters: Double,
    val netAreaSqMeters: Double,
    val outerPerimeterMeters: Double,
    val cutoutPerimeterMeters: Double,
    val wasteFactor: Double,
    val grossMaterialAreaSqMeters: Double,
    val boxesRequired: Int,
    val individualPiecesRequired: Int?,
    val purchasedCoverageSqMeters: Double,
    val consumables: List<ConsumableEstimate>,
    val materialSubtotal: BigDecimal,
    val consumablesSubtotal: BigDecimal,
    val laborSubtotal: BigDecimal,
    val subtotalBeforeTax: BigDecimal,
    val taxRate: Double,
    val taxPolicy: TaxPolicy,
    val taxAmount: BigDecimal,
    val grandTotal: BigDecimal,
    val lineItems: List<QuoteLineItem>
) {
    /** Alias for [boxesRequired] reflecting non-box primary packaging (gallons, sheets, rolls). */
    val primaryUnitsRequired: Int
        get() = boxesRequired
}

internal object CurrencyMath {
    fun money(amount: Double): BigDecimal =
        BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)

    fun multiplyQty(quantity: Int, unitPrice: Double): BigDecimal =
        BigDecimal.valueOf(quantity.toLong())
            .multiply(BigDecimal.valueOf(unitPrice))
            .setScale(2, RoundingMode.HALF_UP)

    fun multiplyRate(quantity: Double, rate: Double): BigDecimal =
        BigDecimal.valueOf(quantity)
            .multiply(BigDecimal.valueOf(rate))
            .setScale(2, RoundingMode.HALF_UP)

    fun rate(amount: Double): BigDecimal = money(amount)

    fun multiplyRate(quantity: Double, unitPrice: BigDecimal): BigDecimal =
        BigDecimal.valueOf(quantity)
            .multiply(unitPrice)
            .setScale(2, RoundingMode.HALF_UP)
}
