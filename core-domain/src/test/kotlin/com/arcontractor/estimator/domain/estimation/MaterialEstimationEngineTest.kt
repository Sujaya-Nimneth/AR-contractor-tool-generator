package com.arcontractor.estimator.domain.estimation

import com.arcontractor.estimator.domain.geometry.BasicSurfaceGeometry
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.PolygonGeometryEngine
import com.arcontractor.estimator.domain.geometry.UnitConverter
import com.arcontractor.estimator.domain.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MaterialEstimationEngineTest {

    @Test
    fun `tiling estimation computes exact waste boxes, volumetric grout, thinset, cutout trim, and BigDecimal totals`() {
        // 4.0m x 2.7m bathroom wall (10.80 m²) minus 0.9x2.1m door (1.89 m²) and 1.2x1.0m window (1.20 m²)
        // Net Area = 7.71 m², Outer Perimeter = 13.40 m, Cutout Perimeter = 10.40 m
        val wallGeometry = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = listOf(
                Vec3(0.0, 0.0, -2.0),
                Vec3(4.0, 0.0, -2.0),
                Vec3(4.0, 2.7, -2.0),
                Vec3(0.0, 2.7, -2.0)
            ),
            cutouts = listOf(
                CutoutPolygonInput(
                    id = "d1",
                    label = "Door",
                    kind = CutoutKind.DOOR,
                    vertices3D = listOf(
                        Vec3(0.4, 0.0, -2.0),
                        Vec3(1.3, 0.0, -2.0),
                        Vec3(1.3, 2.1, -2.0),
                        Vec3(0.4, 2.1, -2.0)
                    )
                ),
                CutoutPolygonInput(
                    id = "w1",
                    label = "Window",
                    kind = CutoutKind.WINDOW,
                    vertices3D = listOf(
                        Vec3(2.0, 1.0, -2.0),
                        Vec3(3.2, 1.0, -2.0),
                        Vec3(3.2, 2.0, -2.0),
                        Vec3(2.0, 2.0, -2.0)
                    )
                )
            ),
            referenceNormal = Vec3.UNIT_Z
        )

        val tileSpec = DefaultMaterialCatalog.porcelainTile300x600Straight // 10% waste, 1.44 m²/box, $46.80/box, $55/m² labor
        val estimate = MaterialEstimationEngine.estimateSurface(
            geometry = wallGeometry,
            materialSpec = tileSpec,
            taxRate = 0.08 // 8% tax
        )

        // Gross material area = 7.71 * 1.10 = 8.481 m²
        assertEquals(8.481, estimate.grossMaterialAreaSqMeters, 1e-6)
        // Boxes = ceil(8.481 / 1.44) = ceil(5.88958) = 6 boxes
        assertEquals(6, estimate.boxesRequired)
        // Single tile area = 0.3 * 0.6 = 0.18 m² -> ceil(8.481 / 0.18) = ceil(47.116) = 48 tiles
        assertNotNull(estimate.individualPiecesRequired)
        assertEquals(48, estimate.individualPiecesRequired!!)

        // Consumables check: Thinset (ceil(8.481 / 2.8) = 4 bags), Grout (1 bag), Edge Trim (ceil(10.40 * 1.10 / 2.50) = 5 strips)
        assertEquals(3, estimate.consumables.size)
        assertEquals(4, estimate.consumables[0].quantityRequired) // Thinset bags
        assertEquals(1, estimate.consumables[1].quantityRequired) // Grout bag (~1.97 kg < 4.54 kg)
        assertEquals(5, estimate.consumables[2].quantityRequired) // Aluminum edge trim strips

        // Financial arithmetic check:
        // Material: 6 * $46.80 = $280.80
        assertEquals(BigDecimal("280.80"), estimate.materialSubtotal)
        // Consumables: (4 * $24.50) + (1 * $21.00) + (5 * $16.50) = $98.00 + $21.00 + $82.50 = $201.50
        assertEquals(BigDecimal("201.50"), estimate.consumablesSubtotal)
        // Labor: 7.71 * $55.00 = $424.05
        assertEquals(BigDecimal("424.05"), estimate.laborSubtotal)
        // Subtotal: 280.80 + 201.50 + 424.05 = $906.35
        assertEquals(BigDecimal("906.35"), estimate.subtotalBeforeTax)
        // Tax (8%): 906.35 * 0.08 = 72.508 -> $72.51
        assertEquals(BigDecimal("72.51"), estimate.taxAmount)
        // Grand Total: 906.35 + 72.51 = $978.86
        assertEquals(BigDecimal("978.86"), estimate.grandTotal)
    }

    @Test
    fun `herringbone 15 percent waste requires more boxes than 10 percent straight lay`() {
        val floorGeom = BasicSurfaceGeometry(
            grossAreaSqMeters = 13.0,
            netAreaSqMeters = 13.0,
            outerPerimeterMeters = 14.6
        )
        val straightSpec = DefaultMaterialCatalog.porcelainTile300x600Straight
        val herringboneSpec = straightSpec.copy(wastePreset = WastePreset.DIAGONAL_OR_HERRINGBONE)

        val straightEst = MaterialEstimationEngine.estimateSurface(floorGeom, straightSpec)
        val herringboneEst = MaterialEstimationEngine.estimateSurface(floorGeom, herringboneSpec)

        // Straight: 13.0 * 1.10 = 14.30 m² -> ceil(14.30 / 1.44) = 10 boxes
        // Herringbone: 13.0 * 1.15 = 14.95 m² -> ceil(14.95 / 1.44) = 11 boxes
        assertEquals(10, straightEst.boxesRequired)
        assertEquals(11, herringboneEst.boxesRequired)
    }

    @Test
    fun `flooring estimation supports Imperial SQ_FOOT labor pricing alongside metric geometry`() {
        val roomFloor = BasicSurfaceGeometry(
            grossAreaSqMeters = 20.0,
            cutoutAreaSqMeters = 2.0, // Kitchen island
            netAreaSqMeters = 18.0,
            outerPerimeterMeters = 18.0,
            cutoutPerimeterMeters = 6.0
        )

        val imperialLvpSpec = DefaultMaterialCatalog.oakLuxuryVinylPlank.copy(
            laborRatePerUnitArea = 3.50, // $3.50 per sq ft
            pricingAreaUnit = PricingAreaUnit.SQ_FOOT
        )

        val estimate = MaterialEstimationEngine.estimateSurface(roomFloor, imperialLvpSpec)
        // Gross material = 18.0 * 1.10 = 19.80 m² -> ceil(19.80 / 2.20) = 9 boxes
        assertEquals(9, estimate.boxesRequired)

        val expectedSqFt = UnitConverter.sqMetersToSqFeet(18.0) // ~193.750387 sq ft
        val expectedLabor = CurrencyMath.multiplyRate(expectedSqFt, 3.50) // $678.13
        assertEquals(BigDecimal("678.13"), estimate.laborSubtotal)
    }

    @Test
    fun `painting estimation accounts for multi-coat finish paint, primer coats, and perimeter tape`() {
        val wallsGeometry = BasicSurfaceGeometry(
            grossAreaSqMeters = 45.0,
            cutoutAreaSqMeters = 5.0,
            netAreaSqMeters = 40.0,
            outerPerimeterMeters = 32.0,
            cutoutPerimeterMeters = 16.0
        )

        val paintSpec = DefaultMaterialCatalog.acrylicEggshellInteriorPaint // 5% waste, 2 finish coats, 1 primer coat
        val estimate = MaterialEstimationEngine.estimateSurface(wallsGeometry, paintSpec)

        // Gross single-coat area = 40.0 * 1.05 = 42.0 m²
        // 2 finish coats = 84.0 m² / 32.52 m²/gal = 2.583 -> 3 gallons finish paint
        assertEquals(42.0, estimate.grossMaterialAreaSqMeters, 1e-6)
        assertEquals(3, estimate.primaryUnitsRequired)

        // Primer: 42.0 m² * 1 coat / 27.87 m²/gal = 1.507 -> 2 gallons primer
        // Masking tape: (32.0 + 16.0) = 48.0 m / 54.86 m/roll = 1 roll
        assertEquals(2, estimate.consumables[0].quantityRequired)
        assertEquals(1, estimate.consumables[1].quantityRequired)
    }

    @Test
    fun `drywall estimation and materials-only tax policy exempts labor from sales tax`() {
        val drywallWall = BasicSurfaceGeometry(
            grossAreaSqMeters = 28.0,
            cutoutAreaSqMeters = 3.0,
            netAreaSqMeters = 25.0,
            outerPerimeterMeters = 22.0,
            cutoutPerimeterMeters = 9.0
        )

        val spec = DefaultMaterialCatalog.standardHalfInchDrywall4x8 // 5% waste, 2.9729 m²/sheet
        val estimate = MaterialEstimationEngine.estimateSurface(
            geometry = drywallWall,
            materialSpec = spec,
            taxRate = 0.10,
            taxPolicy = TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY
        )

        // Gross area = 25.0 * 1.05 = 26.25 m² -> ceil(26.25 / 2.9729) = 9 sheets
        assertEquals(9, estimate.primaryUnitsRequired)
        assertEquals(4, estimate.consumables.size) // Mud, Tape, Screws, Cutout J-Bead

        val expectedGoodsTax = estimate.materialSubtotal
            .add(estimate.consumablesSubtotal)
            .multiply(BigDecimal("0.10"))
            .setScale(2, java.math.RoundingMode.HALF_UP)

        assertEquals(expectedGoodsTax, estimate.taxAmount)
        assertEquals(estimate.subtotalBeforeTax.add(expectedGoodsTax), estimate.grandTotal)
    }

    @Test
    fun `project quote aggregator combines multiple room surfaces into reconciled grand total`() {
        val bathWall = BasicSurfaceGeometry(
            grossAreaSqMeters = 9.0,
            cutoutAreaSqMeters = 1.0,
            netAreaSqMeters = 8.0,
            outerPerimeterMeters = 12.0,
            cutoutPerimeterMeters = 4.0
        )
        val bathCeiling = BasicSurfaceGeometry(
            grossAreaSqMeters = 6.0,
            netAreaSqMeters = 6.0,
            outerPerimeterMeters = 10.0
        )

        val summary = ProjectQuoteAggregator.aggregateProject(
            projectName = "Master Bathroom Remodel",
            surfaces = listOf(
                ProjectSurfaceInput(
                    surfaceId = "s1",
                    surfaceName = "Shower Feature Wall",
                    geometry = bathWall,
                    materialSpec = DefaultMaterialCatalog.herringboneSubwayTile75x300
                ),
                ProjectSurfaceInput(
                    surfaceId = "s2",
                    surfaceName = "Moisture-Resistant Ceiling",
                    geometry = bathCeiling,
                    materialSpec = DefaultMaterialCatalog.acrylicEggshellInteriorPaint
                )
            ),
            taxRate = 0.075
        )

        assertEquals(15.0, summary.totalGrossAreaSqMeters, 1e-6)
        assertEquals(1.0, summary.totalCutoutAreaSqMeters, 1e-6)
        assertEquals(14.0, summary.totalNetAreaSqMeters, 1e-6)
        assertTrue(summary.grandTotal > summary.subtotalBeforeTax)
        assertEquals(
            summary.materialSubtotal.add(summary.consumablesSubtotal).add(summary.laborSubtotal),
            summary.subtotalBeforeTax
        )
        assertEquals(
            summary.grandTotal,
            summary.surfaces.fold(BigDecimal.ZERO) { acc, surface ->
                acc + surface.estimate.grandTotal
            }.setScale(2, java.math.RoundingMode.HALF_UP)
        )
    }

    @Test
    fun `project tax allocation reconciles penny rounding for both tax policies`() {
        listOf(
            TaxPolicy.TAX_TOTAL_SUBTOTAL,
            TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY
        ).forEach { policy ->
            val geometry = listOf(0.01, 0.06, 0.13).map { netArea ->
                BasicSurfaceGeometry(
                    grossAreaSqMeters = netArea,
                    netAreaSqMeters = netArea,
                    outerPerimeterMeters = 0.4,
                    cutoutPerimeterMeters = 0.0
                )
            }
            val spec = DefaultMaterialCatalog.acrylicEggshellInteriorPaint.copy(
                boxCoverageSqMeters = 1_000.0,
                unitCost = 0.01,
                laborRatePerUnitArea = 0.0,
                tradeParameters = TradeParameters.Painting(
                    finishCoatCount = 1,
                    primerCoatCount = 0,
                    includePaintersTape = false
                )
            )
            val summary = ProjectQuoteAggregator.aggregateProject(
                projectName = "Penny reconciliation",
                surfaces = geometry.mapIndexed { index, surface ->
                    ProjectSurfaceInput(
                        surfaceId = "s$index",
                        surfaceName = "Surface $index",
                        geometry = surface,
                        materialSpec = spec
                    )
                },
                taxRate = 0.0825,
                taxPolicy = policy
            )

            assertEquals(
                summary.taxAmount,
                summary.surfaces.fold(BigDecimal.ZERO) { acc, surface ->
                    acc + surface.estimate.taxAmount
                }.setScale(2, java.math.RoundingMode.HALF_UP)
            )
            assertEquals(
                summary.grandTotal,
                summary.surfaces.fold(BigDecimal.ZERO) { acc, surface ->
                    acc + surface.estimate.grandTotal
                }.setScale(2, java.math.RoundingMode.HALF_UP)
            )
        }
    }

    @Test
    fun `displayed labor unit price is the rate used for the line total`() {
        val geometry = BasicSurfaceGeometry(
            grossAreaSqMeters = 2.0,
            netAreaSqMeters = 2.0,
            outerPerimeterMeters = 6.0
        )
        val spec = DefaultMaterialCatalog.acrylicEggshellInteriorPaint.copy(
            boxCoverageSqMeters = 1_000.0,
            unitCost = 0.0,
            laborRatePerUnitArea = 12.345,
            tradeParameters = TradeParameters.Painting(
                finishCoatCount = 1,
                primerCoatCount = 0,
                includePaintersTape = false
            )
        )

        val estimate = MaterialEstimationEngine.estimateSurface(geometry, spec)
        val laborLine = estimate.lineItems.single {
            it.category == QuoteLineCategory.LABOR
        }

        assertEquals(BigDecimal("12.35"), laborLine.unitPrice)
        assertEquals(
            BigDecimal.valueOf(laborLine.quantity)
                .multiply(laborLine.unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_UP),
            laborLine.lineTotal
        )
    }

    @Test
    fun `flooring baseboard run skips floor-flush door thresholds`() {
        // 5m x 4m room (20 m², 18 m perimeter) with a 0.9m wide floor-flush door.
        val roomFloor = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(5.0, 0.0, 0.0),
                Vec3(5.0, 0.0, -4.0),
                Vec3(0.0, 0.0, -4.0)
            ),
            referenceNormal = Vec3.UNIT_Y
        )
        val withDoor = BasicSurfaceGeometry(
            grossAreaSqMeters = roomFloor.grossAreaSqMeters,
            netAreaSqMeters = roomFloor.netAreaSqMeters,
            outerPerimeterMeters = roomFloor.outerPerimeterMeters,
            doorOpeningWidthMeters = 0.9
        )

        val estimate = MaterialEstimationEngine.estimateSurface(
            geometry = withDoor,
            materialSpec = DefaultMaterialCatalog.oakLuxuryVinylPlank
        )
        val baseboard = estimate.consumables.single { it.name.contains("Baseboard") }
        // (18.0 - 0.9) * 1.10 = 18.81 m -> ceil(18.81 / 2.44) = 8 pieces (vs 9 without door).
        assertEquals(8, baseboard.quantityRequired)
        assertTrue(baseboard.calculationBasis.contains("doorways"))

        val withoutDoor = MaterialEstimationEngine.estimateSurface(
            geometry = roomFloor,
            materialSpec = DefaultMaterialCatalog.oakLuxuryVinylPlank
        )
        assertEquals(
            9,
            withoutDoor.consumables.single { it.name.contains("Baseboard") }.quantityRequired
        )
    }

    @Test
    fun `overlapping windows bill trim on the union outline instead of summed perimeters`() {
        // 4m x 2.7m wall with two overlapping 1x1 windows (union outline 5.0 m, raw sum 8.0 m).
        val wall = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = listOf(
                Vec3(0.0, 0.0, -2.0),
                Vec3(4.0, 0.0, -2.0),
                Vec3(4.0, 2.7, -2.0),
                Vec3(0.0, 2.7, -2.0)
            ),
            cutouts = listOf(
                CutoutPolygonInput(
                    id = "w1",
                    label = "Window A",
                    kind = CutoutKind.WINDOW,
                    vertices3D = listOf(
                        Vec3(1.0, 1.0, -2.0),
                        Vec3(2.0, 1.0, -2.0),
                        Vec3(2.0, 2.0, -2.0),
                        Vec3(1.0, 2.0, -2.0)
                    )
                ),
                CutoutPolygonInput(
                    id = "w2",
                    label = "Window B",
                    kind = CutoutKind.WINDOW,
                    vertices3D = listOf(
                        Vec3(1.5, 1.0, -2.0),
                        Vec3(2.5, 1.0, -2.0),
                        Vec3(2.5, 2.0, -2.0),
                        Vec3(1.5, 2.0, -2.0)
                    )
                )
            ),
            referenceNormal = Vec3.UNIT_Z
        )

        assertEquals(8.0, wall.cutoutPerimeterMeters, 1e-8)
        assertEquals(5.0, wall.effectiveCutoutPerimeterMeters, 1e-8)

        val estimate = MaterialEstimationEngine.estimateSurface(
            geometry = wall,
            materialSpec = DefaultMaterialCatalog.porcelainTile300x600Straight
        )
        val trim = estimate.consumables.single { it.name.contains("Edge Trim") }
        // ceil(5.0 * 1.10 / 2.50) = ceil(2.2) = 3 strips (raw perimeters would bill 4).
        assertEquals(3, trim.quantityRequired)
    }
}
