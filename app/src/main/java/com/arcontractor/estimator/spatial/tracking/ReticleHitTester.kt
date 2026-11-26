package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.CoplanarityConfig
import com.arcontractor.estimator.domain.geometry.CoplanarityResult
import com.arcontractor.estimator.domain.geometry.CoplanarityValidator
import com.arcontractor.estimator.domain.geometry.EmaFilterConfig
import com.arcontractor.estimator.domain.geometry.EmaPoseFilter
import com.arcontractor.estimator.domain.geometry.PlanarBasis
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.Vec3
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState

/**
 * Performs a continuous center-reticle raycast, stabilizes the selected pose with the pure JVM EMA
 * filter, and enforces the current surface lock before exposing a placeable sample.
 */
class ReticleHitTester(
    private val poseFilter: EmaPoseFilter = EmaPoseFilter(
        EmaFilterConfig(
            adaptiveVelocityEnabled = true,
            minAlpha = 0.12,
            maxAlpha = 0.75,
            stabilityJitterThresholdMeters = 0.004,
            minSamplesForStability = 3
        )
    ),
    private val coplanarityValidator: CoplanarityValidator = CoplanarityValidator(
        CoplanarityConfig.MEASUREMENT_STANDARD
    )
) {
    private var lockedReferencePose: Pose3D? = null
    private var lockedBasis: PlanarBasis? = null
    private var lockedSurfaceNormal: Vec3? = null

    /**
     * Locks subsequent samples to the first placed vertex. Once three or more vertices exist, the
     * caller should also supply the best-fit basis for stronger normal and plane checks.
     */
    fun setSurfaceLock(referencePose: Pose3D?, basis: PlanarBasis?) {
        lockedReferencePose = referencePose
        lockedBasis = basis
        lockedSurfaceNormal = basis?.normal ?: referencePose?.normal
        if (referencePose == null && basis == null) {
            poseFilter.reset()
        }
    }

    fun onTrackingLost() {
        poseFilter.reset()
    }

    /**
     * Samples the center pixel of the AR view. Coordinates are in the view's pixel coordinate system,
     * matching [Frame.hitTest].
     */
    fun sample(
        frame: Frame,
        viewportWidthPx: Int,
        viewportHeightPx: Int
    ): ReticleSample? {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return null

        val hits = runCatching {
            frame.hitTest(viewportWidthPx * 0.5f, viewportHeightPx * 0.5f)
        }.getOrElse {
            poseFilter.reset()
            return null
        }
        val selected = ReticleHitSelector.select(
            hits.mapNotNull { it.toCandidate(frame.timestamp) }
        )
        if (selected == null) {
            poseFilter.reset()
            return null
        }

        val normalizedCandidate = selected.withLockedNormal(lockedSurfaceNormal)
        val filtered = poseFilter.update(normalizedCandidate)
        val smoothedPose = filtered.smoothedPose
        val coplanarity = validateAgainstLock(smoothedPose)
        return ReticleSample(
            rawPose = normalizedCandidate,
            pose = smoothedPose,
            source = selected.source,
            isStableForLock = filtered.isStableForLock,
            rollingJitterMeters = filtered.rollingJitterMeters,
            coplanarity = coplanarity
        )
    }

    internal fun validateAgainstLock(candidatePose: Pose3D): CoplanarityResult? {
        lockedBasis?.let { basis ->
            return coplanarityValidator.validateAgainstBasis(
                basis = basis,
                candidatePosition = candidatePose.position,
                // Always compare the *hit* normal so a plane hit that drifted onto an adjacent
                // surface is genuinely rejected; only depth hits adopt the locked normal upstream.
                candidateNormal = candidatePose.normal
            )
        }
        val reference = lockedReferencePose ?: return null
        return coplanarityValidator.validatePose(reference, candidatePose)
    }

    private fun SurfaceHitCandidate.withLockedNormal(lockedNormal: Vec3?): Pose3D =
        if (source == SurfaceHitSource.PLANE || lockedNormal == null) {
            pose
        } else {
            pose.copy(normal = lockedNormal)
        }

    private fun HitResult.toCandidate(timestampNanos: Long): SurfaceHitCandidate? {
        if (trackable.trackingState != TrackingState.TRACKING) return null

        val source = when (trackable) {
            is Plane -> SurfaceHitSource.PLANE
            is DepthPoint -> SurfaceHitSource.DEPTH_POINT
            else -> return null
        }
        val normal = hitPose.getTransformedAxis(1, 1.0f)
        val normalVector = Vec3(
            x = normal[0].toDouble(),
            y = normal[1].toDouble(),
            z = normal[2].toDouble()
        ).normalizedOrNull() ?: return null

        val insidePolygon = (trackable as? Plane)?.isPoseInPolygon(hitPose) ?: true
        return SurfaceHitCandidate(
            source = source,
            pose = Pose3D(
                position = Vec3(
                    x = hitPose.tx().toDouble(),
                    y = hitPose.ty().toDouble(),
                    z = hitPose.tz().toDouble()
                ),
                normal = normalVector,
                timestampNanos = timestampNanos
            ),
            distanceMeters = distance,
            isInsidePlanePolygon = insidePolygon
        )
    }
}
