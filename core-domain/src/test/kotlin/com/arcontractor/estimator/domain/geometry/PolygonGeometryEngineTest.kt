package com.arcontractor.estimator.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonGeometryEngineTest {

    @Test
    fun `computes exact Shoelace area and perimeter for concave L-shaped kitchen floor regardless of winding`() {
        // L-shaped floor: 5m x 4m main block (20 m²) minus 2m x 1.5m corner notch (3 m²) = 17.0 m²
        // Perimeter = 5 + 4 + 3 + 1.5 + 2 + 2.5 = 18.0 m
        val lShapeFloor3D = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(5.0, 0.0, 0.0),
            Vec3(5.0, 0.0, -2.5),
            Vec3(3.0, 0.0, -2.5), // Reflex (concave) interior corner!
            Vec3(3.0, 0.0, -4.0),
            Vec3(0.0, 0.0, -4.0)
        )

        val ccwAnalysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = lShapeFloor3D,
            referenceNormal = Vec3.UNIT_Y
        )
        val cwAnalysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = lShapeFloor3D.asReversed(),
            referenceNormal = Vec3.UNIT_Y
        )

        assertEquals(17.0, ccwAnalysis.grossAreaSqMeters, 1e-8)
        assertEquals(17.0, ccwAnalysis.netAreaSqMeters, 1e-8)
        assertEquals(18.0, ccwAnalysis.outerPerimeterMeters, 1e-8)
        assertEquals(17.0, cwAnalysis.grossAreaSqMeters, 1e-8)
        assertEquals(18.0, cwAnalysis.outerPerimeterMeters, 1e-8)
        assertEquals(6, ccwAnalysis.outerEdges.size)
    }

    @Test
    fun `subtracts floor-flush door and interior window cutouts while preserving cutout perimeters and hole-punched mesh`() {
        // Vertical wall: 4.0m wide x 2.7m tall = 10.80 m², outer perimeter = 13.40 m
        val wall3D = listOf(
            Vec3(0.0, 0.0, -2.5),
            Vec3(4.0, 0.0, -2.5),
            Vec3(4.0, 2.7, -2.5),
            Vec3(0.0, 2.7, -2.5)
        )

        // Door resting directly on floor edge y = 0.0: 0.90m x 2.10m = 1.89 m², perimeter = 6.00 m
        val doorCutout = CutoutPolygonInput(
            id = "door-1",
            label = "Entry Door",
            kind = CutoutKind.DOOR,
            vertices3D = listOf(
                Vec3(0.5, 0.0, -2.5),
                Vec3(1.4, 0.0, -2.5),
                Vec3(1.4, 2.1, -2.5),
                Vec3(0.5, 2.1, -2.5)
            )
        )

        // Interior Window: 1.20m x 1.00m = 1.20 m², perimeter = 4.40 m
        val windowCutout = CutoutPolygonInput(
            id = "win-1",
            label = "Casement Window",
            kind = CutoutKind.WINDOW,
            vertices3D = listOf(
                Vec3(2.2, 1.1, -2.5),
                Vec3(3.4, 1.1, -2.5),
                Vec3(3.4, 2.1, -2.5),
                Vec3(2.2, 2.1, -2.5)
            )
        )

        val result = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = wall3D,
            cutouts = listOf(doorCutout, windowCutout),
            referenceNormal = Vec3.UNIT_Z
        )

        assertEquals(10.80, result.grossAreaSqMeters, 1e-8)
        assertEquals(3.09, result.cutoutAreaSqMeters, 1e-8) // 1.89 + 1.20
        assertEquals(7.71, result.netAreaSqMeters, 1e-8)    // 10.80 - 3.09
        assertEquals(13.40, result.outerPerimeterMeters, 1e-8)
        assertEquals(10.40, result.cutoutPerimeterMeters, 1e-8) // 6.00 + 4.40 preserved for trim!
        assertFalse(result.hadOverlappingCutouts)

        // Verify hole-punched 2D render mesh triangles sum to exact netAreaSqMeters
        val meshAreaSum = result.netSurfaceMeshTriangles2D.sumOf { it.areaSqMeters }
        assertEquals(7.71, meshAreaSum, 1e-8)
    }

    @Test
    fun `overlapping cutouts do not double-subtract shared overlap region`() {
        val outer = listOf(
            Vec2(0.0, 0.0),
            Vec2(4.0, 0.0),
            Vec2(4.0, 4.0),
            Vec2(0.0, 4.0)
        ) // 16.0 m²

        // Two 1.0 x 1.0 cutouts overlapping by 0.5 x 1.0 -> Union area = 1.50 m² (NOT 2.00 m²)
        val cutA = CutoutPolygon2DInput(
            id = "c1",
            label = "Cutout A",
            vertices2D = listOf(Vec2(1.0, 1.0), Vec2(2.0, 1.0), Vec2(2.0, 2.0), Vec2(1.0, 2.0))
        )
        val cutB = CutoutPolygon2DInput(
            id = "c2",
            label = "Cutout B",
            vertices2D = listOf(Vec2(1.5, 1.0), Vec2(2.5, 1.0), Vec2(2.5, 2.0), Vec2(1.5, 2.0))
        )

        val result = PolygonGeometryEngine.analyze2DPolygon(
            outerVertices2D = outer,
            cutouts2D = listOf(cutA, cutB)
        )

        assertTrue(result.hadOverlappingCutouts)
        assertEquals(16.0, result.grossAreaSqMeters, 1e-8)
        assertEquals(1.50, result.cutoutAreaSqMeters, 1e-8)
        assertEquals(14.50, result.netAreaSqMeters, 1e-8)
    }

    @Test(expected = InvalidPolygonException::class)
    fun `strict cutout policy rejects cutout placed outside parent surface`() {
        val outer = listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.0), Vec2(3.0, 3.0), Vec2(0.0, 3.0))
        val outsideCutout = CutoutPolygon2DInput(
            id = "out",
            label = "Misplaced Window",
            vertices2D = listOf(Vec2(4.0, 1.0), Vec2(5.0, 1.0), Vec2(5.0, 2.0), Vec2(4.0, 2.0))
        )
        PolygonGeometryEngine.analyze2DPolygon(
            outerVertices2D = outer,
            cutouts2D = listOf(outsideCutout),
            cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
        )
    }

    @Test
    fun `CLIP_TO_PARENT policy clips partially protruding cutout cleanly to boundary`() {
        val outer = listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.0), Vec2(3.0, 3.0), Vec2(0.0, 3.0)) // 9.0 m²
        // Door extending 0.2m below floor line v=0: [1, 2] x [-0.2, 2.0] -> interior overlap is 1.0 x 2.0 = 2.0 m²
        val protrudingDoor = CutoutPolygon2DInput(
            id = "door",
            label = "Protruding Door",
            kind = CutoutKind.DOOR,
            vertices2D = listOf(Vec2(1.0, -0.2), Vec2(2.0, -0.2), Vec2(2.0, 2.0), Vec2(1.0, 2.0))
        )

        val result = PolygonGeometryEngine.analyze2DPolygon(
            outerVertices2D = outer,
            cutouts2D = listOf(protrudingDoor),
            cutoutPolicy = CutoutValidationPolicy.CLIP_TO_PARENT
        )

        assertEquals(9.0, result.grossAreaSqMeters, 1e-8)
        assertEquals(2.0, result.cutoutAreaSqMeters, 1e-8)
        assertEquals(7.0, result.netAreaSqMeters, 1e-8)
        assertEquals(CutoutContainmentStatus.BOUNDARY_FLUSH_OR_CLIPPED, result.cutouts.first().containmentStatus)
    }

    @Test(expected = InvalidPolygonException::class)
    fun `self-intersecting figure-8 polygon is rejected`() {
        val bowtie = listOf(
            Vec2(0.0, 0.0),
            Vec2(2.0, 2.0),
            Vec2(2.0, 0.0),
            Vec2(0.0, 2.0)
        )
        PolygonGeometryEngine.analyze2DPolygon(bowtie)
    }

    @Test
    fun `unit converter formats metric and architectural imperial dimensions accurately`() {
        assertEquals(10.7639104, UnitConverter.sqMetersToSqFeet(1.0), 1e-6)
        assertEquals("8' 0\"", UnitConverter.formatArchitecturalFeetInches(2.4384))
        assertEquals("3' 6\"", UnitConverter.formatArchitecturalFeetInches(1.0668))
    }
}
