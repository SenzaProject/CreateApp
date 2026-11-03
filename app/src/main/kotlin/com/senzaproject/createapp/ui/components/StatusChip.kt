package com.senzaproject.createapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.module.ModuleStatus

@Composable
fun StatusChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun ModuleStatusChip(status: ModuleStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        ModuleStatus.READY -> Triple(
            stringResource(R.string.status_ready),
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
        )
        ModuleStatus.IN_PROGRESS -> Triple(
            stringResource(R.string.status_in_progress),
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
        ModuleStatus.PLANNED -> Triple(
            stringResource(R.string.status_planned),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant,
        )
    }
    StatusChip(
        text = label,
        containerColor = container,
        contentColor = content,
        modifier = modifier,
    )
}
