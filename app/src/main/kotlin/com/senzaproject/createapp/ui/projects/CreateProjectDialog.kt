package com.senzaproject.createapp.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.NewProjectRequest
import com.senzaproject.createapp.domain.project.ProjectTemplate
import com.senzaproject.createapp.domain.project.checkPackageName
import com.senzaproject.createapp.domain.project.checkProjectName
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.ui.common.messageRes

private const val DEFAULT_PACKAGE = "com.example.myapp"

/**
 * Dialog proyek baru: nama, template, dan (untuk template Android) package name.
 *
 * @param existingNames nama proyek yang sudah ada dalam huruf kecil, untuk pemeriksaan langsung.
 */
@Composable
fun CreateProjectDialog(
    existingNames: Set<String>,
    busy: Boolean,
    serverError: AppError?,
    onDismiss: () -> Unit,
    onCreate: (NewProjectRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf(DEFAULT_PACKAGE) }
    var template by remember { mutableStateOf(ProjectTemplate.EMPTY) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmedName = name.trim()
    val trimmedPackage = packageName.trim()
    val nameFailure: OperationFailure? = when {
        name.isEmpty() -> null
        checkProjectName(trimmedName) != null -> checkProjectName(trimmedName)
        trimmedName.lowercase() in existingNames -> OperationFailure.NAME_TAKEN
        else -> null
    }
    val packageFailure: OperationFailure? =
        if (template == ProjectTemplate.ANDROID_KOTLIN) checkPackageName(trimmedPackage) else null
    val canSubmit = trimmedName.isNotEmpty() && nameFailure == null && packageFailure == null && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.project_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.project_name_label)) },
                    singleLine = true,
                    isError = nameFailure != null,
                    supportingText = if (nameFailure != null) {
                        { Text(stringResource(nameFailure.messageRes())) }
                    } else {
                        null
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.project_template_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                TemplateOption(
                    selected = template == ProjectTemplate.EMPTY,
                    label = stringResource(R.string.project_template_empty),
                    onSelect = { template = ProjectTemplate.EMPTY },
                )
                TemplateOption(
                    selected = template == ProjectTemplate.ANDROID_KOTLIN,
                    label = stringResource(R.string.project_template_android),
                    onSelect = { template = ProjectTemplate.ANDROID_KOTLIN },
                )
                if (template == ProjectTemplate.ANDROID_KOTLIN) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.project_package_label)) },
                        singleLine = true,
                        isError = packageFailure != null,
                        supportingText = {
                            Text(
                                stringResource(
                                    if (packageFailure != null) packageFailure.messageRes() else R.string.project_package_hint,
                                ),
                            )
                        },
                    )
                }
                if (serverError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(serverError.messageRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        NewProjectRequest(
                            name = trimmedName,
                            packageName = if (template == ProjectTemplate.ANDROID_KOTLIN) trimmedPackage else "",
                            template = template,
                        ),
                    )
                },
                enabled = canSubmit,
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TemplateOption(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
