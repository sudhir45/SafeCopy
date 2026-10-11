package com.sudhir.safecopy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    /** null until DataStore has loaded once. */
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(f: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(f) }
    }

    /**
     * Spec 11 "Clear all data": settings, recent recipients, any image in memory and every
     * temp file. Onboarding then starts again (onboardingDone is cleared with everything else).
     */
    fun clearAllData(onDone: () -> Unit) {
        container.appScope.launch {
            container.editSession.clear()
            container.settings.clear()
            container.tempFiles.clearAll()
            viewModelScope.launch { onDone() }
        }
    }
}
