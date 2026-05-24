package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BadassColorScheme = darkColorScheme(
    primary = HighVoltageRed,
    secondary = WarningAmber,
    tertiary = CyberNeonGreen,
    background = DarkCore,
    surface = DarkCard,
    onPrimary = TextWhite,
    onSecondary = DarkCore,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BadassColorScheme,
        typography = Typography,
        content = content
    )
}
