package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.CoplanarityRejectionReason
import com.arcontractor.estimator.domain.geometry.PlanarBasis
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.SurfaceOrientation
import com.arcontractor.estimator.domain.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReticleHitSelectorTest {

    @Test
    fun `in polygon plane wins over nearer depth point`() {
        val plane = candidate(SurfaceHitSource.PLANE, distance = 2.0f)
        val depth = candidate(SurfaceHitSource.DEPTH_POINT, distance = 0.8f)

        val selected = ReticleHitSelector.select(listOf(depth, plane))

        assertEquals(SurfaceHitSource.PLANE, selected?.source)
    }

    @Test
    fun `plane outside polygon falls back to depth point`() {
        val outsidePlane = candidate(
            source = SurfaceHitSource.PLANE,
            distance = 0.5f,
            insidePolygon = false
        )
        val depth = candidate(SurfaceHitSource.DEPTH_POINT, distance = 1.4f)

        val selected = ReticleHitSelector.select(listOf(outsidePlane, depth))

        assertEquals(SurfaceHitSource.DEPTH_POINT, selected?.source)
    }

    @Test
    fun `nearest candidate wins within same priority tier`() {
        val farPlane = candidate(SurfaceHitSource.PLANE, distance = 3.0f)
        val nearPlane = candidate(SurfaceHitSource.PLANE, distance = 1.1f)
        val depth = candidate(SurfaceHitSource.DEPTH_POINT, distance = 0.4f)

        val selected = ReticleHitSelector.select(listOf(farPlane, depth, nearPlane))

        assertEquals(nearPlane, selected)
    }

    @Test
    fun `no supported candidates returns null`() {
        assertNull(
            ReticleHitSelector.select(
                listOf(
                    candidate(
                        SurfaceHitSource.PLANE,
                        distance = 1.0f,
                        insidePolygon = false
                    )
                )
            )
        )
    }

    @Test
    fun `locked basis rejects a plane hit whose normal drifted onto another surface`() {
        val hitTester = ReticleHitTester()
        hitTester.setSurfaceLock(
            referencePose = Pose3D(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0)),
            basis = PlanarBasis(
                origin = Vec3(0.0, 0.0, 0.0),
                uAxis = Vec3(1.0, 0.0, 0.0),
                vAxis = Vec3(0.0, 1.0, 0.0),
                normal = Vec3(0.0, 0.0, 1.0),
                orientation = SurfaceOrientation.VERTICAL_WALL
            )
        )

        val aligned = hitTester.validateAgainstLock(
            Pose3D(Vec3(0.4, 0.2, 0.0), Vec3(0.0, 0.0, 1.0))
        )
        val drifted = hitTester.validateAgainstLock(
            Pose3D(Vec3(0.4, 0.2, 0.0), Vec3(0.0, 1.0, 0.0))
        )

        assertEquals(true, aligned?.isCoplanar)
        assertEquals(false, drifted?.isCoplanar)
        assertEquals(
            CoplanarityRejectionReason.NORMAL_MISALIGNMENT,
            drifted?.rejectionReason
        )
    }

    @Test
    fun `locked reference pose rejects a candidate beyond the plane offset gate`() {
        val hitTester = ReticleHitTester()
        hitTester.setSurfaceLock(
            referencePose = Pose3D(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0)),
            basis = null
        )

        val recessed = hitTester.validateAgainstLock(
            Pose3D(Vec3(0.0, 0.0, 0.9), Vec3(0.0, 0.0, 1.0))
        )

        assertEquals(false, recessed?.isCoplanar)
        assertEquals(CoplanarityRejectionReason.PLANE_OFFSET_EXCEEDED, recessed?.rejectionReason)
    }

    private fun candidate(
        source: SurfaceHitSource,
        distance: Float,
        insidePolygon: Boolean = true
    ) = SurfaceHitCandidate(
        source = source,
        pose = Pose3D(Vec3.ZERO, Vec3.UNIT_Y),
        distanceMeters = distance,
        isInsidePlanePolygon = insidePolygon
    )
}
