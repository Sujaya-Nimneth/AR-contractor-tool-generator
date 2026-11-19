package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap

private val currencyFormatters = ConcurrentHashMap<String, NumberFormat>()

fun formatMoney(value: BigDecimal, currencyCode: String = "USD"): String {
    val formatter = currencyFormatters.getOrPut(currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
    }
    return formatter.format(value)
}

fun formatArea(
    squareMeters: Double,
    units: DisplayUnitSystem = DisplayUnitSystem.METRIC
): String = when (units) {
    DisplayUnitSystem.METRIC -> String.format(Locale.US, "%.2f m²", squareMeters)
    DisplayUnitSystem.IMPERIAL -> String.format(
        Locale.US,
        "%.1f ft²",
        com.arcontractor.estimator.domain.geometry.UnitConverter.sqMetersToSqFeet(squareMeters)
    )
}

fun formatAreaCompact(
    squareMeters: Double,
    units: DisplayUnitSystem = DisplayUnitSystem.METRIC
): String = when (units) {
    DisplayUnitSystem.METRIC -> String.format(Locale.US, "%.1f m²", squareMeters)
    DisplayUnitSystem.IMPERIAL -> String.format(
        Locale.US,
        "%.0f ft²",
        com.arcontractor.estimator.domain.geometry.UnitConverter.sqMetersToSqFeet(squareMeters)
    )
}

fun formatLength(
    meters: Double,
    units: DisplayUnitSystem = DisplayUnitSystem.METRIC
): String = when (units) {
    DisplayUnitSystem.METRIC -> String.format(Locale.US, "%.2f m", meters)
    DisplayUnitSystem.IMPERIAL ->
        com.arcontractor.estimator.domain.geometry.UnitConverter.formatArchitecturalFeetInches(meters)
}

fun formatPercent(fraction: Double): String =
    String.format(Locale.US, "%.1f%%", fraction * 100.0)

fun formatRate(rate: Double, unit: String, currencyCode: String = "USD"): String =
    "${formatMoney(BigDecimal.valueOf(rate), currencyCode)} / $unit"

val numericTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum"
    )

@Composable
fun SectionHeader(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = detail,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        action?.invoke()
    }
}

@Composable
fun EmptyLedgerPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val borderColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                shape = RoundedCornerShape(8.dp)
            )
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            .padding(18.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    action()
                }
            }
        }
    }
}

@Composable
fun LedgerValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (emphasized) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = value,
            style = numericTextStyle.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (emphasized) MaterialTheme.typography.titleMedium.fontSize else
                    MaterialTheme.typography.bodyMedium.fontSize
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
