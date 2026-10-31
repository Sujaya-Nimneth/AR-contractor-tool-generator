package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.PackageUnitType
import com.arcontractor.estimator.domain.estimation.PricingAreaUnit
import com.arcontractor.estimator.domain.estimation.TradeParameters
import com.arcontractor.estimator.domain.estimation.TradeType
import com.arcontractor.estimator.domain.estimation.WastePreset

@Composable
fun MaterialCatalogDialog(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit
) {
    Dialog(
        onDismissRequest = { onAction(QuoteAction.CloseCatalog) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                CatalogDialogHeader(
                    editing = state.catalogEditor != null,
                    onBack = {
                        if (state.catalogEditor != null) {
                            onAction(QuoteAction.CloseCatalogEditor)
                        } else {
                            onAction(QuoteAction.CloseCatalog)
                        }
                    },
                    onClose = { onAction(QuoteAction.CloseCatalog) }
                )
                if (state.message != null) {
                    CatalogMessage(
                        message = state.message,
                        onDismiss = { onAction(QuoteAction.DismissMessage) }
                    )
                }
                val editor = state.catalogEditor
                if (editor == null) {
                    CatalogList(
                        materials = state.workspace?.materials.orEmpty(),
                        onAction = onAction
                    )
                } else {
                    CatalogEditor(
                        draft = editor,
                        isSaving = state.isSavingCatalog,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogDialogHeader(
    editing: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to material catalog"
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (editing) "Edit material" else "Material catalog",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (editing) {
                    "Pricing and package assumptions"
                } else {
                    "Offline materials for every trade"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close material catalog"
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CatalogMessage(
    message: QuoteMessage,
    onDismiss: () -> Unit
) {
    val color = if (message.isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss message"
                )
            }
        }
    }
}

@Composable
private fun CatalogList(
    materials: List<MaterialSpec>,
    onAction: (QuoteAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 32.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${materials.size} materials",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Package coverage, cost, waste, and labor stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onAction(QuoteAction.EditCatalogMaterial(null)) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("New material")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (materials.isEmpty()) {
            item {
                EmptyLedgerPanel(
                    title = "No catalog materials",
                    body = "Create a material before pricing a measured surface."
                )
            }
        } else {
            items(materials, key = MaterialSpec::id) { material ->
                CatalogMaterialRow(
                    material = material,
                    onClick = {
                        onAction(QuoteAction.EditCatalogMaterial(material.id))
                    }
                )
            }
        }
    }
}

@Composable
private fun CatalogMaterialRow(
    material: MaterialSpec,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${material.tradeType.shortName()} · ${material.sku.ifBlank { "No SKU" }}",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatMoney(java.math.BigDecimal.valueOf(material.unitCost)),
                    style = numericTextStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                CatalogFact(
                    label = "Package",
                    value = material.primaryUnitType.singular,
                    modifier = Modifier.weight(1f)
                )
                CatalogFact(
                    label = "Coverage",
                    value = formatAreaCompact(material.boxCoverageSqMeters),
                    modifier = Modifier.weight(1f)
                )
                CatalogFact(
                    label = "Waste",
                    value = formatPercent(material.effectiveWasteFactor),
                    modifier = Modifier.weight(1f)
                )
                CatalogFact(
                    label = "Labor",
                    value = formatRate(
                        material.laborRatePerUnitArea,
                        material.pricingAreaUnit.symbol
                    ),
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
private fun CatalogFact(
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

@Composable
private fun CatalogEditor(
    draft: CatalogEditorDraft,
    isSaving: Boolean,
    onAction: (QuoteAction) -> Unit
) {
    var packageMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 36.dp)
    ) {
        item {
            Text(
                text = if (draft.isNew) "New catalog material" else "Material details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Coverage and cost are per primary package. Labor is per selected area unit.",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            CatalogTextField(
                value = draft.name,
                onValueChange = {
                    onAction(QuoteAction.UpdateCatalogEditor(draft.copy(name = it)))
                },
                label = "Material name",
                modifier = Modifier.padding(top = 18.dp)
            )
        }

        item {
            CatalogTextField(
                value = draft.sku,
                onValueChange = {
                    onAction(QuoteAction.UpdateCatalogEditor(draft.copy(sku = it)))
                },
                label = "SKU",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            CatalogSectionLabel(
                label = "Trade",
                detail = "Changing trade also restores that trade's default consumable assumptions.",
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                TradeType.entries.forEach { trade ->
                    FilterChip(
                        selected = draft.tradeType == trade,
                        onClick = {
                            onAction(
                                QuoteAction.UpdateCatalogEditor(draft.copy(tradeType = trade))
                            )
                        },
                        label = { Text(trade.shortName()) },
                        shape = RoundedCornerShape(5.dp)
                    )
                }
            }
        }

        item {
            CatalogSectionLabel(
                label = "Primary package",
                detail = "The unit purchased and priced before quantity rounding.",
                modifier = Modifier.padding(top = 20.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
            ) {
                OutlinedButton(
                    onClick = { packageMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = draft.primaryUnitType.plural,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "CHANGE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                }
                DropdownMenu(
                    expanded = packageMenuOpen,
                    onDismissRequest = { packageMenuOpen = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    PackageUnitType.entries.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.plural) },
                            onClick = {
                                packageMenuOpen = false
                                onAction(
                                    QuoteAction.UpdateCatalogEditor(
                                        draft.copy(primaryUnitType = unit)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        item {
            CatalogTextField(
                value = draft.coverageText,
                onValueChange = {
                    onAction(QuoteAction.UpdateCatalogEditor(draft.copy(coverageText = it)))
                },
                label = "Coverage per ${draft.primaryUnitType.singular.lowercase()} (m²)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        item {
            CatalogTextField(
                value = draft.unitCostText,
                onValueChange = {
                    onAction(QuoteAction.UpdateCatalogEditor(draft.copy(unitCostText = it)))
                },
                label = "Cost per ${draft.primaryUnitType.singular.lowercase()} (USD)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            CatalogSectionLabel(
                label = "Waste allowance",
                detail = "Applied to net measured area before package rounding.",
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                WastePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = draft.wastePreset == preset,
                        onClick = {
                            onAction(
                                QuoteAction.UpdateCatalogEditor(
                                    draft.copy(wastePreset = preset)
                                )
                            )
                        },
                        label = { Text(preset.quoteLabel()) },
                        shape = RoundedCornerShape(5.dp)
                    )
                }
            }
        }

        if (draft.wastePreset == WastePreset.CUSTOM) {
            item {
                CatalogTextField(
                    value = draft.customWastePercentText,
                    onValueChange = {
                        onAction(
                            QuoteAction.UpdateCatalogEditor(
                                draft.copy(customWastePercentText = it)
                            )
                        )
                    },
                    label = "Custom waste (%)",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        item {
            CatalogTextField(
                value = draft.laborRateText,
                onValueChange = {
                    onAction(QuoteAction.UpdateCatalogEditor(draft.copy(laborRateText = it)))
                },
                label = "Labor rate per ${draft.pricingAreaUnit.symbol} (USD)",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item {
            CatalogSectionLabel(
                label = "Labor area unit",
                detail = "Core geometry remains metric; pricing converts square feet when selected.",
                modifier = Modifier.padding(top = 20.dp)
            )
            Row(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PricingAreaUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = draft.pricingAreaUnit == unit,
                        onClick = {
                            onAction(
                                QuoteAction.UpdateCatalogEditor(
                                    draft.copy(pricingAreaUnit = unit)
                                )
                            )
                        },
                        label = { Text(unit.symbol) },
                        shape = RoundedCornerShape(5.dp)
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Trade assumptions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = draft.tradeParameters.summary(),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!draft.isNew) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !isSaving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete material")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onAction(QuoteAction.CloseCatalogEditor) },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onAction(QuoteAction.SaveCatalogMaterial) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isSaving) "Saving…" else "Save material")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.name}?") },
            text = {
                Text(
                    "A material assigned to a measured surface cannot be deleted. " +
                        "Reassign those surfaces first."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onAction(QuoteAction.DeleteCatalogMaterial)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete material")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Keep material")
                }
            }
        )
    }
}

@Composable
private fun CatalogSectionLabel(
    label: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = detail,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

fun WastePreset.quoteLabel(): String = when (this) {
    WastePreset.STRAIGHT_LAY -> "Straight 10%"
    WastePreset.DIAGONAL_OR_HERRINGBONE -> "Diagonal 15%"
    WastePreset.COMPLEX_PATTERN -> "Complex 18%"
    WastePreset.PAINT_OR_DRYWALL -> "Paint / board 5%"
    WastePreset.DRYWALL_MULTI_CUTOUT -> "Multi-opening 10%"
    WastePreset.CUSTOM -> "Custom"
}

private fun TradeParameters.summary(): String = when (this) {
    is TradeParameters.Tiling ->
        "${tileWidthMm.toInt()} × ${tileHeightMm.toInt()} mm tile · ${jointWidthMm} mm joint · " +
            "${thinsetBagWeightKg} kg thinset bag"

    is TradeParameters.Flooring ->
        "${underlaymentRollCoverageSqMeters} m² underlayment roll · " +
            "${baseboardPieceLengthMeters} m baseboard pieces"

    is TradeParameters.Painting ->
        "$finishCoatCount finish coat${if (finishCoatCount == 1) "" else "s"} · " +
            "$primerCoatCount primer coat${if (primerCoatCount == 1) "" else "s"}"

    is TradeParameters.Drywall ->
        "${sheetWidthMeters} × ${sheetHeightMeters} m sheets · " +
            "${jointCompoundBucketCoverageSqMeters} m² per compound bucket"
}
