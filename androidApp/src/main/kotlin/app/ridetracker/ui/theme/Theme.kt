package app.ridetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colours from the "Rideshare cockpit" design system: full M3 scheme from seed #0A6C8B,
// light and dark. Dynamic colour is intentionally off so the brand colour stays consistent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF136682), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEE9FF), onPrimaryContainer = Color(0xFF004D65),
    secondary = Color(0xFF4D616C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E6F2), onSecondaryContainer = Color(0xFF354A54),
    tertiary = Color(0xFF5D5B7D), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3DFFF), onTertiaryContainer = Color(0xFF464364),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6FAFE), onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFE), onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E9), onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D), outlineVariant = Color(0xFFC0C8CD),
    inverseSurface = Color(0xFF2C3134), inverseOnSurface = Color(0xFFEDF1F5), inversePrimary = Color(0xFF8BD0F0),
    surfaceDim = Color(0xFFD6DBDE), surfaceBright = Color(0xFFF6FAFE),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2), surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD0F0), onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004D65), onPrimaryContainer = Color(0xFFBEE9FF),
    secondary = Color(0xFFB4CAD6), onSecondary = Color(0xFF1F333C),
    secondaryContainer = Color(0xFF354A54), onSecondaryContainer = Color(0xFFD0E6F2),
    tertiary = Color(0xFFC7C2EA), onTertiary = Color(0xFF2F2D4C),
    tertiaryContainer = Color(0xFF464364), onTertiaryContainer = Color(0xFFE3DFFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417), onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417), onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF40484C), onSurfaceVariant = Color(0xFFC0C8CD),
    outline = Color(0xFF8A9297), outlineVariant = Color(0xFF40484C),
    inverseSurface = Color(0xFFDFE3E7), inverseOnSurface = Color(0xFF2C3134), inversePrimary = Color(0xFF136682),
    surfaceDim = Color(0xFF0F1417), surfaceBright = Color(0xFF353A3D),
    surfaceContainerLowest = Color(0xFF0A0F11), surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023), surfaceContainerHigh = Color(0xFF262B2E),
    surfaceContainerHighest = Color(0xFF303538),
)

/**
 * The design system's one custom colour: green for "money up" (Material 3 has no success role).
 * Readable on surfaces and on the primary container in both themes.
 */
@Composable
fun positiveColor(): Color = if (isSystemInDarkTheme()) Color(0xFF7EE2A8) else Color(0xFF146C3E)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RideTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
