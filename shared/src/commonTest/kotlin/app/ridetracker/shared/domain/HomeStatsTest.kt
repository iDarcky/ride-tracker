package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.TripWithPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class HomeStatsTest {
    private val start = LocalDate(2026, 10, 5) // Monday
    private val week = DateRange(start, LocalDate(2026, 10, 11))
    private fun day(offset: Int) = start.toEpochDays() + offset

    private fun entry(id: Long, platform: Long, offset: Int, amount: Long, minutes: Int? = null) =
        IncomeEntryEntity(id = id, platformId = platform, amountMinor = amount, date = day(offset), createdAt = 0, onlineMinutes = minutes)

    private fun line(entry: Long, kind: IncomeLineKind, amount: Long) =
        LineInRange(entryId = entry, platformId = 2, date = 0, kind = kind.id, amountMinor = amount)

    private fun trip(offset: Int, minute: Int, fare: Long, meters: Long? = null) = TripWithPlatform(
        id = 0, platformId = 2, platformName = "Bolt", platformColorArgb = 0, date = day(offset), startMinute = minute,
        fareMinor = fare, paymentMethod = "cash", distanceMeters = meters, durationSeconds = null, importKind = "x",
    )

    @Test
    fun splitsGrossFeesAndNet() {
        val entries = listOf(entry(1, 2, 0, 10000), entry(2, 1, 1, 5000))
        val lines = listOf(
            line(1, IncomeLineKind.FARE, 9000),
            line(1, IncomeLineKind.TIP, 1000),
            line(1, IncomeLineKind.BONUS, 2000),
            line(1, IncomeLineKind.TOLL, 500),
            line(1, IncomeLineKind.COMMISSION, -2500),
        )
        val s = HomeStatsCalculator.compute(week, entries, lines, emptyList())
        assertEquals(15000, s.netIncomeMinor)
        assertEquals(17500, s.grossMinor) // 12500 from the breakdown + 5000 typed in
        assertEquals(-2500, s.feesMinor)
        assertEquals(s.netIncomeMinor, s.grossMinor + s.feesMinor)
        assertEquals(false, s.feesKnownForAll) // entry 2 has no breakdown
        assertEquals(14000, s.faresMinor) // 9000 + the 5000 typed in
        assertEquals(3000, s.bonusesAndTipsMinor)
        assertEquals(500, s.otherIncomeMinor)
    }

    @Test
    fun daysCoverTheWholeRangeWithAppsSeparate() {
        val entries = listOf(entry(1, 2, 0, 100), entry(2, 1, 0, 50), entry(3, 2, 6, 70))
        val s = HomeStatsCalculator.compute(week, entries, emptyList(), emptyList())
        assertEquals(7, s.days.size)
        assertEquals(mapOf(2L to 100L, 1L to 50L), s.days[0].byPlatform)
        assertEquals(0, s.days[3].totalMinor)
        assertEquals(70, s.days[6].totalMinor)
    }

    @Test
    fun perHourAndPerKmOnlyFromWhatHasThem() {
        val entries = listOf(entry(1, 1, 0, 6000, minutes = 120), entry(2, 2, 1, 9999))
        val trips = listOf(trip(0, 600, 2000, meters = 4000), trip(0, 700, 1000))
        val s = HomeStatsCalculator.compute(week, entries, emptyList(), trips)
        assertEquals(3000, s.perHourMinor) // 60.00 over 2 h; the entry without hours is left out
        assertEquals(2500, s.perKmMinor) // platform 2's income (99.99) over its 4 km
        assertEquals(2, s.tripCount)
        assertEquals(1500, s.averageFareMinor)
    }

    @Test
    fun missingFiguresStayNull() {
        val s = HomeStatsCalculator.compute(week, listOf(entry(1, 1, 0, 100)), emptyList(), emptyList())
        assertNull(s.perHourMinor)
        assertNull(s.perKmMinor)
        assertNull(s.averageFareMinor)
        assertNull(s.onlineMinutes)
    }

    @Test
    fun heatGroupsFaresByWeekdayAndFourHours() {
        val trips = listOf(trip(0, 17 * 60, 1000), trip(0, 19 * 60, 500), trip(6, 23 * 60 + 59, 300))
        val s = HomeStatsCalculator.compute(week, emptyList(), emptyList(), trips)
        assertEquals(1500, s.heat[0][4]) // Monday 16–20
        assertEquals(300, s.heat[6][5]) // Sunday 20–24
    }

    @Test
    fun daysCountTripsFromImportsOrEntries() {
        val entries = listOf(entry(1, 1, 0, 100).copy(tripCount = 4), entry(2, 2, 0, 100).copy(tripCount = 9))
        val trips = listOf(trip(0, 600, 500), trip(0, 700, 500))
        val s = HomeStatsCalculator.compute(week, entries, emptyList(), trips)
        assertEquals(6, s.days[0].tripCount) // 4 reported by platform 1 + 2 imported for platform 2 (its 9 is not added)
    }

    @Test
    fun monthlyKmFromThePlatformsOwnTotal() {
        val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val summary = app.ridetracker.shared.data.PeriodSummaryEntity(
            platformId = 2, importBatchId = 1, periodStart = september.start.toEpochDays(),
            periodEnd = september.endInclusive.toEpochDays(), distanceMeters = 100_000,
        )
        val entries = listOf(
            IncomeEntryEntity(id = 1, platformId = 2, amountMinor = 30000, date = september.start.toEpochDays(), createdAt = 0),
            IncomeEntryEntity(id = 2, platformId = 1, amountMinor = 10000, date = september.start.toEpochDays(), createdAt = 0),
        )
        val s = HomeStatsCalculator.compute(september, entries, emptyList(), emptyList(), listOf(summary))
        assertEquals(100_000, s.distanceMeters)
        assertEquals(300, s.perKmMinor) // platform 2's 300.00 over 100 km; platform 1 has no km
        assertEquals(setOf(2L), s.kmPlatformIds)
        assertEquals(setOf(1L, 2L), s.incomePlatformIds)
        // A week inside September does not use the monthly total.
        val week = DateRange(LocalDate(2026, 9, 7), LocalDate(2026, 9, 13))
        assertNull(HomeStatsCalculator.compute(week, emptyList(), emptyList(), emptyList(), listOf(summary)).distanceMeters)
    }

    @Test
    fun aPlatformsOwnMonthlyBreakdownGivesGrossAndFees() {
        val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        val summary = app.ridetracker.shared.data.PeriodSummaryEntity(
            platformId = 2, importBatchId = 1, periodStart = september.start.toEpochDays(), periodEnd = september.endInclusive.toEpochDays(),
            grossFareMinor = 200000, cancellationMinor = 1000, tipsMinor = 2000, bonusMinor = 50000,
            platformFeeMinor = -50000, earningsMinor = 210000,
        )
        // Estimated days (no lines) add up to the monthly earnings.
        val entries = listOf(
            IncomeEntryEntity(id = 1, platformId = 2, amountMinor = 210000, date = september.start.toEpochDays(), createdAt = 0, source = "estimate"),
        )
        val s = HomeStatsCalculator.compute(september, entries, emptyList(), emptyList(), listOf(summary))
        assertEquals(260000, s.grossMinor)
        assertEquals(-50000, s.feesMinor)
        assertEquals(s.netIncomeMinor, s.grossMinor + s.feesMinor)
        assertEquals(200000, s.faresMinor)
        assertEquals(52000, s.bonusesAndTipsMinor)
        assertEquals(8000, s.otherIncomeMinor) // cancellations and other parts of the breakdown
        assertEquals(true, s.feesKnownForAll)
        assertEquals(210000, s.estimatedMinor)
    }

    @Test
    fun onlineHoursFromThePlatformsActivityTotals() {
        val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        fun hours(start: LocalDate, end: LocalDate, minutes: Int, id: Long = 0) = app.ridetracker.shared.data.PeriodSummaryEntity(
            id = id, platformId = 2, importBatchId = 1, periodStart = start.toEpochDays(), periodEnd = end.toEpochDays(), onlineMinutes = minutes,
        )
        val entries = listOf(
            IncomeEntryEntity(id = 1, platformId = 2, amountMinor = 60000, date = LocalDate(2026, 9, 10).toEpochDays(), createdAt = 0),
            IncomeEntryEntity(id = 2, platformId = 2, amountMinor = 30000, date = LocalDate(2026, 9, 11).toEpochDays(), createdAt = 0),
        )
        // Whole month: the month's total; an older import of the same month is replaced by the newer one.
        val month = HomeStatsCalculator.compute(
            september, entries, emptyList(), emptyList(),
            listOf(hours(september.start, september.endInclusive, 500, id = 1), hours(september.start, september.endInclusive, 600, id = 2)),
        )
        assertEquals(600, month.onlineMinutes)
        assertEquals(9000, month.perHourMinor) // 900.00 over 10 h
        assertEquals(setOf(2L), month.hourPlatformIds)
        // A week: only the day it has, with that day's income.
        val week = DateRange(LocalDate(2026, 9, 7), LocalDate(2026, 9, 13))
        val days = HomeStatsCalculator.compute(week, entries, emptyList(), emptyList(), listOf(hours(LocalDate(2026, 9, 10), LocalDate(2026, 9, 10), 240)))
        assertEquals(240, days.onlineMinutes)
        assertEquals(15000, days.perHourMinor) // 600.00 over 4 h; the 11th has no hours
        // A longer period: the month's total plus a later week, but not a day already inside that week.
        val autumn = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 10, 9))
        val mixed = HomeStatsCalculator.compute(
            autumn, entries, emptyList(), emptyList(),
            listOf(
                hours(september.start, september.endInclusive, 600, id = 1),
                hours(LocalDate(2026, 10, 5), LocalDate(2026, 10, 11), 300, id = 2), // week still in progress
                hours(LocalDate(2026, 10, 6), LocalDate(2026, 10, 6), 100, id = 3),
            ),
        )
        assertEquals(900, mixed.onlineMinutes)
    }

    @Test
    fun longRangesChartMonths() {
        val range = DateRange(LocalDate(2026, 7, 20), LocalDate(2026, 10, 9)) // 82 days
        val entries = listOf(
            IncomeEntryEntity(id = 1, platformId = 2, amountMinor = 100, date = LocalDate(2026, 8, 25).toEpochDays(), createdAt = 0),
            IncomeEntryEntity(id = 2, platformId = 2, amountMinor = 200, date = LocalDate(2026, 9, 3).toEpochDays(), createdAt = 0),
            IncomeEntryEntity(id = 3, platformId = 1, amountMinor = 50, date = LocalDate(2026, 9, 30).toEpochDays(), createdAt = 0),
        )
        val s = HomeStatsCalculator.compute(range, entries, emptyList(), listOf(trip(0, 600, 10).copy(date = LocalDate(2026, 9, 3).toEpochDays())))
        assertEquals(true, s.monthly)
        assertEquals(listOf(7, 8, 9, 10), s.days.map { it.date.month.ordinal + 1 })
        assertEquals(listOf(0L, 100L, 250L, 0L), s.days.map { it.totalMinor })
        assertEquals(1, s.days[2].tripCount)
    }
}
