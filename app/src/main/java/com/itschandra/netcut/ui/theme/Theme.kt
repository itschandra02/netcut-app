package com.itschandra.netcut.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- brand palette (konsisten dg web) ----
val Brand = Color(0xFF006DF0)
val BrandDark = Color(0xFF0057C2)
val BrandSoft = Color(0xFFE8F1FF)

val Green = Color(0xFF16A34A)
val GreenSoft = Color(0xFFE6F7EF)
val Amber = Color(0xFFF59E0B)
val AmberSoft = Color(0xFFFFF4E0)
val Red = Color(0xFFE5484D)
val RedSoft = Color(0xFFFDECEC)

val BgLight = Color(0xFFF4F6FB)
val CardLight = Color(0xFFFFFFFF)
val TextLight = Color(0xFF1F2733)
val MutedLight = Color(0xFF7B8494)
val BorderLight = Color(0xFFE6EAF2)

val BgDark = Color(0xFF0E1218)
val CardDark = Color(0xFF161B23)
val TextDark = Color(0xFFE7ECF3)
val MutedDark = Color(0xFF8B94A7)
val BorderDark = Color(0xFF242B36)

val BrandDarkTheme = Color(0xFF3D8BFD)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = GreenSoft,
    onSecondaryContainer = Green,
    background = BgLight,
    onBackground = TextLight,
    surface = CardLight,
    onSurface = TextLight,
    surfaceVariant = CardLight,
    onSurfaceVariant = MutedLight,
    outline = BorderLight,
    error = Red,
    onError = Color.White,
    errorContainer = RedSoft,
    onErrorContainer = Red,
)

private val DarkColors = darkColorScheme(
    primary = BrandDarkTheme,
    onPrimary = Color.White,
    primaryContainer = Color(0x243D8BFD),
    onPrimaryContainer = BrandDarkTheme,
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF04211D),
    secondaryContainer = Color(0x2434D399),
    onSecondaryContainer = Color(0xFF34D399),
    background = BgDark,
    onBackground = TextDark,
    surface = CardDark,
    onSurface = TextDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = MutedDark,
    outline = BorderDark,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0E0D),
    errorContainer = Color(0x24FF6B6B),
    onErrorContainer = Color(0xFFFF6B6B),
)

@Composable
fun NetCutTheme(
    darkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content,
    )
}
