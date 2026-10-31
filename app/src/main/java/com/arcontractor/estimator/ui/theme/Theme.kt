package com.arcontractor.estimator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ContractorDarkColors = darkColorScheme(
    primary = Color(0xFFF4F6F8),
    onPrimary = Color(0xFF111316),
    primaryContainer = Color(0xFF272B31),
    onPrimaryContainer = Color(0xFFE6E9ED),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B2F5F),
    secondaryContainer = Color(0xFF173A68),
    onSecondaryContainer = Color(0xFFD8E7FF),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFF3F5F7),
    surface = Color(0xFF171A1F),
    onSurface = Color(0xFFF3F5F7),
    surfaceVariant = Color(0xFF24282E),
    onSurfaceVariant = Color(0xFFB6BDC7),
    outline = Color(0xFF3A4049),
    outlineVariant = Color(0xFF272C33),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val ContractorLightColors = lightColorScheme(
    primary = Color(0xFF181B1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E9EE),
    onPrimaryContainer = Color(0xFF181B1F),
    secondary = Color(0xFF1D5FBF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8FA),
    onSecondaryContainer = Color(0xFF0B3C7D),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF16191D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16191D),
    surfaceVariant = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFF505965),
    outline = Color(0xFFD3D9E1),
    outlineVariant = Color(0xFFE7EBF0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val ContractorTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun ArContractorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ContractorDarkColors else ContractorLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ContractorTypography,
        content = content
    )
}
