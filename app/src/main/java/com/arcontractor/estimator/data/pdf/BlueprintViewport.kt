package com.arcontractor.estimator.data.pdf

import com.arcontractor.estimator.domain.geometry.BoundingBox2D
import com.arcontractor.estimator.domain.geometry.EdgeSegment
import com.arcontractor.estimator.domain.geometry.Vec2
import kotlin.math.max
import kotlin.math.min

data class BlueprintBounds(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double
) {
    init {
        require(width > 0.0 && height > 0.0) { "Blueprint bounds must be positive." }
    }

    val right: Double get() = left + width
    val bottom: Double get() = top + height
}

data class BlueprintPoint(
    val x: Double,
    val y: Double
)

/**
 * Pure planar-to-page mapping used by the Android Canvas renderer.
 *
 * The 12% margin keeps outward dimension callouts inside the PDF page, while the
 * vertical inversion maps planar +v (world up) to PDF -y (page down).
 */
data class BlueprintViewport(
    val minU: Double,
    val minV: Double,
    val maxV: Double,
    val scale: Double,
    val drawingLeft: Double,
    val drawingTop: Double
) {
    fun map(point: Vec2): BlueprintPoint = BlueprintPoint(
        x = drawingLeft + (point.u - minU) * scale,
        y = drawingTop + (maxV - point.v) * scale
    )

    fun mapLength(meters: Double): Double = meters * scale

    fun screenOutwardNormal(edge: EdgeSegment): Vec2 {
        val planar = edge.outwardNormal2D(isCounterClockwise = true)
        return Vec2(planar.u, -planar.v)
    }

    companion object {
        const val DEFAULT_CALLOUT_MARGIN_FRACTION = 0.12

        fun create(
            bounds: BlueprintBounds,
            box: BoundingBox2D,
            marginFraction: Double = DEFAULT_CALLOUT_MARGIN_FRACTION
        ): BlueprintViewport {
            require(marginFraction in 0.0..0.30) {
                "Blueprint margin fraction must be between 0 and 0.30."
            }
            val planarWidth = max(box.maxU - box.minU, 1e-6)
            val planarHeight = max(box.maxV - box.minV, 1e-6)
            val usableWidth = bounds.width * (1.0 - marginFraction * 2.0)
            val usableHeight = bounds.height * (1.0 - marginFraction * 2.0)
            val scale = min(usableWidth / planarWidth, usableHeight / planarHeight)
            val drawingWidth = planarWidth * scale
            val drawingHeight = planarHeight * scale
            return BlueprintViewport(
                minU = box.minU,
                minV = box.minV,
                maxV = box.maxV,
                scale = scale,
                drawingLeft = bounds.left + (bounds.width - drawingWidth) / 2.0,
                drawingTop = bounds.top + (bounds.height - drawingHeight) / 2.0
            )
        }
    }
}

fun chooseNiceMetricLength(targetMeters: Double): Double {
    val safeTarget = max(targetMeters, 1e-6)
    val candidates = doubleArrayOf(
        0.05, 0.10, 0.20, 0.25, 0.50,
        1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 50.0, 100.0
    )
    return candidates.minBy { candidate -> kotlin.math.abs(candidate - safeTarget) }
}
