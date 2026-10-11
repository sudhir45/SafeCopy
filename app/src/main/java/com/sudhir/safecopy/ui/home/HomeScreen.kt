package com.sudhir.safecopy.ui.home

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sudhir.safecopy.R
import com.sudhir.safecopy.appContainer
import com.sudhir.safecopy.ui.theme.SafeCopyTheme
import kotlinx.coroutines.launch

/** Home with its ViewModel, photo picker and camera launchers wired up. */
@Composable
fun HomeRoute(onOpenEditor: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(createSavedStateHandle(), container) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.onPicked(uri)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onCaptureResult(ok)
    }

    LaunchedEffect(state.openEditor) {
        if (state.openEditor) {
            vm.editorOpened()
            onOpenEditor()
        }
    }

    HomeScreen(
        importing = state.importing,
        error = state.error,
        onErrorShown = vm::errorShown,
        onChoosePhoto = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            scope.launch {
                val uri = vm.prepareCapture()
                try {
                    takePicture.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    vm.onCameraUnavailable()
                }
            }
        },
    )
}

/** Home (spec 5.2). Settings and Help land in M5. */
@Composable
fun HomeScreen(
    importing: Boolean = false,
    error: HomeError? = null,
    onErrorShown: () -> Unit = {},
    onChoosePhoto: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    fun orComingSoon(action: (() -> Unit)?): () -> Unit =
        action ?: { scope.launch { snackbar.showSnackbar(comingSoon) } }

    val errorText = error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(error) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            onErrorShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onChoosePhoto,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) { Text(stringResource(R.string.home_choose_photo), style = MaterialTheme.typography.titleMedium) }

                FilledTonalButton(
                    onClick = onTakePhoto,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) { Text(stringResource(R.string.home_take_photo), style = MaterialTheme.typography.titleMedium) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = orComingSoon(onSettings), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.home_settings))
                    }
                    TextButton(onClick = orComingSoon(onHelp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.home_help))
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.home_tip),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    stringResource(R.string.home_trust_badge),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (importing) {
                val loading = stringResource(R.string.home_loading_image)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .semantics { contentDescription = loading },
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

private fun HomeError.messageRes(): Int = when (this) {
    HomeError.TOO_LARGE -> R.string.error_too_large
    HomeError.NOT_AN_IMAGE -> R.string.error_not_an_image
    HomeError.UNREADABLE -> R.string.error_unreadable
    HomeError.NO_CAMERA_APP -> R.string.error_no_camera_app
    HomeError.CAMERA_FAILED -> R.string.error_camera_failed
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SafeCopyTheme(dynamicColor = false) { HomeScreen() }
}
