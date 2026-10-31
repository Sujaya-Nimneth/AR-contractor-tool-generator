package com.arcontractor.estimator.ui.ar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcontractor.estimator.domain.geometry.CutoutKind
import java.util.Locale

private val HudAmber = Color(0xFFFFB547)
private val HudMint = Color(0xFF23D5AB)
private val HudCyan = Color(0xFF32D5C7)
private val HudRed = Color(0xFFFF5A5F)
private val HudInk = Color(0xE612161B)
private val HudLine = Color(0x33FFFFFF)

@Composable
internal fun MeasurementTopHud(state: ArMeasureUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.86f),
                        Color.Black.copy(alpha = 0.42f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AR MEASURE",
                    color = HudAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = state.phase.title(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TrackingBadge(state)
        }

        Spacer(Modifier.height(14.dp))
        PhaseProgress(state.phase)
        Spacer(Modifier.height(12.dp))
        MetricStrip(state)
        state.surfaceAnalysis?.let { analysis ->
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChip(
                    label = if (state.isWithinFlatnessTolerance == true) "FLAT SURFACE" else "FLATNESS CHECK",
                    accent = if (state.isWithinFlatnessTolerance == true) HudMint else HudAmber
                )
                HudChip(
                    label = "RMS ${formatMillimeters(analysis.rmsPlanarErrorMeters * 1_000.0)}",
                    accent = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
internal fun CenterReticle(state: ArMeasureUiState) {
    val reticleColor = when {
        !state.isTracking -> HudRed
        state.reticleSample?.canPlaceVertex == true -> HudMint
        state.reticleSample != null -> HudAmber
        else -> Color.White.copy(alpha = 0.72f)
    }
    val transition = rememberInfiniteTransition(label = "reticlePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticlePulseAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(76.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.40f
                drawCircle(
                    color = reticleColor.copy(alpha = 0.12f * pulse),
                    radius = radius * 1.18f,
                    center = center
                )
                drawCircle(
                    color = reticleColor.copy(alpha = 0.95f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = reticleColor,
                    radius = 2.25.dp.toPx(),
                    center = center
                )
                val arm = 12.dp.toPx()
                val gap = 5.dp.toPx()
                drawLine(
                    color = reticleColor,
                    start = Offset(center.x - arm, center.y),
                    end = Offset(center.x - gap, center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = reticleColor,
                    start = Offset(center.x + gap, center.y),
                    end = Offset(center.x + arm, center.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = reticleColor,
                    start = Offset(center.x, center.y - arm),
                    end = Offset(center.x, center.y - gap),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = reticleColor,
                    start = Offset(center.x, center.y + gap),
                    end = Offset(center.x, center.y + arm),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.70f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = reticleColor.copy(alpha = 0.55f)
            )
        ) {
            Text(
                text = state.reticleLabel(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                color = reticleColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
internal fun MeasurementBottomHud(
    state: ArMeasureUiState,
    onAction: (ArMeasureAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.46f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(HudInk)
                .border(1.dp, HudLine, RoundedCornerShape(26.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workflowSummary(state),
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.76f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp
                )
                if (state.cutouts.isNotEmpty()) {
                    HudChip(
                        label = "${state.cutouts.size} OPENING${if (state.cutouts.size == 1) "" else "S"}",
                        accent = HudRed
                    )
                }
            }

            if (state.phase == ArMeasurePhase.PLACING_CUTOUT) {
                Spacer(Modifier.height(12.dp))
                CutoutKindSelector(
                    selectedKind = state.selectedCutoutKind,
                    onSelected = { onAction(ArMeasureAction.SelectCutoutKind(it)) }
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIconButton(
                    label = "Undo",
                    enabled = state.canUndo,
                    onClick = { onAction(ArMeasureAction.Undo) }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                }
                HudIconButton(
                    label = "Redo",
                    enabled = state.canRedo,
                    onClick = { onAction(ArMeasureAction.Redo) }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                }
                Button(
                    onClick = {
                        if (state.phase == ArMeasurePhase.COMPLETED) {
                            onAction(ArMeasureAction.Reset)
                        } else {
                            onAction(ArMeasureAction.PlacePoint)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    enabled = state.primaryActionEnabled(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudAmber,
                        contentColor = Color(0xFF1A1104),
                        disabledContainerColor = Color.White.copy(alpha = 0.10f),
                        disabledContentColor = Color.White.copy(alpha = 0.36f)
                    )
                ) {
                    Text(
                        text = state.primaryActionLabel(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.7.sp
                    )
                }
            }

            val secondaryActions = state.secondaryActions()
            if (secondaryActions.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secondaryActions.forEach { action ->
                        OutlinedButton(
                            onClick = { onAction(action.action) },
                            enabled = action.enabled,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (action.destructive) HudRed.copy(alpha = 0.72f)
                                else Color.White.copy(alpha = 0.25f)
                            )
                        ) {
                            Text(
                                text = action.label,
                                color = if (action.destructive) HudRed else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeasurementMessage(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xF2231A10),
            border = androidx.compose.foundation.BorderStroke(1.dp, HudAmber.copy(alpha = 0.70f))
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFFFE0AE),
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss message",
                        tint = Color(0xFFFFE0AE)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingBadge(state: ArMeasureUiState) {
    val color = if (state.isTracking) HudMint else HudRed
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.48f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (state.isTracking) "TRACKING" else "TRACKING LOST",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun PhaseProgress(phase: ArMeasurePhase) {
    val activeIndex = phase.ordinal.coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(ArMeasurePhase.entries.size) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            index < activeIndex -> HudCyan.copy(alpha = 0.66f)
                            index == activeIndex -> HudAmber
                            else -> Color.White.copy(alpha = 0.18f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun MetricStrip(state: ArMeasureUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            label = "EDGE",
            value = formatLength(state.liveEdgeMeters ?: state.lastMeasuredEdgeMeters),
            accent = HudAmber
        )
        MetricTile(
            label = if (state.outerClosed) "NET AREA" else "AREA",
            value = formatArea(state.netAreaSqMeters),
            accent = HudCyan
        )
        MetricTile(
            label = "PERIMETER",
            value = formatLength(state.outerPerimeterMeters),
            accent = Color.White.copy(alpha = 0.88f)
        )
        MetricTile(
            label = "CORNERS",
            value = state.outerVertices.size.toString().padStart(2, '0'),
            accent = HudMint
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = accent.copy(alpha = 0.78f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun HudChip(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = accent.copy(alpha = 0.11f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.34f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun CutoutKindSelector(
    selectedKind: CutoutKind,
    onSelected: (CutoutKind) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        CutoutKind.entries.forEach { kind ->
            FilterChip(
                selected = selectedKind == kind,
                onClick = { onSelected(kind) },
                label = {
                    Text(
                        text = kind.shortLabel(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    labelColor = Color.White.copy(alpha = 0.68f),
                    selectedContainerColor = HudRed.copy(alpha = 0.20f),
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedKind == kind,
                    borderColor = Color.White.copy(alpha = 0.16f),
                    selectedBorderColor = HudRed.copy(alpha = 0.72f)
                )
            )
        }
    }
}

@Composable
private fun HudIconButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(54.dp)
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) 0.18f else 0.07f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private data class SecondaryAction(
    val label: String,
    val action: ArMeasureAction,
    val enabled: Boolean = true,
    val destructive: Boolean = false
)

private fun ArMeasureUiState.secondaryActions(): List<SecondaryAction> = buildList {
    when (phase) {
        ArMeasurePhase.PLACING_OUTER_BOUNDARY -> {
            add(
                SecondaryAction(
                    label = "CLOSE POLYGON",
                    action = ArMeasureAction.CloseOuterPolygon,
                    enabled = canCloseOuterPolygon
                )
            )
        }

        ArMeasurePhase.PLACING_CUTOUT -> {
            if (activeCutoutVertices.isEmpty()) {
                add(
                    SecondaryAction(
                        label = "FINISH SURFACE",
                        action = ArMeasureAction.FinishMeasurement,
                        enabled = canFinishMeasurement
                    )
                )
            } else {
                if (activeCutoutVertices.size >= 3) {
                    add(
                        SecondaryAction(
                            label = "FINISH OPENING",
                            action = ArMeasureAction.CompleteCutout,
                            enabled = true
                        )
                    )
                }
                add(
                    SecondaryAction(
                        label = "CANCEL OPENING",
                        action = ArMeasureAction.CancelCutout,
                        destructive = true
                    )
                )
            }
        }

        ArMeasurePhase.SCANNING,
        ArMeasurePhase.LOCKING_PLANE,
        ArMeasurePhase.COMPLETED -> Unit
    }
}

private fun ArMeasureUiState.primaryActionEnabled(): Boolean = when (phase) {
    ArMeasurePhase.COMPLETED -> true
    ArMeasurePhase.SCANNING -> false
    else -> canPlaceCurrentPoint
}

private fun ArMeasureUiState.primaryActionLabel(): String = when (phase) {
    ArMeasurePhase.SCANNING -> "SCAN FOR SURFACE"
    ArMeasurePhase.LOCKING_PLANE -> "LOCK SURFACE"
    ArMeasurePhase.PLACING_OUTER_BOUNDARY -> "PLACE CORNER"
    ArMeasurePhase.PLACING_CUTOUT -> "PLACE OPENING CORNER"
    ArMeasurePhase.COMPLETED -> "NEW MEASUREMENT"
}

private fun ArMeasureUiState.reticleLabel(): String = when {
    !isTracking -> "TRACKING LOST"
    reticleSample == null -> "NO SURFACE"
    reticleSample.isStableForLock.not() -> "HOLD STEADY"
    reticleSample.isCoplanarWithLock.not() -> "ALIGN TO PLANE"
    phase == ArMeasurePhase.SCANNING || phase == ArMeasurePhase.LOCKING_PLANE -> "SURFACE READY"
    else -> "READY TO PLACE"
}

private fun ArMeasurePhase.title(): String = when (this) {
    ArMeasurePhase.SCANNING -> "Find a surface"
    ArMeasurePhase.LOCKING_PLANE -> "Surface found"
    ArMeasurePhase.PLACING_OUTER_BOUNDARY -> "Trace the outer edge"
    ArMeasurePhase.PLACING_CUTOUT -> "Mark the openings"
    ArMeasurePhase.COMPLETED -> "Measurement ready"
}

private fun workflowSummary(state: ArMeasureUiState): String = when (state.phase) {
    ArMeasurePhase.SCANNING -> "MOVE SLOWLY / AIM AT A FLAT AREA"
    ArMeasurePhase.LOCKING_PLANE -> "STEADY THE RETICLE / LOCK THE PLANE"
    ArMeasurePhase.PLACING_OUTER_BOUNDARY ->
        "OUTER ${state.outerVertices.size} PTS / TAP EACH CORNER"

    ArMeasurePhase.PLACING_CUTOUT -> {
        if (state.activeCutoutVertices.isEmpty()) {
            "OUTER CLOSED / ADD AN OPENING OR FINISH"
        } else {
            "OPENING ${state.activeCutoutVertices.size} PTS / TRACE THE FRAME"
        }
    }

    ArMeasurePhase.COMPLETED -> "SURFACE CAPTURED / READY FOR MATERIALS"
}

private fun CutoutKind.shortLabel(): String = when (this) {
    CutoutKind.DOOR -> "DOOR"
    CutoutKind.WINDOW -> "WINDOW"
    CutoutKind.VENT -> "VENT"
    CutoutKind.ISLAND_OR_FIXTURE -> "FIXTURE"
    CutoutKind.OTHER -> "OTHER"
}

private fun formatLength(meters: Double?): String =
    meters?.let { String.format(Locale.US, "%.2f m", it) } ?: "--"

private fun formatArea(squareMeters: Double?): String =
    squareMeters?.let { String.format(Locale.US, "%.2f m2", it) } ?: "--"

private fun formatMillimeters(millimeters: Double): String =
    String.format(Locale.US, "%.1f mm", millimeters)
