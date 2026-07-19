package com.example.fakenews.ui.theme

import com.example.fakenews.data.model.NewsPress
import androidx.compose.ui.graphics.Color

val NewsBlue = Color(0xFF165D8F)
val NewsRed = Color(0xFFB3261E)
val NewsGreen = Color(0xFF286B4D)
val InkDark = Color(0xFF1B1D21)
val PaperLight = Color(0xFFFAFBFC)

fun NewsPress.toComposeColor(): Color = cardColor.toComposeColor()

fun String.toComposeColor(): Color {
    val hex = removePrefix("#")
    require(hex.length == 6) { "Color hex must be in RRGGBB format." }
    return Color(
        red = hex.substring(0, 2).toInt(16),
        green = hex.substring(2, 4).toInt(16),
        blue = hex.substring(4, 6).toInt(16),
        alpha = 0xFF
    )
}
