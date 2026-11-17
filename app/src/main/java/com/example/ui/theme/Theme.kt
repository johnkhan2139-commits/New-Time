package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TimeDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = CrispWhite,
    primaryContainer = ElectricBlueSoft,
    onPrimaryContainer = CyanPulse,
    secondary = CyanPulse,
    onSecondary = ObsidianBlack,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CrispWhite,
    tertiary = SoftWhite,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = MidnightSurface,
    onSurface = CrispWhite,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = MutedSlate,
    outline = BorderSlate,
    error = ErrorCoral,
    onError = CrispWhite
)

private val TimeLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = CrispWhite,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = ElectricBlueDark,
    secondary = ElectricBlueDark,
    onSecondary = CrispWhite,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = InkBlack,
    tertiary = InkBlack,
    onTertiary = CrispWhite,
    background = LightBackground,
    onBackground = InkBlack,
    surface = LightSurface,
    onSurface = InkBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    outline = LightOutline,
    error = ErrorCoral,
    onError = CrispWhite
)

val TimeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TimeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TimeDarkColorScheme else TimeLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TimeTypography,
        shapes = TimeShapes,
        content = content
    )
}
