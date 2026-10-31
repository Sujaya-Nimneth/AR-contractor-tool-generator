package com.arcontractor.estimator.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.arcontractor.estimator.domain.geometry.Vec3

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val clientName: String,
    val taxRate: Double,
    val taxPolicy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "material_specs",
    indices = [Index(value = ["tradeType"])]
)
data class MaterialSpecEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sku: String,
    val tradeType: String,
    val primaryUnitType: String,
    val boxCoverageSqMeters: Double,
    val unitCost: Double,
    val wastePreset: String,
    val customWasteFactor: Double?,
    val laborRatePerUnitArea: Double,
    val pricingAreaUnit: String,
    val tradeParametersJson: String,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "surfaces",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaterialSpecEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["materialId"])
    ]
)
data class SurfaceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val outerVertices: List<Vec3>,
    val referenceNormal: Vec3?,
    val materialId: String,
    val wastePresetOverride: String?,
    val customWasteFactorOverride: Double?,
    val laborRateOverride: Double?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "cutouts",
    foreignKeys = [
        ForeignKey(
            entity = SurfaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["surfaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["surfaceId"])]
)
data class CutoutEntity(
    @PrimaryKey val id: String,
    val surfaceId: String,
    val label: String,
    val kind: String,
    val vertices: List<Vec3>,
    val displayOrder: Int
)

data class SurfaceWithCutouts(
    @Embedded val surface: SurfaceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "surfaceId"
    )
    val cutouts: List<CutoutEntity>
)
