package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PeriodSummaryEntity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthEndTest {
    private val september = Period.Month(kotlinx.datetime.YearMonth(2026, 9))
    private val start = LocalDate(2026, 9, 1).toEpochDays()
    private val end = LocalDate(2026, 9, 30).toEpochDays()

    private fun entry(platform: Long, day: Int, amount: Long, source: IncomeSource = IncomeSource.SCREENSHOT, minutes: Int? = null) =
        IncomeEntryEntity(
            platformId = platform, amountMinor = amount, date = LocalDate(2026, 9, day).toEpochDays(), createdAt = 0,
            source = source.id, onlineMinutes = minutes,
        )

    private fun summary(platform: Long, from: Long = start, to: Long = end, earnings: Long? = null, minutes: Int? = null) =
        PeriodSummaryEntity(
            platformId = platform, importBatchId = 1, periodStart = from, periodEnd = to, grossFareMinor = null,
            cancellationMinor = null, tipsMinor = null, bonusMinor = null, platformFeeMinor = null, earningsMinor = earnings,
            distanceMeters = null, tripCount = null, onlineMinutes = minutes,
        )

    @Test
    fun appIsCompleteWithTotalAndHours() {
        val entries = listOf(entry(1, 3, 20000), entry(1, 4, 30000))
        val missing = MonthEnd.compute(september, entries, emptyList())
        assertFalse(missing.complete)
        assertEquals(0, missing.appsDone)
        val done = MonthEnd.compute(september, entries, listOf(summary(1, earnings = 50000), summary(1, minutes = 600)))
        assertTrue(done.complete)
        assertNull(done.apps.single().differenceMinor) // the days add up to the total
    }

    @Test
    fun differenceIsWhatTheTotalAddsAndFoldsInWhatWasAdded() {
        val entries = listOf(entry(1, 3, 20000), entry(1, 4, 30000))
        val total = listOf(summary(1, earnings = 58400))
        assertEquals(8400, MonthEnd.compute(september, entries, total).apps.single().differenceMinor)
        val added = entries + entry(1, 30, 8400, IncomeSource.MONTH_DIFFERENCE)
        val after = MonthEnd.compute(september, added, total).apps.single()
        assertNull(after.differenceMinor)
        assertEquals(8400, after.addedMinor)
        // Rounding isn't offered.
        assertNull(MonthEnd.compute(september, entries, listOf(summary(1, earnings = 50050))).apps.single().differenceMinor)
    }

    @Test
    fun hoursMustCoverEveryDrivenDay() {
        val entries = listOf(entry(2, 3, 20000, minutes = 300), entry(2, 4, 30000))
        assertFalse(MonthEnd.compute(september, entries, emptyList()).apps.single().hasHours)
        val week = summary(2, from = LocalDate(2026, 9, 1).toEpochDays(), to = LocalDate(2026, 9, 7).toEpochDays(), minutes = 900)
        assertTrue(MonthEnd.compute(september, entries, listOf(week)).apps.single().hasHours)
    }

    @Test
    fun aMonthDifferenceIsNotADayIn() {
        val entries = listOf(entry(1, 30, 8400, IncomeSource.MONTH_DIFFERENCE))
        assertTrue(MonthEnd.compute(september, entries, emptyList()).apps.isEmpty())
        val range = DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 9, 30))
        assertEquals(0 to 3, MonthEnd.daysIn(range, LocalDate(2026, 10, 2), entries, emptySet()))
    }

    @Test
    fun daysInCountsDaysOffAndStopsAtToday() {
        val entries = listOf(entry(1, 1, 100), entry(2, 1, 100), entry(1, 3, 100))
        val off = setOf(LocalDate(2026, 9, 2).toEpochDays())
        assertEquals(3 to 4, MonthEnd.daysIn(september.range, LocalDate(2026, 9, 4), entries, off))
    }

    @Test
    fun statusShowsInTheFirstDays() {
        assertEquals(september, MonthEnd.statusMonth(LocalDate(2026, 10, 1)))
        assertEquals(september, MonthEnd.statusMonth(LocalDate(2026, 10, 10)))
        assertNull(MonthEnd.statusMonth(LocalDate(2026, 10, 11)))
    }
}
