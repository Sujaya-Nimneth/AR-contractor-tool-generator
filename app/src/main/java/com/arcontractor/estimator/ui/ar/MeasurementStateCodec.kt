package com.arcontractor.estimator.ui.ar

import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.CutoutValidationPolicy
import com.arcontractor.estimator.domain.geometry.PlanarProjector
import com.arcontractor.estimator.domain.geometry.PolygonGeometryEngine
import com.arcontractor.estimator.domain.geometry.Vec3
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PersistedMeasurementDraft(
    val phase: ArMeasurePhase,
    val referencePosition: PersistedVec3? = null,
    val referenceNormal: PersistedVec3? = null,
    val outerVertices: List<PersistedVec3> = emptyList(),
    val outerClosed: Boolean = false,
    val activeCutoutVertices: List<PersistedVec3> = emptyList(),
    val selectedCutoutKind: CutoutKind = CutoutKind.DOOR,
    val cutouts: List<PersistedCutout> = emptyList()
)

@Serializable
internal data class PersistedVec3(
    val x: Double,
    val y: Double,
    val z: Double
)

@Serializable
internal data class PersistedCutout(
    val id: String,
    val label: String,
    val vertices: List<PersistedVec3>,
    val kind: CutoutKind
)

internal object MeasurementStateCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: ArMeasureUiState): String? {
        if (!state.hasPersistedMeasurement()) return null
        return json.encodeToString(state.toPersistedDraft())
    }

    fun decode(value: String?): ArMeasureUiState? {
        if (value.isNullOrBlank()) return null
        val draft = runCatching {
            json.decodeFromString<PersistedMeasurementDraft>(value)
        }.getOrNull() ?: return null
        return runCatching { draft.toUiState() }.getOrNull()
    }

    private fun ArMeasureUiState.hasPersistedMeasurement(): Boolean =
        outerVertices.isNotEmpty() ||
            activeCutoutVertices.isNotEmpty() ||
            cutouts.isNotEmpty() ||
            referencePose != null

    private fun ArMeasureUiState.toPersistedDraft() = PersistedMeasurementDraft(
        phase = phase,
        referencePosition = referencePose?.position?.let(::persisted),
        referenceNormal = referencePose?.normal?.let(::persisted),
        outerVertices = outerVertices.map(::persisted),
        outerClosed = outerClosed,
        activeCutoutVertices = activeCutoutVertices.map(::persisted),
        selectedCutoutKind = selectedCutoutKind,
        cutouts = cutouts.map { cutout ->
            PersistedCutout(
                id = cutout.id,
                label = cutout.label,
                vertices = cutout.vertices3D.map(::persisted),
                kind = cutout.kind
            )
        }
    )

    private fun PersistedMeasurementDraft.toUiState(): ArMeasureUiState {
        val normal = referenceNormal?.let(::domainVec3)
        val referencePose = referencePosition?.let { position ->
            normal?.let {
                com.arcontractor.estimator.domain.geometry.Pose3D(
                    position = domainVec3(position),
                    normal = it
                )
            }
        }
        val outer = outerVertices.map(::domainVec3)
        val domainCutouts = cutouts.map { cutout ->
            CutoutPolygonInput(
                id = cutout.id,
                label = cutout.label,
                vertices3D = cutout.vertices.map(::domainVec3),
                kind = cutout.kind
            )
        }
        val basis = if (outer.size >= 3) {
            runCatching { PlanarProjector.computeBasis(outer, normal) }.getOrNull()
        } else {
            null
        }
        val analysis = if (outer.size >= 3) {
            runCatching {
                PolygonGeometryEngine.analyze3DSurface(
                    outerVertices3D = outer,
                    cutouts = domainCutouts,
                    referenceNormal = normal,
                    cutoutPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
                )
            }.getOrNull()
        } else {
            null
        }
        return ArMeasureUiState(
            phase = phase,
            referencePose = referencePose,
            surfaceBasis = basis ?: analysis?.basis,
            outerVertices = outer,
            outerClosed = outerClosed,
            activeCutoutVertices = activeCutoutVertices.map(::domainVec3),
            selectedCutoutKind = selectedCutoutKind,
            cutouts = domainCutouts,
            surfaceAnalysis = analysis
        )
    }

    private fun persisted(value: Vec3) = PersistedVec3(value.x, value.y, value.z)

    private fun domainVec3(value: PersistedVec3) = Vec3(value.x, value.y, value.z)
}
