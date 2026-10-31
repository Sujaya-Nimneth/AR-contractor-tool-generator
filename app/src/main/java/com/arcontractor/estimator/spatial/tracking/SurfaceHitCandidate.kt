package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.Pose3D

/**
 * Source of a center-reticle surface hit. Plane hits are preferred because they have persistent
 * trackable geometry and a stable surface normal; depth hits provide coverage on feature-poor areas.
 */
enum class SurfaceHitSource {
    PLANE,
    DEPTH_POINT
}

/**
 * Platform-neutral representation of one ARCore hit result.
 */
data class SurfaceHitCandidate(
    val source: SurfaceHitSource,
    val pose: Pose3D,
    val distanceMeters: Float,
    val isInsidePlanePolygon: Boolean = true
)

/**
 * Selects one hit from ARCore's ordered hit list according to the Phase 2 accuracy contract:
 * in-polygon planes take precedence over depth points, then the nearest candidate within that tier.
 */
object ReticleHitSelector {
    fun select(candidates: List<SurfaceHitCandidate>): SurfaceHitCandidate? {
        val validPlaneHit = candidates
            .asSequence()
            .filter { it.source == SurfaceHitSource.PLANE && it.isInsidePlanePolygon }
            .minByOrNull { it.distanceMeters }
        if (validPlaneHit != null) return validPlaneHit

        return candidates
            .asSequence()
            .filter { it.source == SurfaceHitSource.DEPTH_POINT }
            .minByOrNull { it.distanceMeters }
    }
}

/**
 * Smoothed reticle observation consumed by the MVI layer.
 */
data class ReticleSample(
    val rawPose: Pose3D,
    val pose: Pose3D,
    val source: SurfaceHitSource,
    val isStableForLock: Boolean,
    val rollingJitterMeters: Double,
    val coplanarity: com.arcontractor.estimator.domain.geometry.CoplanarityResult? = null
) {
    val isCoplanarWithLock: Boolean
        get() = coplanarity?.isCoplanar ?: true

    val canPlaceVertex: Boolean
        get() = isStableForLock && isCoplanarWithLock
}
