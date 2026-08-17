package com.voicedaw.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC0A0FF),
    onPrimary = Color(0xFF2C0074),
    primaryContainer = Color(0xFF43168E),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFCAC3DC),
    onSecondary = Color(0xFF322E41),
    secondaryContainer = Color(0xFF484458),
    onSecondaryContainer = Color(0xFFE6DFF9),
    tertiary = Color(0xFFEDA8C3),
    onTertiary = Color(0xFF49172E),
    tertiaryContainer = Color(0xFF622E44),
    onTertiaryContainer = Color(0xFFFFD9E5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
)

@Composable
fun VoiceDawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DawTypography,
        content = content
    )
}
