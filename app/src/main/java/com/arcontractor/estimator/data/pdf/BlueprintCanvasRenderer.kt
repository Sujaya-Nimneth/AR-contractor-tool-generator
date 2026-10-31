package com.arcontractor.estimator.data.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.withClip
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.domain.geometry.AnalyzedCutout
import com.arcontractor.estimator.domain.geometry.EdgeSegment
import com.arcontractor.estimator.domain.geometry.UnitConverter
import java.util.Locale

data class BlueprintPalette(
    val ink: Int = Color.rgb(24, 27, 31),
    val mutedInk: Int = Color.rgb(88, 97, 109),
    val line: Int = Color.rgb(210, 217, 225),
    val paper: Int = Color.WHITE,
    val surfaceFill: Int = Color.rgb(247, 248, 250),
    val hatch: Int = Color.rgb(151, 160, 172),
    val dimension: Int = Color.rgb(29, 95, 191)
)

/**
 * Draws one measured surface into an Android Canvas as resolution-independent PDF vectors.
 */
class BlueprintCanvasRenderer(
    private val palette: BlueprintPalette = BlueprintPalette()
) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.surfaceFill
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.ink
        strokeWidth = 1.35f
        strokeJoin = Paint.Join.MITER
    }
    private val cutoutStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.ink
        strokeWidth = 1.0f
    }
    private val cutoutFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.paper
    }
    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.hatch
        strokeWidth = 0.7f
    }
    private val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.dimension
        strokeWidth = 0.75f
    }
    private val labelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.paper
    }
    private val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.line
        strokeWidth = 0.6f
    }
    private val dimensionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.dimension
        textSize = 7.2f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.ink
        textSize = 7.0f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.mutedInk
        textSize = 7.2f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val legendStrongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.ink
        textSize = 7.2f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.ink
        strokeWidth = 0.9f
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(179, 38, 30)
        textSize = 9f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun render(
        canvas: Canvas,
        bounds: RectF,
        surface: SurfaceQuoteLine
    ) {
        val analysis = surface.analysis
        if (analysis == null) {
            canvas.drawText(
                surface.loadError ?: "Surface geometry is unavailable.",
                bounds.centerX(),
                bounds.centerY(),
                errorPaint
            )
            return
        }

        val legendHeight = 34f
        val drawingBounds = RectF(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom - legendHeight
        )
        canvas.drawRect(drawingBounds, labelBorderPaint)

        val viewport = BlueprintViewport.create(
            bounds = BlueprintBounds(
                left = drawingBounds.left.toDouble(),
                top = drawingBounds.top.toDouble(),
                width = drawingBounds.width().toDouble(),
                height = drawingBounds.height().toDouble()
            ),
            box = analysis.boundingBox2D
        )
        val outerPath = buildOuterPath(surface, viewport)
        canvas.drawPath(outerPath, outerPaint)
        canvas.drawPath(outerPath, strokePaint)

        analysis.cutouts.forEach { cutout ->
            drawCutout(
                canvas = canvas,
                cutout = cutout,
                viewport = viewport,
                drawingBounds = drawingBounds
            )
        }
        drawDimensions(
            canvas = canvas,
            edges = analysis.outerEdges,
            viewport = viewport,
            drawingBounds = drawingBounds
        )
        drawScaleBar(
            canvas = canvas,
            viewport = viewport,
            drawingBounds = drawingBounds
        )
        drawSurfaceLegend(
            canvas = canvas,
            bounds = bounds,
            surface = surface
        )
    }

    private fun buildOuterPath(
        surface: SurfaceQuoteLine,
        viewport: BlueprintViewport
    ): Path {
        val vertices = surface.analysis?.outerVertices2D.orEmpty()
        return Path().apply {
            vertices.forEachIndexed { index, point ->
                val mapped = viewport.map(point)
                if (index == 0) {
                    moveTo(mapped.x.toFloat(), mapped.y.toFloat())
                } else {
                    lineTo(mapped.x.toFloat(), mapped.y.toFloat())
                }
            }
            close()
        }
    }

    private fun drawCutout(
        canvas: Canvas,
        cutout: AnalyzedCutout,
        viewport: BlueprintViewport,
        drawingBounds: RectF
    ) {
        val path = buildPath(cutout.vertices2D, viewport)
        canvas.drawPath(path, cutoutFillPaint)

        val mappedPoints = cutout.vertices2D.map(viewport::map)
        val minX = mappedPoints.minOf { it.x }.toFloat()
        val maxX = mappedPoints.maxOf { it.x }.toFloat()
        val minY = mappedPoints.minOf { it.y }.toFloat()
        val maxY = mappedPoints.maxOf { it.y }.toFloat()

        canvas.withClip(path) {
            val spacing = 7f
            var x = minX - (maxY - minY)
            while (x <= maxX) {
                drawLine(x, maxY, x + (maxY - minY), minY, hatchPaint)
                x += spacing
            }
        }
        canvas.drawPath(path, cutoutStrokePaint)

        val label = cutout.kind.name.replace('_', ' ')
        if (maxX - minX >= 34f && maxY - minY >= 14f) {
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val textWidth = labelTextPaint.measureText(label)
            val rect = RectF(
                centerX - textWidth / 2f - 3f,
                centerY - 5f,
                centerX + textWidth / 2f + 3f,
                centerY + 5f
            )
            canvas.drawRoundRect(rect, 1.5f, 1.5f, labelFillPaint)
            canvas.drawText(
                label,
                centerX,
                centerY - (labelTextPaint.ascent() + labelTextPaint.descent()) / 2f,
                labelTextPaint
            )
        }
    }

    private fun drawDimensions(
        canvas: Canvas,
        edges: List<EdgeSegment>,
        viewport: BlueprintViewport,
        drawingBounds: RectF
    ) {
        val placedLabels = ArrayList<RectF>(edges.size)
        edges.forEach { edge ->
            val midpoint = viewport.map(edge.midpoint2D)
            val normal = viewport.screenOutwardNormal(edge)
            val label = "${UnitConverter.formatMeters(edge.length2DMeters)} / " +
                UnitConverter.formatArchitecturalFeetInches(edge.length2DMeters)
            val textWidth = dimensionTextPaint.measureText(label)
            val labelWidth = textWidth + 6f
            val labelHeight = 12f
            var offset = 19f
            var rect = dimensionLabelRect(
                midpoint = midpoint,
                normal = normal,
                offset = offset,
                width = labelWidth,
                height = labelHeight
            )
            var attempts = 0
            while (
                attempts < 4 &&
                placedLabels.any { previous -> RectF.intersects(previous, rect) }
            ) {
                offset += 14f
                rect = dimensionLabelRect(
                    midpoint = midpoint,
                    normal = normal,
                    offset = offset,
                    width = labelWidth,
                    height = labelHeight
                )
                attempts++
            }
            clampToBounds(rect, drawingBounds, padding = 2f)
            placedLabels += RectF(rect)

            val anchorX = midpoint.x.toFloat() + normal.u.toFloat() * offset
            val anchorY = midpoint.y.toFloat() + normal.v.toFloat() * offset
            canvas.drawLine(
                midpoint.x.toFloat(),
                midpoint.y.toFloat(),
                anchorX,
                anchorY,
                leaderPaint
            )
            canvas.drawRoundRect(rect, 1.5f, 1.5f, labelFillPaint)
            canvas.drawRoundRect(rect, 1.5f, 1.5f, labelBorderPaint)
            canvas.drawText(
                label,
                rect.centerX(),
                rect.centerY() - (dimensionTextPaint.ascent() + dimensionTextPaint.descent()) / 2f,
                dimensionTextPaint
            )
        }
    }

    private fun dimensionLabelRect(
        midpoint: BlueprintPoint,
        normal: com.arcontractor.estimator.domain.geometry.Vec2,
        offset: Float,
        width: Float,
        height: Float
    ): RectF {
        val centerX = midpoint.x.toFloat() + normal.u.toFloat() * offset
        val centerY = midpoint.y.toFloat() + normal.v.toFloat() * offset
        return RectF(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )
    }

    private fun clampToBounds(rect: RectF, bounds: RectF, padding: Float) {
        val dx = when {
            rect.left < bounds.left + padding -> bounds.left + padding - rect.left
            rect.right > bounds.right - padding -> bounds.right - padding - rect.right
            else -> 0f
        }
        val dy = when {
            rect.top < bounds.top + padding -> bounds.top + padding - rect.top
            rect.bottom > bounds.bottom - padding -> bounds.bottom - padding - rect.bottom
            else -> 0f
        }
        rect.offset(dx, dy)
    }

    private fun drawScaleBar(
        canvas: Canvas,
        viewport: BlueprintViewport,
        drawingBounds: RectF
    ) {
        val targetMeters = (drawingBounds.width() * 0.22 / viewport.scale)
        val barMeters = chooseNiceMetricLength(targetMeters)
        val barWidth = viewport.mapLength(barMeters).toFloat().coerceAtLeast(24f)
        val x = drawingBounds.left + 12f
        val y = drawingBounds.bottom - 16f
        canvas.drawLine(x, y, x + barWidth, y, scalePaint)
        canvas.drawLine(x, y - 3f, x, y + 3f, scalePaint)
        canvas.drawLine(x + barWidth, y - 3f, x + barWidth, y + 3f, scalePaint)
        canvas.drawText(
            "${UnitConverter.formatMeters(barMeters)} / " +
                UnitConverter.formatArchitecturalFeetInches(barMeters),
            x,
            y - 5f,
            legendPaint
        )
    }

    private fun drawSurfaceLegend(
        canvas: Canvas,
        bounds: RectF,
        surface: SurfaceQuoteLine
    ) {
        val analysis = surface.analysis ?: return
        val legendTop = bounds.bottom - 29f
        canvas.drawLine(bounds.left, legendTop, bounds.right, legendTop, labelBorderPaint)
        var x = bounds.left + 2f
        val baseline = legendTop + 15f
        x = drawLegendValue(canvas, "NET", formatDualArea(analysis.netAreaSqMeters), x, baseline)
        x = drawLegendValue(
            canvas,
            "PERIMETER",
            formatDualLength(analysis.outerPerimeterMeters),
            x + 20f,
            baseline
        )
        drawLegendValue(
            canvas,
            "OPENINGS",
            analysis.cutouts.size.toString(),
            x + 20f,
            baseline
        )
    }

    private fun drawLegendValue(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        baseline: Float
    ): Float {
        canvas.drawText(label, x, baseline, legendPaint)
        val valueX = x + legendPaint.measureText(label) + 4f
        canvas.drawText(value, valueX, baseline, legendStrongPaint)
        return valueX + legendStrongPaint.measureText(value)
    }

    private fun buildPath(
        points: List<com.arcontractor.estimator.domain.geometry.Vec2>,
        viewport: BlueprintViewport
    ): Path = Path().apply {
        points.forEachIndexed { index, point ->
            val mapped = viewport.map(point)
            if (index == 0) {
                moveTo(mapped.x.toFloat(), mapped.y.toFloat())
            } else {
                lineTo(mapped.x.toFloat(), mapped.y.toFloat())
            }
        }
        close()
    }

    private fun formatDualLength(meters: Double): String =
        "${UnitConverter.formatMeters(meters)} / " +
            UnitConverter.formatArchitecturalFeetInches(meters)

    private fun formatDualArea(squareMeters: Double): String = String.format(
        Locale.US,
        "%.2f m2 / %.2f sq ft",
        squareMeters,
        UnitConverter.sqMetersToSqFeet(squareMeters)
    )
}
