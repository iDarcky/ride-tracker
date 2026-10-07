package app.ridetracker.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun monthlyOnThe31stClampsWithoutDrifting() {
        val anchor = d(2026, 1, 31)
        assertEquals(d(2026, 2, 28), Recurrence.nextAfter(anchor, anchor, Frequency.MONTHLY))
        assertEquals(d(2026, 3, 31), Recurrence.nextAfter(d(2026, 2, 28), anchor, Frequency.MONTHLY))
        assertEquals(d(2026, 4, 30), Recurrence.nextAfter(d(2026, 3, 31), anchor, Frequency.MONTHLY))
    }

    @Test
    fun weekly() {
        assertEquals(d(2026, 10, 14), Recurrence.nextAfter(d(2026, 10, 7), d(2026, 10, 7), Frequency.WEEKLY))
    }

    @Test
    fun yearlyOnLeapDay() {
        val anchor = d(2028, 2, 29)
        assertEquals(d(2029, 2, 28), Recurrence.nextAfter(anchor, anchor, Frequency.YEARLY))
        assertEquals(d(2032, 2, 29), Recurrence.nextAfter(d(2031, 2, 28), anchor, Frequency.YEARLY))
    }

    @Test
    fun skipsToTheFirstOccurrenceAfterADate() {
        assertEquals(d(2026, 12, 5), Recurrence.nextAfter(d(2026, 11, 20), d(2026, 1, 5), Frequency.MONTHLY))
    }

    @Test
    fun pending() {
        val today = d(2026, 10, 7)
        assertTrue(Recurrence.isPending(d(2026, 10, 5), null, today))
        assertTrue(Recurrence.isPending(today, d(2026, 12, 31), today))
        assertFalse(Recurrence.isPending(d(2026, 10, 8), null, today))
        assertFalse(Recurrence.isPending(d(2026, 10, 5), d(2026, 10, 1), today)) // series ended
    }
}
