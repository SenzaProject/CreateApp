package com.senzaproject.createapp.ui.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.ui.theme.CreateAppTheme

/**
 * Baris proyek: nama, package (bila ada), waktu ubah terakhir, ukuran, dan penanda favorit.
 * [onClick] membuat seluruh kartu dapat diketuk; [trailing] adalah slot di ujung kanan (mis. menu).
 */
@Composable
fun ProjectListItem(
    project: Project,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val meta = remember(project.lastModifiedMillis, project.sizeBytes) {
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            project.lastModifiedMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        "$relativeTime • ${Formatter.formatShortFileSize(context, project.sizeBytes)}"
    }

    val shape = RoundedCornerShape(12.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (project.packageName.isNotBlank()) {
                    Text(
                        text = project.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (project.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.project_favorite),
                    tint = Color(0xFFFACC15),
                )
            }
            if (trailing != null) trailing()
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            content = content,
        )
    }
}

// Hanya untuk pratinjau di Android Studio; data ini tidak pernah tampil di aplikasi.
@Preview(showBackground = true)
@Composable
private fun ProjectListItemPreview() {
    CreateAppTheme {
        ProjectListItem(
            project = Project(
                id = "preview",
                name = "ContohProyek",
                packageName = "com.contoh.proyek",
                lastModifiedMillis = 0L,
                sizeBytes = 1_048_576L,
                isFavorite = true,
            ),
        )
    }
}
