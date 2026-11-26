package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.arcontractor.estimator.data.local.ProjectEntity

@Composable
fun ProjectSwitcherDialog(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    AlertDialog(
        onDismissRequest = { onAction(QuoteAction.CloseProjectSwitcher) },
        title = {
            Column {
                Text(
                    text = "Projects",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Each project keeps its own surfaces, client, tax, and currency.",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogErrorBanner(message = state.message)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.projects, key = ProjectEntity::id) { project ->
                        ProjectSwitcherRow(
                            project = project,
                            isActive = project.id == state.projectId,
                            canDelete = state.projects.size > 1,
                            onOpen = { onAction(QuoteAction.SwitchProject(project.id)) },
                            onDelete = { pendingDelete = project }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = state.newProjectNameText,
                    onValueChange = { onAction(QuoteAction.UpdateNewProjectName(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New project name") },
                    singleLine = true
                )
                Button(
                    onClick = { onAction(QuoteAction.CreateProject) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.newProjectNameText.isNotBlank(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Create project")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(QuoteAction.CloseProjectSwitcher) }) {
                Text("Done")
            }
        }
    )

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${deleteTarget.name}?") },
            text = {
                Text(
                    "All measured surfaces and openings in this project will be deleted. " +
                        "Catalog materials are shared and stay untouched."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onAction(QuoteAction.DeleteProject(deleteTarget.id))
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete project")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep project")
                }
            }
        )
    }
}

@Composable
private fun ProjectSwitcherRow(
    project: ProjectEntity,
    isActive: Boolean,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(project.currencyCode)
                        if (project.clientName.isNotBlank()) {
                            append(" · ")
                            append(project.clientName)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (canDelete) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
