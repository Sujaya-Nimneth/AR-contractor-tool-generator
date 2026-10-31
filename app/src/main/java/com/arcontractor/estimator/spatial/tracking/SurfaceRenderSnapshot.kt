package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.Vec3

/**
 * Render-only projection of the measurement state. It intentionally has no dependency on Compose or
 * the UI state machine, keeping the Filament renderer reusable and independently testable.
 */
data class SurfaceRenderSnapshot(
    val outerVertices: List<Vec3> = emptyList(),
    val outerClosed: Boolean = false,
    val activeCutoutVertices: List<Vec3> = emptyList(),
    val surfaceAnalysis: AnalyzedSurfacePolygon? = null,
    val previewPoint: Vec3? = null
)
