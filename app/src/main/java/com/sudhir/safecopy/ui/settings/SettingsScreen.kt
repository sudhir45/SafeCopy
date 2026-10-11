package com.sudhir.safecopy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sudhir.safecopy.BuildConfig
import com.sudhir.safecopy.R
import com.sudhir.safecopy.appContainer
import com.sudhir.safecopy.domain.detect.AadhaarMaskMode
import com.sudhir.safecopy.domain.detect.PanMaskMode
import com.sudhir.safecopy.ui.watermark.AdvancedControls
import com.sudhir.safecopy.ui.watermark.Segmented
import com.sudhir.safecopy.ui.watermark.TemplatePicker

/** Settings (spec 5.2, 11): mask defaults, watermark defaults, clear all data, about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit, onHelp: () -> Unit, onCleared: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(R.string.settings_masks)
            Label(R.string.settings_aadhaar_mode)
            Segmented(
                options = AadhaarMaskMode.entries,
                selected = s.aadhaarMaskMode,
                label = { if (it == AadhaarMaskMode.FIRST_8) R.string.settings_aadhaar_first8 else R.string.settings_aadhaar_all12 },
                onSelect = { m -> vm.update { it.copy(aadhaarMaskMode = m) } },
            )
            Label(R.string.settings_pan_mode)
            Segmented(
                options = PanMaskMode.entries,
                selected = s.panMaskMode,
                label = { if (it == PanMaskMode.FIRST_6) R.string.settings_pan_first6 else R.string.settings_pan_all },
                onSelect = { m -> vm.update { it.copy(panMaskMode = m) } },
            )
            SwitchRow(R.string.settings_mask_qr, s.maskQrByDefault) { on -> vm.update { it.copy(maskQrByDefault = on) } }
            Text(
                stringResource(R.string.settings_masks_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Section(R.string.settings_watermark)
            TemplatePicker(s.defaultTemplate) { t -> vm.update { it.copy(defaultTemplate = t) } }
            AdvancedControls(
                style = s.watermarkStyle,
                uppercase = s.uppercase,
                onOpacity = { v -> vm.update { it.copy(watermarkStyle = it.watermarkStyle.copy(opacity = v)) } },
                onAngle = { v -> vm.update { it.copy(watermarkStyle = it.watermarkStyle.copy(angleDegrees = v)) } },
                onSize = { v -> vm.update { it.copy(watermarkStyle = it.watermarkStyle.copy(size = v)) } },
                onColour = { v -> vm.update { it.copy(watermarkStyle = it.watermarkStyle.copy(colour = v)) } },
                onUppercase = { v -> vm.update { it.copy(uppercase = v) } },
            )

            HorizontalDivider()
            Section(R.string.settings_privacy)
            Text(stringResource(R.string.settings_clear_explain), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_clear)) }

            HorizontalDivider()
            Section(R.string.settings_about)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_no_internet), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onHelp, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_how_to_verify))
            }
            Text(
                stringResource(R.string.settings_not_official),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAllData(onCleared)
                }) { Text(stringResource(R.string.settings_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
