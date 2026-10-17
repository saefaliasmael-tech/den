package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DuyooniColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = PureWhite,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = EmeraldVariant,
    onSecondary = PureWhite,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = AmberDebt,
    onTertiary = PureWhite,
    tertiaryContainer = AmberDebtContainer,
    onTertiaryContainer = OnAmberDebt,
    background = OffWhiteBackground,
    onBackground = CharcoalTextPrimary,
    surface = SurfaceCard,
    onSurface = CharcoalTextPrimary,
    surfaceVariant = SurfaceVariantSubtle,
    onSurfaceVariant = MutedTextSecondary,
    outline = SoftBorder,
    outlineVariant = SurfaceVariantSubtle
)

@Composable
fun DuyooniProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DuyooniColorScheme,
        typography = AppTypography,
        content = content
    )
}
