package com.senzaproject.createapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.diagnostics.AppDiagnostics
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.KeyValueRow
import com.senzaproject.createapp.ui.components.ModuleStatusChip
import com.senzaproject.createapp.ui.components.SectionCard
import com.senzaproject.createapp.ui.theme.CodeTextStyle

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEditor: () -> Unit,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.app_name))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WelcomeCard()
            ShortcutsCard(onOpenEditor = onOpenEditor, onOpenTerminal = onOpenTerminal)

            if (state.isLoading && state.diagnostics == null) {
                LoadingCard()
            }
            if (state.hasError) {
                ErrorCard(onRetry = viewModel::refresh)
            }
            state.diagnostics?.let { diagnostics ->
                AppInfoCard(diagnostics)
                ModulesCard(diagnostics.modules)
            }
            state.lastCrash?.let { crash ->
                CrashCard(report = crash, onClear = viewModel::clearCrashReport)
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_welcome_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.home_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ShortcutsCard(
    onOpenEditor: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.home_shortcuts_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onOpenEditor, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_open_editor))
            }
            OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_open_terminal))
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    SectionCard(title = stringResource(R.string.home_loading)) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit) {
    SectionCard(title = stringResource(R.string.home_error)) {
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun AppInfoCard(diagnostics: AppDiagnostics) {
    val app = diagnostics.app
    val device = diagnostics.device
    val buildType = stringResource(
        if (app.isDebug) R.string.build_type_debug else R.string.build_type_release,
    )
    SectionCard(title = stringResource(R.string.home_foundation_title)) {
        KeyValueRow(
            label = stringResource(R.string.diag_version),
            value = "${app.versionName} (${app.versionCode})",
        )
        KeyValueRow(label = stringResource(R.string.diag_package), value = app.packageName)
        KeyValueRow(label = stringResource(R.string.diag_build_type), value = buildType)
        KeyValueRow(
            label = stringResource(R.string.diag_android),
            value = stringResource(R.string.diag_android_value, device.androidRelease, device.sdkInt),
        )
        KeyValueRow(
            label = stringResource(R.string.diag_device),
            value = "${device.manufacturer} ${device.model}",
        )
        KeyValueRow(
            label = stringResource(R.string.diag_abi),
            value = device.supportedAbis.joinToString(),
        )
    }
}

@Composable
private fun ModulesCard(modules: List<ModuleInfo>) {
    SectionCard(title = stringResource(R.string.home_modules_title)) {
        modules.forEach { module ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${module.id} • ${stringResource(R.string.module_stage, module.plannedStage)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ModuleStatusChip(status = module.status)
            }
        }
    }
}

@Composable
private fun CrashCard(report: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.crash_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = report,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onClear) {
                Text(stringResource(R.string.crash_clear))
            }
        }
    }
}
