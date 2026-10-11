package com.sudhir.safecopy.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sudhir.safecopy.R
import com.sudhir.safecopy.domain.detect.DocType
import com.sudhir.safecopy.domain.model.FieldType

/** Detection summary (spec 5.2): document type, one switch per detected field type, hints. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldsSheet(
    docType: DocType,
    fields: List<FieldSummary>,
    qrHint: Boolean,
    aadhaarAll: Boolean,
    panAll: Boolean,
    onToggle: (FieldType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    when (docType) {
                        DocType.AADHAAR -> R.string.fields_doc_aadhaar
                        DocType.PAN -> R.string.fields_doc_pan
                        DocType.UNKNOWN -> R.string.fields_doc_unknown
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            if (fields.isEmpty()) {
                Hint(stringResource(R.string.fields_nothing_found))
            } else {
                Text(
                    stringResource(R.string.fields_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (f in fields) FieldRow(f, f.type.label(aadhaarAll, panAll), onToggle)
            }
            if (qrHint) Hint(stringResource(R.string.fields_qr_hint))

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fields_done))
            }
        }
    }
}

@Composable
private fun FieldRow(f: FieldSummary, label: Int, onToggle: (FieldType, Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = f.enabled, role = Role.Switch, onValueChange = { onToggle(f.type, it) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                if (f.count > 1) add(pluralStringResource(R.plurals.fields_found_count, f.count, f.count))
                if (f.needsCheck) add(stringResource(R.string.fields_needs_check))
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (f.needsCheck) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The whole row is the toggle target; the switch only shows the state.
        Switch(checked = f.enabled, onCheckedChange = null)
    }
}

@Composable
private fun Hint(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
}

private fun FieldType.label(aadhaarAll: Boolean, panAll: Boolean): Int = when (this) {
    FieldType.AADHAAR_NUMBER -> if (aadhaarAll) R.string.field_aadhaar_number_all else R.string.field_aadhaar_number
    FieldType.AADHAAR_VID -> R.string.field_vid
    FieldType.QR -> R.string.field_qr
    FieldType.PAN -> if (panAll) R.string.field_pan_all else R.string.field_pan
    FieldType.DOB -> R.string.field_dob
    FieldType.ADDRESS -> R.string.field_address
    FieldType.PHOTO -> R.string.field_photo
    FieldType.MANUAL -> R.string.field_manual
}
