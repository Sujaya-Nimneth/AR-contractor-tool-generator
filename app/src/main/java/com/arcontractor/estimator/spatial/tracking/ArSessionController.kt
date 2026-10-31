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
     * Mutates and commits the SceneView-provided config so unrelated SceneView settings are kept.
     */
    fun configure(session: Session, config: Config) {
        config.depthMode = Config.DepthMode.AUTOMATIC
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        config.focusMode = Config.FocusMode.AUTO
        session.configure(config)
    }
}
