package com.arcontractor.estimator.spatial.tracking

import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Applies the metric-accuracy ARCore configuration required by the surface measurement workflow.
 *
 * Instant placement is deliberately disabled because its estimated pose is not accurate enough for
 * contractor takeoffs. Plane and depth hits are fused so freshly painted, feature-poor walls still
 * provide usable anchors.
 */
class ArSessionController {

    /**
     * Mutates the SceneView-provided config so unrelated SceneView settings are kept.
     *
     * SceneView invokes this inside its own `Session.configure` block. Do not call
     * `Session.configure` from here or the ARSession wrapper will re-enter its callback.
     */
    fun applyTo(config: Config) {
        config.depthMode = Config.DepthMode.AUTOMATIC
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        config.focusMode = Config.FocusMode.AUTO
    }

    /**
     * Applies and commits the measurement config for callers that own the session lifecycle.
     */
    fun configure(session: Session, config: Config) {
        applyTo(config)
        session.configure(config)
    }
}
