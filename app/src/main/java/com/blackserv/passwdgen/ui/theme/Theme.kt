package com.blackserv.passwdgen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val PasswdGenColors = darkColorScheme(
    primary = Color(0xFF22DDE5),
    onPrimary = Color(0xFF001517),
    primaryContainer = Color(0xFF0D3940),
    onPrimaryContainer = Color(0xFFA7F8F5),
    secondary = Color(0xFF5F9CFF),
    onSecondary = Color(0xFF001B3D),
    background = Color(0xFF030B11),
    onBackground = Color(0xFFF2F8FA),
    surface = Color(0xFF0A151E),
    onSurface = Color(0xFFF2F8FA),
    surfaceVariant = Color(0xFF142530),
    onSurfaceVariant = Color(0xFF91A7B3),
    outline = Color(0xFF345365),
    error = Color(0xFFFF6F7D),
)

private val PasswdGenTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PasswdGenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PasswdGenColors,
        typography = PasswdGenTypography,
        content = content,
    )
}
