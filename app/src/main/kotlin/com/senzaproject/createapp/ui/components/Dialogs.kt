package com.senzaproject.createapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.ui.common.messageRes

/**
 * Dialog satu kolom nama dengan validasi langsung.
 *
 * @param validate memeriksa nama yang sudah dipangkas; null berarti valid.
 * @param serverError kegagalan dari operasi sebelumnya (mis. nama sudah dipakai); ditampilkan di bawah kolom.
 * @param extraContent konten tambahan di bawah kolom (mis. pilihan jenis file).
 */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    validate: (String) -> OperationFailure?,
    busy: Boolean,
    serverError: AppError?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(text = initialValue, selection = TextRange(0, initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = field.text.trim()
    val failure = if (field.text.isEmpty()) null else validate(trimmed)
    val canSubmit = trimmed.isNotEmpty() && failure == null && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(label) },
                    singleLine = true,
                    isError = failure != null,
                    supportingText = if (failure != null) {
                        { Text(stringResource(failure.messageRes())) }
                    } else {
                        null
                    },
                )
                if (extraContent != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    extraContent()
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
            TextButton(onClick = { onConfirm(trimmed) }, enabled = canSubmit) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Dialog konfirmasi untuk tindakan yang tidak dapat dibatalkan; tombol konfirmasi berwarna peringatan. */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
