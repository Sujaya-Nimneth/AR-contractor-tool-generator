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
    private val configWriter: (Config, ArSessionConfig) -> Unit

    var isDepthEnabled: Boolean = true
        private set
    var depthStatusMessage: String? = null
        private set
    var appliedConfig: ArSessionConfig? = null
        private set

    constructor() : this(
        configWriter = { config, values ->
            config.depthMode = values.depthMode
            config.planeFindingMode = values.planeFindingMode
            config.instantPlacementMode = values.instantPlacementMode
            config.focusMode = values.focusMode
        }
    )

    internal constructor(configWriter: (Config, ArSessionConfig) -> Unit) {
        this.configWriter = configWriter
    }

    data class ArSessionConfig(
        val depthMode: Config.DepthMode,
        val planeFindingMode: Config.PlaneFindingMode =
            Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
        val instantPlacementMode: Config.InstantPlacementMode =
            Config.InstantPlacementMode.DISABLED,
        val focusMode: Config.FocusMode = Config.FocusMode.AUTO
    )

    /**
     * Mutates the SceneView-provided config so unrelated SceneView settings are kept.
     *
     * SceneView invokes this inside its own `Session.configure` block. Do not call
     * `Session.configure` from here or the ARSession wrapper will re-enter its callback.
     */
    fun applyTo(session: Session, config: Config) {
        isDepthEnabled = runCatching {
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        }.getOrDefault(false)
        depthStatusMessage = if (isDepthEnabled) {
            null
        } else {
            "Depth sensing is unavailable on this device. Measurement will use detected planes only."
        }
        val values =
            ArSessionConfig(
                depthMode = if (isDepthEnabled) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
            )
        appliedConfig = values
        configWriter(config, values)
    }

    /**
     * Applies and commits the measurement config for callers that own the session lifecycle.
     */
    fun configure(session: Session, config: Config) {
        applyTo(session, config)
        session.configure(config)
    }

}
