package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.WastePreset
import java.util.Locale

@Composable
fun SurfacePricingPanel(
    surface: SurfaceQuoteLine,
    materials: List<MaterialSpec>,
    onAction: (QuoteAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameText by remember(surface.surfaceId, surface.surfaceName) {
        mutableStateOf(surface.surfaceName)
    }
    var materialMenuOpen by remember { mutableStateOf(false) }
    var showLineItems by remember(surface.surfaceId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val wasteFactor = surface.customWasteFactorOverride
        ?: surface.effectiveMaterial.effectiveWasteFactor
    var wasteSliderValue by remember(surface.surfaceId, wasteFactor) {
        mutableFloatStateOf(wasteFactor.toFloat())
    }
    val laborRate = surface.effectiveMaterial.laborRatePerUnitArea
    var laborSliderValue by remember(surface.surfaceId, laborRate) {
        mutableFloatStateOf(laborRate.toFloat())
    }

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
                        text = "Surface pricing",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Changes recalculate every quote line immediately.",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                surface.estimate?.let {
                    Text(
                        text = formatMoney(it.grandTotal),
                        style = numericTextStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                text = "Surface name",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    label = { Text("Name") }
                )
                TextButton(
                    onClick = {
                        onAction(QuoteAction.RenameSurface(surface.surfaceId, nameText))
                    },
                    enabled = nameText.isNotBlank() && nameText.trim() != surface.surfaceName,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Catalog material",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                OutlinedButton(
                    onClick = { materialMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = surface.effectiveMaterial.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${surface.effectiveMaterial.tradeType.shortName()} · " +
                                surface.effectiveMaterial.sku.ifBlank { "No SKU" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "CHANGE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                }
                DropdownMenu(
                    expanded = materialMenuOpen,
                    onDismissRequest = { materialMenuOpen = false },
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    materials.forEach { material ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = material.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${material.tradeType.shortName()} · " +
                                            "${formatRate(material.unitCost, material.primaryUnitType.singular)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                materialMenuOpen = false
                                onAction(
                                    QuoteAction.SetSurfaceMaterial(
                                        surface.surfaceId,
                                        material.id
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Waste allowance",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatPercent(surface.effectiveMaterial.effectiveWasteFactor),
                    style = numericTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                WastePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = surface.effectiveMaterial.wastePreset == preset,
                        onClick = {
                            onAction(
                                QuoteAction.SetSurfaceWastePreset(surface.surfaceId, preset)
                            )
                        },
                        label = {
                            Text(
                                text = preset.quoteLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        shape = RoundedCornerShape(5.dp)
                    )
                }
            }
            if (surface.effectiveMaterial.wastePreset == WastePreset.CUSTOM) {
                Text(
                    text = "Custom waste ${String.format(Locale.US, "%.1f%%", wasteSliderValue * 100f)}",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = wasteSliderValue,
                    onValueChange = { wasteSliderValue = it },
                    onValueChangeFinished = {
                        onAction(
                            QuoteAction.SetSurfaceWasteFactor(
                                surface.surfaceId,
                                wasteSliderValue.toDouble()
                            )
                        )
                    },
                    valueRange = 0f..0.35f,
                    steps = 34
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Labor rate",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatRate(
                        laborSliderValue.toDouble(),
                        surface.effectiveMaterial.pricingAreaUnit.symbol
                    ),
                    style = numericTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Slider(
                value = laborSliderValue,
                onValueChange = { laborSliderValue = it },
                onValueChangeFinished = {
                    onAction(
                        QuoteAction.SetSurfaceLaborRate(
                            surface.surfaceId,
                            laborSliderValue.toDouble()
                        )
                    )
                },
                valueRange = 0f..200f,
                steps = 39
            )

            surface.analysis?.let { analysis ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    MeasurementFact(
                        label = "Gross",
                        value = formatArea(analysis.grossAreaSqMeters),
                        modifier = Modifier.weight(1f)
                    )
                    MeasurementFact(
                        label = "Openings",
                        value = formatArea(analysis.cutoutAreaSqMeters),
                        modifier = Modifier.weight(1f)
                    )
                    MeasurementFact(
                        label = "Net",
                        value = formatArea(analysis.netAreaSqMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "${surface.cutouts.size} opening${if (surface.cutouts.size == 1) "" else "s"} · " +
                        "outer perimeter ${String.format(Locale.US, "%.2f m", analysis.outerPerimeterMeters)} · " +
                        "flatness RMS ${String.format(Locale.US, "%.1f mm", analysis.rmsPlanarErrorMeters * 1_000.0)}",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            surface.estimate?.let { estimate ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pricing lines",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${estimate.lineItems.size} material, consumable, and labor entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showLineItems = !showLineItems }) {
                        Text(if (showLineItems) "Hide" else "Show")
                    }
                }
                if (showLineItems) {
                    estimate.lineItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${formatQuantity(item.quantity)} ${item.unitLabel} · ${item.skuOrBasis}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatMoney(item.lineTotal),
                                style = numericTextStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove surface")
                }
                TextButton(
                    onClick = {
                        onAction(QuoteAction.OpenManualEntry(surface.surfaceId))
                    }
                ) {
                    Text(if (surface.loadError != null) "Fix dimensions" else "Re-enter dimensions")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Editable at any time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${surface.surfaceName}?") },
            text = {
                Text(
                    "The saved anchors and its openings will be deleted from this project. " +
                        "Catalog materials are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onAction(QuoteAction.DeleteSurface(surface.surfaceId))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove surface")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep surface")
                }
            }
        )
    }
}

@Composable
private fun MeasurementFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp),
            style = numericTextStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", quantity)
    } else {
        String.format(Locale.US, "%.2f", quantity)
    }
