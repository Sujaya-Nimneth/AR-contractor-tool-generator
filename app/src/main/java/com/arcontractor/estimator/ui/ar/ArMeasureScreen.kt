package com.arcontractor.estimator.ui.ar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcontractor.estimator.spatial.tracking.ArSessionController
import com.arcontractor.estimator.spatial.tracking.PolygonSceneRenderer
import com.arcontractor.estimator.spatial.tracking.ReticleHitTester
import com.arcontractor.estimator.spatial.tracking.SurfaceRenderSnapshot
import com.arcontractor.estimator.ui.quote.MeasurementCapture
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.delay

/**
 * Full-screen AR measurement workspace. ARCore telemetry flows into the ViewModel, while user
 * intents flow back through [ArMeasureAction] and SceneView renders an immutable domain snapshot.
 */
@Composable
fun ArMeasureScreen(
    viewModel: ArMeasureViewModel = viewModel(),
    onSaveMeasurement: (MeasurementCapture) -> Unit = {},
    onBackToQuote: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    val nodes: MutableList<Node> = rememberNodes()
    val renderer = remember(engine, materialLoader) {
        PolygonSceneRenderer(engine, materialLoader)
    }
    val hitTester = remember { ReticleHitTester() }
    val sessionController = remember { ArSessionController() }
    var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }

    val renderSnapshot = remember(
        state.outerVertices,
        state.outerClosed,
        state.activeCutoutVertices,
        state.surfaceAnalysis,
        state.reticleSample
    ) {
        SurfaceRenderSnapshot(
            outerVertices = state.outerVertices,
            outerClosed = state.outerClosed,
            activeCutoutVertices = state.activeCutoutVertices,
            surfaceAnalysis = state.surfaceAnalysis,
            previewPoint = state.reticleSample
                ?.takeIf { it.canPlaceVertex }
                ?.pose
                ?.position
        )
    }

    LaunchedEffect(cameraStream) {
        cameraStream.isDepthOcclusionEnabled = true
    }
    LaunchedEffect(state.referencePose, state.surfaceBasis) {
        hitTester.setSurfaceLock(state.referencePose, state.surfaceBasis)
    }
    LaunchedEffect(renderSnapshot) {
        if (!renderer.render(renderSnapshot, nodes)) {
            viewModel.onAction(
                ArMeasureAction.RenderFailed(
                    "The 3D surface preview could not be rendered. Measurement data is intact."
                )
            )
        }
    }
    LaunchedEffect(state.message, state.sessionErrorMessage) {
        if (state.message != null && state.sessionErrorMessage == null) {
            delay(4_500)
            viewModel.onAction(ArMeasureAction.ClearMessage)
        }
    }
    DisposableEffect(renderer, nodes) {
        onDispose {
            renderer.clear(nodes)
            hitTester.setSurfaceLock(null, null)
            arSceneView = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            cameraStream = cameraStream,
            planeRenderer = true,
            childNodes = nodes,
            sessionConfiguration = { _, config ->
                sessionController.applyTo(config)
            },
            onSessionCreated = {
                viewModel.onAction(ArMeasureAction.TrackingChanged(true))
            },
            onSessionResumed = {
                viewModel.onAction(ArMeasureAction.TrackingChanged(true))
            },
            onSessionPaused = {
                hitTester.onTrackingLost()
                viewModel.onAction(
                    ArMeasureAction.TrackingChanged(
                        isTracking = false,
                        failureMessage = "AR session paused. Resume the camera to continue."
                    )
                )
            },
            onSessionFailed = { exception ->
                viewModel.onAction(
                    ArMeasureAction.SessionFailed(
                        exception.message ?: "ARCore could not start on this device."
                    )
                )
            },
            onSessionUpdated = { _, frame ->
                val camera = frame.camera
                if (camera.trackingState == TrackingState.TRACKING) {
                    viewModel.onAction(ArMeasureAction.TrackingChanged(true))
                    val sample = arSceneView?.let { view ->
                        hitTester.sample(
                            frame = frame,
                            viewportWidthPx = view.width,
                            viewportHeightPx = view.height
                        )
                    }
                    viewModel.onAction(ArMeasureAction.ReticleUpdated(sample))
                } else {
                    hitTester.onTrackingLost()
                    viewModel.onAction(
                        ArMeasureAction.TrackingChanged(
                            isTracking = false,
                            failureMessage = camera.trackingFailureReason.userMessage()
                        )
                    )
                    viewModel.onAction(ArMeasureAction.ReticleUpdated(null))
                }
            },
            onTrackingFailureChanged = { reason ->
                if (reason != null && reason != TrackingFailureReason.NONE) {
                    viewModel.onAction(
                        ArMeasureAction.TrackingChanged(
                            isTracking = false,
                            failureMessage = reason.userMessage()
                        )
                    )
                }
            },
            onViewCreated = {
                arSceneView = this
            }
        )

        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            MeasurementTopHud(
                state = state,
                onBackToQuote = onBackToQuote
            )
            AnimatedVisibility(
                visible = !state.isTracking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TrackingGuidance(
                    message = state.trackingFailureMessage
                        ?: state.sessionErrorMessage
                        ?: "Tracking is unavailable. Move the device slowly across the surface."
                )
            }
        }

        AnimatedVisibility(
            visible = state.phase != ArMeasurePhase.COMPLETED,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CenterReticle(state)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            MeasurementMessage(
                message = state.message,
                onDismiss = { viewModel.onAction(ArMeasureAction.ClearMessage) },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            MeasurementBottomHud(
                state = state,
                onAction = viewModel::onAction,
                onSaveMeasurement = {
                    onSaveMeasurement(
                        MeasurementCapture(
                            outerVertices = state.outerVertices,
                            referenceNormal = state.referencePose?.normal,
                            cutouts = state.cutouts
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TrackingGuidance(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xE6201315),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFFF5A5F).copy(alpha = 0.68f)
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            color = Color(0xFFFFD7D8),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun TrackingFailureReason.userMessage(): String = when (this) {
    TrackingFailureReason.NONE -> "AR tracking is recovering."
    TrackingFailureReason.BAD_STATE -> "ARCore is restarting. Hold the camera still."
    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Increase the light level on the surface."
    TrackingFailureReason.EXCESSIVE_MOTION -> "Move the phone more slowly."
    TrackingFailureReason.INSUFFICIENT_FEATURES ->
        "Aim at a textured edge, corner, or fixture for better tracking."

    TrackingFailureReason.CAMERA_UNAVAILABLE ->
        "The rear camera is unavailable. Close other camera apps and try again."
}
