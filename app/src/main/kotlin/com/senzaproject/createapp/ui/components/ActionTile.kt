package com.senzaproject.createapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ActionTileData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val color: Color,
    val onClick: () -> Unit,
)

/** Jumlah kolom grid pintasan: 2 di ponsel, 3 di layar lebar. */
fun tileColumnsFor(width: Dp): Int = if (width >= 560.dp) 3 else 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTile(
    data: ActionTileData,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = data.onClick,
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = data.color,
            contentColor = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = data.icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** Grid pintasan responsif tanpa LazyGrid (aman di dalam layar yang bisa di-scroll). */
@Composable
fun ActionTileGrid(
    tiles: List<ActionTileData>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = tileColumnsFor(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowTiles.forEach { tile ->
                        ActionTile(data = tile, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
