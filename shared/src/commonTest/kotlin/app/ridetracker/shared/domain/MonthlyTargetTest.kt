package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class MonthlyTargetTest {
    private val october = Period.Month.containing(LocalDate(2026, 10, 1))
    private val monToSat = TargetSettings.DEFAULT_DRIVING_DAYS

    @Test
    fun aMonthTakesTheLatestEarlierTarget() {
        val t = TargetSettings(amounts = mapOf("2026-08" to 400000, "2026-09" to 500000))
        assertEquals(500000, t.targetFor(october))
        assertEquals(400000, t.targetFor(Period.Month.containing(LocalDate(2026, 8, 15))))
        assertNull(t.targetFor(Period.Month.containing(LocalDate(2026, 7, 1))))
        assertEquals(600000, t.copy(amounts = t.amounts + ("2026-10" to 600000)).targetFor(october))
    }

    @Test
    fun storesAmountsAndDays() {
        val amounts = mapOf("2026-10" to 600000L, "2026-09" to 500000L)
        assertEquals(amounts, TargetSettings.parseAmounts(TargetSettings.formatAmounts(amounts)))
        assertEquals(monToSat, TargetSettings.parseDays(TargetSettings.formatDays(monToSat)))
        assertEquals(monToSat, TargetSettings.parseDays(null))
    }

    @Test
    fun perDayAndPaceCountDrivingDays() {
        // October 2026: 31 days, 4 Sundays, so 27 driving days Mon–Sat. Friday 9 Oct: 7 driving days before it.
        val p = TargetCalculator.progress(october, targetMinor = 540000, achievedMinor = 150000, drivingDays = monToSat, today = LocalDate(2026, 10, 9))
        assertEquals(20, p.drivingDaysLeft)
        assertEquals(19500, p.perDayMinor) // 3,900 left over 20 days
        assertEquals(150000 - 540000 * 7 / 27, p.paceMinor) // 1,500 against 1,400: ahead by 100
        assertTrue(p.paceMinor!! > 0)
    }

    @Test
    fun reachedAndFinishedMonths() {
        val reached = TargetCalculator.progress(october, 500000, 520000, monToSat, LocalDate(2026, 10, 20))
        assertTrue(reached.reached)
        assertNull(reached.perDayMinor)
        assertEquals(0, reached.remainingMinor)
        val past = TargetCalculator.progress(october, 500000, 450000, monToSat, LocalDate(2026, 11, 2))
        assertTrue(past.finished)
        assertNull(past.paceMinor)
        assertEquals(0, past.drivingDaysLeft)
        val everyDay = TargetCalculator.progress(october, 310000, 0, DayOfWeek.entries.toSet(), LocalDate(2026, 10, 1))
        assertEquals(10000, everyDay.perDayMinor)
    }
}
