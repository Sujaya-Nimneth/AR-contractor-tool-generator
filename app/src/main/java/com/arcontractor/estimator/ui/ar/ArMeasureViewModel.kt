package com.arcontractor.estimator.ui.ar

import androidx.lifecycle.ViewModel
import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.CoplanarityConfig
import com.arcontractor.estimator.domain.geometry.CoplanarityRejectionReason
import com.arcontractor.estimator.domain.geometry.CoplanarityResult
import com.arcontractor.estimator.domain.geometry.CoplanarityValidator
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.CutoutValidationPolicy
import com.arcontractor.estimator.domain.geometry.PlanarBasis
import com.arcontractor.estimator.domain.geometry.PlanarProjector
import com.arcontractor.estimator.domain.geometry.PolygonGeometryEngine
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.Vec3
import com.arcontractor.estimator.spatial.tracking.ReticleSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the complete Phase 2 measurement state machine and its bounded undo/redo history.
 *
 * Geometry analysis is recomputed from raw 3D anchors after every structural edit. The domain
 * engine remains the only source of area, perimeter, triangulation, and flatness calculations.
 */
class ArMeasureViewModel : ViewModel() {
    private val coplanarityValidator = CoplanarityValidator(
        CoplanarityConfig(
            maxNormalAngleDegrees = 15.0,
            maxOrthogonalDistanceMeters = 0.06
        )
    )

    private val undoStack = ArrayDeque<MeasurementSnapshot>()
    private val redoStack = ArrayDeque<MeasurementSnapshot>()

    private val _uiState = MutableStateFlow(ArMeasureUiState())
    val uiState: StateFlow<ArMeasureUiState> = _uiState.asStateFlow()

    fun onAction(action: ArMeasureAction) {
        when (action) {
            is ArMeasureAction.ReticleUpdated -> updateReticle(action.sample)
            is ArMeasureAction.TrackingChanged -> updateTracking(action)
            is ArMeasureAction.SessionFailed -> showSessionError(action.message)
            is ArMeasureAction.RenderFailed -> _uiState.value =
                _uiState.value.copy(message = action.message)

            is ArMeasureAction.SelectCutoutKind -> selectCutoutKind(action.kind)
            ArMeasureAction.PlacePoint -> placePoint()
            ArMeasureAction.CloseOuterPolygon -> closeOuterPolygon()
            ArMeasureAction.CompleteCutout -> completeCutout()
            ArMeasureAction.CancelCutout -> cancelCutout()
            ArMeasureAction.FinishMeasurement -> finishMeasurement()
            ArMeasureAction.Undo -> undo()
            ArMeasureAction.Redo -> redo()
            ArMeasureAction.Reset -> reset()
            ArMeasureAction.ClearMessage -> _uiState.value = _uiState.value.copy(message = null)
        }
    }

    private fun updateReticle(sample: ReticleSample?) {
        val current = _uiState.value
        val validated = sample?.let { validateSample(it, current) }
        val nextPhase = when {
            current.phase != ArMeasurePhase.SCANNING &&
                current.phase != ArMeasurePhase.LOCKING_PLANE -> current.phase

            validated?.isStableForLock == true -> ArMeasurePhase.LOCKING_PLANE
            else -> ArMeasurePhase.SCANNING
        }
        _uiState.value = current.copy(
            phase = nextPhase,
            reticleSample = validated
        )
    }

    private fun updateTracking(action: ArMeasureAction.TrackingChanged) {
        val current = _uiState.value
        val nextPhase = if (!action.isTracking &&
            current.outerVertices.isEmpty() &&
            current.phase in setOf(ArMeasurePhase.SCANNING, ArMeasurePhase.LOCKING_PLANE)
        ) {
            ArMeasurePhase.SCANNING
        } else {
            current.phase
        }
        _uiState.value = current.copy(
            phase = nextPhase,
            isTracking = action.isTracking,
            trackingFailureMessage = action.failureMessage,
            reticleSample = if (action.isTracking) current.reticleSample else null
        )
    }

    private fun showSessionError(message: String) {
        _uiState.value = _uiState.value.copy(
            isTracking = false,
            reticleSample = null,
            sessionErrorMessage = message,
            message = message
        )
    }

    private fun selectCutoutKind(kind: CutoutKind) {
        _uiState.value = _uiState.value.copy(selectedCutoutKind = kind)
    }

    private fun placePoint() {
        val current = _uiState.value
        val sample = current.reticleSample?.let { validateSample(it, current) }
        if (sample?.canPlaceVertex != true) {
            val blockedState = current.copy(reticleSample = sample)
            _uiState.value = blockedState.copy(message = placementBlockedMessage(blockedState))
            return
        }

        when (current.phase) {
            ArMeasurePhase.LOCKING_PLANE -> {
                pushUndo(current)
                _uiState.value = current.copy(
                    phase = ArMeasurePhase.PLACING_OUTER_BOUNDARY,
                    referencePose = sample.pose,
                    outerVertices = listOf(sample.pose.position),
                    message = null,
                    canUndo = true,
                    canRedo = false
                )
            }

            ArMeasurePhase.PLACING_OUTER_BOUNDARY -> {
                if (isDuplicateOfLastVertex(current.outerVertices, sample.pose.position)) {
                    _uiState.value = current.copy(
                        reticleSample = sample,
                        message = "Move the reticle to the next corner before placing another point."
                    )
                    return
                }
                pushUndo(current)
                val vertices = current.outerVertices + sample.pose.position
                _uiState.value = current.copy(
                    reticleSample = sample,
                    outerVertices = vertices,
                    surfaceBasis = computeBasis(vertices, current.referencePose),
                    surfaceAnalysis = analyzePreview(vertices, current.cutouts, current.referencePose),
                    message = null,
                    canUndo = true,
                    canRedo = false
                )
            }

            ArMeasurePhase.PLACING_CUTOUT -> {
                if (isDuplicateOfLastVertex(current.activeCutoutVertices, sample.pose.position)) {
                    _uiState.value = current.copy(
                        reticleSample = sample,
                        message = "Move the reticle to the next opening corner."
                    )
                    return
                }
                pushUndo(current)
                _uiState.value = current.copy(
                    reticleSample = sample,
                    activeCutoutVertices = current.activeCutoutVertices + sample.pose.position,
                    message = null,
                    canUndo = true,
                    canRedo = false
                )
            }

            ArMeasurePhase.SCANNING,
            ArMeasurePhase.COMPLETED -> Unit
        }
    }

    private fun closeOuterPolygon() {
        val current = _uiState.value
        if (!current.canCloseOuterPolygon) {
            _uiState.value = current.copy(
                message = "Place at least three outer corners before closing the surface."
            )
            return
        }

        val analysis = runCatching {
            PolygonGeometryEngine.analyze3DSurface(
                outerVertices3D = current.outerVertices,
                referenceNormal = current.referencePose?.normal,
                cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
            )
        }.getOrElse { error ->
            _uiState.value = current.copy(
                message = error.message ?: "The outer boundary is not a valid polygon."
            )
            return
        }

        pushUndo(current)
        _uiState.value = current.copy(
            phase = ArMeasurePhase.PLACING_CUTOUT,
            outerClosed = true,
            surfaceBasis = analysis.basis,
            surfaceAnalysis = analysis,
            activeCutoutVertices = emptyList(),
            message = null,
            canUndo = true,
            canRedo = false
        )
    }

    private fun completeCutout() {
        val current = _uiState.value
        if (!current.canCompleteCutout) {
            _uiState.value = current.copy(
                message = "Place at least three cutout corners before finishing the opening."
            )
            return
        }

        val cutout = CutoutPolygonInput(
            id = "cutout-${current.cutouts.size + 1}",
            label = cutoutLabel(current.selectedCutoutKind),
            vertices3D = current.activeCutoutVertices,
            kind = current.selectedCutoutKind
        )
        val updatedCutouts = current.cutouts + cutout
        val analysis = runCatching {
            PolygonGeometryEngine.analyze3DSurface(
                outerVertices3D = current.outerVertices,
                cutouts = updatedCutouts,
                referenceNormal = current.referencePose?.normal,
                cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
            )
        }.getOrElse { error ->
            _uiState.value = current.copy(
                message = error.message ?: "The cutout must remain inside the measured surface."
            )
            return
        }

        pushUndo(current)
        _uiState.value = current.copy(
            cutouts = updatedCutouts,
            activeCutoutVertices = emptyList(),
            surfaceBasis = analysis.basis,
            surfaceAnalysis = analysis,
            message = null,
            canUndo = true,
            canRedo = false
        )
    }

    private fun cancelCutout() {
        val current = _uiState.value
        if (current.activeCutoutVertices.isEmpty()) return
        pushUndo(current)
        _uiState.value = current.copy(
            activeCutoutVertices = emptyList(),
            message = "Cutout draft cleared.",
            canUndo = true,
            canRedo = false
        )
    }

    private fun finishMeasurement() {
        val current = _uiState.value
        if (!current.canFinishMeasurement) {
            _uiState.value = current.copy(
                message = "Finish or cancel the active cutout before completing the surface."
            )
            return
        }
        pushUndo(current)
        _uiState.value = current.copy(
            phase = ArMeasurePhase.COMPLETED,
            reticleSample = null,
            message = "Measurement complete.",
            canUndo = true,
            canRedo = false
        )
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value
        redoStack.addLast(current.toSnapshot())
        val restored = undoStack.removeLast()
        _uiState.value = restoreSnapshot(
            snapshot = restored,
            previous = current,
            message = "Undid last measurement action."
        )
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value
        undoStack.addLast(current.toSnapshot())
        val restored = redoStack.removeLast()
        _uiState.value = restoreSnapshot(
            snapshot = restored,
            previous = current,
            message = "Redid measurement action."
        )
    }

    private fun reset() {
        undoStack.clear()
        redoStack.clear()
        val current = _uiState.value
        _uiState.value = ArMeasureUiState(
            isTracking = current.isTracking,
            trackingFailureMessage = current.trackingFailureMessage,
            sessionErrorMessage = current.sessionErrorMessage
        )
    }

    private fun validateSample(
        sample: ReticleSample,
        state: ArMeasureUiState
    ): ReticleSample {
        if (!sample.isStableForLock) return sample.copy(coplanarity = null)

        val validation: CoplanarityResult? = when {
            state.surfaceBasis != null -> coplanarityValidator.validateAgainstBasis(
                basis = state.surfaceBasis,
                candidatePosition = sample.pose.position,
                candidateNormal = sample.pose.normal
            )

            state.referencePose != null -> coplanarityValidator.validatePose(
                referencePose = state.referencePose,
                candidatePose = sample.pose
            )

            else -> null
        }
        return sample.copy(coplanarity = validation)
    }

    private fun computeBasis(
        vertices: List<Vec3>,
        referencePose: Pose3D?
    ): PlanarBasis? {
        if (vertices.size < 3) return null
        return runCatching {
            PlanarProjector.computeBasis(vertices, referencePose?.normal)
        }.getOrNull()
    }

    private fun analyzePreview(
        outerVertices: List<Vec3>,
        cutouts: List<CutoutPolygonInput>,
        referencePose: Pose3D?
    ): AnalyzedSurfacePolygon? {
        if (outerVertices.size < 3) return null
        return runCatching {
            PolygonGeometryEngine.analyze3DSurface(
                outerVertices3D = outerVertices,
                cutouts = cutouts,
                referenceNormal = referencePose?.normal,
                cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
            )
        }.getOrNull()
    }

    private fun placementBlockedMessage(state: ArMeasureUiState): String = when {
        !state.isTracking -> "AR tracking is unavailable. Point the camera at the surface again."
        state.reticleSample == null -> "No surface at the reticle. Move closer and scan slowly."
        state.reticleSample.isStableForLock.not() ->
            "Hold the reticle steady until the surface locks."

        state.reticleSample.coplanarity?.rejectionReason ==
            CoplanarityRejectionReason.NORMAL_MISALIGNMENT ->
            "This point is not on the locked surface. Align with the measured plane."

        state.reticleSample.coplanarity?.rejectionReason ==
            CoplanarityRejectionReason.PLANE_OFFSET_EXCEEDED ->
            "This point is too far in front of or behind the locked plane."

        state.reticleSample.coplanarity?.rejectionReason ==
            CoplanarityRejectionReason.DEGENERATE_NORMAL ->
            "The surface normal is unstable. Move the reticle to a clearer area."

        else -> "The reticle is not ready for placement."
    }

    private fun pushUndo(current: ArMeasureUiState) {
        undoStack.addLast(current.toSnapshot())
        if (undoStack.size > MAX_HISTORY_DEPTH) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    private fun restoreSnapshot(
        snapshot: MeasurementSnapshot,
        previous: ArMeasureUiState,
        message: String
    ): ArMeasureUiState {
        val restored = ArMeasureUiState(
            phase = snapshot.phase,
            isTracking = previous.isTracking,
            trackingFailureMessage = previous.trackingFailureMessage,
            sessionErrorMessage = previous.sessionErrorMessage,
            reticleSample = previous.reticleSample,
            referencePose = snapshot.referencePose,
            surfaceBasis = snapshot.surfaceBasis,
            outerVertices = snapshot.outerVertices,
            outerClosed = snapshot.outerClosed,
            activeCutoutVertices = snapshot.activeCutoutVertices,
            selectedCutoutKind = snapshot.selectedCutoutKind,
            cutouts = snapshot.cutouts,
            surfaceAnalysis = snapshot.surfaceAnalysis,
            message = message,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        return restored.copy(
            reticleSample = restored.reticleSample?.let { validateSample(it, restored) }
        )
    }

    private fun ArMeasureUiState.toSnapshot() = MeasurementSnapshot(
        phase = phase,
        referencePose = referencePose,
        surfaceBasis = surfaceBasis,
        outerVertices = outerVertices,
        outerClosed = outerClosed,
        activeCutoutVertices = activeCutoutVertices,
        selectedCutoutKind = selectedCutoutKind,
        cutouts = cutouts,
        surfaceAnalysis = surfaceAnalysis
    )

    private fun cutoutLabel(kind: CutoutKind): String = when (kind) {
        CutoutKind.WINDOW -> "Window"
        CutoutKind.DOOR -> "Door"
        CutoutKind.VENT -> "Vent"
        CutoutKind.ISLAND_OR_FIXTURE -> "Fixture"
        CutoutKind.OTHER -> "Opening"
    }

    private fun isDuplicateOfLastVertex(vertices: List<Vec3>, candidate: Vec3): Boolean =
        vertices.lastOrNull()?.distanceTo(candidate)?.let {
            it < MIN_VERTEX_SEPARATION_METERS
        } ?: false

    private data class MeasurementSnapshot(
        val phase: ArMeasurePhase,
        val referencePose: Pose3D?,
        val surfaceBasis: PlanarBasis?,
        val outerVertices: List<Vec3>,
        val outerClosed: Boolean,
        val activeCutoutVertices: List<Vec3>,
        val selectedCutoutKind: CutoutKind,
        val cutouts: List<CutoutPolygonInput>,
        val surfaceAnalysis: AnalyzedSurfacePolygon?
    )

    private companion object {
        const val MAX_HISTORY_DEPTH = 50
        const val MIN_VERTEX_SEPARATION_METERS = 0.005
    }
}
