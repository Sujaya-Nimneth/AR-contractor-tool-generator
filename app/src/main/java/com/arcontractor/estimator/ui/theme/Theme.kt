package com.arcontractor.estimator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ContractorDarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF533300),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00363D),
    background = Color(0xFF121417),
    surface = Color(0xFF1B1E23),
    error = Color(0xFFFF6B6B)
)

private val ContractorLightColors = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626)
)

@Composable
fun ArContractorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ContractorDarkColors else ContractorLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
