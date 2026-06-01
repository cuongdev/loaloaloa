package com.tingting.notifier.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 color tokens recovered from the Stitch "Modern fintech, teal brand"
 * design system. Light scheme is the primary surface for the app; a derived dark
 * scheme keeps the same hues for system dark mode.
 */

// --- Brand / surface tokens (light) ---
val Primary = Color(0xFF006A6A)
val OnPrimary = Color(0xFFE0FFFE)
val PrimaryContainer = Color(0xFF7DF5F4)
val OnPrimaryContainer = Color(0xFF005C5B)
val Secondary = Color(0xFF4A6463)
val OnSecondary = Color(0xFFE2FFFD)
val SecondaryContainer = Color(0xFFCCE8E7)
val OnSecondaryContainer = Color(0xFF3D5655)
val Tertiary = Color(0xFF006E2E)
val OnTertiary = Color(0xFFE9FFE6)
val TertiaryContainer = Color(0xFF82FF99)
val OnTertiaryContainer = Color(0xFF006127)
val ErrorColor = Color(0xFFA83836)
val OnError = Color(0xFFFFF7F6)
val ErrorContainer = Color(0xFFFA746F)
val OnErrorContainer = Color(0xFF6E0A12)
val Background = Color(0xFFF6FAF9)
val OnBackground = Color(0xFF2A3433)
val SurfaceColor = Color(0xFFF6FAF9)
val OnSurface = Color(0xFF2A3433)
val SurfaceVariant = Color(0xFFD9E5E3)
val OnSurfaceVariant = Color(0xFF566160)
val Outline = Color(0xFF727D7B)
val OutlineVariant = Color(0xFFA9B4B2)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFEEF5F3)
val SurfaceContainer = Color(0xFFE7F0EE)
val SurfaceContainerHigh = Color(0xFFE1EAE8)
val SurfaceContainerHighest = Color(0xFFD9E5E3)
val InverseSurface = Color(0xFF0A0F0F)
val InverseOnSurface = Color(0xFF999E9D)
val InversePrimary = Color(0xFF86FEFC)

// --- Dark scheme tokens (derived, same hue family) ---
val DarkPrimary = Color(0xFF6EE7E5)
val DarkOnPrimary = Color(0xFF003737)
val DarkPrimaryContainer = Color(0xFF005D5D)
val DarkOnPrimaryContainer = Color(0xFF7DF5F4)
val DarkBackground = Color(0xFF0E1514)
val DarkOnBackground = Color(0xFFDDE4E2)
val DarkSurface = Color(0xFF0E1514)
val DarkOnSurface = Color(0xFFDDE4E2)
val DarkSurfaceContainerLowest = Color(0xFF161D1C)
val DarkSurfaceVariant = Color(0xFF3F4948)
val DarkOnSurfaceVariant = Color(0xFFBEC9C7)
val DarkOutline = Color(0xFF899391)
val DarkOutlineVariant = Color(0xFF3F4948)

val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = ErrorColor,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceColor,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
)

val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = SecondaryContainer,
    onSecondary = OnSecondaryContainer,
    tertiary = TertiaryContainer,
    onTertiary = OnTertiaryContainer,
    error = ErrorContainer,
    onError = OnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

/**
 * Semantic money colors that are NOT part of the M3 scheme: income green
 * (rendered with a leading `+`) and outgoing red (leading `−`). Exposed through a
 * [staticCompositionLocalOf] so screens read them as `LocalAppExtraColors.current`.
 */
@Immutable
data class AppExtraColors(
    val income: Color = Color(0xFF16A34A),
    val outgoing: Color = Color(0xFFDC2626),
)

val LightExtraColors = AppExtraColors()
val DarkExtraColors = AppExtraColors(
    income = Color(0xFF4ADE80),
    outgoing = Color(0xFFF87171),
)

val LocalAppExtraColors = staticCompositionLocalOf { LightExtraColors }
