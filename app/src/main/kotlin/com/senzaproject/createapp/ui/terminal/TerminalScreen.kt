package com.senzaproject.createapp.ui.terminal

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.core.terminal.TermuxPaths
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.terminal.TerminalBackend
import com.senzaproject.createapp.domain.terminal.TerminalLine
import com.senzaproject.createapp.domain.terminal.TerminalState
import com.senzaproject.createapp.domain.terminal.TermuxStatus
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.SectionCard
import com.senzaproject.createapp.ui.theme.CodeTextStyle

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.terminal.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTermuxGuide by rememberSaveable { mutableStateOf(false) }

    // Pengguna bisa memasang Termux atau mengubah izin di luar aplikasi, jadi periksa ulang setiap kembali.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshTermux() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshTermux()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        AppTopBar(
            title = stringResource(R.string.title_terminal),
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        copyToClipboard(context, terminalTranscript(state.lines, context))
                        Toast.makeText(context, R.string.terminal_copied, Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.lines.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.terminal_copy_all))
                }
                IconButton(onClick = viewModel::clear, enabled = state.lines.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.terminal_clear))
                }
            },
        )

        BackendBar(
            state = state,
            projects = projects,
            onSelectBackend = viewModel::selectBackend,
            onGoHome = viewModel::goHome,
            onSelectProject = viewModel::useProjectDirectory,
        )

        if (state.backend == TerminalBackend.TERMUX) {
            TermuxPanel(
                status = viewModel.termuxStatus,
                showGuide = showTermuxGuide,
                onToggleGuide = { showTermuxGuide = !showTermuxGuide },
                onRequestPermission = { permissionLauncher.launch(TermuxPaths.PERMISSION_RUN_COMMAND) },
                onOpenAppSettings = { openAppSettings(context) },
                onOpenTermux = { openTermux(context) },
                onCopyCommand = {
                    copyToClipboard(context, TermuxPaths.ENABLE_EXTERNAL_APPS_COMMAND)
                    Toast.makeText(context, R.string.terminal_command_copied, Toast.LENGTH_SHORT).show()
                },
            )
        }

        DirectoryLine(state = state)

        TerminalOutput(
            lines = state.lines,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        CommandInput(
            input = viewModel.input,
            isRunning = state.isRunning,
            onInputChange = viewModel::onInputChange,
            onSubmit = viewModel::submit,
            onStop = viewModel::stop,
            onHistoryUp = viewModel::historyUp,
            onHistoryDown = viewModel::historyDown,
        )
    }
}

@Composable
private fun BackendBar(
    state: TerminalState,
    projects: List<Project>,
    onSelectBackend: (TerminalBackend) -> Unit,
    onGoHome: () -> Unit,
    onSelectProject: (String) -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackendButton(
            label = stringResource(R.string.terminal_backend_local),
            selected = state.backend == TerminalBackend.LOCAL_SHELL,
            enabled = !state.isRunning,
            onClick = { onSelectBackend(TerminalBackend.LOCAL_SHELL) },
        )
        BackendButton(
            label = stringResource(R.string.terminal_backend_termux),
            selected = state.backend == TerminalBackend.TERMUX,
            enabled = !state.isRunning,
            onClick = { onSelectBackend(TerminalBackend.TERMUX) },
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = { menuOpen = true }, enabled = !state.isRunning) {
                Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.terminal_directory_menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.terminal_dir_home)) },
                    onClick = {
                        menuOpen = false
                        onGoHome()
                    },
                )
                // Folder privat proyek tidak bisa diakses Termux (UID berbeda), jadi hanya untuk shell lokal.
                if (state.backend == TerminalBackend.LOCAL_SHELL && projects.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.terminal_dir_projects)) },
                        onClick = {},
                        enabled = false,
                    )
                    for (project in projects) {
                        DropdownMenuItem(
                            text = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                menuOpen = false
                                onSelectProject(project.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

@Composable
private fun DirectoryLine(state: TerminalState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.workingDirectory,
            style = CodeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (state.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TerminalOutput(lines: List<TerminalLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val lastId = lines.lastOrNull()?.id

    // Ikuti output terbaru, kecuali pengguna sedang menggulir sendiri.
    LaunchedEffect(lastId) {
        if (lines.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (lines.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.terminal_empty_hint),
                    style = CodeTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = lines, key = { it.id }) { line -> TerminalLineView(line) }
            }
        }
    }
}

@Composable
private fun TerminalLineView(line: TerminalLine) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    when (line) {
        is TerminalLine.Command -> {
            val folder = line.workingDirectory.substringAfterLast('/').ifEmpty { "/" }
            Text(
                text = "$folder $ ${line.text}",
                style = CodeTextStyle.copy(fontWeight = FontWeight.Bold),
                color = colors.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        is TerminalLine.Output -> Text(
            text = line.text.ifEmpty { " " },
            style = CodeTextStyle,
            color = if (line.isError) colors.error else colors.onSurface,
        )

        is TerminalLine.Status -> Text(
            text = line.message.toText(context),
            style = CodeTextStyle.copy(fontStyle = FontStyle.Italic),
            color = if (line.message.isError()) colors.error else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommandInput(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHistoryUp, enabled = !isRunning) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.terminal_history_previous))
        }
        IconButton(onClick = onHistoryDown, enabled = !isRunning) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.terminal_history_next))
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = CodeTextStyle.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            placeholder = { Text(stringResource(R.string.terminal_input_hint), style = CodeTextStyle) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        )
        if (isRunning) {
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.terminal_stop),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            IconButton(onClick = onSubmit, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.terminal_send))
            }
        }
    }
}

@Composable
private fun TermuxPanel(
    status: TermuxStatus,
    showGuide: Boolean,
    onToggleGuide: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onCopyCommand: () -> Unit,
) {
    val ready = status is TermuxStatus.Ready
    if (ready && !showGuide) {
        // versionName berasal dari modul lain, jadi tidak bisa di-smart-cast; salin ke variabel lokal.
        val version = (status as? TermuxStatus.Ready)?.versionName
        val label = if (version != null) {
            stringResource(R.string.terminal_termux_ready_version, version)
        } else {
            stringResource(R.string.terminal_termux_ready)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggleGuide) { Text(stringResource(R.string.terminal_termux_help_show)) }
        }
        return
    }

    SectionCard(
        title = stringResource(R.string.terminal_termux_title),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        when (status) {
            TermuxStatus.NotInstalled -> {
                BodyText(stringResource(R.string.terminal_termux_not_installed))
            }

            TermuxStatus.PermissionMissing -> {
                BodyText(stringResource(R.string.terminal_termux_permission_needed))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.terminal_termux_grant)) }
                    OutlinedButton(onClick = onOpenAppSettings) {
                        Text(stringResource(R.string.terminal_termux_app_settings))
                    }
                }
            }

            is TermuxStatus.Ready -> Unit
        }

        if (status !is TermuxStatus.NotInstalled) {
            BodyText(stringResource(R.string.terminal_termux_step_properties))
            Text(
                text = TermuxPaths.ENABLE_EXTERNAL_APPS_COMMAND,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopyCommand) { Text(stringResource(R.string.terminal_termux_copy_command)) }
                OutlinedButton(onClick = onOpenTermux) { Text(stringResource(R.string.terminal_termux_open)) }
            }
            BodyText(stringResource(R.string.terminal_termux_limits))
        }

        if (ready) {
            TextButton(onClick = onToggleGuide) { Text(stringResource(R.string.terminal_termux_help_hide)) }
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Create App", text))
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Tidak ada aplikasi pengaturan; tidak ada yang bisa dilakukan.
    }
}

private fun openTermux(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(TermuxPaths.PACKAGE) ?: return
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Termux terpasang tetapi tidak bisa dibuka; abaikan.
    }
}
