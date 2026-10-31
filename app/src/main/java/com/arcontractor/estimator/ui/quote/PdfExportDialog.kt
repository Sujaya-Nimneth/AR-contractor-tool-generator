package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcontractor.estimator.data.pdf.PdfPaperSize

@Composable
fun PdfExportDialog(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit
) {
    val workspace = state.workspace ?: return
    val draft = state.exportDraft
    val hasSurfaces = workspace.surfaces.isNotEmpty()
    val hasValidGeometry = workspace.surfaces.all { it.analysis != null && it.estimate != null }
    val validityIsInvalid = draft.validityDaysText.isNotEmpty() && draft.validityDays == null
    val canExport = hasSurfaces &&
        hasValidGeometry &&
        draft.validityDays != null &&
        !state.isExportingPdf

    AlertDialog(
        onDismissRequest = {
            if (!state.isExportingPdf) {
                onAction(QuoteAction.CloseExportDialog)
            }
        },
        title = {
            Column {
                Text(
                    text = "Export client PDF",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "A vector blueprint and itemized estimate generated entirely on this device.",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(
                        text = "PAPER SIZE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfPaperSize.entries.forEach { paperSize ->
                            FilterChip(
                                selected = draft.paperSize == paperSize,
                                onClick = {
                                    onAction(QuoteAction.SetExportPaperSize(paperSize))
                                },
                                label = { Text(paperSize.displayName) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = draft.validityDaysText,
                    onValueChange = {
                        onAction(QuoteAction.SetExportValidityDays(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quote validity (days)") },
                    singleLine = true,
                    isError = validityIsInvalid,
                    supportingText = {
                        Text(
                            if (validityIsInvalid) {
                                "Enter a whole number from 1 to 365."
                            } else {
                                "The acceptance page prints the calculated expiry date."
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )

                Text(
                    text = buildString {
                        append(workspace.surfaces.size)
                        append(if (workspace.surfaces.size == 1) " measured surface" else " measured surfaces")
                        append(" with ")
                        append(
                            workspace.surfaces.sumOf { it.estimate?.lineItems?.size ?: 0 }
                        )
                        append(" itemized lines.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasSurfaces) {
                    Text(
                        text = "Measure at least one surface before exporting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!hasValidGeometry) {
                    Text(
                        text = "Fix surfaces with missing geometry before exporting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(QuoteAction.ExportPdf) },
                enabled = canExport
            ) {
                if (state.isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(if (state.isExportingPdf) "Rendering PDF" else "Generate PDF")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(QuoteAction.CloseExportDialog) },
                enabled = !state.isExportingPdf
            ) {
                Text("Cancel")
            }
        }
    )
}
