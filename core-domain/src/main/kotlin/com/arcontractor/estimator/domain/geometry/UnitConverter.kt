package com.arcontractor.estimator.domain.geometry

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Lossless SI (metric) and Imperial unit conversions and architectural string formatters.
 */
object UnitConverter {
    const val METERS_PER_FOOT: Double = 0.3048
    const val METERS_PER_INCH: Double = 0.0254
    const val SQ_METERS_PER_SQ_FOOT: Double = METERS_PER_FOOT * METERS_PER_FOOT // 0.09290304 exact
    const val MILLIMETERS_PER_METER: Double = 1000.0

    fun metersToFeet(meters: Double): Double = meters / METERS_PER_FOOT
    fun feetToMeters(feet: Double): Double = feet * METERS_PER_FOOT

    fun metersToInches(meters: Double): Double = meters / METERS_PER_INCH
    fun inchesToMeters(inches: Double): Double = inches * METERS_PER_INCH

    fun millimetersToMeters(mm: Double): Double = mm / MILLIMETERS_PER_METER
    fun metersToMillimeters(meters: Double): Double = meters * MILLIMETERS_PER_METER

    fun sqMetersToSqFeet(sqMeters: Double): Double = sqMeters / SQ_METERS_PER_SQ_FOOT
    fun sqFeetToSqMeters(sqFeet: Double): Double = sqFeet * SQ_METERS_PER_SQ_FOOT

    /**
     * Formats a metric length as `2.45 m`.
     */
    fun formatMeters(meters: Double, decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f m", meters)

    /**
     * Formats a metric length in architectural feet and inches rounded to nearest half-inch or inch,
     * e.g., `2.4384 m -> 8' 0"`, `1.0668 m -> 3' 6"`.
     */
    fun formatArchitecturalFeetInches(meters: Double): String {
        val sign = if (meters < 0.0) "-" else ""
        val totalInchesRoundedTenths = (abs(meters) / METERS_PER_INCH * 2.0).roundToInt() / 2.0
        var wholeFeet = floor(totalInchesRoundedTenths / 12.0).toInt()
        var remainingInches = totalInchesRoundedTenths - wholeFeet * 12.0
        if (remainingInches >= 11.99) {
            wholeFeet += 1
            remainingInches = 0.0
        }
        val inchText = if (abs(remainingInches - remainingInches.roundToInt()) < 1e-4) {
            "${remainingInches.roundToInt()}\""
        } else {
            String.format(Locale.US, "%.1f\"", remainingInches)
        }
        return "$sign${wholeFeet}' $inchText"
    }
}
