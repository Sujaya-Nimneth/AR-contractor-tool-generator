package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import java.util.Locale

@Composable
fun ProjectAdjustmentsPanel(
    taxRate: Double,
    taxPolicy: TaxPolicy,
    onAction: (QuoteAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(taxRate) { mutableFloatStateOf(taxRate.toFloat()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Project adjustments",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tax is applied across every measured surface.",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f%%", sliderValue * 100f),
                    style = numericTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onAction(QuoteAction.SetProjectTaxRate(sliderValue.toDouble()))
                },
                valueRange = 0f..0.25f,
                steps = 49,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "Set to 0% when the project is not taxable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "Taxable subtotal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = taxPolicy == TaxPolicy.TAX_TOTAL_SUBTOTAL,
                    onClick = {
                        onAction(
                            QuoteAction.SetProjectTaxPolicy(TaxPolicy.TAX_TOTAL_SUBTOTAL)
                        )
                    },
                    label = { Text("Goods + labor") },
                    shape = RoundedCornerShape(5.dp)
                )
                FilterChip(
                    selected = taxPolicy == TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY,
                    onClick = {
                        onAction(
                            QuoteAction.SetProjectTaxPolicy(
                                TaxPolicy.TAX_MATERIALS_AND_CONSUMABLES_ONLY
                            )
                        )
                    },
                    label = { Text("Goods only") },
                    shape = RoundedCornerShape(5.dp)
                )
            }
        }
    }
}
