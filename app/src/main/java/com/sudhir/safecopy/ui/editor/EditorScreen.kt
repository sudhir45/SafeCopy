package com.sudhir.safecopy.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sudhir.safecopy.R
import com.sudhir.safecopy.appContainer

/**
 * Editor (spec 5.2, 7.3): manual masks with undo/redo, rotate, discard confirmation.
 * Automatic detection and the Fields sheet arrive in M4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorRoute(onNext: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: EditorViewModel = viewModel(factory = viewModelFactory { initializer { EditorViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    if (vm.sessionExpired) {
        // Spec 5.3: process death discards the session; tell the user and go back.
        val message = stringResource(R.string.editor_session_expired)
        LaunchedEffect(Unit) {
            container.tempFiles.clearOriginals()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onExit()
        }
        return
    }

    fun exit() {
        vm.discard()
        onExit()
    }

    fun onBack() {
        if (state.hasEdits) confirmDiscard = true else exit()
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = ::onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconToggleButton(checked = state.drawMode, onCheckedChange = { vm.toggleDrawMode() }) {
                        Icon(painterResource(R.drawable.ic_add_box), contentDescription = stringResource(R.string.editor_add_mask))
                    }
                    ToolButton(R.drawable.ic_rotate_right, R.string.editor_rotate, enabled = !state.rotating, onClick = vm::rotate)
                    ToolButton(R.drawable.ic_undo, R.string.editor_undo, enabled = state.canUndo, onClick = vm::undo)
                    ToolButton(R.drawable.ic_redo, R.string.editor_redo, enabled = state.canRedo, onClick = vm::redo)
                    ToolButton(
                        R.drawable.ic_delete, R.string.editor_delete_mask,
                        enabled = state.selectedId != null, onClick = vm::deleteSelected,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            vm.next()
                            onNext()
                        },
                        enabled = state.image != null && !state.rotating,
                    ) { Text(stringResource(R.string.action_next)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            state.image?.let { image ->
                EditorCanvas(
                    bitmap = image,
                    masks = state.masks,
                    selectedId = state.selectedId,
                    drawMode = state.drawMode,
                    contentDescription = stringResource(R.string.editor_image_description),
                    onTap = vm::select,
                    onCreate = vm::addMask,
                    onChange = vm::updateMask,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.drawMode) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.editor_draw_hint),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.rotating) CircularProgressIndicator()
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    exit()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
}

@Composable
private fun ToolButton(@DrawableRes icon: Int, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}
