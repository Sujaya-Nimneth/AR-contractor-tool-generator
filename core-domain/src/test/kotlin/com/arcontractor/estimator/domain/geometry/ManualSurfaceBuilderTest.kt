package com.arcontractor.estimator.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSurfaceBuilderTest {

    @Test
    fun `manual wall with flush door analyzes identically to AR anchors`() {
        val manual = ManualSurfaceBuilder.buildRectangularSurface(
            widthMeters = 4.0,
            heightMeters = 2.7,
            orientation = ManualSurfaceOrientation.WALL,
            cutouts = listOf(
                ManualCutoutSpec(
                    label = "Door",
                    kind = CutoutKind.DOOR,
                    offsetHorizontalMeters = 0.4,
                    offsetVerticalMeters = 0.0,
                    widthMeters = 0.9,
                    heightMeters = 2.1
                )
            )
        )

        val analysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = manual.outerVertices3D,
            cutouts = manual.cutouts,
            referenceNormal = manual.referenceNormal
        )

        assertEquals(10.8, analysis.grossAreaSqMeters, 1e-8)
        assertEquals(1.89, analysis.cutoutAreaSqMeters, 1e-8)
        assertEquals(8.91, analysis.netAreaSqMeters, 1e-8)
        assertEquals(0.9, analysis.doorOpeningWidthMeters, 1e-8)
        assertEquals(SurfaceOrientation.VERTICAL_WALL, analysis.basis.orientation)
    }

    @Test
    fun `manual floor synthesizes a gravity-aligned horizontal surface`() {
        val manual = ManualSurfaceBuilder.buildRectangularSurface(
            widthMeters = 5.0,
            heightMeters = 4.0,
            orientation = ManualSurfaceOrientation.FLOOR,
            cutouts = listOf(
                ManualCutoutSpec(
                    label = "Island",
                    kind = CutoutKind.ISLAND_OR_FIXTURE,
                    offsetHorizontalMeters = 1.5,
                    offsetVerticalMeters = 1.0,
                    widthMeters = 1.2,
                    heightMeters = 0.9
                )
            )
        )

        val analysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = manual.outerVertices3D,
            cutouts = manual.cutouts,
            referenceNormal = manual.referenceNormal
        )

        assertEquals(20.0, analysis.grossAreaSqMeters, 1e-8)
        assertEquals(1.08, analysis.cutoutAreaSqMeters, 1e-8)
        assertEquals(18.92, analysis.netAreaSqMeters, 1e-8)
        assertEquals(SurfaceOrientation.HORIZONTAL_FLOOR, analysis.basis.orientation)
        assertEquals(0.0, analysis.rmsPlanarErrorMeters, 1e-12)
    }

    @Test(expected = InvalidPolygonException::class)
    fun `cutout extending past the surface edge is rejected`() {
        ManualSurfaceBuilder.buildRectangularSurface(
            widthMeters = 3.0,
            heightMeters = 2.4,
            orientation = ManualSurfaceOrientation.WALL,
            cutouts = listOf(
                ManualCutoutSpec(
                    label = "Window",
                    kind = CutoutKind.WINDOW,
                    offsetHorizontalMeters = 2.5,
                    offsetVerticalMeters = 1.0,
                    widthMeters = 1.0,
                    heightMeters = 1.0
                )
            )
        )
    }

    @Test(expected = InvalidPolygonException::class)
    fun `dimensions below one centimeter are rejected`() {
        ManualSurfaceBuilder.buildRectangularSurface(
            widthMeters = 0.005,
            heightMeters = 2.4,
            orientation = ManualSurfaceOrientation.WALL
        )
    }
}
