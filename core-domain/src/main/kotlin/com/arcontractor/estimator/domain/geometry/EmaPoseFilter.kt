package com.arcontractor.estimator.domain.geometry

import kotlin.math.max

/**
 * Tuning configuration for [EmaPoseFilter].
 *
 * @property alpha Fixed smoothing weight in `(0.0, 1.0]` applied when [adaptiveVelocityEnabled] is false
 *                 or frame timestamps are unavailable. Lower values attenuate camera hand-shake more strongly.
 * @property normalAlpha Smoothing weight in `(0.0, 1.0]` for surface unit normals prior to renormalization.
 * @property adaptiveVelocityEnabled Enables dynamic velocity-scaled alpha between [minAlpha] and [maxAlpha]
 *                                   to eliminate rest jitter without adding panning lag.
 * @property minAlpha Minimum alpha applied during sub-centimeter aiming hand-shake.
 * @property maxAlpha Maximum alpha applied during fast intentional camera sweeps.
 * @property lowVelocityThresholdMps 3D reticle speed (m/s) below which [minAlpha] is enforced.
 * @property highVelocityThresholdMps 3D reticle speed (m/s) above which [maxAlpha] is enforced.
 * @property maxSnapJumpMeters Discontinuity threshold (m). If a raycast jumps across an edge (e.g. doorway)
 *                             by more than this distance, the filter resets immediately instead of lagging.
 * @property stabilityJitterThresholdMeters Maximum smoothed residual jitter (m) required to flag [FilteredPoseResult.isStableForLock].
 * @property minSamplesForStability Minimum consecutive frames before [FilteredPoseResult.isStableForLock] can become true.
 */
data class EmaFilterConfig(
    val alpha: Double = 0.25,
    val normalAlpha: Double = 0.20,
    val adaptiveVelocityEnabled: Boolean = false,
    val minAlpha: Double = 0.12,
    val maxAlpha: Double = 0.75,
    val lowVelocityThresholdMps: Double = 0.03,
    val highVelocityThresholdMps: Double = 0.60,
    val maxSnapJumpMeters: Double = 0.75,
    val stabilityJitterThresholdMeters: Double = 0.005,
    val minSamplesForStability: Int = 3
) {
    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0.0, 1.0], got $alpha" }
        require(normalAlpha > 0.0 && normalAlpha <= 1.0) { "normalAlpha must be in (0.0, 1.0], got $normalAlpha" }
        require(minAlpha > 0.0 && minAlpha <= maxAlpha && maxAlpha <= 1.0) {
            "Expected 0 < minAlpha <= maxAlpha <= 1.0, got [$minAlpha, $maxAlpha]"
        }
        require(highVelocityThresholdMps > lowVelocityThresholdMps && lowVelocityThresholdMps >= 0.0) {
            "Velocity thresholds must satisfy 0 <= low < high"
        }
        require(maxSnapJumpMeters > 0.0) { "maxSnapJumpMeters must be positive" }
        require(stabilityJitterThresholdMeters >= 0.0) { "stabilityJitterThresholdMeters must be non-negative" }
        require(minSamplesForStability >= 1) { "minSamplesForStability must be >= 1" }
    }
}

/**
 * Output of a single [EmaPoseFilter.update] step.
 */
data class FilteredPoseResult(
    val smoothedPose: Pose3D,
    val effectiveAlpha: Double,
    val instantaneousResidualMeters: Double,
    val rollingJitterMeters: Double,
    val isStableForLock: Boolean,
    val wasJumpReset: Boolean,
    val sampleCount: Int
)

/**
 * Exponential Moving Average (EMA) low-pass spatial pose filter for AR hit-test stabilization.
 *
 * Eliminates high-frequency hand-tremor (`4-12 Hz`) on 3D world-space hit coordinates `[x, y, z]`
 * and surface normals before locking a polygon vertex anchor.
 */
class EmaPoseFilter(
    val config: EmaFilterConfig = EmaFilterConfig()
) {
    private var statePose: Pose3D? = null
    private var rollingJitter: Double = 0.0
    private var samples: Int = 0

    val currentPose: Pose3D?
        get() = statePose

    val sampleCount: Int
        get() = samples

    /**
     * Clears accumulated filter history (call when tracking is lost or plane lock is reset).
     */
    fun reset() {
        statePose = null
        rollingJitter = 0.0
        samples = 0
    }

    /**
     * Ingests a raw 3D raycast hit pose and returns the smoothed world-space pose and lock telemetry.
     */
    fun update(rawPose: Pose3D): FilteredPoseResult {
        val rawUnitNormal = rawPose.normal.normalized()
        val previous = statePose

        // First sample or discontinuity jump across depth edges
        if (previous == null) {
            val initial = Pose3D(
                position = rawPose.position,
                normal = rawUnitNormal,
                timestampNanos = rawPose.timestampNanos
            )
            statePose = initial
            rollingJitter = 0.0
            samples = 1
            return FilteredPoseResult(
                smoothedPose = initial,
                effectiveAlpha = 1.0,
                instantaneousResidualMeters = 0.0,
                rollingJitterMeters = 0.0,
                isStableForLock = samples >= config.minSamplesForStability,
                wasJumpReset = false,
                sampleCount = samples
            )
        }

        val displacement = previous.position.distanceTo(rawPose.position)
        if (displacement > config.maxSnapJumpMeters) {
            val jumped = Pose3D(
                position = rawPose.position,
                normal = rawUnitNormal,
                timestampNanos = rawPose.timestampNanos
            )
            statePose = jumped
            rollingJitter = 0.0
            samples = 1
            return FilteredPoseResult(
                smoothedPose = jumped,
                effectiveAlpha = 1.0,
                instantaneousResidualMeters = 0.0,
                rollingJitterMeters = 0.0,
                isStableForLock = samples >= config.minSamplesForStability,
                wasJumpReset = true,
                sampleCount = samples
            )
        }

        val effectiveAlpha = computeEffectiveAlpha(
            displacementMeters = displacement,
            prevTimestampNanos = previous.timestampNanos,
            currTimestampNanos = rawPose.timestampNanos
        )

        // Position EMA: p_t = alpha * x_t + (1 - alpha) * p_{t-1}
        val smoothedPosition = previous.position.lerp(rawPose.position, effectiveAlpha)

        // Normal EMA with unit-sphere renormalization and antipodal guard
        val blendedNormal = previous.normal.lerp(rawUnitNormal, config.normalAlpha)
        val smoothedNormal = blendedNormal.normalizedOrNull() ?: rawUnitNormal

        val updatedPose = Pose3D(
            position = smoothedPosition,
            normal = smoothedNormal,
            timestampNanos = rawPose.timestampNanos
        )
        statePose = updatedPose
        samples += 1

        val residual = rawPose.position.distanceTo(smoothedPosition)
        rollingJitter = if (samples == 2) {
            residual
        } else {
            config.alpha * residual + (1.0 - config.alpha) * rollingJitter
        }

        val isStable = samples >= config.minSamplesForStability &&
            rollingJitter <= config.stabilityJitterThresholdMeters

        return FilteredPoseResult(
            smoothedPose = updatedPose,
            effectiveAlpha = effectiveAlpha,
            instantaneousResidualMeters = residual,
            rollingJitterMeters = rollingJitter,
            isStableForLock = isStable,
            wasJumpReset = false,
            sampleCount = samples
        )
    }

    private fun computeEffectiveAlpha(
        displacementMeters: Double,
        prevTimestampNanos: Long,
        currTimestampNanos: Long
    ): Double {
        if (!config.adaptiveVelocityEnabled ||
            prevTimestampNanos <= 0L ||
            currTimestampNanos <= prevTimestampNanos
        ) {
            return config.alpha
        }
        val dtSeconds = max((currTimestampNanos - prevTimestampNanos) * 1e-9, 1e-4)
        val speedMps = displacementMeters / dtSeconds
        val ramp = ((speedMps - config.lowVelocityThresholdMps) /
            (config.highVelocityThresholdMps - config.lowVelocityThresholdMps)).coerceIn(0.0, 1.0)
        return config.minAlpha + ramp * (config.maxAlpha - config.minAlpha)
    }
}
