package app.ridetracker

import app.ridetracker.ui.common.LocalDistanceUnit
import androidx.compose.runtime.CompositionLocalProvider
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
import app.ridetracker.shared.domain.Period
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import app.ridetracker.ui.AppNavigation
import app.ridetracker.ui.theme.RideTrackerTheme
import app.ridetracker.ui.welcome.WelcomeScreen

/** AppCompatActivity (not ComponentActivity) so the in-app language choice applies on all Android versions. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RideTrackerApplication).container
        val settingsFlow = container.settingsRepository.settings
        if (savedInstanceState == null) {
            container.period.value = Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            receiveShare(intent)
        }
        setContent {
            RideTrackerTheme {
                val settings: AppSettings? by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
                when {
                    settings == null -> Unit // Still loading; the window background shows.
                    settings?.country == null -> WelcomeScreen()
                    else -> CompositionLocalProvider(LocalDistanceUnit provides settings!!.distanceUnit) { AppNavigation() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** Screenshots or reports shared to the app ("Share → Ride Tracker") go to the Import screen, as does the import reminder. */
    private fun receiveShare(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_IMPORT, false) == true) {
            (application as RideTrackerApplication).container.openImport.value = true
        }
        intent?.getStringExtra(EXTRA_OPEN_ROUTE)?.let { (application as RideTrackerApplication).container.openRoute.value = it }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TARGET, false) == true) {
            (application as RideTrackerApplication).container.openTarget.value = true
        }
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) (application as RideTrackerApplication).container.sharedFiles.value = uris
    }

    companion object {
        /** Set by the import reminder's notification: open the Import screen. */
        const val EXTRA_OPEN_IMPORT = "open_import"

        /** Set by the quick-actions widget: the screen to open (a navigation route). */
        const val EXTRA_OPEN_ROUTE = "open_route"

        /** Set by the target widget when no target is set yet: open the target page. */
        const val EXTRA_OPEN_TARGET = "open_target"
    }
}
