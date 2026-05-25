package org.leetboard.ime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LeetBoardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E676),
            onPrimary = Color(0xFF001B0A),
            background = Color(0xFF050807),
            onBackground = Color(0xFFE6F4EA),
            surface = Color(0xFF101511),
            onSurface = Color(0xFFE6F4EA),
        ),
        content = content,
    )
}

