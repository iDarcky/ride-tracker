package app.ridetracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ridetracker.shared.data.AppSettings
import app.ridetracker.ui.AppNavigation
import app.ridetracker.ui.theme.RideTrackerTheme
import app.ridetracker.ui.welcome.WelcomeScreen

/** AppCompatActivity (not ComponentActivity) so the in-app language choice applies on all Android versions. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsFlow = (application as RideTrackerApplication).container.settingsRepository.settings
        setContent {
            RideTrackerTheme {
                val settings: AppSettings? by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
                when {
                    settings == null -> Unit // Still loading; the window background shows.
                    settings?.country == null -> WelcomeScreen()
                    else -> AppNavigation()
                }
            }
        }
    }
}
