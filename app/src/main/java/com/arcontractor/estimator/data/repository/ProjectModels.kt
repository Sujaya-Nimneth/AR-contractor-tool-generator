package com.arcontractor.estimator.data.repository

import com.arcontractor.estimator.data.local.ProjectEntity
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.ProjectQuoteSummary
import com.arcontractor.estimator.domain.estimation.SurfaceEstimate
import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.Vec3

/**
 * Raw anchors captured by the AR workflow. Derived geometry is intentionally not persisted.
 */
data class SurfaceMeasurementDraft(
    val name: String,
    val outerVertices: List<Vec3>,
    val referenceNormal: Vec3?,
    val cutouts: List<CutoutPolygonInput>,
    val materialId: String,
    val wastePresetOverride: String? = null,
    val customWasteFactorOverride: Double? = null,
    val laborRateOverride: Double? = null
)

/**
 * A persisted surface reconstructed against the current catalog and project tax settings.
 */
data class SurfaceQuoteLine(
    val surfaceId: String,
    val surfaceName: String,
    val materialId: String,
    val outerVertices: List<Vec3>,
    val referenceNormal: Vec3?,
    val cutouts: List<CutoutPolygonInput>,
    val effectiveMaterial: MaterialSpec,
    val analysis: AnalyzedSurfacePolygon?,
    val estimate: SurfaceEstimate?,
    val loadError: String? = null
)

data class ProjectWorkspace(
    val project: ProjectEntity,
    val materials: List<MaterialSpec>,
    val surfaces: List<SurfaceQuoteLine>,
    val quote: ProjectQuoteSummary,
    val loadErrors: List<String>
)

sealed interface MaterialDeleteResult {
    data object Deleted : MaterialDeleteResult
    data class InUse(val surfaceCount: Int) : MaterialDeleteResult
}
