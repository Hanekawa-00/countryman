package com.github.countryman.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SlateDark,
    secondary = MistDark,
    tertiary = Graphite700,
    background = Graphite900,
    surface = Graphite800,
    surfaceVariant = Graphite700,
    onPrimary = Graphite900,
    onSecondary = Graphite900,
    onTertiary = Graphite100,
    onBackground = Graphite100,
    onSurface = Graphite100,
    onSurfaceVariant = Graphite300,
    outline = Color(0xFF4A4D52),
    outlineVariant = Color(0xFF2D3034)
)

private val LightColorScheme = lightColorScheme(
    primary = Slate,
    secondary = Mist,
    tertiary = PaperMuted,
    background = Paper,
    surface = PaperCard,
    surfaceVariant = PaperMuted,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Graphite900,
    onBackground = Graphite900,
    onSurface = Graphite900,
    onSurfaceVariant = Color(0xFF70757D),
    outline = Color(0xFFB8BDC5),
    outlineVariant = Color(0xFFDDDFE4)
)

@Composable
fun CountrymanTheme(
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
        typography = Typography,
        content = content
    )
}
