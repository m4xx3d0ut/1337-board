package org.leetboard.ime

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.leetboard.ime.prefs.PreferenceRepository
import org.leetboard.ime.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = PreferenceRepository(applicationContext)
        setContent {
            SettingsScreen(
                repository = repository,
                onOpenDiagnostics = {
                    startActivity(Intent(this, DiagnosticsActivity::class.java))
                },
                onClose = ::finish,
            )
        }
    }
}
