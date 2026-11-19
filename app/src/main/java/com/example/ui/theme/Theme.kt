package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SafeCallLightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = SoftBlueTint,
    onPrimaryContainer = DeepNavy,
    secondary = ElectricBlue,
    onSecondary = PureWhite,
    secondaryContainer = ColorTokens.SecondaryContainerLight,
    onSecondaryContainer = DeepNavy,
    tertiary = SkyBlueAccent,
    onTertiary = DeepNavy,
    background = IceBlueSurface,
    onBackground = TextPrimaryNavy,
    surface = PureWhite,
    onSurface = TextPrimaryNavy,
    surfaceVariant = SoftBlueTint,
    onSurfaceVariant = TextSecondarySlate,
    outline = SlateBorder,
    error = DangerRed,
    onError = PureWhite,
    errorContainer = DangerRedContainer,
    onErrorContainer = DangerRed
)

private val SafeCallDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = PureWhite,
    primaryContainer = DarkCardElevated,
    onPrimaryContainer = PureWhite,
    secondary = SkyBlueAccent,
    onSecondary = DeepNavy,
    secondaryContainer = MidnightBlue,
    onSecondaryContainer = PureWhite,
    tertiary = ActiveEmeraldLight,
    onTertiary = DeepNavy,
    background = DarkCanvas,
    onBackground = PureWhite,
    surface = DarkCard,
    onSurface = PureWhite,
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = ColorTokens.ErrorDark,
    onError = PureWhite,
    errorContainer = ColorTokens.ErrorContainerDark,
    onErrorContainer = ColorTokens.ErrorDark
)

private object ColorTokens {
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFDBEAFE)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFF87171)
    val ErrorContainerDark = androidx.compose.ui.graphics.Color(0xFF450A0A)
}

val SafeCallShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SafeCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SafeCallDarkColorScheme else SafeCallLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeCallTypography,
        shapes = SafeCallShapes,
        content = content
    )
}
