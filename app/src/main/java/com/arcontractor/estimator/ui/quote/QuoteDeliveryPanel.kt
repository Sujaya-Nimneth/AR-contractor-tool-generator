package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcontractor.estimator.data.repository.ProjectWorkspace

@Composable
fun QuoteDeliveryPanel(
    workspace: ProjectWorkspace,
    isExporting: Boolean,
    onEditProject: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project = workspace.project
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 680.dp
            Column(modifier = Modifier.padding(18.dp)) {
                if (compact) {
                    DeliveryHeading(project.contractorName)
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onEditProject) {
                            Text("Edit details")
                        }
                        ExportButton(
                            enabled = workspace.surfaces.isNotEmpty() && !isExporting,
                            isExporting = isExporting,
                            onClick = onExportPdf
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DeliveryHeading(
                            contractorName = project.contractorName,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onEditProject) {
                            Text("Edit details")
                        }
                        ExportButton(
                            enabled = workspace.surfaces.isNotEmpty() && !isExporting,
                            isExporting = isExporting,
                            onClick = onExportPdf
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    DeliveryFact(
                        label = "CLIENT",
                        value = project.clientName.ifBlank { "Not entered" },
                        modifier = Modifier.weight(1f)
                    )
                    DeliveryFact(
                        label = "CONTACT",
                        value = project.contractorContact.ifBlank { "Not entered" },
                        modifier = Modifier.weight(1f)
                    )
                    if (!compact) {
                        DeliveryFact(
                            label = "LICENSE",
                            value = project.contractorLicense.ifBlank { "Not entered" },
                            modifier = Modifier.weight(0.72f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryHeading(
    contractorName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "QUOTE DELIVERY",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = contractorName.ifBlank { "Add your contractor branding" },
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Export an A4 or Letter quote with blueprints, line items, terms, and signature.",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeliveryFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExportButton(
    enabled: Boolean,
    isExporting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(if (isExporting) "Rendering" else "Export PDF")
    }
}
