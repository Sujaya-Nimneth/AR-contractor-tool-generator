package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.Pose3D
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
