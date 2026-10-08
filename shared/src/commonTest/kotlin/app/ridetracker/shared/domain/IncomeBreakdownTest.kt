package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Made-up numbers. */
class IncomeBreakdownTest {
    private val sep = Period.Month.containing(LocalDate(2026, 9, 1)).range
    private fun day(d: Int) = LocalDate(2026, 9, d).toEpochDays()

    private fun entry(id: Long, platform: Long, d: Int, amount: Long, cash: Long? = null, source: IncomeSource = IncomeSource.SCREENSHOT) =
        IncomeEntryEntity(id = id, platformId = platform, amountMinor = amount, date = day(d), createdAt = 0, cashCollectedMinor = cash, source = source.id)

    private fun line(entry: Long, kind: IncomeLineKind, amount: Long, cash: Boolean = false, platform: Long = 1) =
        LineInRange(entryId = entry, platformId = platform, date = 0, kind = kind.id, amountMinor = amount, inCash = cash)

    private fun trip(fare: Long, method: PaymentMethod, platform: Long = 1) = TripWithPlatform(
        id = 0, platformId = platform, platformName = "P", platformColorArgb = 0, date = day(3), startMinute = 0,
        fareMinor = fare, paymentMethod = method.id, distanceMeters = null, durationSeconds = null, importKind = "x",
    )

    // Day 2: in app 100 fares + 5 tip, cash 40 fares + 3 promotion, commission -30 = 118 earned, 40 cash in hand.
    private val entries = listOf(entry(1, 1, 2, 11800, cash = 4000), entry(2, 1, 3, 5000, source = IncomeSource.ESTIMATE), entry(3, 2, 4, 7000))
    private val lines = listOf(
        line(1, IncomeLineKind.FARE, 10000), line(1, IncomeLineKind.TIP, 500),
        line(1, IncomeLineKind.FARE, 4000, cash = true), line(1, IncomeLineKind.PROMOTION, 300, cash = true),
        line(1, IncomeLineKind.COMMISSION, -3000),
    )

    @Test
    fun partsAndPaymentForOnePlatform() {
        val b = IncomeBreakdownCalculator.compute(sep, entries, lines, listOf(trip(2000, PaymentMethod.CASH), trip(3000, PaymentMethod.IN_APP)), emptyList(), platformId = 1)
        assertEquals(16800, b.earnedMinor)
        assertEquals(5000, b.estimatedMinor)
        assertEquals(5000, b.withoutBreakdownMinor)
        assertEquals(
            listOf(IncomePart(IncomeLineKind.FARE, 14000), IncomePart(IncomeLineKind.TIP, 500), IncomePart(IncomeLineKind.PROMOTION, 300), IncomePart(IncomeLineKind.COMMISSION, -3000)),
            b.parts,
        )
        val p = b.payment!!
        assertEquals(10500, p.inAppMinor)
        assertEquals(4300, p.cashMinor)
        assertEquals(4000, p.cashInHandMinor)
        assertEquals(7800, p.toBankMinor) // 118 earned - 40 in hand
        assertTrue(p.partial) // the estimated day has no breakdown
        assertEquals(1, p.cashTrips)
        assertEquals(3000, p.inAppFaresMinor)
    }

    @Test
    fun monthlyTotalGivesTheParts() {
        val summary = PeriodSummaryEntity(
            id = 1, platformId = 1, importBatchId = 1, periodStart = sep.start.toEpochDays(), periodEnd = sep.endInclusive.toEpochDays(),
            grossFareMinor = 50000, tipsMinor = 1000, bonusMinor = 20000, cancellationMinor = 500, platformFeeMinor = -12000, earningsMinor = 60000,
        )
        val b = IncomeBreakdownCalculator.compute(sep, entries, lines, emptyList(), listOf(summary), platformId = 1)
        assertEquals(0, b.withoutBreakdownMinor)
        assertEquals(50000 + 1000 + 20000 + 500 + 500 - 12000, b.parts.sumOf { it.amountMinor })
        assertEquals(500, b.parts.first { it.kind == IncomeLineKind.OTHER }.amountMinor) // 600 - named parts
        assertEquals(IncomeLineKind.COMMISSION, b.deductions.single().kind)
    }

    @Test
    fun noPaymentInfoWithoutBreakdownsOrTrips() {
        val b = IncomeBreakdownCalculator.compute(sep, entries, emptyList(), emptyList(), emptyList(), platformId = 2)
        assertNull(b.payment)
        assertEquals(7000, b.withoutBreakdownMinor)
    }
}
