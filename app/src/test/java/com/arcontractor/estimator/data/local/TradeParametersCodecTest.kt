package com.arcontractor.estimator.data.local

import com.arcontractor.estimator.domain.estimation.TradeParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class TradeParametersCodecTest {
    @Test
    fun `tiling parameters round trip`() {
        assertRoundTrip(
            TradeParameters.Tiling(
                tileWidthMm = 300.0,
                tileHeightMm = 600.0,
                tileThicknessMm = 9.5,
                jointWidthMm = 3.0,
                thinsetBagWeightKg = 22.68,
                thinsetBagUnitCost = 24.5,
                thinsetCoverageOverrideSqMetersPerBag = 4.0,
                groutBagWeightKg = 4.54,
                groutBagUnitCost = 21.0,
                groutDensityGramsPerCm3 = 1.65,
                groutMixingWasteFactor = 0.1,
                includeCutoutEdgeTrim = true,
                edgeTrimStripLengthMeters = 2.5,
                edgeTrimStripUnitCost = 16.5
            )
        )
    }

    @Test
    fun `flooring parameters round trip`() {
        assertRoundTrip(
            TradeParameters.Flooring(
                includeUnderlayment = false,
                underlaymentRollCoverageSqMeters = 10.0,
                underlaymentRollUnitCost = 35.0,
                includePerimeterBaseboard = true,
                baseboardPieceLengthMeters = 2.4,
                baseboardMiterWasteFactor = 0.12,
                baseboardPieceUnitCost = 13.0
            )
        )
    }

    @Test
    fun `painting parameters round trip`() {
        assertRoundTrip(
            TradeParameters.Painting(
                finishCoatCount = 3,
                primerCoatCount = 2,
                primerSpreadRateSqMetersPerGallon = 26.0,
                primerGallonUnitCost = 33.0,
                includePaintersTape = false,
                tapeRollLengthMeters = 50.0,
                tapeRollUnitCost = 8.0
            )
        )
    }

    @Test
    fun `drywall parameters round trip`() {
        assertRoundTrip(
            TradeParameters.Drywall(
                sheetWidthMeters = 1.2,
                sheetHeightMeters = 2.4,
                jointCompoundBucketCoverageSqMeters = 38.0,
                jointCompoundBucketUnitCost = 23.0,
                jointTapeRollLengthMeters = 75.0,
                jointTapeRollUnitCost = 7.0,
                screwsPerSqMeter = 13.0,
                screwsPerBox = 800,
                screwBoxUnitCost = 15.0,
                includeCutoutJTrim = false,
                jTrimStripLengthMeters = 3.0,
                jTrimStripUnitCost = 6.0
            )
        )
    }

    private fun assertRoundTrip(parameters: TradeParameters) {
        assertEquals(parameters, TradeParametersCodec.decode(TradeParametersCodec.encode(parameters)))
    }
}
