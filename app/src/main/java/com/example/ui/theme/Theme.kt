package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RavenDarkColorScheme = darkColorScheme(
    primary = MetallicGold,
    onPrimary = MidnightBlack,
    primaryContainer = DeepGoldContainer,
    onPrimaryContainer = BrightGold,
    secondary = ElectricViolet,
    onSecondary = SoftWhite,
    secondaryContainer = DeepPurple,
    onSecondaryContainer = SoftWhite,
    tertiary = ElectricBlue,
    onTertiary = MidnightBlack,
    tertiaryContainer = ElectricBlueContainer,
    onTertiaryContainer = SoftWhite,
    background = MidnightBlack,
    onBackground = SoftWhite,
    surface = ObsidianSurface,
    onSurface = SoftWhite,
    surfaceVariant = StudioCardSurface,
    onSurfaceVariant = MutedSilver,
    surfaceContainerLowest = MidnightBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = StudioCardSurface,
    surfaceContainerHigh = ElevatedStudioSurface,
    outline = StudioBorder,
    error = StudioError,
    onError = MidnightBlack
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RavenStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RavenDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
