package com.senzaproject.createapp.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senzaproject.createapp.R
import com.senzaproject.createapp.core.editor.CursorPosition
import com.senzaproject.createapp.core.editor.cursorPositionOf
import com.senzaproject.createapp.core.editor.lineCountOf
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.theme.CodeTextStyle

/**
 * Editor awal (Tahap 2): satu buffer teks sementara dengan nomor baris, scroll dua arah, dan
 * status posisi kursor. Belum ada penyimpanan file, syntax highlighting, undo/redo, atau multi-tab.
 */
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = viewModel.value
    val text = value.text
    val position = remember(text, value.selection.start) { cursorPositionOf(text, value.selection.start) }
    val lineCount = remember(text) { lineCountOf(text) }

    val scheme = MaterialTheme.colorScheme
    val editorStyle = CodeTextStyle.copy(
        color = scheme.onBackground,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        AppTopBar(title = stringResource(R.string.title_editor), onBack = onBack)
        NoticeBar(text = stringResource(R.string.editor_notice))
        TabStrip(title = stringResource(R.string.editor_tab_scratch))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(scheme.surface),
        ) {
            val viewportHeight = maxHeight
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                LineNumbers(
                    lineCount = lineCount,
                    style = editorStyle,
                    modifier = Modifier.heightIn(min = viewportHeight),
                )
                // Area ini bisa diketuk di mana saja untuk memfokuskan kolom teks.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = viewportHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { focusRequester.requestFocus() },
                        ),
                ) {
                    // horizontalScroll memberi lebar tak terbatas sehingga baris tidak dibungkus
                    // dan nomor baris tetap sejajar dengan baris teks.
                    BasicTextField(
                        value = value,
                        onValueChange = viewModel::onValueChange,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        textStyle = editorStyle,
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.editor_hint),
                                        style = editorStyle,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
        }

        StatusBar(position = position, lineCount = lineCount, charCount = text.length)
    }
}

@Composable
private fun NoticeBar(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun TabStrip(title: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .padding(start = 8.dp, top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = scheme.surface,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun LineNumbers(
    lineCount: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
    Text(
        text = numbers,
        style = style.copy(color = scheme.onSurfaceVariant, textAlign = TextAlign.End),
        modifier = modifier
            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatusBar(
    position: CursorPosition,
    lineCount: Int,
    charCount: Int,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.editor_status_position, position.line, position.column),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.editor_status_lines, lineCount) + " • " +
                stringResource(R.string.editor_status_chars, charCount),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}
