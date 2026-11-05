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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcontractor.estimator.spatial.tracking.ArReadiness
import com.arcontractor.estimator.spatial.tracking.ArRecoveryAction
import com.arcontractor.estimator.spatial.tracking.ArSessionController
import com.arcontractor.estimator.spatial.tracking.PolygonSceneRenderer
import com.arcontractor.estimator.spatial.tracking.ReticleHitTester
import com.arcontractor.estimator.spatial.tracking.SurfaceRenderSnapshot
import com.arcontractor.estimator.spatial.tracking.checkArReadiness
import com.arcontractor.estimator.spatial.tracking.guidance
import com.arcontractor.estimator.spatial.tracking.intent
import com.arcontractor.estimator.spatial.tracking.recoveryAction
import com.arcontractor.estimator.spatial.tracking.resolveArReadiness
import com.arcontractor.estimator.spatial.tracking.toArSessionFailure
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var sessionGeneration by remember { mutableIntStateOf(0) }
    var readinessGuidance by remember {
        mutableStateOf(context.checkArReadiness().guidance())
    }
    var depthEnabled by remember { mutableStateOf(true) }
    var isResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            isResumed = event == Lifecycle.Event.ON_RESUME
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.persistMeasurementState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        readinessGuidance = context.resolveArReadiness()
    }
    LaunchedEffect(isResumed, state.isSessionRestartRequested) {
        if (isResumed) {
            readinessGuidance = context.checkArReadiness().guidance()
        }
        if (state.isSessionRestartRequested) {
            sessionGeneration += 1
            viewModel.onAction(ArMeasureAction.SessionRestartHandled)
        }
    }
    val canMountArScene = readinessGuidance.readiness != ArReadiness.UNSUPPORTED_DEVICE &&
        readinessGuidance.readiness != ArReadiness.INSTALLATION_UNAVAILABLE &&
        state.sessionErrorMessage == null
    val readiness = readinessGuidance.readiness
    val isArReady = readiness == ArReadiness.READY

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

    LaunchedEffect(cameraStream, depthEnabled) {
        cameraStream.isDepthOcclusionEnabled = depthEnabled
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
        if (canMountArScene) {
            androidx.compose.runtime.key(sessionGeneration) {
                ARScene(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    materialLoader = materialLoader,
                    cameraStream = cameraStream,
                    planeRenderer = true,
                    childNodes = nodes,
                    sessionConfiguration = { session, config ->
                        sessionController.applyTo(session, config)
                        val nextDepthEnabled = sessionController.isDepthEnabled
                        if (depthEnabled != nextDepthEnabled) {
                            depthEnabled = nextDepthEnabled
                            sessionController.depthStatusMessage?.let { message ->
                                viewModel.onAction(ArMeasureAction.RenderFailed(message))
                            }
                        }
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
                        val failure = exception.toArSessionFailure(context)
                        viewModel.onAction(
                            ArMeasureAction.SessionIssue(
                                message = failure.message,
                                recoveryAction = failure.kind.recoveryAction(),
                                canRetrySession = failure.canRetrySession
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
            }
        }

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

        if (!isArReady || state.sessionErrorMessage != null) {
            ArRecoveryCard(
                title = if (!isArReady) "AR not ready" else "AR session needs attention",
                message = if (!isArReady) readinessGuidance.message else state.sessionErrorMessage.orEmpty(),
                recoveryAction = if (!isArReady) {
                    readinessGuidance.recoveryAction
                } else {
                    state.sessionRecoveryAction ?: ArRecoveryAction.RETRY
                },
                canRetrySession = if (!isArReady) {
                    readinessGuidance.recoveryAction == ArRecoveryAction.RETRY
                } else {
                    state.canRetrySession
                },
                onRetrySession = {
                    if (!isArReady) {
                        readinessGuidance = context.resolveArReadiness(requestInstall = true)
                    }
                    viewModel.onAction(ArMeasureAction.RetrySession)
                },
                onBackToQuote = onBackToQuote,
                openSettings = { action ->
                    action.intent(context)?.let(context::startActivity)
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp)
            )
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
private fun ArRecoveryCard(
    title: String,
    message: String,
    recoveryAction: ArRecoveryAction,
    canRetrySession: Boolean,
    onRetrySession: () -> Unit,
    onBackToQuote: () -> Unit,
    openSettings: (ArRecoveryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF01A1715),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB547).copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            if (canRetrySession) {
                OutlinedButton(
                    onClick = onRetrySession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RETRY AR SESSION")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (recoveryAction == ArRecoveryAction.OPEN_CAMERA_SETTINGS) {
                OutlinedButton(
                    onClick = { openSettings(recoveryAction) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OPEN CAMERA SETTINGS")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onBackToQuote,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK TO QUOTE")
            }
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
