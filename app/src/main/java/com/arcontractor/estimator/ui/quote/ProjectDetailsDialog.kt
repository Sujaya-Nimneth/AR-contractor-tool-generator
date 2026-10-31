package com.arcontractor.estimator.ui.quote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun ProjectDetailsDialog(
    state: QuoteUiState,
    onAction: (QuoteAction) -> Unit
) {
    val draft = state.projectDetailsDraft ?: return
    AlertDialog(
        onDismissRequest = {
            if (!state.isSavingProjectDetails) {
                onAction(QuoteAction.CloseProjectDetails)
            }
        },
        title = {
            Column {
                Text(
                    text = "Project & contractor",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "These details appear on the client quote and blueprint PDF.",
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
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        onAction(QuoteAction.UpdateProjectDetails(draft.copy(name = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project name") },
                    singleLine = true,
                    isError = draft.name.isBlank(),
                    supportingText = if (draft.name.isBlank()) {
                        { Text("A project name is required.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = draft.clientName,
                    onValueChange = {
                        onAction(QuoteAction.UpdateProjectDetails(draft.copy(clientName = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Client name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = draft.contractorName,
                    onValueChange = {
                        onAction(QuoteAction.UpdateProjectDetails(draft.copy(contractorName = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contractor / company") },
                    singleLine = true,
                    supportingText = { Text("Used as the PDF branding header.") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = draft.contractorContact,
                    onValueChange = {
                        onAction(QuoteAction.UpdateProjectDetails(draft.copy(contractorContact = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone, email, or website") },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = draft.contractorLicense,
                    onValueChange = {
                        onAction(QuoteAction.UpdateProjectDetails(draft.copy(contractorLicense = it)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("License / registration") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(QuoteAction.SaveProjectDetails) },
                enabled = draft.name.isNotBlank() && !state.isSavingProjectDetails
            ) {
                if (state.isSavingProjectDetails) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(if (state.isSavingProjectDetails) "Saving" else "Save details")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(QuoteAction.CloseProjectDetails) },
                enabled = !state.isSavingProjectDetails
            ) {
                Text("Cancel")
            }
        }
    )
}
