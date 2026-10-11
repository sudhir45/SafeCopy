package com.sudhir.safecopy.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sudhir.safecopy.R
import com.sudhir.safecopy.ui.theme.SafeCopyTheme
import kotlinx.coroutines.launch

/**
 * Home (spec 5.2). In M0 every action is a placeholder; M1 wires up the picker and camera.
 */
@Composable
fun HomeScreen(
    onChoosePhoto: (() -> Unit)? = null,
    onTakePhoto: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    fun orComingSoon(action: (() -> Unit)?): () -> Unit =
        action ?: { scope.launch { snackbar.showSnackbar(comingSoon) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                onClick = orComingSoon(onChoosePhoto),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.home_choose_photo), style = MaterialTheme.typography.titleMedium) }

            FilledTonalButton(
                onClick = orComingSoon(onTakePhoto),
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
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SafeCopyTheme(dynamicColor = false) { HomeScreen() }
}
