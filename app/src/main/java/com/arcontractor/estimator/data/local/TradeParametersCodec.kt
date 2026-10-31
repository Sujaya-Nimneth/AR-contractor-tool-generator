package com.arcontractor.estimator.data.local

import com.arcontractor.estimator.domain.estimation.TradeParameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object TradeParametersCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(parameters: TradeParameters): String =
        json.encodeToString(TradeParametersDto.fromDomain(parameters))

    fun decode(value: String): TradeParameters =
        json.decodeFromString<TradeParametersDto>(value).toDomain()

    @Serializable
    private sealed interface TradeParametersDto {
        fun toDomain(): TradeParameters

        @Serializable
        @SerialName("tiling")
        data class Tiling(
            val tileWidthMm: Double,
            val tileHeightMm: Double,
            val tileThicknessMm: Double,
            val jointWidthMm: Double,
            val thinsetBagWeightKg: Double,
            val thinsetBagUnitCost: Double,
            val thinsetCoverageOverrideSqMetersPerBag: Double?,
            val groutBagWeightKg: Double,
            val groutBagUnitCost: Double,
            val groutDensityGramsPerCm3: Double,
            val groutMixingWasteFactor: Double,
            val includeCutoutEdgeTrim: Boolean,
            val edgeTrimStripLengthMeters: Double,
            val edgeTrimStripUnitCost: Double
        ) : TradeParametersDto {
            override fun toDomain() = TradeParameters.Tiling(
                tileWidthMm = tileWidthMm,
                tileHeightMm = tileHeightMm,
                tileThicknessMm = tileThicknessMm,
                jointWidthMm = jointWidthMm,
                thinsetBagWeightKg = thinsetBagWeightKg,
                thinsetBagUnitCost = thinsetBagUnitCost,
                thinsetCoverageOverrideSqMetersPerBag = thinsetCoverageOverrideSqMetersPerBag,
                groutBagWeightKg = groutBagWeightKg,
                groutBagUnitCost = groutBagUnitCost,
                groutDensityGramsPerCm3 = groutDensityGramsPerCm3,
                groutMixingWasteFactor = groutMixingWasteFactor,
                includeCutoutEdgeTrim = includeCutoutEdgeTrim,
                edgeTrimStripLengthMeters = edgeTrimStripLengthMeters,
                edgeTrimStripUnitCost = edgeTrimStripUnitCost
            )
        }

        @Serializable
        @SerialName("flooring")
        data class Flooring(
            val includeUnderlayment: Boolean,
            val underlaymentRollCoverageSqMeters: Double,
            val underlaymentRollUnitCost: Double,
            val includePerimeterBaseboard: Boolean,
            val baseboardPieceLengthMeters: Double,
            val baseboardMiterWasteFactor: Double,
            val baseboardPieceUnitCost: Double
        ) : TradeParametersDto {
            override fun toDomain() = TradeParameters.Flooring(
                includeUnderlayment = includeUnderlayment,
                underlaymentRollCoverageSqMeters = underlaymentRollCoverageSqMeters,
                underlaymentRollUnitCost = underlaymentRollUnitCost,
                includePerimeterBaseboard = includePerimeterBaseboard,
                baseboardPieceLengthMeters = baseboardPieceLengthMeters,
                baseboardMiterWasteFactor = baseboardMiterWasteFactor,
                baseboardPieceUnitCost = baseboardPieceUnitCost
            )
        }

        @Serializable
        @SerialName("painting")
        data class Painting(
            val finishCoatCount: Int,
            val primerCoatCount: Int,
            val primerSpreadRateSqMetersPerGallon: Double,
            val primerGallonUnitCost: Double,
            val includePaintersTape: Boolean,
            val tapeRollLengthMeters: Double,
            val tapeRollUnitCost: Double
        ) : TradeParametersDto {
            override fun toDomain() = TradeParameters.Painting(
                finishCoatCount = finishCoatCount,
                primerCoatCount = primerCoatCount,
                primerSpreadRateSqMetersPerGallon = primerSpreadRateSqMetersPerGallon,
                primerGallonUnitCost = primerGallonUnitCost,
                includePaintersTape = includePaintersTape,
                tapeRollLengthMeters = tapeRollLengthMeters,
                tapeRollUnitCost = tapeRollUnitCost
            )
        }

        @Serializable
        @SerialName("drywall")
        data class Drywall(
            val sheetWidthMeters: Double,
            val sheetHeightMeters: Double,
            val jointCompoundBucketCoverageSqMeters: Double,
            val jointCompoundBucketUnitCost: Double,
            val jointTapeRollLengthMeters: Double,
            val jointTapeRollUnitCost: Double,
            val screwsPerSqMeter: Double,
            val screwsPerBox: Int,
            val screwBoxUnitCost: Double,
            val includeCutoutJTrim: Boolean,
            val jTrimStripLengthMeters: Double,
            val jTrimStripUnitCost: Double
        ) : TradeParametersDto {
            override fun toDomain() = TradeParameters.Drywall(
                sheetWidthMeters = sheetWidthMeters,
                sheetHeightMeters = sheetHeightMeters,
                jointCompoundBucketCoverageSqMeters = jointCompoundBucketCoverageSqMeters,
                jointCompoundBucketUnitCost = jointCompoundBucketUnitCost,
                jointTapeRollLengthMeters = jointTapeRollLengthMeters,
                jointTapeRollUnitCost = jointTapeRollUnitCost,
                screwsPerSqMeter = screwsPerSqMeter,
                screwsPerBox = screwsPerBox,
                screwBoxUnitCost = screwBoxUnitCost,
                includeCutoutJTrim = includeCutoutJTrim,
                jTrimStripLengthMeters = jTrimStripLengthMeters,
                jTrimStripUnitCost = jTrimStripUnitCost
            )
        }

        companion object {
            fun fromDomain(parameters: TradeParameters): TradeParametersDto = when (parameters) {
                is TradeParameters.Tiling -> Tiling(
                    tileWidthMm = parameters.tileWidthMm,
                    tileHeightMm = parameters.tileHeightMm,
                    tileThicknessMm = parameters.tileThicknessMm,
                    jointWidthMm = parameters.jointWidthMm,
                    thinsetBagWeightKg = parameters.thinsetBagWeightKg,
                    thinsetBagUnitCost = parameters.thinsetBagUnitCost,
                    thinsetCoverageOverrideSqMetersPerBag =
                        parameters.thinsetCoverageOverrideSqMetersPerBag,
                    groutBagWeightKg = parameters.groutBagWeightKg,
                    groutBagUnitCost = parameters.groutBagUnitCost,
                    groutDensityGramsPerCm3 = parameters.groutDensityGramsPerCm3,
                    groutMixingWasteFactor = parameters.groutMixingWasteFactor,
                    includeCutoutEdgeTrim = parameters.includeCutoutEdgeTrim,
                    edgeTrimStripLengthMeters = parameters.edgeTrimStripLengthMeters,
                    edgeTrimStripUnitCost = parameters.edgeTrimStripUnitCost
                )

                is TradeParameters.Flooring -> Flooring(
                    includeUnderlayment = parameters.includeUnderlayment,
                    underlaymentRollCoverageSqMeters =
                        parameters.underlaymentRollCoverageSqMeters,
                    underlaymentRollUnitCost = parameters.underlaymentRollUnitCost,
                    includePerimeterBaseboard = parameters.includePerimeterBaseboard,
                    baseboardPieceLengthMeters = parameters.baseboardPieceLengthMeters,
                    baseboardMiterWasteFactor = parameters.baseboardMiterWasteFactor,
                    baseboardPieceUnitCost = parameters.baseboardPieceUnitCost
                )

                is TradeParameters.Painting -> Painting(
                    finishCoatCount = parameters.finishCoatCount,
                    primerCoatCount = parameters.primerCoatCount,
                    primerSpreadRateSqMetersPerGallon =
                        parameters.primerSpreadRateSqMetersPerGallon,
                    primerGallonUnitCost = parameters.primerGallonUnitCost,
                    includePaintersTape = parameters.includePaintersTape,
                    tapeRollLengthMeters = parameters.tapeRollLengthMeters,
                    tapeRollUnitCost = parameters.tapeRollUnitCost
                )

                is TradeParameters.Drywall -> Drywall(
                    sheetWidthMeters = parameters.sheetWidthMeters,
                    sheetHeightMeters = parameters.sheetHeightMeters,
                    jointCompoundBucketCoverageSqMeters =
                        parameters.jointCompoundBucketCoverageSqMeters,
                    jointCompoundBucketUnitCost = parameters.jointCompoundBucketUnitCost,
                    jointTapeRollLengthMeters = parameters.jointTapeRollLengthMeters,
                    jointTapeRollUnitCost = parameters.jointTapeRollUnitCost,
                    screwsPerSqMeter = parameters.screwsPerSqMeter,
                    screwsPerBox = parameters.screwsPerBox,
                    screwBoxUnitCost = parameters.screwBoxUnitCost,
                    includeCutoutJTrim = parameters.includeCutoutJTrim,
                    jTrimStripLengthMeters = parameters.jTrimStripLengthMeters,
                    jTrimStripUnitCost = parameters.jTrimStripUnitCost
                )
            }
        }
    }
}
