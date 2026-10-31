package com.arcontractor.estimator.domain.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos

/**
 * Thresholds for verifying whether a candidate 3D hit pose lies on the currently locked surface plane.
 *
 * @property maxNormalAngleDegrees Maximum allowed angle in degrees between the reference plane normal
 *                                 and candidate hit normal (prevents wall-to-floor or wall-to-ceiling snaps).
 * @property maxOrthogonalDistanceMeters Maximum signed perpendicular offset in meters from the reference
 *                                       plane (prevents snapping through open doorways onto recessed walls).
 */
data class CoplanarityConfig(
    val maxNormalAngleDegrees: Double = 15.0,
    val maxOrthogonalDistanceMeters: Double = 0.06
) {
    init {
        require(maxNormalAngleDegrees > 0.0 && maxNormalAngleDegrees < 90.0) {
            "maxNormalAngleDegrees must be in (0, 90), got $maxNormalAngleDegrees"
        }
        require(maxOrthogonalDistanceMeters > 0.0) {
            "maxOrthogonalDistanceMeters must be positive, got $maxOrthogonalDistanceMeters"
        }
    }

    val minNormalDotProduct: Double = cos(maxNormalAngleDegrees * PI / 180.0)
}

enum class CoplanarityRejectionReason {
    DEGENERATE_NORMAL,
    NORMAL_MISALIGNMENT,
    PLANE_OFFSET_EXCEEDED
}

data class CoplanarityResult(
    val isCoplanar: Boolean,
    val normalDotProduct: Double,
    val normalAngleDegrees: Double,
    val orthogonalDistanceMeters: Double,
    val rejectionReason: CoplanarityRejectionReason? = null
)

/**
 * Dual-gate coplanarity validator using dot-product normal comparison and orthogonal plane distance.
 *
 * Ensures vertices placed on a vertical wall cannot accidentally snap to an adjacent horizontal floor,
 * sloped ceiling, or parallel recessed wall behind an opening.
 */
class CoplanarityValidator(
    val config: CoplanarityConfig = CoplanarityConfig()
) {
    /**
     * Validates a candidate hit [Pose3D] against an initial locked anchor [referencePose].
     */
    fun validatePose(
        referencePose: Pose3D,
        candidatePose: Pose3D
    ): CoplanarityResult {
        val refNormal = referencePose.normal.normalizedOrNull()
            ?: return degenerateResult()
        val candNormal = candidatePose.normal.normalizedOrNull()
            ?: return degenerateResult()

        return evaluate(
            planeOrigin = referencePose.position,
            planeUnitNormal = refNormal,
            candidatePosition = candidatePose.position,
            candidateUnitNormal = candNormal
        )
    }

    /**
     * Validates a candidate 3D point (and optional surface normal) against an established [PlanarBasis].
     */
    fun validateAgainstBasis(
        basis: PlanarBasis,
        candidatePosition: Vec3,
        candidateNormal: Vec3? = null
    ): CoplanarityResult {
        val refNormal = basis.normal.normalizedOrNull()
            ?: return degenerateResult()

        val candUnitNormal = if (candidateNormal != null) {
            candidateNormal.normalizedOrNull() ?: return degenerateResult()
        } else {
            refNormal
        }

        return evaluate(
            planeOrigin = basis.origin,
            planeUnitNormal = refNormal,
            candidatePosition = candidatePosition,
            candidateUnitNormal = candUnitNormal
        )
    }

    private fun evaluate(
        planeOrigin: Vec3,
        planeUnitNormal: Vec3,
        candidatePosition: Vec3,
        candidateUnitNormal: Vec3
    ): CoplanarityResult {
        val dot = planeUnitNormal.dot(candidateUnitNormal).coerceIn(-1.0, 1.0)
        val angleDegrees = acos(dot) * 180.0 / PI
        val orthogonalDistance = abs((candidatePosition - planeOrigin).dot(planeUnitNormal))

        if (dot < config.minNormalDotProduct) {
            return CoplanarityResult(
                isCoplanar = false,
                normalDotProduct = dot,
                normalAngleDegrees = angleDegrees,
                orthogonalDistanceMeters = orthogonalDistance,
                rejectionReason = CoplanarityRejectionReason.NORMAL_MISALIGNMENT
            )
        }

        if (orthogonalDistance > config.maxOrthogonalDistanceMeters) {
            return CoplanarityResult(
                isCoplanar = false,
                normalDotProduct = dot,
                normalAngleDegrees = angleDegrees,
                orthogonalDistanceMeters = orthogonalDistance,
                rejectionReason = CoplanarityRejectionReason.PLANE_OFFSET_EXCEEDED
            )
        }

        return CoplanarityResult(
            isCoplanar = true,
            normalDotProduct = dot,
            normalAngleDegrees = angleDegrees,
            orthogonalDistanceMeters = orthogonalDistance,
            rejectionReason = null
        )
    }

    private fun degenerateResult(): CoplanarityResult = CoplanarityResult(
        isCoplanar = false,
        normalDotProduct = 0.0,
        normalAngleDegrees = 90.0,
        orthogonalDistanceMeters = Double.POSITIVE_INFINITY,
        rejectionReason = CoplanarityRejectionReason.DEGENERATE_NORMAL
    )
}
