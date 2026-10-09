package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Made-up numbers in the layout of Uber Driver's screens, one string per row as text recognition joins them. */
class UberScreensTest {
    private val taken = LocalDate(2026, 10, 9)

    private val earningsRows = listOf(
        "13:22",
        "Oct 5 - Oct 12",
        "RON 210.50",
        "RON 120.00",
        "5 6 7 8 9 10 11",
        "Mon Tue Wed Thu Fri Sat Sun",
        "Stats",
        "Online Trips",
        "3 h 05 m 9",
        "Points",
        "14",
        "How we calculate stats",
        "Breakdown",
        "Net Fare",
        "RON 195.50",
        "Tips",
        "RON 15.00",
        "Total Earnings RON 210.50",
        "See customer fare breakdown",
    )

    @Test
    fun readsTheWeekWhenNoDayIsTapped() {
        val e = assertNotNull(UberEarningsParser.parse(earningsRows, taken, selectedDayOfMonth = null))
        // "Oct 5 - Oct 12" is Monday 5 to Sunday 11; Oct 12 is after the screenshot, but the year comes from Oct 5.
        assertEquals(DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 11)), e.week)
        assertNull(e.day)
        assertEquals(21050, e.earningsMinor)
        assertEquals(3 * 60 + 5, e.onlineMinutes)
        assertEquals(9, e.trips)
        assertTrue(e.addsUp)
        assertEquals(listOf(IncomeLineKind.FARE, IncomeLineKind.TIP), e.lines.map { it.kind })
        assertEquals(1500, e.asSummary().tipsMinor)
    }

    @Test
    fun readsTheTappedDay() {
        val e = assertNotNull(UberEarningsParser.parse(earningsRows, taken, selectedDayOfMonth = 6))
        assertEquals(LocalDate(2026, 10, 6), e.day)
        assertEquals(LocalDate(2026, 10, 6), e.asDay()?.date)
        assertNull(UberEarningsParser.parse(earningsRows, taken, selectedDayOfMonth = 20)) // not in that week
    }

    @Test
    fun readsTripsOnTheirOwnRow() {
        val rows = earningsRows.flatMap { if (it == "3 h 05 m 9") listOf("Trips", "3 h 05 m", "9") else if (it == "Online Trips") listOf("Online") else listOf(it) }
        val e = assertNotNull(UberEarningsParser.parse(rows, taken, null))
        assertEquals(185, e.onlineMinutes)
        assertEquals(9, e.trips)
    }

    @Test
    fun readsMinutesOnly() {
        val rows = earningsRows.map { if (it == "3 h 05 m 9") "45 m 2" else it }
        val e = assertNotNull(UberEarningsParser.parse(rows, taken, null))
        assertEquals(45, e.onlineMinutes)
        assertEquals(2, e.trips)
    }

    private val paymentsTop = listOf(
        "13:21",
        "Uber | Payments",
        "Sep 28 - Oct 5",
        "We spent RON 10.00 extra to encourage both you",
        "and customers to take more trips with the Uber App",
        "105% You",
        "-6% Uber",
        "1% Other",
        "Customer payments RON 900.00",
        "Tap arrow to view total customer fare 100%",
        "Total customer fare +RON 1,000.00",
        "Customer promotions -RON 100.00",
        "Third-party fees and other -RON 6.00",
        "expenses 1%",
        "Airport Fee -RON 6.00",
        "Amount Uber spent +RON 10.00",
        "Tap arrow to view Uber's service fee -1%",
        "Service Fee -RON 140.00",
    )

    private val paymentsBottom = listOf(
        "13:22",
        "Uber | Payments",
        "expenses 1%",
        "Airport Fee -RON 6.00",
        "Amount Uber spent +RON 10.00",
        "Tap arrow to view Uber's service fee -1%",
        "Service Fee -RON 140.00",
        "Customer promotions +RON 100.00",
        "Quest +RON 50.00",
        "Earnings from fares RON 904.00",
        "100%",
        "Tips +RON 20.00",
        "Always 100% yours",
        "Your total earnings RON 924.00",
        "Including tips",
        "Learn more",
    )

    @Test
    fun readsTheWholePaymentsScreen() {
        val p = assertNotNull(UberPaymentsParser.parse(paymentsTop + paymentsBottom.drop(7), taken))
        assertEquals(DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4)), p.week)
        assertEquals(100000, p.customerFareMinor)
        assertEquals(-14000, p.serviceFeeMinor)
        assertEquals(5000, p.bonusMinor) // the customer promotions Uber paid back cancel out
        assertEquals(-600, p.thirdPartyMinor)
        assertEquals(2000, p.tipsMinor)
        assertEquals(92400, p.earningsMinor)
        assertTrue(p.complete)
        assertTrue(p.addsUp)
        val s = assertNotNull(p.asSummary())
        assertEquals(-14000, s.platformFeeMinor)
    }

    @Test
    fun joinsTwoParts() {
        val top = assertNotNull(UberPaymentsParser.parse(paymentsTop, taken))
        val bottom = assertNotNull(UberPaymentsParser.parse(paymentsBottom, taken))
        assertFalse(top.complete)
        assertFalse(bottom.complete) // no week on it
        assertTrue(top.overlaps(bottom))
        val whole = top.merge(bottom)
        assertTrue(whole.complete)
        assertTrue(whole.addsUp)
        assertEquals(92400, whole.earningsMinor)
    }

    @Test
    fun tellsTheScreensApart() {
        assertNull(UberEarningsParser.parse(paymentsTop, taken, null))
        assertNull(UberPaymentsParser.parse(earningsRows, taken))
        assertFalse(UberEarningsParser.recognises(listOf("Earnings breakdown", "Your earnings lei 10.00", "Bolt commission -lei 2.00")))
    }
}
