package com.sudhir.safecopy.ui.watermark

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sudhir.safecopy.R
import com.sudhir.safecopy.appContainer
import com.sudhir.safecopy.domain.model.WatermarkColour
import com.sudhir.safecopy.domain.model.WatermarkSize
import com.sudhir.safecopy.domain.model.WatermarkStyle
import com.sudhir.safecopy.domain.model.WatermarkTemplate
import com.sudhir.safecopy.domain.watermark.TemplateEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Watermark screen (spec 5.2, 8): template, recipient, purpose, date, advanced style, live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkRoute(onBack: () -> Unit, onNext: () -> Unit, onExpired: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: WatermarkViewModel = viewModel(factory = viewModelFactory { initializer { WatermarkViewModel(container) } })

    if (vm.sessionExpired) {
        val message = stringResource(R.string.editor_session_expired)
        LaunchedEffect(Unit) {
            container.tempFiles.clearOriginals()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onExpired()
        }
        return
    }

    val config by vm.config.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watermark_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            vm.next()
                            onNext()
                        },
                        enabled = config.isComplete,
                    ) { Text(stringResource(R.string.action_next)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = preview
                if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Image(
                        image,
                        contentDescription = stringResource(R.string.watermark_preview_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
            if (!config.isComplete) {
                Text(
                    stringResource(R.string.watermark_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TemplatePicker(config.template, vm::setTemplate)

            OutlinedTextField(
                value = config.recipient,
                onValueChange = vm::setRecipient,
                label = { Text(stringResource(R.string.watermark_recipient)) },
                supportingText = { Text(stringResource(R.string.watermark_recipient_help)) },
                isError = config.recipient.isNotEmpty() && config.recipient.isBlank(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            if (suggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        SuggestionChip(onClick = { vm.setRecipient(s) }, label = { Text(s) })
                    }
                }
            }

            OutlinedTextField(
                value = config.purpose,
                onValueChange = vm::setPurpose,
                label = { Text(stringResource(R.string.watermark_purpose)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            val dateText = TemplateEngine.formatDate(config.date, LocalConfiguration.current.locales[0])
            Box {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.watermark_date)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // A read-only text field swallows taps, so an overlay opens the picker.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.watermark_change_date)) { pickDate = true },
                )
            }

            HorizontalDivider()
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(stringResource(if (showAdvanced) R.string.watermark_hide_advanced else R.string.watermark_show_advanced))
            }
            if (showAdvanced) {
                AdvancedControls(
                    style = config.style,
                    uppercase = config.uppercase,
                    onOpacity = vm::setOpacity,
                    onAngle = vm::setAngle,
                    onSize = vm::setSize,
                    onColour = vm::setColour,
                    onUppercase = vm::setUppercase,
                )
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = config.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatePicker(selected: WatermarkTemplate, onSelect: (WatermarkTemplate) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.label()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.watermark_template)) },
            supportingText = { Text(selected.pattern, maxLines = 2) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (t in WatermarkTemplate.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(t.label())) },
                    onClick = {
                        onSelect(t)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedControls(
    style: WatermarkStyle,
    uppercase: Boolean,
    onOpacity: (Float) -> Unit,
    onAngle: (Float) -> Unit,
    onSize: (WatermarkSize) -> Unit,
    onColour: (WatermarkColour) -> Unit,
    onUppercase: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.watermark_opacity, (style.opacity * 100).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = style.opacity,
            onValueChange = { onOpacity((it * 20).roundToInt() / 20f) }, // 5 % steps
            valueRange = WatermarkStyle.MIN_OPACITY..WatermarkStyle.MAX_OPACITY,
        )

        Text(stringResource(R.string.watermark_angle, style.angleDegrees.roundToInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = style.angleDegrees,
            onValueChange = { onAngle((it / 5).roundToInt() * 5f) }, // 5° steps
            valueRange = WatermarkStyle.MIN_ANGLE..WatermarkStyle.MAX_ANGLE,
        )

        Text(stringResource(R.string.watermark_size), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = WatermarkSize.entries,
            selected = style.size,
            label = {
                when (it) {
                    WatermarkSize.S -> R.string.watermark_size_small
                    WatermarkSize.M -> R.string.watermark_size_medium
                    WatermarkSize.L -> R.string.watermark_size_large
                }
            },
            onSelect = onSize,
        )

        Text(stringResource(R.string.watermark_colour), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = WatermarkColour.entries,
            selected = style.colour,
            label = {
                when (it) {
                    WatermarkColour.DARK -> R.string.watermark_colour_dark
                    WatermarkColour.LIGHT -> R.string.watermark_colour_light
                    WatermarkColour.RED -> R.string.watermark_colour_red
                }
            },
            onSelect = onColour,
        )

        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.watermark_uppercase), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = uppercase, onCheckedChange = onUppercase)
        }
    }
}

@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label(option))) }
        }
    }
}

@StringRes
internal fun WatermarkTemplate.label(): Int = when (this) {
    WatermarkTemplate.KYC -> R.string.template_kyc
    WatermarkTemplate.SPECIFIC_PURPOSE -> R.string.template_specific_purpose
    WatermarkTemplate.RENTAL -> R.string.template_rental
    WatermarkTemplate.HOTEL -> R.string.template_hotel
    WatermarkTemplate.JOB -> R.string.template_job
    WatermarkTemplate.NOT_VALID_ELSEWHERE -> R.string.template_not_valid_elsewhere
}
