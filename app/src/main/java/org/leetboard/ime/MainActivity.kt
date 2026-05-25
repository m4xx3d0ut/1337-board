package org.leetboard.ime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.getSystemService
import org.leetboard.ime.ui.InfoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InfoScreen(
                title = getString(R.string.app_name),
                subtitle = "Desktop-style Android keyboard for terminal, editor, and power-user workflows.",
                primaryAction = "Open input settings",
                secondaryAction = "Choose keyboard",
                onPrimaryAction = {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
                onSecondaryAction = {
                    getSystemService<InputMethodManager>()?.showInputMethodPicker()
                },
            )
        }
    }
}

