package com.sudhir.safecopy.ui.export

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.sudhir.safecopy.core.files.ShareIntents
import com.sudhir.safecopy.render.ExportNaming

/** Preview & Export (spec 5.2): shows the real rendered output, then Share or Save to device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewRoute(onBack: () -> Unit, onDone: () -> Unit, onExpired: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: PreviewViewModel = viewModel(factory = viewModelFactory { initializer { PreviewViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    if (vm.sessionExpired) {
        val message = stringResource(R.string.editor_session_expired)
        LaunchedEffect(Unit) {
            container.tempFiles.clearOriginals()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onExpired()
        }
        return
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        vm.onSaveDestination(uri)
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            vm.doneHandled()
            onDone()
        }
    }
    val saveFailedText = stringResource(R.string.export_save_failed)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(saveFailedText)
            vm.saveFailedShown()
        }
    }

    val shareTitle = stringResource(R.string.export_share_title)
    val noApps = stringResource(R.string.export_no_share_target)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val output = state.output
                when {
                    state.rendering -> CircularProgressIndicator()
                    state.failed || output == null -> Text(
                        stringResource(R.string.export_render_failed),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> {
                        val image = remember(output) { output.asImageBitmap() }
                        Image(
                            image,
                            contentDescription = stringResource(R.string.export_preview_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            state.export?.let { export ->
                val output = state.output
                if (output != null) {
                    Text(
                        stringResource(
                            R.string.export_file_info,
                            output.width,
                            output.height,
                            ExportNaming.formatSize(export.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val ready = state.export != null && !state.saving
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { state.export?.let { saveLauncher.launch(it.file.name) } },
                    enabled = ready,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_save))
                }
                Button(
                    onClick = {
                        val export = state.export ?: return@Button
                        try {
                            context.startActivity(ShareIntents.chooser(export.uri, shareTitle))
                            vm.onShared()
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, noApps, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_share))
                }
            }
        }
    }
}
