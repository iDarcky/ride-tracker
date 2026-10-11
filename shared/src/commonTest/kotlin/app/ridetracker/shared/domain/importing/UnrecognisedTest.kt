package app.ridetracker.shared.domain.importing

import kotlin.test.Test
import kotlin.test.assertEquals

/** Made-up screen text: what each kind of failed screenshot says it is. */
class UnrecognisedTest {
    private fun reason(vararg rows: String) = ScreenshotDiagnosis.reason(rows.toList())

    @Test
    fun almostNoTextIsNotAScreenshot() {
        assertEquals(UnrecognisedReason.NO_TEXT, reason("12:04", "LTE"))
    }

    @Test
    fun uberEarningsWithoutTheWeek() {
        assertEquals(
            UnrecognisedReason.UBER_NO_WEEK,
            reason("Stats", "Online Trips", "2 h 10 m 6", "Breakdown", "Net Fare", "RON 100.00", "Total Earnings RON 100.00"),
        )
        assertEquals(
            UnrecognisedReason.UBER_CROPPED,
            reason("Oct 5 - Oct 12", "Stats", "Online Trips", "2 h 10 m 6", "Total Earnings"),
        )
    }

    @Test
    fun boltBreakdownCutOffOrWithoutDate() {
        assertEquals(UnrecognisedReason.BOLT_CROPPED, reason("Earnings breakdown", "Your earnings RON 200.00", "Ride payments RON 180.00"))
        assertEquals(
            UnrecognisedReason.BOLT_NO_DATE,
            reason("Earnings breakdown", "Your earnings RON 200.00", "Ride payments RON 250.00", "Commission -RON 50.00"),
        )
    }

    @Test
    fun otherScreens() {
        assertEquals(UnrecognisedReason.UBER_OTHER_SCREEN, reason("Uber Pro", "Earn points on every trip", "Gold status this period"))
        assertEquals(UnrecognisedReason.BOLT_OTHER_SCREEN, reason("Bolt Driver", "You are offline", "Go online to get ride requests"))
        assertEquals(UnrecognisedReason.OTHER_IMAGE, reason("Shopping list", "milk bread eggs", "coffee and apples"))
        // Instructions that name the screen aren't the screen.
        assertEquals(
            UnrecognisedReason.BOLT_OTHER_SCREEN,
            reason("Import", "Bolt", "Bolt Driver → Earnings → Earnings breakdown → Daily, pick the day"),
        )
    }
}
