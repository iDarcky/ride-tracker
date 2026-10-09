package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class ZReportTest {
    private val thursday = LocalDate(2026, 10, 8)
    private val friday = LocalDate(2026, 10, 9)
    private val reminder = ZReportReminder(enabled = true, minuteOfDay = 22 * 60, enabledFrom = thursday.toEpochDays())

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(date.year, date.month, date.day, hour, minute)

    @Test
    fun todayWaitsOnceTheTimeHasPassed() {
        assertEquals(friday, reminder.waitingDay(at(friday, 22, 0)))
        assertEquals(friday, reminder.waitingDay(at(friday, 23, 59)))
    }

    @Test
    fun aMissedDayWaitsUntilTheNextReminder() {
        assertEquals(thursday, reminder.waitingDay(at(friday, 9)))
        assertNull(reminder.copy(doneThrough = thursday.toEpochDays()).waitingDay(at(friday, 9)))
    }

    @Test
    fun nothingWaitsBeforeItWasTurnedOnOrWhenOff() {
        assertNull(reminder.waitingDay(at(thursday, 9))) // Wednesday is before it was turned on
        assertNull(reminder.copy(enabled = false).waitingDay(at(friday, 23)))
        assertNull(reminder.copy(doneThrough = friday.toEpochDays()).waitingDay(at(friday, 23)))
    }
}
