package com.arcontractor.estimator.data.pdf

import com.arcontractor.estimator.domain.geometry.BoundingBox2D
import com.arcontractor.estimator.domain.geometry.EdgeSegment
import com.arcontractor.estimator.domain.geometry.Vec2
import com.arcontractor.estimator.domain.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintViewportTest {
    @Test
    fun `viewport preserves aspect ratio and reserves callout margin`() {
        val viewport = BlueprintViewport.create(
            bounds = BlueprintBounds(left = 100.0, top = 100.0, width = 100.0, height = 100.0),
            box = BoundingBox2D(minU = 0.0, minV = 0.0, maxU = 2.0, maxV = 1.0)
        )

        assertEquals(38.0, viewport.scale, 1e-9)
        assertEquals(112.0, viewport.drawingLeft, 1e-9)
        assertEquals(131.0, viewport.drawingTop, 1e-9)

        val topLeft = viewport.map(Vec2(0.0, 1.0))
        val bottomRight = viewport.map(Vec2(2.0, 0.0))
        assertEquals(112.0, topLeft.x, 1e-9)
        assertEquals(131.0, topLeft.y, 1e-9)
        assertEquals(188.0, bottomRight.x, 1e-9)
        assertEquals(169.0, bottomRight.y, 1e-9)
        assertTrue(bottomRight.y > topLeft.y)
    }

    @Test
    fun `screen outward normal keeps callouts outside a counterclockwise wall`() {
        val viewport = BlueprintViewport.create(
            bounds = BlueprintBounds(left = 0.0, top = 0.0, width = 300.0, height = 200.0),
            box = BoundingBox2D(minU = 0.0, minV = 0.0, maxU = 3.0, maxV = 2.0)
        )
        val bottomEdge = EdgeSegment(
            index = 0,
            start2D = Vec2(0.0, 0.0),
            end2D = Vec2(3.0, 0.0),
            start3D = Vec3(0.0, 0.0, 0.0),
            end3D = Vec3(3.0, 0.0, 0.0)
        )

        val normal = viewport.screenOutwardNormal(bottomEdge)

        assertEquals(0.0, normal.u, 1e-9)
        assertEquals(1.0, normal.v, 1e-9)
    }

    @Test
    fun `scale bar chooses a readable round metric length`() {
        assertEquals(1.0, chooseNiceMetricLength(1.08), 0.0)
        assertEquals(2.0, chooseNiceMetricLength(1.90), 0.0)
        assertEquals(0.50, chooseNiceMetricLength(0.57), 0.0)
    }
}
