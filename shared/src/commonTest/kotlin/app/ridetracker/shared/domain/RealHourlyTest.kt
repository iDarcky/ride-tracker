package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealHourlyTest {
    private fun stats(net: Long, minutes: Int?, hourIncome: Long) = HomeStats(
        netIncomeMinor = net, grossMinor = net, feesMinor = 0, feesKnownForAll = true, faresMinor = net, bonusesAndTipsMinor = 0,
        otherIncomeMinor = 0, tripCount = 0, averageFareMinor = null, onlineMinutes = minutes,
        perHourMinor = minutes?.let { (hourIncome * 60) / it }, hourIncomeMinor = hourIncome, distanceMeters = null, perKmMinor = null,
        incomePlatformIds = emptySet(), tripPlatformIds = emptySet(), hourPlatformIds = emptySet(), kmPlatformIds = emptySet(),
        days = emptyList(), heat = emptyList(),
    )

    @Test
    fun takesFuelAndExpensesOffEachHour() {
        // RON 500 over 10 hours; RON 100 fuel, RON 150 expenses in all.
        val r = RealHourlyCalculator.compute(stats(50000, 600, 50000), fuelMinor = 10000, expensesMinor = 15000)!!
        assertEquals(5000, r.perHourMinor)
        assertEquals(4000, r.afterFuelPerHourMinor)
        assertEquals(3500, r.afterExpensesPerHourMinor)
        assertFalse(r.partial)
    }

    @Test
    fun sharesCostsWhenHoursCoverPartOfTheIncome() {
        // Hours only for half the income: half the fuel is charged to those hours.
        val r = RealHourlyCalculator.compute(stats(100000, 600, 50000), fuelMinor = 20000, expensesMinor = 20000)!!
        assertEquals(4000, r.afterFuelPerHourMinor)
        assertNull(r.afterExpensesPerHourMinor) // nothing but fuel
        assertTrue(r.partial)
    }

    @Test
    fun nothingWithoutHoursAndNoFuelLineWithoutFuel() {
        assertNull(RealHourlyCalculator.compute(stats(50000, null, 0), 0, 0))
        assertNull(RealHourlyCalculator.compute(stats(50000, 600, 50000), 0, 0)!!.afterFuelPerHourMinor)
    }
}

class WeekPayoutTest {
    private fun entry(platform: Long, amount: Long, cash: Long?, source: IncomeSource = IncomeSource.SCREENSHOT) =
        IncomeEntryEntity(platformId = platform, amountMinor = amount, date = 1, createdAt = 0, source = source.id, cashCollectedMinor = cash)

    @Test
    fun earnedMinusCashIsWhatComesToTheAccount() {
        val payout = WeekPayouts.compute(
            listOf(entry(1, 30000, 12000), entry(1, 20000, null), entry(2, 40000, null), entry(2, 5000, null, IncomeSource.MONTH_DIFFERENCE)),
        )!!
        val bolt = payout.apps.first { it.platformId == 1L }
        assertEquals(50000, bolt.earnedMinor)
        assertEquals(12000, bolt.cashInHandMinor)
        assertEquals(38000, bolt.toAccountMinor)
        assertNull(payout.apps.first { it.platformId == 2L }.cashInHandMinor)
        assertEquals(78000, payout.toAccountMinor)
    }

    @Test
    fun moreCashThanEarnedMeansOwingTheApp() {
        assertEquals(-4000, WeekPayouts.compute(listOf(entry(1, 20000, 24000)))!!.toAccountMinor)
        assertNull(WeekPayouts.compute(emptyList()))
    }
}
