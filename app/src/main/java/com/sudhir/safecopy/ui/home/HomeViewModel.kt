package com.sudhir.safecopy.ui.home

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.image.LoadResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val importing: Boolean = false,
    val error: HomeError? = null,
    /** One-shot: the image is loaded into the session, open the editor. */
    val openEditor: Boolean = false,
)

enum class HomeError { TOO_LARGE, NOT_AN_IMAGE, UNREADABLE, NO_CAMERA_APP, CAMERA_FAILED }

class HomeViewModel(
    private val savedState: SavedStateHandle,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            container.incomingShare.filterNotNull().collect { uri ->
                container.incomingShare.value = null
                import(uri)
            }
        }
    }

    /**
     * Pending camera capture URI. Kept in SavedStateHandle because Android may kill the app
     * while the camera is open; the capture then comes back to a new process.
     */
    private var pendingCapture: Uri?
        get() = savedState[KEY_CAPTURE]
        set(value) { savedState[KEY_CAPTURE] = value }

    fun onPicked(uri: Uri?) {
        if (uri != null) import(uri)
    }

    suspend fun prepareCapture(): Uri = container.tempFiles.newCameraCapture().also { pendingCapture = it }

    fun onCaptureResult(success: Boolean) {
        val uri = pendingCapture ?: return
        pendingCapture = null
        if (success) {
            import(uri, deleteAfter = true)
        } else {
            viewModelScope.launch { container.tempFiles.deleteCameraCapture(uri) }
        }
    }

    fun onCameraUnavailable() {
        pendingCapture?.let { uri -> viewModelScope.launch { container.tempFiles.deleteCameraCapture(uri) } }
        pendingCapture = null
        _state.update { it.copy(error = HomeError.NO_CAMERA_APP) }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    fun editorOpened() = _state.update { it.copy(openEditor = false) }

    private fun import(uri: Uri, deleteAfter: Boolean = false) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            val result = try {
                container.imageLoader.load(uri)
            } finally {
                // Camera captures are originals: gone from disk as soon as they're in memory.
                if (deleteAfter) container.tempFiles.deleteCameraCapture(uri)
            }
            when (result) {
                is LoadResult.Success -> {
                    container.editSession.start(result.bitmap)
                    _state.update { it.copy(importing = false, openEditor = true) }
                }
                is LoadResult.Failure -> {
                    val error = when (result.reason) {
                        LoadResult.Reason.TOO_LARGE -> HomeError.TOO_LARGE
                        LoadResult.Reason.NOT_AN_IMAGE -> HomeError.NOT_AN_IMAGE
                        LoadResult.Reason.UNREADABLE ->
                            if (deleteAfter) HomeError.CAMERA_FAILED else HomeError.UNREADABLE
                    }
                    _state.update { it.copy(importing = false, error = error) }
                }
            }
        }
    }

    private companion object {
        const val KEY_CAPTURE = "pendingCapture"
    }
}
