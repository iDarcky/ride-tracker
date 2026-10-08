package app.ridetracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
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
        if (savedInstanceState == null) receiveShare(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** Screenshots or reports shared to the app ("Share → Ride Tracker") go to the Import screen. */
    private fun receiveShare(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) (application as RideTrackerApplication).container.sharedFiles.value = uris
    }
}
