package com.arcontractor.estimator.ui.quote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.data.pdf.PdfExportResult
import com.arcontractor.estimator.domain.estimation.ProjectQuoteSummary
import com.arcontractor.estimator.domain.estimation.TradeType

@Composable
fun QuoteScreen(
    viewModel: QuoteViewModel,
    onMeasureSurface: () -> Unit,
    onRemeasureSurface: (String) -> Unit = {},
    onSharePdf: (PdfExportResult) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportedPdf = state.exportedPdf

    LaunchedEffect(exportedPdf?.contentUri) {
        if (exportedPdf != null) {
            onSharePdf(exportedPdf)
            viewModel.onAction(QuoteAction.ConsumeExportedPdf)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading && state.workspace == null) {
            QuoteLoading()
        } else {
            QuoteWorkspace(
                state = state,
                onAction = viewModel::onAction,
                onMeasureSurface = onMeasureSurface,
                onRemeasureSurface = onRemeasureSurface
            )
        }
    }

    if (state.isCatalogOpen) {
        MaterialCatalogDialog(
            state = state,
            onAction = viewModel::onAction
        )
    }
    if (state.isManualEntryOpen) {
        ManualSurfaceDialog(
            state = state,
            onAction = viewModel::onAction
        )
    }
    if (state.isProjectDetailsOpen) {
        ProjectDetailsDialog(
            state = state,
            onAction = viewModel::onAction
        )
    }
    if (state.isExportDialogOpen) {
        PdfExportDialog(
            state = state,
            onAction = viewModel::onAction
        )
    }
}

@Composable
private fun QuoteLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        SiteMark(modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Opening your site ledger",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Loading projects, material pricing, and measured surfaces.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(24.dp),
            color = MaterialTheme.colorScheme.secondary,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun QuoteWorkspace(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit,
    onMeasureSurface: () -> Unit,
    onRemeasureSurface: (String) -> Unit
) {
    val workspace = state.workspace
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1180.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 36.dp)
        ) {
            item {
                QuoteHeader(
                    projectName = workspace?.project?.name ?: "Untitled contractor quote",
                    onOpenCatalog = { onAction(QuoteAction.OpenCatalog) },
                    onMeasureSurface = onMeasureSurface,
                    onManualEntry = { onAction(QuoteAction.OpenManualEntry()) }
                )
            }

            item {
                QuoteTitleRow(
                    workspace = workspace,
                    onMeasureSurface = onMeasureSurface
                )
            }

            if (state.message != null) {
                item {
                    MessageBanner(
                        message = state.message,
                        onDismiss = { onAction(QuoteAction.DismissMessage) }
                    )
                }
            }

            if (workspace != null) {
                item {
                    QuoteOverview(
                        workspace = workspace,
                        selectedSurfaceId = state.selectedSurfaceId,
                        onSelectSurface = { onAction(QuoteAction.SelectSurface(it)) },
                        onMeasureSurface = onMeasureSurface
                    )
                }

                item {
                    SectionHeader(
                        title = "Measured surfaces",
                        detail = if (workspace.surfaces.isEmpty()) {
                            "Nothing measured yet"
                        } else {
                            "${workspace.surfaces.size} surface${if (workspace.surfaces.size == 1) "" else "s"} in this quote"
                        },
                        modifier = Modifier.padding(top = 30.dp, bottom = 12.dp)
                    )
                }

                if (workspace.surfaces.isEmpty()) {
                    item {
                        EmptyLedgerPanel(
                            title = "No surfaces measured yet",
                            body = "Walk the room once and save the first wall or floor, or type its dimensions manually. Material, waste, and labor stay editable here.",
                            action = {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onMeasureSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Measure first surface")
                                    }
                                    OutlinedButton(
                                        onClick = { onAction(QuoteAction.OpenManualEntry()) },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Enter dimensions")
                                    }
                                }
                            }
                        )
                    }
                } else {
                    items(
                        items = workspace.surfaces,
                        key = SurfaceQuoteLine::surfaceId
                    ) { surface ->
                        SurfaceLedgerRow(
                            surface = surface,
                            isSelected = surface.surfaceId == state.selectedSurfaceId,
                            onClick = {
                                onAction(QuoteAction.SelectSurface(surface.surfaceId))
                            }
                        )
                    }
                }

                val selectedSurface = state.selectedSurface
                if (selectedSurface != null) {
                    item {
                        SurfacePricingPanel(
                            surface = selectedSurface,
                            materials = workspace.materials,
                            onAction = onAction,
                            onRemeasure = { onRemeasureSurface(selectedSurface.surfaceId) },
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }

                item {
                    QuoteTotalsCard(
                        quote = workspace.quote,
                        modifier = Modifier.padding(top = 30.dp)
                    )
                }

                item {
                    ProjectAdjustmentsPanel(
                        taxRate = workspace.project.taxRate,
                        taxPolicy = workspace.quote.taxPolicy,
                        onAction = onAction,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                item {
                    QuoteDeliveryPanel(
                        workspace = workspace,
                        isExporting = state.isExportingPdf,
                        onEditProject = { onAction(QuoteAction.OpenProjectDetails) },
                        onExportPdf = { onAction(QuoteAction.OpenExportDialog) },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                item {
                    CatalogSummaryPanel(
                        workspace = workspace,
                        onOpenCatalog = { onAction(QuoteAction.OpenCatalog) },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                if (workspace.loadErrors.isNotEmpty()) {
                    item {
                        LoadErrorPanel(
                            errors = workspace.loadErrors,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            } else if (!state.isLoading) {
                item {
                    EmptyLedgerPanel(
                        title = "The local project could not be loaded",
                        body = state.message?.text ?: "Retry opening the on-device quote database.",
                        modifier = Modifier.padding(top = 24.dp),
                        action = {
                            Button(onClick = { onAction(QuoteAction.Retry) }) {
                                Text("Retry")
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Calculated on this device from measured anchors. No cloud account required.",
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuoteHeader(
    projectName: String,
    onOpenCatalog: () -> Unit,
    onMeasureSurface: () -> Unit,
    onManualEntry: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteMark(modifier = Modifier.size(34.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp)
            ) {
                Text(
                    text = "SITE LEDGER",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = projectName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onOpenCatalog) {
                Text("Catalog")
            }
            TextButton(onClick = onManualEntry) {
                Text("Manual")
            }
            Button(
                onClick = onMeasureSurface,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Measure")
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun QuoteTitleRow(
    workspace: ProjectWorkspace?,
    onMeasureSurface: () -> Unit
) {
    val surfaceCount = workspace?.surfaces?.size ?: 0
    val finding = when {
        workspace == null -> "Loading the local project."
        surfaceCount == 0 -> "Start with one measured surface, then tune material and labor."
        surfaceCount == 1 -> "1 surface is driving the current quote."
        else -> "$surfaceCount surfaces are pooled into one client total."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Quote summary",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = finding,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (workspace != null && surfaceCount == 0) {
            Button(
                onClick = onMeasureSurface,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Add surface")
            }
        }
    }
}

@Composable
private fun MessageBanner(
    message: QuoteMessage,
    onDismiss: () -> Unit
) {
    val accent = if (message.isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.48f))
        ) {
            Row(
                modifier = Modifier.padding(start = 13.dp, top = 7.dp, bottom = 7.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape)
                )
                Text(
                    text = message.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteOverview(
    workspace: ProjectWorkspace,
    selectedSurfaceId: String?,
    onSelectSurface: (String) -> Unit,
    onMeasureSurface: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 840.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                QuoteTotalCard(
                    quote = workspace.quote,
                    surfaceCount = workspace.surfaces.size,
                    modifier = Modifier.weight(1.08f)
                )
                CoverageLedger(
                    workspace = workspace,
                    selectedSurfaceId = selectedSurfaceId,
                    onSelectSurface = onSelectSurface,
                    onMeasureSurface = onMeasureSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuoteTotalCard(
                    quote = workspace.quote,
                    surfaceCount = workspace.surfaces.size
                )
                CoverageLedger(
                    workspace = workspace,
                    selectedSurfaceId = selectedSurfaceId,
                    onSelectSurface = onSelectSurface,
                    onMeasureSurface = onMeasureSurface
                )
            }
        }
    }
}

@Composable
private fun QuoteTotalCard(
    quote: ProjectQuoteSummary,
    surfaceCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "CURRENT TOTAL",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = formatMoney(quote.grandTotal),
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = (-1.2).sp
            )
            Text(
                text = "${formatArea(quote.totalNetAreaSqMeters)} net · $surfaceCount measured surface" +
                    if (surfaceCount == 1) "" else "s",
                modifier = Modifier.padding(top = 5.dp),
                style = numericTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LedgerValueRow("Materials", formatMoney(quote.materialSubtotal))
            LedgerValueRow("Consumables", formatMoney(quote.consumablesSubtotal))
            LedgerValueRow("Labor", formatMoney(quote.laborSubtotal))
            LedgerValueRow(
                label = "Tax · ${formatPercent(quote.taxRate)}",
                value = formatMoney(quote.taxAmount)
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LedgerValueRow(
                label = "Quoted total",
                value = formatMoney(quote.grandTotal),
                emphasized = true
            )
        }
    }
}

@Composable
private fun CoverageLedger(
    workspace: ProjectWorkspace,
    selectedSurfaceId: String?,
    onSelectSurface: (String) -> Unit,
    onMeasureSurface: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalArea = workspace.quote.totalNetAreaSqMeters
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Measured coverage",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Net area after opening deductions",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatArea(totalArea),
                    style = numericTextStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(13.dp))
            if (workspace.surfaces.isEmpty()) {
                EmptyLedgerPanel(
                    title = "No measured area",
                    body = "The coverage ledger fills as surfaces are saved from AR.",
                    action = {
                        TextButton(onClick = onMeasureSurface) {
                            Text("Measure a surface")
                        }
                    }
                )
            } else {
                workspace.surfaces.take(6).forEach { surface ->
                    CoverageLedgerRow(
                        surface = surface,
                        fraction = if (totalArea > 0.0) {
                            (surface.analysis?.netAreaSqMeters ?: 0.0) / totalArea
                        } else {
                            0.0
                        },
                        selected = surface.surfaceId == selectedSurfaceId,
                        onClick = { onSelectSurface(surface.surfaceId) }
                    )
                }
                if (workspace.surfaces.size > 6) {
                    Text(
                        text = "+${workspace.surfaces.size - 6} more surfaces in the ledger below",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Gross ${formatArea(workspace.quote.totalGrossAreaSqMeters)}",
                        modifier = Modifier.weight(1f),
                        style = numericTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Openings −${formatArea(workspace.quote.totalCutoutAreaSqMeters)}",
                        style = numericTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverageLedgerRow(
    surface: SurfaceQuoteLine,
    fraction: Double,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = surface.surfaceName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatAreaCompact(surface.analysis?.netAreaSqMeters ?: 0.0),
                style = numericTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun SurfaceLedgerRow(
    surface: SurfaceQuoteLine,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val estimate = surface.estimate
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = surface.surfaceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Text(
                            text = "EDITING",
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Text(
                    text = "${surface.effectiveMaterial.tradeType.shortName()} · ${surface.effectiveMaterial.name}",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(modifier = Modifier.padding(top = 9.dp)) {
                    Text(
                        text = formatArea(surface.analysis?.netAreaSqMeters ?: 0.0),
                        style = numericTextStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (surface.cutouts.isNotEmpty()) {
                        Text(
                            text = " · ${surface.cutouts.size} opening${if (surface.cutouts.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (surface.loadError != null) {
                    Text(
                        text = surface.loadError,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = estimate?.grandTotal?.let(::formatMoney) ?: "—",
                    style = numericTextStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isSelected) "SELECTED" else "OPEN",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }
        }
    }
}

@Composable
private fun QuoteTotalsCard(
    quote: ProjectQuoteSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Quote totals",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Every line is recomputed from saved anchors and current catalog pricing.",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 13.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LedgerValueRow("Measured gross area", formatArea(quote.totalGrossAreaSqMeters))
            LedgerValueRow("Opening deductions", "−${formatArea(quote.totalCutoutAreaSqMeters)}")
            LedgerValueRow("Net priced area", formatArea(quote.totalNetAreaSqMeters))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 9.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LedgerValueRow("Material packages", formatMoney(quote.materialSubtotal))
            LedgerValueRow("Trade consumables", formatMoney(quote.consumablesSubtotal))
            LedgerValueRow("Labor", formatMoney(quote.laborSubtotal))
            LedgerValueRow("Subtotal before tax", formatMoney(quote.subtotalBeforeTax))
            LedgerValueRow(
                "Tax · ${formatPercent(quote.taxRate)}",
                formatMoney(quote.taxAmount)
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            LedgerValueRow(
                label = "Client total",
                value = formatMoney(quote.grandTotal),
                emphasized = true
            )
        }
    }
}

@Composable
private fun CatalogSummaryPanel(
    workspace: ProjectWorkspace,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = workspace.materials.groupingBy { it.tradeType }.eachCount()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Material catalog",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${workspace.materials.size} offline materials · " +
                        TradeType.entries.joinToString(" / ") { "${counts[it] ?: 0} ${it.shortName()}" },
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenCatalog) {
                Text("Edit catalog")
            }
        }
    }
}

@Composable
private fun LoadErrorPanel(
    errors: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Saved surface needs attention",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            errors.forEach { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SiteMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val data = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = modifier.semantics { contentDescription = "Site Ledger mark" }
    ) {
        val stroke = 2.dp.toPx()
        val inset = stroke
        drawLine(
            color = ink,
            start = Offset(inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Square
        )
        drawLine(
            color = ink,
            start = Offset(inset, size.height - inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Square
        )
        drawLine(
            color = ink,
            start = Offset(inset, inset),
            end = Offset(size.width * 0.55f, inset),
            strokeWidth = stroke,
            cap = StrokeCap.Square
        )
        drawCircle(
            color = data,
            radius = size.minDimension * 0.10f,
            center = Offset(size.width * 0.77f, size.height * 0.26f)
        )
    }
}

fun TradeType.shortName(): String = when (this) {
    TradeType.TILING -> "Tile"
    TradeType.FLOORING -> "Flooring"
    TradeType.PAINTING -> "Paint"
    TradeType.DRYWALL -> "Drywall"
}
