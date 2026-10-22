package com.divan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Divan palette – deep green & white with gold accent (brand design)
val Green900 = Color(0xFF0F3D2E)
val Green800 = Color(0xFF1B4D3E)
val Green700 = Color(0xFF245C49)
val Green600 = Color(0xFF2E7D5B)
val Green100 = Color(0xFFDCEFE4)
val Gold = Color(0xFFD9B25F)
val Red400 = Color(0xFFE2574C)
val Blue400 = Color(0xFF4A7FD4)
val SurfaceCream = Color(0xFFF7FAF8)
val InkText = Color(0xFF14261E)

private val LightColors = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Gold,
    onSecondary = Color(0xFF3A2E10),
    secondaryContainer = Color(0xFFF3E6C8),
    onSecondaryContainer = Color(0xFF3A2E10),
    tertiary = Blue400,
    background = SurfaceCream,
    onBackground = InkText,
    surface = Color.White,
    onSurface = InkText,
    surfaceVariant = Color(0xFFEDF3EF),
    onSurfaceVariant = Color(0xFF4A5B52),
    error = Red400,
    outline = Color(0xFFB9C9C0),
)

private val DarkColors = darkColorScheme(
    primary = Green600,
    onPrimary = Color.White,
    primaryContainer = Green800,
    onPrimaryContainer = Green100,
    secondary = Gold,
    tertiary = Blue400,
    background = Color(0xFF0C1512),
    onBackground = Color(0xFFE2EAE5),
    surface = Color(0xFF121E19),
    onSurface = Color(0xFFE2EAE5),
    surfaceVariant = Color(0xFF1C2A24),
    onSurfaceVariant = Color(0xFFA9BCB2),
    error = Red400,
    outline = Color(0xFF3D4F46),
)

val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DivanTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
