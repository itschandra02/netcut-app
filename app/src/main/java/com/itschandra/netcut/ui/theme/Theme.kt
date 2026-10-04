package com.itschandra.netcut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ===== EXACT web palette (light) =====
val Brand = Color(0xFF006DF0)
val BrandDark = Color(0xFF0057C2)
val BrandSoft = Color(0xFFE8F1FF)
val BgLight = Color(0xFFF4F6FB)
val CardLight = Color(0xFFFFFFFF)
val Card2Light = Color(0xFFFFFFFF)
val TextLight = Color(0xFF1F2733)
val MutedLight = Color(0xFF7B8494)
val FaintLight = Color(0xFFA8B0BE)
val BorderLight = Color(0xFFE6EAF2)
val AccLight = Color(0xFF006DF0)   // download = brand
val CyLight = Color(0xFF0AA2C0)    // upload = cyan
val GreenLight = Color(0xFF16A34A)
val GreenSoftLight = Color(0xFFE6F7EF)
val RedLight = Color(0xFFE5484D)
val RedSoftLight = Color(0xFFFDECEC)
val AmberLight = Color(0xFFF59E0B)
val AmberSoftLight = Color(0xFFFFF4E0)
val GridLight = Color(0x2E313F51)  // rgba(31,39,51,.18) — lebih visible

// ===== EXACT web palette (dark) =====
val BrandDarkT = Color(0xFF3D8BFD)
val BrandDarkDark = Color(0xFF2F6FD0)
val BrandSoftDark = Color(0x243D8BFD)  // rgba(61,139,253,.14)
val BgDark = Color(0xFF0E1218)
val CardDark = Color(0xFF161B23)
val Card2Dark = Color(0xFF1A2029)
val TextDark = Color(0xFFE7ECF3)
val MutedDark = Color(0xFF8B94A7)
val FaintDark = Color(0xFF5B6474)
val BorderDark = Color(0xFF242B36)
val AccDark = Color(0xFF3D8BFD)
val CyDark = Color(0xFF22D3EE)
val GreenDark = Color(0xFF34D399)
val GreenSoftDark = Color(0x2434D399)
val RedDark = Color(0xFFFF6B6B)
val RedSoftDark = Color(0x21FF6B6B)
val AmberDark = Color(0xFFFBBF24)
val AmberSoftDark = Color(0x21FBBF24)
val GridDark = Color(0x2EE7ECF3)   // rgba(231,236,243,.18) — lebih visible

data class NetCutColors(
    val brand: Color, val brandDark: Color, val brandSoft: Color,
    val bg: Color, val card: Color, val card2: Color,
    val text: Color, val muted: Color, val faint: Color, val border: Color,
    val acc: Color, val cy: Color,
    val green: Color, val greenSoft: Color,
    val red: Color, val redSoft: Color,
    val amber: Color, val amberSoft: Color,
    val grid: Color,
)

val LightPalette = NetCutColors(
    Brand, BrandDark, BrandSoft, BgLight, CardLight, Card2Light,
    TextLight, MutedLight, FaintLight, BorderLight,
    AccLight, CyLight, GreenLight, GreenSoftLight, RedLight, RedSoftLight,
    AmberLight, AmberSoftLight, GridLight,
)

val DarkPalette = NetCutColors(
    BrandDarkT, BrandDarkDark, BrandSoftDark, BgDark, CardDark, Card2Dark,
    TextDark, MutedDark, FaintDark, BorderDark,
    AccDark, CyDark, GreenDark, GreenSoftDark, RedDark, RedSoftDark,
    AmberDark, AmberSoftDark, GridDark,
)

private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = BrandSoft, onPrimaryContainer = Brand,
    secondary = GreenLight, onSecondary = Color.White,
    background = BgLight, onBackground = TextLight,
    surface = CardLight, onSurface = TextLight,
    surfaceVariant = Card2Light, onSurfaceVariant = MutedLight,
    outline = BorderLight,
    error = RedLight, onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandDarkT, onPrimary = Color.White,
    primaryContainer = BrandSoftDark, onPrimaryContainer = BrandDarkT,
    secondary = GreenDark, onSecondary = Color(0xFF04211D),
    background = BgDark, onBackground = TextDark,
    surface = CardDark, onSurface = TextDark,
    surfaceVariant = Card2Dark, onSurfaceVariant = MutedDark,
    outline = BorderDark,
    error = RedDark, onError = Color(0xFF2A0E0D),
)

@Composable
fun NetCutTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        content = content,
    )
}
