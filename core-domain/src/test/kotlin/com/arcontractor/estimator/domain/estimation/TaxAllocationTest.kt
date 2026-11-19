package com.arcontractor.estimator.domain.estimation

import com.arcontractor.estimator.domain.geometry.BasicSurfaceGeometry
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxAllocationTest {

    private fun paintSpec() = DefaultMaterialCatalog.acrylicEggshellInteriorPaint.copy(
        boxCoverageSqMeters = 1_000.0,
        unitCost = 0.0,
        laborRatePerUnitArea = 0.0,
        tradeParameters = TradeParameters.Painting(
            finishCoatCount = 1,
            primerCoatCount = 0,
            includePaintersTape = false
        )
    )

    private fun surface(id: String, laborRate: Double, netArea: Double = 1.0) =
        ProjectSurfaceInput(
            surfaceId = id,
            surfaceName = id,
            geometry = BasicSurfaceGeometry(
                grossAreaSqMeters = netArea,
                netAreaSqMeters = netArea,
                outerPerimeterMeters = 4.0
            ),
            materialSpec = paintSpec().copy(laborRatePerUnitArea = laborRate)
        )

    @Test
    fun `empty project produces zero tax and zero total`() {
        val summary = ProjectQuoteAggregator.aggregateProject(
            projectName = "Empty",
            surfaces = emptyList(),
            taxRate = 0.18
        )
        assertEquals(BigDecimal("0.00"), summary.taxAmount)
        assertEquals(BigDecimal("0.00"), summary.grandTotal)
    }

    @Test
    fun `single surface receives the full project tax`() {
        val summary = ProjectQuoteAggregator.aggregateProject(
            projectName = "Single",
            surfaces = listOf(surface("s1", laborRate = 33.33)),
            taxRate = 0.075
        )
        val expected = BigDecimal("33.33").multiply(BigDecimal("0.075"))
            .setScale(2, RoundingMode.HALF_UP)
        assertEquals(expected, summary.taxAmount)
        assertEquals(expected, summary.surfaces.single().estimate.taxAmount)
    }

    @Test
    fun `remainder cents are distributed deterministically and reconcile exactly`() {
        // Three surfaces at $0.05 each with 50% tax: exact per-surface tax 0.025,
        // project tax on 0.15 = 0.075 -> 0.08. Floors sum to 0.06; two remainder
        // cents must go to the earliest tied surfaces deterministically.
        val surfaces = listOf(
            surface("s1", laborRate = 0.05),
            surface("s2", laborRate = 0.05),
            surface("s3", laborRate = 0.05)
        )
        val first = ProjectQuoteAggregator.aggregateProject("Ties", surfaces, taxRate = 0.50)
        val second = ProjectQuoteAggregator.aggregateProject("Ties", surfaces, taxRate = 0.50)

        assertEquals(BigDecimal("0.08"), first.taxAmount)
        assertEquals(
            first.surfaces.map { it.estimate.taxAmount },
            second.surfaces.map { it.estimate.taxAmount }
        )
        assertEquals(
            first.taxAmount,
            first.surfaces.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.taxAmount }
        )
        assertEquals(
            first.grandTotal,
            first.surfaces.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.grandTotal }
        )
        assertEquals(BigDecimal("0.03"), first.surfaces[0].estimate.taxAmount)
        assertEquals(BigDecimal("0.03"), first.surfaces[1].estimate.taxAmount)
        assertEquals(BigDecimal("0.02"), first.surfaces[2].estimate.taxAmount)
    }

    @Test
    fun `zero tax rate allocates zero to every surface`() {
        val summary = ProjectQuoteAggregator.aggregateProject(
            projectName = "Untaxed",
            surfaces = listOf(
                surface("s1", laborRate = 10.0),
                surface("s2", laborRate = 99.99)
            ),
            taxRate = 0.0
        )
        assertEquals(BigDecimal("0.00"), summary.taxAmount)
        summary.surfaces.forEach {
            assertEquals(BigDecimal("0.00"), it.estimate.taxAmount)
            assertEquals(it.estimate.subtotalBeforeTax, it.estimate.grandTotal)
        }
    }

    @Test
    fun `hundred percent tax doubles the subtotal exactly`() {
        val summary = ProjectQuoteAggregator.aggregateProject(
            projectName = "Full tax",
            surfaces = listOf(
                surface("s1", laborRate = 12.34),
                surface("s2", laborRate = 56.78)
            ),
            taxRate = 1.0
        )
        assertEquals(summary.subtotalBeforeTax, summary.taxAmount)
        assertEquals(
            summary.subtotalBeforeTax.multiply(BigDecimal(2)),
            summary.grandTotal
        )
        assertEquals(
            summary.grandTotal,
            summary.surfaces.fold(BigDecimal.ZERO) { acc, s -> acc + s.estimate.grandTotal }
        )
    }
}
