package com.senzaproject.createapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Membatasi lebar konten dan memusatkannya di layar lebar (tablet, landscape),
 * sementara di ponsel konten tetap memenuhi lebar layar.
 */
@Composable
fun ContentContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize(),
            content = content,
        )
    }
}
