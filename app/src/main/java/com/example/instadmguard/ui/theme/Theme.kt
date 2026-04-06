package com.example.instadmguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Marine,
        secondary = CyanAccent,
        tertiary = DeepInk,
        background = Mist,
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = DeepInk,
        onTertiary = Color.White,
        onBackground = DeepInk,
        onSurface = DeepInk,
    )

private val DarkColors =
    darkColorScheme(
        primary = CyanAccent,
        secondary = Marine,
        tertiary = CyanAccent,
        background = Night,
        surface = DeepInk,
        onPrimary = DeepInk,
        onSecondary = Color.White,
        onTertiary = DeepInk,
        onBackground = Color.White,
        onSurface = Color.White,
    )

@Composable
fun InstaDMGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
