package com.example.fakenews.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NewsBlue,
    secondary = NewsGreen,
    tertiary = NewsRed,
    background = PaperLight,
    surface = PaperLight,
    onPrimary = PaperLight,
    onSecondary = PaperLight,
    onTertiary = PaperLight,
    onBackground = InkDark,
    onSurface = InkDark
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorTokens.DarkPrimary,
    secondary = ColorTokens.DarkSecondary,
    tertiary = ColorTokens.DarkTertiary,
    background = ColorTokens.DarkBackground,
    surface = ColorTokens.DarkSurface,
    onPrimary = InkDark,
    onSecondary = InkDark,
    onTertiary = InkDark,
    onBackground = PaperLight,
    onSurface = PaperLight
)

@Composable
fun NewsAppTheme(
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val DarkPrimary = androidx.compose.ui.graphics.Color(0xFF9DCAEF)
    val DarkSecondary = androidx.compose.ui.graphics.Color(0xFF8BD7B0)
    val DarkTertiary = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val DarkBackground = androidx.compose.ui.graphics.Color(0xFF111418)
    val DarkSurface = androidx.compose.ui.graphics.Color(0xFF111418)
}
