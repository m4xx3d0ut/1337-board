package org.leetboard.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen(
    title: String,
    subtitle: String,
    primaryAction: String,
    secondaryAction: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    LeetBoardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPrimaryAction) {
                        Text(primaryAction)
                    }
                    OutlinedButton(onClick = onSecondaryAction) {
                        Text(secondaryAction)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    LeetBoardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("1337 Board Settings", style = MaterialTheme.typography.headlineMedium)
                SettingsSection(
                    title = "Prototype Status",
                    body = "This build includes the IME scaffold, custom keyboard surface, key dispatch, theme presets, optional key model, and numpad layout model.",
                )
                SettingsSection(
                    title = "Next Settings Slice",
                    body = "Persist layout, theme, action slot, and privacy preferences with DataStore-backed controls.",
                )
                SettingsSection(
                    title = "Privacy Baseline",
                    body = "The base app requests no network, contacts, or microphone permission. Speech input remains a later opt-in local model feature.",
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

