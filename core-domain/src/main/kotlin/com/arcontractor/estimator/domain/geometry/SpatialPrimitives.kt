package com.arcontractor.estimator.domain.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure Kotlin 2D planar coordinate/vector in meters within a [PlanarBasis] `(u, v)` frame.
 */
data class Vec2(
    val u: Double,
    val v: Double
) {
    operator fun plus(other: Vec2): Vec2 = Vec2(u + other.u, v + other.v)
    operator fun minus(other: Vec2): Vec2 = Vec2(u - other.u, v - other.v)
    operator fun unaryMinus(): Vec2 = Vec2(-u, -v)
    operator fun times(scalar: Double): Vec2 = Vec2(u * scalar, v * scalar)
    operator fun div(scalar: Double): Vec2 {
        require(abs(scalar) > EPSILON) { "Division by near-zero scalar: $scalar" }
        return Vec2(u / scalar, v / scalar)
    }

    fun dot(other: Vec2): Double = u * other.u + v * other.v

    /**
     * 2D scalar cross product (z-component of 3D cross product): `u * other.v - v * other.u`.
     * Positive if [other] is counter-clockwise from `this`.
     */
    fun cross(other: Vec2): Double = u * other.v - v * other.u

    fun lengthSquared(): Double = u * u + v * v
    fun length(): Double = hypot(u, v)
    fun distanceTo(other: Vec2): Double = hypot(u - other.u, v - other.v)

    fun normalized(): Vec2 {
        val len = length()
        if (len <= EPSILON) {
            throw DegenerateGeometryException("Cannot normalize near-zero 2D vector ($u, $v)")
        }
        return Vec2(u / len, v / len)
    }

    fun lerp(other: Vec2, t: Double): Vec2 = Vec2(
        u = u + (other.u - u) * t,
        v = v + (other.v - v) * t
    )

    fun approxEquals(other: Vec2, tolerance: Double = 1e-6): Boolean =
        abs(u - other.u) <= tolerance && abs(v - other.v) <= tolerance

    companion object {
        private const val EPSILON = 1e-12
        val ZERO = Vec2(0.0, 0.0)
        val UNIT_U = Vec2(1.0, 0.0)
        val UNIT_V = Vec2(0.0, 1.0)
    }
}

/**
 * Pure Kotlin 3D world-space coordinate/vector in meters (right-handed Y-up convention).
 */
data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)
    operator fun times(scalar: Double): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double): Vec3 {
        require(abs(scalar) > EPSILON) { "Division by near-zero scalar: $scalar" }
        return Vec3(x / scalar, y / scalar, z / scalar)
    }

    fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x
    )

    fun lengthSquared(): Double = x * x + y * y + z * z
    fun length(): Double = sqrt(lengthSquared())
    fun distanceTo(other: Vec3): Double = (this - other).length()

    fun normalized(): Vec3 {
        val len = length()
        if (len <= EPSILON) {
            throw DegenerateGeometryException("Cannot normalize near-zero 3D vector ($x, $y, $z)")
        }
        return Vec3(x / len, y / len, z / len)
    }

    fun normalizedOrNull(): Vec3? {
        val len = length()
        return if (len <= EPSILON) null else Vec3(x / len, y / len, z / len)
    }

    fun lerp(other: Vec3, t: Double): Vec3 = Vec3(
        x = x + (other.x - x) * t,
        y = y + (other.y - y) * t,
        z = z + (other.z - z) * t
    )

    /**
     * Removes the component parallel to [unitNormal], projecting this vector onto the plane.
     */
    fun projectOntoPlane(unitNormal: Vec3): Vec3 {
        val parallel = this.dot(unitNormal)
        return this - (unitNormal * parallel)
    }

    /**
     * Unsigned angle in radians in `[0, PI]` between `this` and [other].
     */
    fun angleToRadians(other: Vec3): Double {
        val denom = this.length() * other.length()
        if (denom <= EPSILON) {
            throw DegenerateGeometryException("Cannot compute angle with zero-length vector")
        }
        val cosTheta = (this.dot(other) / denom).coerceIn(-1.0, 1.0)
        return acos(cosTheta)
    }

    fun approxEquals(other: Vec3, tolerance: Double = 1e-6): Boolean =
        abs(x - other.x) <= tolerance &&
            abs(y - other.y) <= tolerance &&
            abs(z - other.z) <= tolerance

    companion object {
        private const val EPSILON = 1e-12
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0) // World Up (opposite gravity in ARCore)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}

/**
 * 3D world-space hit pose produced by spatial raycasting.
 *
 * @property position World-space hit position `[x, y, z]` in meters.
 * @property normal Outward surface normal vector (normalized to unit length on construction if non-zero).
 * @property timestampNanos Monotonic frame timestamp in nanoseconds (0L if unspecified).
 */
data class Pose3D(
    val position: Vec3,
    val normal: Vec3,
    val timestampNanos: Long = 0L
) {
    fun withNormalizedNormal(): Pose3D = copy(normal = normal.normalized())
}

/**
 * Physical orientation classification derived from the plane normal relative to World Up (+Y).
 */
enum class SurfaceOrientation {
    HORIZONTAL_FLOOR,
    HORIZONTAL_CEILING,
    VERTICAL_WALL,
    SLOPED_SURFACE
}

/**
 * Right-handed orthonormal 3D planar coordinate frame `(uAxis, vAxis, normal)` anchored at [origin].
 *
 * Guarantees:
 * - `|uAxis| == 1`, `|vAxis| == 1`, `|normal| == 1`
 * - `uAxis . vAxis == 0`, `uAxis . normal == 0`, `vAxis . normal == 0`
 * - `uAxis x vAxis == normal`
 */
data class PlanarBasis(
    val origin: Vec3,
    val uAxis: Vec3,
    val vAxis: Vec3,
    val normal: Vec3,
    val orientation: SurfaceOrientation
) {
    /**
     * Orthogonally projects a 3D world-space point into local 2D planar coordinates `(u, v)` in meters.
     */
    fun projectTo2D(point3D: Vec3): Vec2 {
        val delta = point3D - origin
        return Vec2(
            u = delta.dot(uAxis),
            v = delta.dot(vAxis)
        )
    }

    /**
     * Signed orthogonal distance in meters from [point3D] to the plane along [normal].
     */
    fun signedPlaneDistance(point3D: Vec3): Double = (point3D - origin).dot(normal)

    /**
     * Reconstructs a 3D world-space point from 2D planar coordinates `(u, v)` and an optional
     * normal offset `w` (useful for rendering z-fighting-free 3D polygon overlays in Filament).
     */
    fun unprojectTo3D(point2D: Vec2, normalOffsetMeters: Double = 0.0): Vec3 =
        origin + (uAxis * point2D.u) + (vAxis * point2D.v) + (normal * normalOffsetMeters)
}

/**
 * Axis-aligned 2D bounding box in planar `(u, v)` space.
 */
data class BoundingBox2D(
    val minU: Double,
    val minV: Double,
    val maxU: Double,
    val maxV: Double
) {
    val widthMeters: Double get() = max(0.0, maxU - minU)
    val heightMeters: Double get() = max(0.0, maxV - minV)
    val center: Vec2 get() = Vec2((minU + maxU) * 0.5, (minV + maxV) * 0.5)

    fun contains(point: Vec2, tolerance: Double = 1e-6): Boolean =
        point.u >= minU - tolerance &&
            point.u <= maxU + tolerance &&
            point.v >= minV - tolerance &&
            point.v <= maxV + tolerance

    fun intersects(other: BoundingBox2D, tolerance: Double = 1e-6): Boolean =
        maxU >= other.minU - tolerance &&
            minU <= other.maxU + tolerance &&
            maxV >= other.minV - tolerance &&
            minV <= other.maxV + tolerance

    companion object {
        fun fromPoints(points: List<Vec2>): BoundingBox2D {
            require(points.isNotEmpty()) { "Cannot compute bounding box of empty point list" }
            var minU = Double.POSITIVE_INFINITY
            var minV = Double.POSITIVE_INFINITY
            var maxU = Double.NEGATIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (p in points) {
                minU = min(minU, p.u)
                minV = min(minV, p.v)
                maxU = max(maxU, p.u)
                maxV = max(maxV, p.v)
            }
            return BoundingBox2D(minU, minV, maxU, maxV)
        }
    }
}

/**
 * Measured sequential edge segment of a polygon boundary or cutout.
 */
data class EdgeSegment(
    val index: Int,
    val start2D: Vec2,
    val end2D: Vec2,
    val start3D: Vec3,
    val end3D: Vec3
) {
    /** Euclidean length in the projected 2D plane (meters). */
    val length2DMeters: Double = start2D.distanceTo(end2D)

    /** Direct Euclidean length in 3D world space (meters). */
    val length3DMeters: Double = start3D.distanceTo(end3D)

    val midpoint2D: Vec2 get() = start2D.lerp(end2D, 0.5)
    val midpoint3D: Vec3 get() = start3D.lerp(end3D, 0.5)

    /**
     * Unit normal pointing outward from the polygon interior in 2D planar space.
     * Used by the Phase 4 PDF vector blueprint engine to offset dimension labels cleanly outside edges.
     */
    fun outwardNormal2D(isCounterClockwise: Boolean = true): Vec2 {
        val du = end2D.u - start2D.u
        val dv = end2D.v - start2D.v
        val len = hypot(du, dv)
        if (len <= 1e-12) return Vec2.UNIT_V
        return if (isCounterClockwise) {
            Vec2(dv / len, -du / len)
        } else {
            Vec2(-dv / len, du / len)
        }
    }
}

open class GeometryException(message: String) : IllegalArgumentException(message)
class DegenerateGeometryException(message: String) : GeometryException(message)
class InvalidPolygonException(message: String) : GeometryException(message)
