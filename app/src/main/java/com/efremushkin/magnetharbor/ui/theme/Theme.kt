package com.efremushkin.magnetharbor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HarborLight = lightColorScheme(
    primary = Color(0xFF006782),
    secondary = Color(0xFF4C626B),
    tertiary = Color(0xFF5C5B7D),
)

private val HarborDark = darkColorScheme(
    primary = Color(0xFF5DD5FB),
    secondary = Color(0xFFB3CBD5),
    tertiary = Color(0xFFC5C3EA),
)

@Composable
fun MagnetHarborTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HarborDark else HarborLight,
        content = content,
    )
}
