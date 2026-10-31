package com.arcontractor.estimator.domain.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 2D counter-clockwise triangle produced by ear-clipping or convex fan triangulation.
 * Used both for exact boolean cutout area subtraction and Phase 2 Filament 3D mesh generation.
 */
data class Triangle2D(
    val a: Vec2,
    val b: Vec2,
    val c: Vec2
) {
    val areaSqMeters: Double
        get() = 0.5 * abs((b - a).cross(c - a))
}

/**
 * Pure Kotlin computational geometry primitives for 2D planar polygons:
 * - Shoelace signed/unsigned area
 * - Segment-segment self-intersection detection
 * - Boundary-tolerant Point-in-Polygon testing
 * - Ear-Clipping triangulation for arbitrary concave/convex simple polygons
 * - Exact Half-Plane Convex Decomposition for boolean cutout subtraction (`P \ union(C_k)`)
 */
object PolygonClipping2D {

    private const val GEOM_EPSILON = 1e-9
    private const val AREA_EPSILON = 1e-9
    private const val DUPLICATE_VERTEX_EPSILON = 1e-7

    /**
     * Computes signed planar area using the **Shoelace Formula**:
     * `0.5 * sum_{i=0}^{N-1} (u_i * v_{i+1} - u_{i+1} * v_i)`.
     *
     * Positive for Counter-Clockwise (CCW) winding; negative for Clockwise (CW) winding.
     */
    fun signedArea(vertices: List<Vec2>): Double {
        if (vertices.size < 3) return 0.0
        var twiceArea = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % n]
            twiceArea += (curr.u * next.v) - (next.u * curr.v)
        }
        return 0.5 * twiceArea
    }

    /**
     * Exact unsigned surface area of any simple convex or concave 2D polygon via the Shoelace Formula.
     */
    fun shoelaceArea(vertices: List<Vec2>): Double = abs(signedArea(vertices))

    /**
     * Computes total Euclidean perimeter of a closed 2D vertex ring.
     */
    fun perimeter(vertices: List<Vec2>): Double {
        if (vertices.size < 2) return 0.0
        var sum = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            sum += vertices[i].distanceTo(vertices[(i + 1) % n])
        }
        return sum
    }

    /**
     * Removes consecutive near-duplicate vertices (including wrap-around).
     */
    fun removeSequentialDuplicates(
        vertices: List<Vec2>,
        tolerance: Double = DUPLICATE_VERTEX_EPSILON
    ): List<Vec2> {
        if (vertices.isEmpty()) return emptyList()
        val cleaned = ArrayList<Vec2>(vertices.size)
        for (v in vertices) {
            if (cleaned.isEmpty() || cleaned.last().distanceTo(v) > tolerance) {
                cleaned.add(v)
            }
        }
        if (cleaned.size > 1 && cleaned.first().distanceTo(cleaned.last()) <= tolerance) {
            cleaned.removeAt(cleaned.lastIndex)
        }
        return cleaned
    }

    /**
     * Returns vertices oriented Counter-Clockwise (CCW, positive signed area).
     */
    fun ensureCounterClockwise(vertices: List<Vec2>): List<Vec2> =
        if (signedArea(vertices) < 0.0) vertices.asReversed() else vertices

    /**
     * Detects whether a closed polygon boundary crosses or pinches itself (figure-8 / bowtie).
     */
    fun hasSelfIntersection(vertices: List<Vec2>): Boolean {
        val n = vertices.size
        if (n < 3) return false

        for (i in 0 until n) {
            val a1 = vertices[i]
            val a2 = vertices[(i + 1) % n]
            if (a1.distanceTo(a2) <= DUPLICATE_VERTEX_EPSILON) return true

            for (j in i + 2 until n) {
                // Skip adjacent edge pair (0) and (n - 1) sharing vertex 0
                if (i == 0 && j == n - 1) continue
                val b1 = vertices[j]
                val b2 = vertices[(j + 1) % n]
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Boundary-aware point-in-polygon test supporting arbitrary concave polygons.
     *
     * @param includeBoundary When true, points lying within [boundaryTolerance] of an edge
     *                        (e.g., floor-flush door cutout corners) are treated as contained.
     */
    fun containsPoint(
        polygon: List<Vec2>,
        point: Vec2,
        includeBoundary: Boolean = true,
        boundaryTolerance: Double = 1e-5
    ): Boolean {
        val n = polygon.size
        if (n < 3) return false

        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            if (pointToSegmentDistance(point, a, b) <= boundaryTolerance) {
                return includeBoundary
            }
        }

        var inside = false
        for (i in 0 until n) {
            val a = polygon[i]
            val b = polygon[(i + 1) % n]
            val straddlesScanline = (a.v > point.v) != (b.v > point.v)
            if (straddlesScanline) {
                val intersectU = a.u + (point.v - a.v) * (b.u - a.u) / (b.v - a.v)
                if (point.u < intersectU) {
                    inside = !inside
                }
            }
        }
        return inside
    }

    /**
     * Shortest Euclidean distance from [point] to line segment `[segStart, segEnd]`.
     */
    fun pointToSegmentDistance(point: Vec2, segStart: Vec2, segEnd: Vec2): Double {
        val ab = segEnd - segStart
        val lenSq = ab.lengthSquared()
        if (lenSq <= 1e-18) return point.distanceTo(segStart)
        val t = ((point - segStart).dot(ab) / lenSq).coerceIn(0.0, 1.0)
        val projection = segStart + (ab * t)
        return point.distanceTo(projection)
    }

    /**
     * Triangulates any simple (convex or concave) 2D polygon into `N - 2` CCW triangles
     * using Ear-Clipping.
     */
    fun triangulateSimplePolygon(polygon: List<Vec2>): List<Triangle2D> {
        val cleaned = ensureCounterClockwise(removeSequentialDuplicates(polygon))
        if (cleaned.size < 3) {
            throw InvalidPolygonException("Cannot triangulate polygon with fewer than 3 distinct vertices")
        }
        if (cleaned.size == 3) {
            val tri = Triangle2D(cleaned[0], cleaned[1], cleaned[2])
            if (tri.areaSqMeters <= AREA_EPSILON) {
                throw DegenerateGeometryException("Polygon vertices are collinear (zero area)")
            }
            return listOf(tri)
        }

        val indices = MutableList(cleaned.size) { it }
        val triangles = ArrayList<Triangle2D>(cleaned.size - 2)
        var guardIterations = cleaned.size * cleaned.size * 3

        while (indices.size > 3 && guardIterations-- > 0) {
            val m = indices.size
            var earFound = false

            // First try pruning any degenerate 180-degree collinear vertex along a straight edge
            var collinearPruned = false
            for (k in 0 until m) {
                val prev = cleaned[indices[(k - 1 + m) % m]]
                val curr = cleaned[indices[k]]
                val next = cleaned[indices[(k + 1) % m]]
                if (abs((curr - prev).cross(next - prev)) <= AREA_EPSILON) {
                    indices.removeAt(k)
                    collinearPruned = true
                    break
                }
            }
            if (collinearPruned) continue

            // Search for a strictly convex ear containing no other active polygon vertex
            var bestEarIndex = -1
            var bestEarScore = Double.NEGATIVE_INFINITY

            for (k in 0 until m) {
                val iPrev = indices[(k - 1 + m) % m]
                val iCurr = indices[k]
                val iNext = indices[(k + 1) % m]

                val a = cleaned[iPrev]
                val b = cleaned[iCurr]
                val c = cleaned[iNext]

                val cross = (b - a).cross(c - a)
                if (cross <= AREA_EPSILON) continue // Reflex or collinear corner

                var containsOtherVertex = false
                for (j in 0 until m) {
                    val candidateIdx = indices[j]
                    if (candidateIdx == iPrev || candidateIdx == iCurr || candidateIdx == iNext) continue
                    val p = cleaned[candidateIdx]
                    if (pointInOrOnTriangle(p, a, b, c, tolerance = 1e-8)) {
                        containsOtherVertex = true
                        break
                    }
                }

                if (!containsOtherVertex) {
                    // Prefer well-proportioned ears (maximize area / perimeter^2) for numerical stability
                    val perim = a.distanceTo(b) + b.distanceTo(c) + c.distanceTo(a)
                    val quality = cross / max(perim * perim, 1e-12)
                    if (quality > bestEarScore) {
                        bestEarScore = quality
                        bestEarIndex = k
                    }
                }
            }

            if (bestEarIndex >= 0) {
                val iPrev = indices[(bestEarIndex - 1 + m) % m]
                val iCurr = indices[bestEarIndex]
                val iNext = indices[(bestEarIndex + 1) % m]
                triangles.add(Triangle2D(cleaned[iPrev], cleaned[iCurr], cleaned[iNext]))
                indices.removeAt(bestEarIndex)
                earFound = true
            }

            if (!earFound) {
                // Fallback pass with slightly relaxed interior tolerance for touching vertices
                for (k in 0 until m) {
                    val iPrev = indices[(k - 1 + m) % m]
                    val iCurr = indices[k]
                    val iNext = indices[(k + 1) % m]
                    val a = cleaned[iPrev]
                    val b = cleaned[iCurr]
                    val c = cleaned[iNext]
                    if ((b - a).cross(c - a) <= AREA_EPSILON) continue

                    var strictlyInside = false
                    for (j in 0 until m) {
                        val candidateIdx = indices[j]
                        if (candidateIdx == iPrev || candidateIdx == iCurr || candidateIdx == iNext) continue
                        if (pointInOrOnTriangle(cleaned[candidateIdx], a, b, c, tolerance = -1e-7)) {
                            strictlyInside = true
                            break
                        }
                    }
                    if (!strictlyInside) {
                        triangles.add(Triangle2D(a, b, c))
                        indices.removeAt(k)
                        earFound = true
                        break
                    }
                }
            }

            if (!earFound) {
                throw InvalidPolygonException("Failed to triangulate polygon; boundary may be self-intersecting")
            }
        }

        if (indices.size == 3) {
            val tri = Triangle2D(cleaned[indices[0]], cleaned[indices[1]], cleaned[indices[2]])
            if (tri.areaSqMeters > AREA_EPSILON) {
                triangles.add(tri)
            }
        }

        return triangles
    }

    /**
     * Computes the exact boolean difference `parent \ union(cutouts)` as a disjoint collection
     * of CCW convex polygons.
     *
     * Handles:
     * - Arbitrary concave parent surfaces (L-shapes, alcoves, gables)
     * - Arbitrary concave or convex cutouts (windows, vents, floor islands)
     * - Boundary-flush cutouts (doors resting on the floor baseline)
     * - Overlapping or protruding cutouts (never double-subtracts overlapping cutout regions)
     */
    fun subtractCutoutsExact(
        parentPolygon: List<Vec2>,
        cutoutPolygons: List<List<Vec2>>
    ): List<List<Vec2>> {
        val parentTriangles = triangulateSimplePolygon(parentPolygon)
        var survivingConvexPieces: List<List<Vec2>> = parentTriangles.map { listOf(it.a, it.b, it.c) }

        if (cutoutPolygons.isEmpty()) {
            return survivingConvexPieces
        }

        for (cutout in cutoutPolygons) {
            val cutoutTriangles = triangulateSimplePolygon(cutout)
            for (holeTri in cutoutTriangles) {
                val holeBBox = BoundingBox2D.fromPoints(listOf(holeTri.a, holeTri.b, holeTri.c))
                val nextPieces = ArrayList<List<Vec2>>(survivingConvexPieces.size + 2)

                for (convexPiece in survivingConvexPieces) {
                    val pieceBBox = BoundingBox2D.fromPoints(convexPiece)
                    if (!pieceBBox.intersects(holeBBox, tolerance = 1e-7)) {
                        nextPieces.add(convexPiece)
                    } else {
                        nextPieces.addAll(subtractConvexTriangleFromConvexPolygon(convexPiece, holeTri))
                    }
                }
                survivingConvexPieces = nextPieces
                if (survivingConvexPieces.isEmpty()) break
            }
            if (survivingConvexPieces.isEmpty()) break
        }

        return survivingConvexPieces
    }

    /**
     * Computes the exact intersection area `Area(polygonA intersect polygonB)` for any two simple polygons.
     */
    fun intersectionArea(polygonA: List<Vec2>, polygonB: List<Vec2>): Double {
        val trisA = triangulateSimplePolygon(polygonA)
        val trisB = triangulateSimplePolygon(polygonB)
        var total = 0.0
        for (ta in trisA) {
            val boxA = BoundingBox2D.fromPoints(listOf(ta.a, ta.b, ta.c))
            val polyTriA = listOf(ta.a, ta.b, ta.c)
            for (tb in trisB) {
                val boxB = BoundingBox2D.fromPoints(listOf(tb.a, tb.b, tb.c))
                if (!boxA.intersects(boxB, tolerance = 1e-7)) continue
                val clipped = clipConvexAgainstConvexTriangle(polyTriA, tb)
                if (clipped.size >= 3) {
                    total += shoelaceArea(clipped)
                }
            }
        }
        return total
    }

    /**
     * Fan-triangulates a collection of disjoint CCW convex polygons into [Triangle2D] primitives.
     */
    fun fanTriangulateConvexPieces(convexPolygons: List<List<Vec2>>): List<Triangle2D> {
        val result = ArrayList<Triangle2D>()
        for (poly in convexPolygons) {
            if (poly.size < 3) continue
            val root = poly[0]
            for (i in 1 until poly.size - 1) {
                val tri = Triangle2D(root, poly[i], poly[i + 1])
                if (tri.areaSqMeters > AREA_EPSILON) {
                    result.add(tri)
                }
            }
        }
        return result
    }

    /**
     * Partitions `K \ H` into up to 3 pairwise-disjoint CCW convex polygons using half-plane decomposition:
     * `K \ (E0^- n E1^- n E2^-) = (K n E0^+) u (K n E0^- n E1^+) u (K n E0^- n E1^- n E2^+)`.
     */
    private fun subtractConvexTriangleFromConvexPolygon(
        convexPoly: List<Vec2>,
        ccwHoleTriangle: Triangle2D
    ): List<List<Vec2>> {
        val edges = arrayOf(
            ccwHoleTriangle.a to ccwHoleTriangle.b,
            ccwHoleTriangle.b to ccwHoleTriangle.c,
            ccwHoleTriangle.c to ccwHoleTriangle.a
        )

        val outsidePieces = ArrayList<List<Vec2>>(3)
        var remainingInside = convexPoly

        for ((edgeStart, edgeEnd) in edges) {
            // For a CCW triangle edge A -> B, points with (B - A) x (P - A) < 0 lie strictly outside the triangle.
            val outsideHalf = clipConvexByDirectedLine(
                convexPoly = remainingInside,
                lineStart = edgeStart,
                lineEnd = edgeEnd,
                keepLeftPositiveSide = false
            )
            if (outsideHalf.size >= 3 && shoelaceArea(outsideHalf) > AREA_EPSILON) {
                outsidePieces.add(outsideHalf)
            }

            remainingInside = clipConvexByDirectedLine(
                convexPoly = remainingInside,
                lineStart = edgeStart,
                lineEnd = edgeEnd,
                keepLeftPositiveSide = true
            )
            if (remainingInside.size < 3 || shoelaceArea(remainingInside) <= AREA_EPSILON) {
                // Convex piece was completely outside this edge of the hole; nothing left to subtract!
                return outsidePieces
            }
        }

        // Whatever remains in remainingInside lies inside all 3 half-planes of ccwHoleTriangle and is discarded.
        return outsidePieces
    }

    private fun clipConvexAgainstConvexTriangle(
        subjectConvex: List<Vec2>,
        clipTriangle: Triangle2D
    ): List<Vec2> {
        var current = subjectConvex
        val edges = arrayOf(
            clipTriangle.a to clipTriangle.b,
            clipTriangle.b to clipTriangle.c,
            clipTriangle.c to clipTriangle.a
        )
        for ((a, b) in edges) {
            current = clipConvexByDirectedLine(current, a, b, keepLeftPositiveSide = true)
            if (current.size < 3) return emptyList()
        }
        return current
    }

    /**
     * One-step Sutherland-Hodgman half-plane clip of a convex polygon against directed line `lineStart -> lineEnd`.
     */
    private fun clipConvexByDirectedLine(
        convexPoly: List<Vec2>,
        lineStart: Vec2,
        lineEnd: Vec2,
        keepLeftPositiveSide: Boolean
    ): List<Vec2> {
        if (convexPoly.isEmpty()) return emptyList()
        val dir = lineEnd - lineStart
        val signMultiplier = if (keepLeftPositiveSide) 1.0 else -1.0

        fun eval(p: Vec2): Double = signMultiplier * dir.cross(p - lineStart)

        val output = ArrayList<Vec2>(convexPoly.size + 1)
        val n = convexPoly.size

        for (i in 0 until n) {
            val s = convexPoly[i]
            val e = convexPoly[(i + 1) % n]
            val sVal = eval(s)
            val eVal = eval(e)
            val sInside = sVal >= -GEOM_EPSILON
            val eInside = eVal >= -GEOM_EPSILON

            if (sInside && eInside) {
                output.add(e)
            } else if (sInside && !eInside) {
                val denom = sVal - eVal
                val t = if (abs(denom) <= 1e-18) 0.0 else (sVal / denom).coerceIn(0.0, 1.0)
                output.add(s.lerp(e, t))
            } else if (!sInside && eInside) {
                val denom = sVal - eVal
                val t = if (abs(denom) <= 1e-18) 0.0 else (sVal / denom).coerceIn(0.0, 1.0)
                output.add(s.lerp(e, t))
                output.add(e)
            }
        }

        return removeSequentialDuplicates(output, tolerance = 1e-8)
    }

    private fun pointInOrOnTriangle(
        p: Vec2,
        a: Vec2,
        b: Vec2,
        c: Vec2,
        tolerance: Double
    ): Boolean {
        val c1 = (b - a).cross(p - a)
        val c2 = (c - b).cross(p - b)
        val c3 = (a - c).cross(p - c)
        return c1 >= -tolerance && c2 >= -tolerance && c3 >= -tolerance
    }

    private fun segmentsIntersect(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Boolean {
        val minAU = min(a1.u, a2.u) - GEOM_EPSILON
        val maxAU = max(a1.u, a2.u) + GEOM_EPSILON
        val minAV = min(a1.v, a2.v) - GEOM_EPSILON
        val maxAV = max(a1.v, a2.v) + GEOM_EPSILON

        val minBU = min(b1.u, b2.u) - GEOM_EPSILON
        val maxBU = max(b1.u, b2.u) + GEOM_EPSILON
        val minBV = min(b1.v, b2.v) - GEOM_EPSILON
        val maxBV = max(b1.v, b2.v) + GEOM_EPSILON

        if (maxAU < minBU || maxBU < minAU || maxAV < minBV || maxBV < minAV) {
            return false
        }

        val d1 = (a2 - a1).cross(b1 - a1)
        val d2 = (a2 - a1).cross(b2 - a1)
        val d3 = (b2 - b1).cross(a1 - b1)
        val d4 = (b2 - b1).cross(a2 - b1)

        if (((d1 > GEOM_EPSILON && d2 < -GEOM_EPSILON) || (d1 < -GEOM_EPSILON && d2 > GEOM_EPSILON)) &&
            ((d3 > GEOM_EPSILON && d4 < -GEOM_EPSILON) || (d3 < -GEOM_EPSILON && d4 > GEOM_EPSILON))
        ) {
            return true
        }

        if (abs(d1) <= GEOM_EPSILON && onSegmentBoundingBox(b1, a1, a2)) return true
        if (abs(d2) <= GEOM_EPSILON && onSegmentBoundingBox(b2, a1, a2)) return true
        if (abs(d3) <= GEOM_EPSILON && onSegmentBoundingBox(a1, b1, b2)) return true
        if (abs(d4) <= GEOM_EPSILON && onSegmentBoundingBox(a2, b1, b2)) return true

        return false
    }

    private fun onSegmentBoundingBox(p: Vec2, s1: Vec2, s2: Vec2): Boolean =
        p.u >= min(s1.u, s2.u) - GEOM_EPSILON &&
            p.u <= max(s1.u, s2.u) + GEOM_EPSILON &&
            p.v >= min(s1.v, s2.v) - GEOM_EPSILON &&
            p.v <= max(s1.v, s2.v) + GEOM_EPSILON
}
