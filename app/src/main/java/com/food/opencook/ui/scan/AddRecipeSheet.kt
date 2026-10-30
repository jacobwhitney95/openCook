/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.ui.scan

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.LocalSnackbarHostState
import com.food.opencook.ui.recipeimport.ImportState
import com.food.opencook.ui.recipeimport.ImportViewModel
import com.food.opencook.ui.theme.Spacing

/**
 * "Add recipe" as a sheet over the recipe list — the "New" sheet of Google Drive rather
 * than a screen of its own. Five equal tiles, so the way you use most (often the web) is
 * as easy to spot as the photo one.
 *
 * Photo and gallery need the server's AI. Instead of a hint card on top they carry their
 * state as a one-line caption: without a server they are faded and a tap explains why;
 * with a server that can't be reached right now they stay live, since the scan queues.
 *
 * Always composed by the host, shown only when [show]: the gallery and file pickers close
 * the sheet, so their result callbacks have to outlive it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onCreateManually: () -> Unit,
    onDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    scanViewModel: ScanViewModel = hiltViewModel(),
    importViewModel: ImportViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    val snackbarHostState = LocalSnackbarHostState.current

    // A scan keeps running in the background; progress shows in the status strip.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanViewModel.startScanFromUri(uri) {}
    }
    // A schema.org .json or a .zip bundle (recipes.json + images/).
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importViewModel.importFromUri(uri, limit = null)
    }
    // Asked for once the sheet opens with a server, so the background "recipe ready"
    // notification can reach someone who leaves the app mid-scan. Non-critical: ignored.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // The file import used to report on the screen that started it; with the sheet gone,
    // a snackbar carries it — "importing…" while it runs, then the outcome.
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val importing = importState is ImportState.Running
    LaunchedEffect(importing, importState as? ImportState.Done, importState as? ImportState.Error) {
        when (val s = importState) {
            // Cancelled (and so dismissed) as soon as the state moves on.
            is ImportState.Running -> snackbarHostState.showSnackbar(
                resources.getString(R.string.import_running),
                duration = SnackbarDuration.Indefinite,
            )
            // Reset only after the message: resetting re-keys this effect, which would
            // cancel — and so dismiss — the snackbar straight away.
            is ImportState.Done -> {
                snackbarHostState.showSnackbar(resources.getString(R.string.import_done, s.imported, s.skipped))
                importViewModel.reset()
            }
            is ImportState.Error -> {
                snackbarHostState.showSnackbar(resources.getString(R.string.import_error, s.message))
                importViewModel.reset()
            }
            ImportState.Idle -> Unit
        }
    }

    if (!show) return

    val state by scanViewModel.uiState.collectAsStateWithLifecycle()
    var explainNoServer by remember { mutableStateOf(false) }
    LaunchedEffect(state.serverConfigured) {
        if (state.serverConfigured && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // One caption for both AI tiles, or none when the server is there and answering.
    val aiCaption = when {
        !state.serverConfigured -> stringResource(R.string.scan_tile_needs_server)
        state.offHomeNetwork || state.serverOffline -> stringResource(R.string.scan_tile_later)
        else -> null
    }
    val aiAction: (() -> Unit) -> Unit = { action ->
        if (state.serverConfigured) {
            onDismiss()
            action()
        } else {
            explainNoServer = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Text(
                stringResource(R.string.recipes_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
            // The ways that work anywhere lead; the two that need the server follow.
            Row(Modifier.fillMaxWidth()) {
                AddTile(Icons.Outlined.Public, stringResource(R.string.scan_discover)) { onDismiss(); onDiscover() }
                AddTile(Icons.Outlined.EditNote, stringResource(R.string.scan_manual)) { onDismiss(); onCreateManually() }
                AddTile(Icons.Outlined.FileOpen, stringResource(R.string.import_from_file), enabled = !importing) {
                    onDismiss()
                    fileLauncher.launch(arrayOf("application/json", "application/zip", "text/plain", "*/*"))
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth()) {
                AddTile(
                    Icons.Outlined.PhotoCamera,
                    stringResource(R.string.scan_take_photo),
                    caption = aiCaption,
                    faded = !state.serverConfigured,
                ) { aiAction(onOpenCamera) }
                AddTile(
                    Icons.Outlined.PhotoLibrary,
                    stringResource(R.string.scan_pick_gallery),
                    caption = aiCaption,
                    faded = !state.serverConfigured,
                ) {
                    aiAction {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (explainNoServer) {
        AlertDialog(
            onDismissRequest = { explainNoServer = false },
            title = { Text(stringResource(R.string.scan_no_server_title)) },
            text = { Text(stringResource(R.string.scan_no_server)) },
            confirmButton = {
                TextButton(onClick = {
                    explainNoServer = false
                    onDismiss()
                    onOpenSettings()
                }) { Text(stringResource(R.string.scan_go_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { explainNoServer = false }) { Text(stringResource(R.string.processing_cancel)) }
            },
        )
    }
}

/**
 * One way to add a recipe: icon in a neutral circle, label below, optional caption.
 * [faded] still takes the tap (it explains itself); [enabled] = false does not.
 */
@Composable
private fun RowScope.AddTile(
    icon: ImageVector,
    label: String,
    caption: String? = null,
    faded: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
            .alpha(if (faded || !enabled) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
