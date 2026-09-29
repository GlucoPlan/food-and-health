package com.glucoplan.foodhealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0B0),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF38656A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD495),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF0C5216),
    onPrimaryContainer = Color(0xFFB8F0B0),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA0CFD4),
)

/** Светлая и тёмная тема по системной настройке. */
@Composable
fun FoodHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
