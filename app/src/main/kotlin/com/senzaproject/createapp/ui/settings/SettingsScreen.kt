package com.senzaproject.createapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senzaproject.createapp.BuildConfig
import com.senzaproject.createapp.R
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.KeyValueRow
import com.senzaproject.createapp.ui.components.SectionCard

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.nav_settings))
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
            Text(
                text = stringResource(R.string.settings_more_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val DEVELOPER = "SenzaProject"
private const val REPOSITORY = "github.com/SenzaProject/CreateApp"
