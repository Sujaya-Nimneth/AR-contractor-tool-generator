package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.ManualSurfaceOrientation

@Composable
fun ManualSurfaceDialog(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit
) {
    val draft = state.manualSurfaceDraft ?: return
    val isReplacing = draft.replaceSurfaceId != null
    AlertDialog(
        onDismissRequest = {
            if (!state.isSavingMeasurement) {
                onAction(QuoteAction.CloseManualEntry)
            }
        },
        title = {
            Column {
                Text(
                    text = if (isReplacing) "Re-enter surface dimensions" else "Add surface manually",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (isReplacing) {
                        "Replaces the saved measurement. Material and pricing stay assigned."
                    } else {
                        "Type measured dimensions — no AR camera needed."
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        onAction(QuoteAction.UpdateManualSurfaceDraft(draft.copy(name = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Surface name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.orientation == ManualSurfaceOrientation.WALL,
                        onClick = {
                            onAction(
                                QuoteAction.UpdateManualSurfaceDraft(
                                    draft.copy(orientation = ManualSurfaceOrientation.WALL)
                                )
                            )
                        },
                        label = { Text("Wall") },
                        shape = RoundedCornerShape(5.dp)
                    )
                    FilterChip(
                        selected = draft.orientation == ManualSurfaceOrientation.FLOOR,
                        onClick = {
                            onAction(
                                QuoteAction.UpdateManualSurfaceDraft(
                                    draft.copy(orientation = ManualSurfaceOrientation.FLOOR)
                                )
                            )
                        },
                        label = { Text("Floor / ceiling") },
                        shape = RoundedCornerShape(5.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ManualLengthUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = draft.unit == unit,
                            onClick = {
                                onAction(QuoteAction.UpdateManualSurfaceDraft(draft.copy(unit = unit)))
                            },
                            label = { Text(unit.label) },
                            shape = RoundedCornerShape(5.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.widthText,
                        onValueChange = {
                            onAction(QuoteAction.UpdateManualSurfaceDraft(draft.copy(widthText = it)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Width (${draft.unit.label})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = draft.heightText,
                        onValueChange = {
                            onAction(QuoteAction.UpdateManualSurfaceDraft(draft.copy(heightText = it)))
                        },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                if (draft.orientation == ManualSurfaceOrientation.WALL) {
                                    "Height (${draft.unit.label})"
                                } else {
                                    "Depth (${draft.unit.label})"
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Openings (${draft.cutouts.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            onAction(
                                QuoteAction.UpdateManualSurfaceDraft(
                                    draft.copy(cutouts = draft.cutouts + ManualCutoutDraft())
                                )
                            )
                        }
                    ) {
                        Text("Add opening")
                    }
                }
                draft.cutouts.forEachIndexed { index, cutout ->
                    ManualCutoutRow(
                        index = index,
                        cutout = cutout,
                        unitLabel = draft.unit.label,
                        onChange = { updated ->
                            onAction(
                                QuoteAction.UpdateManualSurfaceDraft(
                                    draft.copy(
                                        cutouts = draft.cutouts.toMutableList().also {
                                            it[index] = updated
                                        }
                                    )
                                )
                            )
                        },
                        onRemove = {
                            onAction(
                                QuoteAction.UpdateManualSurfaceDraft(
                                    draft.copy(
                                        cutouts = draft.cutouts.filterIndexed { i, _ -> i != index }
                                    )
                                )
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(QuoteAction.SaveManualSurface) },
                enabled = !state.isSavingMeasurement
            ) {
                if (state.isSavingMeasurement) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isReplacing) "Replace measurement" else "Add to quote")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(QuoteAction.CloseManualEntry) },
                enabled = !state.isSavingMeasurement
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ManualCutoutRow(
    index: Int,
    cutout: ManualCutoutDraft,
    unitLabel: String,
    onChange: (ManualCutoutDraft) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Opening ${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRemove) { Text("Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CutoutKind.entries.forEach { kind ->
                FilterChip(
                    selected = cutout.kind == kind,
                    onClick = { onChange(cutout.copy(kind = kind)) },
                    label = { Text(kind.dialogLabel()) },
                    shape = RoundedCornerShape(5.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cutout.widthText,
                onValueChange = { onChange(cutout.copy(widthText = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Width ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = cutout.heightText,
                onValueChange = { onChange(cutout.copy(heightText = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Height ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cutout.offsetHorizontalText,
                onValueChange = { onChange(cutout.copy(offsetHorizontalText = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("From left ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = cutout.offsetVerticalText,
                onValueChange = { onChange(cutout.copy(offsetVerticalText = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("From bottom ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun CutoutKind.dialogLabel(): String = when (this) {
    CutoutKind.DOOR -> "Door"
    CutoutKind.WINDOW -> "Window"
    CutoutKind.VENT -> "Vent"
    CutoutKind.ISLAND_OR_FIXTURE -> "Fixture"
    CutoutKind.OTHER -> "Other"
}
