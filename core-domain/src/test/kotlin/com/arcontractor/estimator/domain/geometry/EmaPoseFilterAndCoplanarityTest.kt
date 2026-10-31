package com.arcontractor.estimator.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EmaPoseFilterAndCoplanarityTest {

    @Test
    fun `fixed alpha EMA matches exact recurrence formula and normalizes surface normal`() {
        val filter = EmaPoseFilter(
            EmaFilterConfig(
                alpha = 0.25,
                normalAlpha = 0.50,
                adaptiveVelocityEnabled = false,
                minSamplesForStability = 2
            )
        )

        val p0 = Pose3D(position = Vec3(1.0, 2.0, -3.0), normal = Vec3(0.0, 0.0, 2.0))
        val r0 = filter.update(p0)
        assertTrue(r0.smoothedPose.position.approxEquals(Vec3(1.0, 2.0, -3.0)))
        assertTrue(r0.smoothedPose.normal.approxEquals(Vec3(0.0, 0.0, 1.0)))
        assertEquals(1, r0.sampleCount)

        val p1 = Pose3D(position = Vec3(1.4, 2.0, -3.0), normal = Vec3(0.1, 0.0, 1.0))
        val r1 = filter.update(p1)
        // Expected x = 0.25 * 1.4 + 0.75 * 1.0 = 1.10
        assertEquals(1.10, r1.smoothedPose.position.x, 1e-9)
        assertEquals(2.00, r1.smoothedPose.position.y, 1e-9)
        assertEquals(-3.00, r1.smoothedPose.position.z, 1e-9)
        assertEquals(1.0, r1.smoothedPose.normal.length(), 1e-9)
    }

    @Test
    fun `EMA filter attenuates simulated high-frequency camera hand-shake and reaches lock stability`() {
        val filter = EmaPoseFilter(
            EmaFilterConfig(
                alpha = 0.15,
                stabilityJitterThresholdMeters = 0.012,
                minSamplesForStability = 5
            )
        )
        val trueAnchor = Vec3(0.85, 1.42, -2.15)
        var rawSqErrorSum = 0.0
        var filteredSqErrorSum = 0.0
        var lastResult: FilteredPoseResult? = null

        // Simulate 40 frames of +-10mm 8Hz hand tremor
        for (frame in 0 until 40) {
            val phase = frame * 0.9
            val jitter = Vec3(
                x = 0.010 * sin(phase),
                y = 0.008 * cos(phase * 1.3),
                z = 0.006 * sin(phase * 1.7)
            )
            val raw = Pose3D(
                position = trueAnchor + jitter,
                normal = Vec3(0.0, 0.0, 1.0),
                timestampNanos = (frame + 1) * 16_666_667L
            )
            val res = filter.update(raw)
            lastResult = res

            if (frame >= 10) {
                val rawErr = raw.position.distanceTo(trueAnchor)
                val filtErr = res.smoothedPose.position.distanceTo(trueAnchor)
                rawSqErrorSum += rawErr * rawErr
                filteredSqErrorSum += filtErr * filtErr
            }
        }

        val rawRms = sqrt(rawSqErrorSum / 30.0)
        val filteredRms = sqrt(filteredSqErrorSum / 30.0)
        assertTrue(
            "Expected filtered RMS ($filteredRms) to be < 40% of raw RMS ($rawRms)",
            filteredRms < rawRms * 0.40
        )
        assertTrue(lastResult!!.isStableForLock)
    }

    @Test
    fun `velocity-adaptive EMA uses low alpha at rest and high alpha during fast camera pan`() {
        val filter = EmaPoseFilter(
            EmaFilterConfig(
                adaptiveVelocityEnabled = true,
                minAlpha = 0.12,
                maxAlpha = 0.78,
                lowVelocityThresholdMps = 0.03,
                highVelocityThresholdMps = 0.50,
                maxSnapJumpMeters = 2.0
            )
        )

        val dtNanos = 33_333_333L // ~30 FPS
        filter.update(Pose3D(Vec3(0.0, 1.0, -2.0), Vec3.UNIT_Z, timestampNanos = dtNanos))

        // Micro-tremor: 0.4 mm over 33ms = 0.012 m/s (< 0.03 m/s) -> minAlpha
        val slowRes = filter.update(
            Pose3D(Vec3(0.0004, 1.0, -2.0), Vec3.UNIT_Z, timestampNanos = 2 * dtNanos)
        )
        assertEquals(0.12, slowRes.effectiveAlpha, 1e-6)

        // Rapid sweep: 3.5 cm over 33ms = 1.05 m/s (> 0.50 m/s) -> maxAlpha
        val fastRes = filter.update(
            Pose3D(Vec3(0.0354, 1.0, -2.0), Vec3.UNIT_Z, timestampNanos = 3 * dtNanos)
        )
        assertEquals(0.78, fastRes.effectiveAlpha, 1e-6)
    }

    @Test
    fun `depth discontinuity jump resets filter immediately instead of floating mid-air`() {
        val filter = EmaPoseFilter(EmaFilterConfig(alpha = 0.20, maxSnapJumpMeters = 0.50))
        filter.update(Pose3D(Vec3(0.0, 1.2, -2.0), Vec3.UNIT_Z))
        filter.update(Pose3D(Vec3(0.01, 1.2, -2.0), Vec3.UNIT_Z))

        // Raycast crosses open doorway onto hallway wall 1.8m deeper
        val jumpRes = filter.update(Pose3D(Vec3(0.02, 1.2, -3.8), Vec3.UNIT_Z))
        assertTrue(jumpRes.wasJumpReset)
        assertEquals(-3.8, jumpRes.smoothedPose.position.z, 1e-9)
        assertEquals(1, jumpRes.sampleCount)
    }

    @Test
    fun `coplanarity validator rejects adjacent floor snap and parallel recessed wall snap`() {
        val validator = CoplanarityValidator(
            CoplanarityConfig(
                maxNormalAngleDegrees = 15.0,
                maxOrthogonalDistanceMeters = 0.05
            )
        )

        val lockedWallAnchor = Pose3D(
            position = Vec3(0.0, 1.2, -2.5),
            normal = Vec3(0.0, 0.0, 1.0) // Vertical wall facing +Z
        )

        // 1. Valid point on the same wall with minor ARCore noise (4 deg normal tilt, 1.8 cm depth noise)
        val angleRad = Math.toRadians(4.0)
        val validWallHit = Pose3D(
            position = Vec3(1.8, 0.4, -2.482),
            normal = Vec3(sin(angleRad), 0.0, cos(angleRad))
        )
        val okResult = validator.validatePose(lockedWallAnchor, validWallHit)
        assertTrue(okResult.isCoplanar)
        assertNull(okResult.rejectionReason)

        // 2. Reticle dips onto horizontal floor (normal = +Y, dot product = 0.0, angle = 90 deg)
        val floorHit = Pose3D(
            position = Vec3(1.8, 0.0, -2.5),
            normal = Vec3(0.0, 1.0, 0.0)
        )
        val floorResult = validator.validatePose(lockedWallAnchor, floorHit)
        assertFalse(floorResult.isCoplanar)
        assertEquals(0.0, floorResult.normalDotProduct, 1e-9)
        assertEquals(90.0, floorResult.normalAngleDegrees, 1e-6)
        assertEquals(CoplanarityRejectionReason.NORMAL_MISALIGNMENT, floorResult.rejectionReason)

        // 3. Reticle shoots through doorway onto parallel wall 1.1m further back (same normal, offset = 1.1m)
        val recessedWallHit = Pose3D(
            position = Vec3(0.5, 1.2, -3.6),
            normal = Vec3(0.0, 0.0, 1.0)
        )
        val recessedResult = validator.validatePose(lockedWallAnchor, recessedWallHit)
        assertFalse(recessedResult.isCoplanar)
        assertEquals(1.10, recessedResult.orthogonalDistanceMeters, 1e-6)
        assertEquals(CoplanarityRejectionReason.PLANE_OFFSET_EXCEEDED, recessedResult.rejectionReason)
    }
}
