package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ImportReminderTest {
    private val today = LocalDate(2026, 10, 11)

    private fun entry(platform: Long, date: LocalDate, source: IncomeSource = IncomeSource.SCREENSHOT) =
        IncomeEntryEntity(platformId = platform, amountMinor = 10000, date = date.toEpochDays(), createdAt = 0, source = source.id)

    @Test
    fun remindsUntilEveryUsualAppHasToday() {
        val week = listOf(entry(1, LocalDate(2026, 10, 9)), entry(2, LocalDate(2026, 10, 10)))
        assertTrue(ImportReminders.shouldRemind(today, week))
        assertTrue(ImportReminders.shouldRemind(today, week + entry(1, today))) // Uber's still missing
        assertFalse(ImportReminders.shouldRemind(today, week + entry(1, today) + entry(2, today)))
    }

    @Test
    fun estimatesDontCount() {
        val week = listOf(entry(1, LocalDate(2026, 10, 9)))
        assertTrue(ImportReminders.shouldRemind(today, week + entry(1, today, IncomeSource.ESTIMATE)))
        // Only estimates lately: any real entry today is enough.
        assertTrue(ImportReminders.shouldRemind(today, listOf(entry(1, LocalDate(2026, 10, 9), IncomeSource.ESTIMATE))))
        assertFalse(ImportReminders.shouldRemind(today, listOf(entry(2, today, IncomeSource.MANUAL))))
    }

    @Test
    fun weekStripShowsDaysWithScreenshots() {
        val days = (5..11).map { LocalDate(2026, 10, it) }
        val entries = listOf(entry(1, LocalDate(2026, 10, 6)), entry(1, LocalDate(2026, 10, 7), IncomeSource.ESTIMATE), entry(2, LocalDate(2026, 10, 8)))
        assertEquals(setOf(LocalDate(2026, 10, 6)), ImportReminders.daysWithEntries(1, entries, days))
    }

    @Test
    fun quietOnADayOff() {
        val week = listOf(entry(1, LocalDate(2026, 10, 9)))
        assertFalse(ImportReminders.shouldRemind(today, week, daysOff = setOf(today.toEpochDays())))
    }
}
