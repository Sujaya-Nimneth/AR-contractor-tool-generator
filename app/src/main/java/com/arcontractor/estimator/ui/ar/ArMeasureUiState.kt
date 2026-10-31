package com.arcontractor.estimator.ui.ar

import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.PlanarBasis
import com.arcontractor.estimator.domain.geometry.PlanarProjectionResult
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.Vec3
import com.arcontractor.estimator.spatial.tracking.ReticleSample

/**
 * Unidirectional measurement workflow states defined by the Phase 2 contract.
 */
enum class ArMeasurePhase {
    SCANNING,
    LOCKING_PLANE,
    PLACING_OUTER_BOUNDARY,
    PLACING_CUTOUT,
    COMPLETED
}

/**
 * Immutable UI state for the AR measurement screen.
 */
data class ArMeasureUiState(
    val phase: ArMeasurePhase = ArMeasurePhase.SCANNING,
    val isTracking: Boolean = true,
    val trackingFailureMessage: String? = null,
    val sessionErrorMessage: String? = null,
    val reticleSample: ReticleSample? = null,
    val referencePose: Pose3D? = null,
    val surfaceBasis: PlanarBasis? = null,
    val outerVertices: List<Vec3> = emptyList(),
    val outerClosed: Boolean = false,
    val activeCutoutVertices: List<Vec3> = emptyList(),
    val selectedCutoutKind: CutoutKind = CutoutKind.DOOR,
    val cutouts: List<CutoutPolygonInput> = emptyList(),
    val surfaceAnalysis: AnalyzedSurfacePolygon? = null,
    val message: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val hasSurfaceLock: Boolean
        get() = referencePose != null

    val currentVertexCount: Int
        get() = when {
            activeCutoutVertices.isNotEmpty() -> activeCutoutVertices.size
            else -> outerVertices.size
        }

    val canPlaceCurrentPoint: Boolean
        get() = when (phase) {
            ArMeasurePhase.LOCKING_PLANE,
            ArMeasurePhase.PLACING_OUTER_BOUNDARY,
            ArMeasurePhase.PLACING_CUTOUT -> reticleSample?.canPlaceVertex == true

            ArMeasurePhase.SCANNING,
            ArMeasurePhase.COMPLETED -> false
        }

    val canCloseOuterPolygon: Boolean
        get() = phase == ArMeasurePhase.PLACING_OUTER_BOUNDARY && outerVertices.size >= 3

    val canCompleteCutout: Boolean
        get() = phase == ArMeasurePhase.PLACING_CUTOUT && activeCutoutVertices.size >= 3

    val canFinishMeasurement: Boolean
        get() = phase == ArMeasurePhase.PLACING_CUTOUT &&
            outerClosed &&
            activeCutoutVertices.isEmpty() &&
            surfaceAnalysis != null

    val lastMeasuredEdgeMeters: Double?
        get() {
            val vertices = activeCutoutVertices.ifEmpty { outerVertices }
            return if (vertices.size >= 2) {
                vertices[vertices.lastIndex].distanceTo(vertices[vertices.lastIndex - 1])
            } else {
                null
            }
        }

    val liveEdgeMeters: Double?
        get() {
            val vertices = activeCutoutVertices.ifEmpty { outerVertices }
            val reticlePose = reticleSample?.takeIf { it.canPlaceVertex }?.pose ?: return null
            return vertices.lastOrNull()?.distanceTo(reticlePose.position)
        }

    val netAreaSqMeters: Double?
        get() = surfaceAnalysis?.netAreaSqMeters

    val grossAreaSqMeters: Double?
        get() = surfaceAnalysis?.grossAreaSqMeters

    val outerPerimeterMeters: Double?
        get() = surfaceAnalysis?.outerPerimeterMeters

    val cutoutAreaSqMeters: Double?
        get() = surfaceAnalysis?.cutoutAreaSqMeters

    val flatnessRmsMillimeters: Double?
        get() = surfaceAnalysis?.rmsPlanarErrorMeters?.times(1_000.0)

    val isWithinFlatnessTolerance: Boolean?
        get() = surfaceAnalysis?.let {
            it.rmsPlanarErrorMeters <= PlanarProjectionResult.STANDARD_FLATNESS_RMS_TOLERANCE_METERS
        }

}

/**
 * User intents accepted by [ArMeasureViewModel]. ARCore frame callbacks only produce telemetry;
 * every measurement mutation is an explicit user action.
 */
sealed interface ArMeasureAction {
    data class ReticleUpdated(val sample: ReticleSample?) : ArMeasureAction
    data class TrackingChanged(
        val isTracking: Boolean,
        val failureMessage: String? = null
    ) : ArMeasureAction

    data class SessionFailed(val message: String) : ArMeasureAction
    data class RenderFailed(val message: String) : ArMeasureAction
    data class SelectCutoutKind(val kind: CutoutKind) : ArMeasureAction

    data object PlacePoint : ArMeasureAction
    data object CloseOuterPolygon : ArMeasureAction
    data object CompleteCutout : ArMeasureAction
    data object CancelCutout : ArMeasureAction
    data object FinishMeasurement : ArMeasureAction
    data object Undo : ArMeasureAction
    data object Redo : ArMeasureAction
    data object Reset : ArMeasureAction
    data object ClearMessage : ArMeasureAction
}
