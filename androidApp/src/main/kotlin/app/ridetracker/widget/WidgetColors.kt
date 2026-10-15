package app.ridetracker.widget

import android.os.Build
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders
import app.ridetracker.ui.theme.DarkColors
import app.ridetracker.ui.theme.LightColors

/**
 * The widgets' colours: the phone's own (Material You, from the wallpaper) on Android 12+, like Google's widgets
 * (owner, 15 Oct 2026); the app's cockpit colours on older phones. Light and dark follow the phone either way.
 */
internal fun widgetColors(): ColorProviders =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else ColorProviders(light = LightColors, dark = DarkColors)
