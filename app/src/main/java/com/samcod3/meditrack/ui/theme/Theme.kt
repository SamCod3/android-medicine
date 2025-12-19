package com.samcod3.meditrack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom colors for MediTrack - Medical/Health aesthetic
private val MediTeal = Color(0xFF00897B)
private val MediTealDark = Color(0xFF4DB6AC)
private val MediBlue = Color(0xFF1E88E5)
private val MediError = Color(0xFFD32F2F)

private val LightColorScheme = lightColorScheme(
    primary = MediTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00352F),
    secondary = MediBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF1C1C1C),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF0F4F3),
    onSurfaceVariant = Color(0xFF404943),
    error = MediError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = MediTealDark,
    onPrimary = Color(0xFF00352F),
    primaryContainer = Color(0xFF00695C),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFBBDEFB),
    tertiary = Color(0xFFB388FF),
    onTertiary = Color(0xFF311B92),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCACACA),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690000)
)

@Composable
fun MediTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediTrackTypography,
        shapes = MediTrackShapes,
        content = content
    )
}

