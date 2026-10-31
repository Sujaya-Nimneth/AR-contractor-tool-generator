package com.arcontractor.estimator.data.local

import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.PackageUnitType
import com.arcontractor.estimator.domain.estimation.PricingAreaUnit
import com.arcontractor.estimator.domain.estimation.TradeType
import com.arcontractor.estimator.domain.estimation.WastePreset
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput

fun MaterialSpecEntity.toDomain(): MaterialSpec = MaterialSpec(
    id = id,
    name = name,
    sku = sku,
    tradeType = TradeType.valueOf(tradeType),
    primaryUnitType = PackageUnitType.valueOf(primaryUnitType),
    boxCoverageSqMeters = boxCoverageSqMeters,
    unitCost = unitCost,
    wastePreset = WastePreset.valueOf(wastePreset),
    customWasteFactor = customWasteFactor,
    laborRatePerUnitArea = laborRatePerUnitArea,
    pricingAreaUnit = PricingAreaUnit.valueOf(pricingAreaUnit),
    tradeParameters = TradeParametersCodec.decode(tradeParametersJson)
)

fun MaterialSpec.toEntity(updatedAtEpochMillis: Long): MaterialSpecEntity =
    MaterialSpecEntity(
        id = id,
        name = name,
        sku = sku,
        tradeType = tradeType.name,
        primaryUnitType = primaryUnitType.name,
        boxCoverageSqMeters = boxCoverageSqMeters,
        unitCost = unitCost,
        wastePreset = wastePreset.name,
        customWasteFactor = customWasteFactor,
        laborRatePerUnitArea = laborRatePerUnitArea,
        pricingAreaUnit = pricingAreaUnit.name,
        tradeParametersJson = TradeParametersCodec.encode(tradeParameters),
        updatedAtEpochMillis = updatedAtEpochMillis
    )

fun CutoutEntity.toDomain(): CutoutPolygonInput = CutoutPolygonInput(
    id = id,
    label = label,
    vertices3D = vertices,
    kind = CutoutKind.valueOf(kind)
)

fun CutoutPolygonInput.toEntity(
    surfaceId: String,
    displayOrder: Int
): CutoutEntity = CutoutEntity(
    id = "$surfaceId-cutout-${displayOrder + 1}",
    surfaceId = surfaceId,
    label = label,
    kind = kind.name,
    vertices = vertices3D,
    displayOrder = displayOrder
)
