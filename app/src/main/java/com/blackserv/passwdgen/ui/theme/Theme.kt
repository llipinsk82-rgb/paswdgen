package com.blackserv.passwdgen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PasswdGenColors = darkColorScheme(
    primary = Color(0xFF29D3C2),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0B3D39),
    onPrimaryContainer = Color(0xFF9EF2E8),
    secondary = Color(0xFF69A8FF),
    onSecondary = Color(0xFF001D36),
    background = Color(0xFF071319),
    onBackground = Color(0xFFE4F2F5),
    surface = Color(0xFF0E1D24),
    onSurface = Color(0xFFE4F2F5),
    surfaceVariant = Color(0xFF172A33),
    onSurfaceVariant = Color(0xFFB9CCD2),
    outline = Color(0xFF6E858D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun PasswdGenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PasswdGenColors,
        content = content,
    )
}
