package com.senzaproject.createapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.BuildConfig
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.diagnostics.AppDiagnostics
import com.senzaproject.createapp.domain.module.ModuleInfo
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.KeyValueRow
import com.senzaproject.createapp.ui.components.ModuleStatusChip
import com.senzaproject.createapp.ui.components.SectionCard

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.nav_settings))
        ContentContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard(title = stringResource(R.string.settings_about_title)) {
                    KeyValueRow(
                        label = stringResource(R.string.settings_developer),
                        value = DEVELOPER,
                    )
                    KeyValueRow(
                        label = stringResource(R.string.settings_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    KeyValueRow(
                        label = stringResource(R.string.settings_repository),
                        value = REPOSITORY,
                    )
                }

                if (state.hasError) {
                    SectionCard(title = stringResource(R.string.home_error)) {
                        Button(onClick = viewModel::refresh) {
                            Text(text = stringResource(R.string.action_retry))
                        }
                    }
                }
                state.diagnostics?.let { diagnostics ->
                    AppInfoCard(diagnostics)
                    ModulesCard(diagnostics.modules)
                }

                Text(
                    text = stringResource(R.string.settings_more_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

private const val DEVELOPER = "SenzaProject"
private const val REPOSITORY = "github.com/SenzaProject/CreateApp"
