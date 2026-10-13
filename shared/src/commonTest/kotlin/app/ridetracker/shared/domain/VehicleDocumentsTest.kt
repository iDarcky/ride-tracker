package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class VehicleDocumentsTest {
    private val expiry = LocalDate(2026, 11, 30).toEpochDays()

    @Test
    fun remindsThirtyAndSevenDaysBeforeAndOnTheDay() {
        assertNull(DocumentReminders.stageDue(expiry, null, LocalDate(2026, 10, 30))) // 31 days left
        assertEquals(30, DocumentReminders.stageDue(expiry, null, LocalDate(2026, 10, 31)))
        assertNull(DocumentReminders.stageDue(expiry, 30, LocalDate(2026, 11, 10))) // already sent
        assertEquals(7, DocumentReminders.stageDue(expiry, 30, LocalDate(2026, 11, 23)))
        assertEquals(0, DocumentReminders.stageDue(expiry, 7, LocalDate(2026, 11, 30)))
        assertNull(DocumentReminders.stageDue(expiry, 0, LocalDate(2026, 12, 5)))
    }

    @Test
    fun aLateDocumentGetsOnlyTheLatestReminder() {
        // Added 5 days before it expires: the 7-day reminder, never the 30-day one afterwards.
        assertEquals(7, DocumentReminders.stageDue(expiry, null, LocalDate(2026, 11, 25)))
        // Expired before the phone checked: the "today" one, once.
        assertEquals(0, DocumentReminders.stageDue(expiry, 7, LocalDate(2026, 12, 2)))
    }

    @Test
    fun needsAttentionFromThirtyDaysBefore() {
        assertFalse(DocumentReminders.needsAttention(expiry, LocalDate(2026, 10, 30)))
        assertTrue(DocumentReminders.needsAttention(expiry, LocalDate(2026, 10, 31)))
        assertTrue(DocumentReminders.needsAttention(expiry, LocalDate(2027, 1, 1)))
        assertEquals(-2, DocumentReminders.daysLeft(expiry, LocalDate(2026, 12, 2)))
    }

    @Test
    fun kindsKeepTheirIds() {
        assertEquals(
            listOf("rca", "itp", "rovinieta", "casco", "driving_licence", "copie_conforma", "badge_uber", "badge_bolt", "other"),
            DocumentKind.entries.map { it.id },
        )
        assertEquals(DocumentKind.OTHER, DocumentKind.fromId("unknown"))
        assertEquals(ExpenseCategory.ROAD_TAX, DocumentKind.ROVINIETA.expenseCategory)
    }

    @Test
    fun romanianKindsOnlyInRomania() {
        val romanian = listOf(DocumentKind.ROVINIETA, DocumentKind.COPIE_CONFORMA, DocumentKind.BADGE_UBER, DocumentKind.BADGE_BOLT)
        assertTrue(DocumentKind.offered(Country.ROMANIA).containsAll(romanian))
        assertTrue(DocumentKind.offered(null).none { it in romanian })
        // A saved rovinietă stays editable after the country changes.
        assertTrue(DocumentKind.ROVINIETA in DocumentKind.offered(null, current = DocumentKind.ROVINIETA))
    }

    @Test
    fun renewalSuggestsAYearOn() {
        // Still valid: a year after the current expiry.
        assertEquals(LocalDate(2027, 11, 30), DocumentRenewal.suggestedExpiry(expiry, LocalDate(2026, 11, 20)))
        // Already expired: a year from today.
        assertEquals(LocalDate(2027, 12, 5), DocumentRenewal.suggestedExpiry(expiry, LocalDate(2026, 12, 5)))
    }
}
