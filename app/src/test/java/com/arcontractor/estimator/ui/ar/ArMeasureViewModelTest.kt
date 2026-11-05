package com.arcontractor.estimator.ui.ar

import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.Vec3
import com.arcontractor.estimator.spatial.tracking.ArRecoveryAction
import com.arcontractor.estimator.spatial.tracking.ReticleSample
import com.arcontractor.estimator.spatial.tracking.SurfaceHitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArMeasureViewModelTest {

    @Test
    fun `stable reticle locks plane and places first outer vertex`() {
        val viewModel = ArMeasureViewModel()
        updateReticle(viewModel, point(0.0, 0.0, 0.0))
        assertEquals(ArMeasurePhase.LOCKING_PLANE, viewModel.uiState.value.phase)

        viewModel.onAction(ArMeasureAction.PlacePoint)

        val state = viewModel.uiState.value
        assertEquals(ArMeasurePhase.PLACING_OUTER_BOUNDARY, state.phase)
        assertEquals(1, state.outerVertices.size)
        assertNotNull(state.referencePose)
        assertTrue(state.canUndo)
    }

    @Test
    fun `closing square boundary computes domain area and perimeter`() {
        val viewModel = squareSurfaceViewModel()
        viewModel.onAction(ArMeasureAction.CloseOuterPolygon)

        val state = viewModel.uiState.value
        assertEquals(ArMeasurePhase.PLACING_CUTOUT, state.phase)
        assertTrue(state.outerClosed)
        assertEquals(1.0, state.netAreaSqMeters ?: 0.0, 1e-6)
        assertEquals(4.0, state.outerPerimeterMeters ?: 0.0, 1e-6)
        assertTrue(state.isWithinFlatnessTolerance == true)
    }

    @Test
    fun `completed door cutout subtracts exact area and can undo redo`() {
        val viewModel = squareSurfaceViewModel()
        viewModel.onAction(ArMeasureAction.CloseOuterPolygon)
        viewModel.onAction(ArMeasureAction.SelectCutoutKind(CutoutKind.DOOR))
        placeCutoutSquare(viewModel)
        viewModel.onAction(ArMeasureAction.CompleteCutout)

        val completedCutoutState = viewModel.uiState.value
        assertEquals(1, completedCutoutState.cutouts.size)
        assertTrue(completedCutoutState.activeCutoutVertices.isEmpty())
        assertEquals(0.75, completedCutoutState.netAreaSqMeters ?: 0.0, 1e-6)
        assertEquals(0.25, completedCutoutState.cutoutAreaSqMeters ?: 0.0, 1e-6)
        assertTrue(completedCutoutState.canUndo)

        viewModel.onAction(ArMeasureAction.Undo)
        val undone = viewModel.uiState.value
        assertTrue(undone.cutouts.isEmpty())
        assertEquals(1.0, undone.netAreaSqMeters ?: 0.0, 1e-6)
        assertTrue(undone.canRedo)

        viewModel.onAction(ArMeasureAction.Redo)
        val redone = viewModel.uiState.value
        assertEquals(1, redone.cutouts.size)
        assertEquals(0.75, redone.netAreaSqMeters ?: 0.0, 1e-6)
    }

    @Test
    fun `non coplanar point is rejected without mutating vertices`() {
        val viewModel = ArMeasureViewModel()
        updateReticle(viewModel, point(0.0, 0.0, 0.0))
        viewModel.onAction(ArMeasureAction.PlacePoint)
        updateReticle(viewModel, point(1.0, 0.2, 0.0))

        viewModel.onAction(ArMeasureAction.PlacePoint)

        val state = viewModel.uiState.value
        assertEquals(1, state.outerVertices.size)
        assertFalse(state.reticleSample?.canPlaceVertex == true)
        assertNotNull(state.message)
    }

    @Test
    fun `duplicate consecutive vertex is rejected`() {
        val viewModel = ArMeasureViewModel()
        val firstPoint = point(0.0, 0.0, 0.0)
        updateReticle(viewModel, firstPoint)
        viewModel.onAction(ArMeasureAction.PlacePoint)
        updateReticle(viewModel, firstPoint)

        viewModel.onAction(ArMeasureAction.PlacePoint)

        assertEquals(1, viewModel.uiState.value.outerVertices.size)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun `undo removes vertex and redo restores it`() {
        val viewModel = ArMeasureViewModel()
        updateReticle(viewModel, point(0.0, 0.0, 0.0))
        viewModel.onAction(ArMeasureAction.PlacePoint)
        updateReticle(viewModel, point(1.0, 0.0, 0.0))
        viewModel.onAction(ArMeasureAction.PlacePoint)
        assertEquals(2, viewModel.uiState.value.outerVertices.size)

        viewModel.onAction(ArMeasureAction.Undo)
        assertEquals(1, viewModel.uiState.value.outerVertices.size)

        viewModel.onAction(ArMeasureAction.Redo)
        assertEquals(2, viewModel.uiState.value.outerVertices.size)
    }

    @Test
    fun `finish measurement requires no active cutout draft`() {
        val viewModel = squareSurfaceViewModel()
        viewModel.onAction(ArMeasureAction.CloseOuterPolygon)
        updateReticle(viewModel, point(0.25, 0.0, 0.25))
        viewModel.onAction(ArMeasureAction.PlacePoint)

        viewModel.onAction(ArMeasureAction.FinishMeasurement)

        assertEquals(ArMeasurePhase.PLACING_CUTOUT, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun `reset clears measurement and history`() {
        val viewModel = squareSurfaceViewModel()
        viewModel.onAction(ArMeasureAction.Reset)

        val state = viewModel.uiState.value
        assertEquals(ArMeasurePhase.SCANNING, state.phase)
        assertTrue(state.outerVertices.isEmpty())
        assertNull(state.surfaceAnalysis)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun `successful tracking clears a stale session error`() {
        val viewModel = ArMeasureViewModel()
        viewModel.onAction(
            ArMeasureAction.SessionIssue(
                message = "ARCore could not start.",
                recoveryAction = ArRecoveryAction.RETRY,
                canRetrySession = true
            )
        )
        assertNotNull(viewModel.uiState.value.sessionErrorMessage)

        viewModel.onAction(ArMeasureAction.TrackingChanged(true))

        val recovered = viewModel.uiState.value
        assertNull(recovered.sessionErrorMessage)
        assertNull(recovered.sessionRecoveryAction)
        assertFalse(recovered.canRetrySession)
        assertTrue(recovered.isTracking)
    }

    @Test
    fun `retry clears the session error and requests a fresh AR view`() {
        val viewModel = ArMeasureViewModel()
        viewModel.onAction(ArMeasureAction.SessionFailed("Camera unavailable"))

        viewModel.onAction(ArMeasureAction.RetrySession)

        val retrying = viewModel.uiState.value
        assertNull(retrying.sessionErrorMessage)
        assertFalse(retrying.canRetrySession)
        assertTrue(retrying.isSessionRestartRequested)

        viewModel.onAction(ArMeasureAction.SessionRestartHandled)
        assertFalse(viewModel.uiState.value.isSessionRestartRequested)
    }

    @Test
    fun `reset clears terminal session state`() {
        val viewModel = ArMeasureViewModel()
        viewModel.onAction(
            ArMeasureAction.SessionIssue(
                message = "Unsupported device",
                recoveryAction = ArRecoveryAction.BACK_TO_QUOTE,
                canRetrySession = false
            )
        )

        viewModel.onAction(ArMeasureAction.Reset)

        val state = viewModel.uiState.value
        assertNull(state.sessionErrorMessage)
        assertNull(state.sessionRecoveryAction)
        assertFalse(state.canRetrySession)
    }

    private fun squareSurfaceViewModel(): ArMeasureViewModel {
        val viewModel = ArMeasureViewModel()
        val corners = listOf(
            point(0.0, 0.0, 0.0),
            point(1.0, 0.0, 0.0),
            point(1.0, 0.0, 1.0),
            point(0.0, 0.0, 1.0)
        )
        corners.forEach { pose ->
            updateReticle(viewModel, pose)
            viewModel.onAction(ArMeasureAction.PlacePoint)
        }
        return viewModel
    }

    private fun placeCutoutSquare(viewModel: ArMeasureViewModel) {
        listOf(
            point(0.25, 0.0, 0.25),
            point(0.75, 0.0, 0.25),
            point(0.75, 0.0, 0.75),
            point(0.25, 0.0, 0.75)
        ).forEach { pose ->
            updateReticle(viewModel, pose)
            viewModel.onAction(ArMeasureAction.PlacePoint)
        }
    }

    private fun updateReticle(viewModel: ArMeasureViewModel, pose: Pose3D) {
        viewModel.onAction(
            ArMeasureAction.ReticleUpdated(
                ReticleSample(
                    rawPose = pose,
                    pose = pose,
                    source = SurfaceHitSource.PLANE,
                    isStableForLock = true,
                    rollingJitterMeters = 0.0
                )
            )
        )
    }

    private fun point(x: Double, y: Double, z: Double) = Pose3D(
        position = Vec3(x, y, z),
        normal = Vec3.UNIT_Y,
        timestampNanos = 1_000_000L
    )
}
