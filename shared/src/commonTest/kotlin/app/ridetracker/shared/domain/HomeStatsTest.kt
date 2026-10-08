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
        assertEquals(500, s.perKmMinor) // 20.00 over 4 km; the trip without distance is left out
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
    fun longRangesSkipTheDailyChart() {
        val year = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31))
        assertEquals(0, HomeStatsCalculator.compute(year, emptyList(), emptyList(), emptyList()).days.size)
    }
}
