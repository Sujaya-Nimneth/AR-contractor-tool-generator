package com.arcontractor.estimator.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PlanarProjectorTest {

    @Test
    fun `projects rotated 3D vertical wall into gravity-aligned orthonormal 2D basis preserving isometry`() {
        // Construct a 3.6m wide x 2.5m tall vertical wall rotated 37 degrees around World Y, translated in 3D
        val yaw = Math.toRadians(37.0)
        val wallRight = Vec3(cos(yaw), 0.0, -sin(yaw))
        val wallUp = Vec3.UNIT_Y
        val wallNormal = wallRight.cross(wallUp).normalized() // Right-handed outward normal
        val anchorOrigin = Vec3(2.3, 0.15, -4.1)

        val corners3D = listOf(
            anchorOrigin,
            anchorOrigin + (wallRight * 3.6),
            anchorOrigin + (wallRight * 3.6) + (wallUp * 2.5),
            anchorOrigin + (wallUp * 2.5)
        )

        val proj = PlanarProjector.projectPolygon(corners3D, referenceNormal = wallNormal)
        val basis = proj.basis

        assertEquals(SurfaceOrientation.VERTICAL_WALL, basis.orientation)
        assertEquals(1.0, basis.uAxis.length(), 1e-9)
        assertEquals(1.0, basis.vAxis.length(), 1e-9)
        assertEquals(1.0, basis.normal.length(), 1e-9)
        assertEquals(0.0, basis.uAxis.dot(basis.vAxis), 1e-9)
        assertEquals(0.0, basis.uAxis.dot(basis.normal), 1e-9)
        assertEquals(0.0, basis.vAxis.dot(basis.normal), 1e-9)
        assertTrue(basis.uAxis.cross(basis.vAxis).approxEquals(basis.normal, 1e-9))

        // Gravity alignment check: +vAxis on a vertical wall must point straight up (+Y)
        assertTrue(basis.vAxis.approxEquals(Vec3.UNIT_Y, 1e-9))

        // Isometry & round-trip unprojection check
        val pts2D = proj.projectedVertices2D
        assertEquals(3.6, pts2D[0].distanceTo(pts2D[1]), 1e-9)
        assertEquals(2.5, pts2D[1].distanceTo(pts2D[2]), 1e-9)
        assertEquals(0.0, pts2D[1].v - pts2D[0].v, 1e-9) // Floor baseline is perfectly horizontal in 2D!
        assertEquals(9.0, PolygonClipping2D.shoelaceArea(pts2D), 1e-9)

        for (i in corners3D.indices) {
            val reconstructed = basis.unprojectTo3D(pts2D[i])
            assertTrue(reconstructed.approxEquals(corners3D[i], 1e-9))
        }
    }

    @Test
    fun `projects doubly-tilted sloped attic ceiling preserving exact area and distances`() {
        // 4.0m x 3.0m sloped ceiling pitched 35 deg and rotated 52 deg
        val pitch = Math.toRadians(35.0)
        val azimuth = Math.toRadians(52.0)

        fun rotate3D(localU: Double, localV: Double): Vec3 {
            // Pitch around X, then yaw around Y, plus 3D translation
            val x1 = localU
            val y1 = localV * sin(pitch)
            val z1 = -localV * cos(pitch)

            val x2 = x1 * cos(azimuth) + z1 * sin(azimuth)
            val y2 = y1
            val z2 = -x1 * sin(azimuth) + z1 * cos(azimuth)
            return Vec3(x2 - 1.2, y2 + 2.4, z2 - 3.3)
        }

        val slopedQuad = listOf(
            rotate3D(0.0, 0.0),
            rotate3D(4.0, 0.0),
            rotate3D(4.0, 3.0),
            rotate3D(0.0, 3.0)
        )

        val proj = PlanarProjector.projectPolygon(slopedQuad)
        assertEquals(SurfaceOrientation.SLOPED_SURFACE, proj.basis.orientation)
        assertEquals(12.0, PolygonClipping2D.shoelaceArea(proj.projectedVertices2D), 1e-8)
        assertEquals(0.0, proj.rmsPlanarErrorMeters, 1e-9)
    }

    @Test
    fun `Newell best-fit plane succeeds even when first three vertices are collinear`() {
        // Floor polygon with 3 collinear points along the bottom edge y=0
        val vertices = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(1.5, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0), // First 3 points are strictly collinear!
            Vec3(3.0, 0.0, -2.0),
            Vec3(0.0, 0.0, -2.0)
        )

        val proj = PlanarProjector.projectPolygon(vertices, referenceNormal = Vec3.UNIT_Y)
        assertEquals(SurfaceOrientation.HORIZONTAL_FLOOR, proj.basis.orientation)
        assertEquals(6.0, PolygonClipping2D.shoelaceArea(proj.projectedVertices2D), 1e-8)
    }

    @Test(expected = DegenerateGeometryException::class)
    fun `strictly collinear 3D vertices throw DegenerateGeometryException`() {
        val collinear = listOf(
            Vec3(1.0, 1.0, 1.0),
            Vec3(2.0, 2.0, 2.0),
            Vec3(3.5, 3.5, 3.5),
            Vec3(5.0, 5.0, 5.0)
        )
        PlanarProjector.projectPolygon(collinear)
    }

    @Test
    fun `computes RMS out-of-plane flatness diagnostic for bowed wall vertices`() {
        // 6 vertices on Z = -2.0m wall, with +/- 15mm bow exceeding 8mm flatness tolerance
        val bowedWall = listOf(
            Vec3(0.0, 0.0, -2.015),
            Vec3(1.5, 0.0, -1.985),
            Vec3(3.0, 0.0, -2.015),
            Vec3(3.0, 2.4, -1.985),
            Vec3(1.5, 2.4, -2.015),
            Vec3(0.0, 2.4, -1.985)
        )
        val proj = PlanarProjector.projectPolygon(bowedWall, referenceNormal = Vec3.UNIT_Z)
        assertEquals(0.015, proj.rmsPlanarErrorMeters, 1e-4)
        assertFalse(proj.isFlatWithinStandardTolerance)
    }
}
