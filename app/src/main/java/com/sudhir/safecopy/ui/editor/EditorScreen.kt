package com.sudhir.safecopy.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
 * Editor shell (spec 5.2, M1): zoomable image, rotate, back with discard confirmation.
 * Mask tools, detection and Next arrive in M2-M4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorRoute(onExit: () -> Unit) {
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
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = vm::rotate, enabled = !state.rotating && state.image != null) {
                        Icon(painterResource(R.drawable.ic_rotate_right), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.editor_rotate))
                    }
                    // Next leads to the watermark screen once masking and export exist (M2/M3).
                    Button(onClick = {}, enabled = false) { Text(stringResource(R.string.action_next)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            state.image?.let { ZoomableImage(it, contentDescription = stringResource(R.string.editor_image_description)) }
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
