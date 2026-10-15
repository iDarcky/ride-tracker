package app.ridetracker.shared.domain

import app.ridetracker.shared.data.BackupService
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedLine
import app.ridetracker.shared.domain.importing.ParsedTrip
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.plus

/**
 * Made-up data for screenshots (Beta app only, owner 15 Oct 2026: Threads posts in EUR without real earnings):
 * last month and this month so far, Bolt and Uber days with breakdowns, hours and rides, fuel and other expenses,
 * and a target. Same every time (fixed seed), and it replaces everything in the app except settings.
 */
object DemoData {

    /** One made-up driving day for one app, with its rides (fares adding up to the day's fares). */
    data class Day(val app: String, val day: ParsedDay, val onlineMinutes: Int, val trips: Int, val rides: List<ParsedTrip>)

    data class Expense(val date: LocalDate, val category: ExpenseCategory, val amountMinor: Long, val note: String?)

    data class Month(val days: List<Day>, val expenses: List<Expense>, val targets: Map<Period.Month, Long>)

    const val CURRENCY = "EUR"

    /** From the 1st of last month to [today]. Amounts in euro cents. */
    fun generate(today: LocalDate): Month {
        val random = Random(2026)
        val thisMonth = Period.Month.containing(today)
        val lastMonth = thisMonth.previous() as Period.Month
        val days = mutableListOf<Day>()
        val expenses = mutableListOf<Expense>()
        var date = lastMonth.range.start
        while (date <= today) {
            val sunday = date.dayOfWeek == DayOfWeek.SUNDAY
            val drives = if (sunday) random.nextDouble() < 0.25 else random.nextDouble() < 0.88
            if (drives) {
                // Friday and Saturday pay best.
                val busy = if (date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY) 1.3 else 1.0
                days += boltDay(date, random, busy)
                if (random.nextDouble() < 0.55) days += uberDay(date, random, busy)
            }
            // Fuel every three or four days, a car wash now and then.
            if (date.day % 4 == 1) expenses += Expense(date, ExpenseCategory.FUEL, cents(random, 48.0, 72.0), null)
            if (date.day == 9 || date.day == 23) expenses += Expense(date, ExpenseCategory.CAR_WASH, cents(random, 7.0, 12.0), null)
            date = date.plus(DatePeriod(days = 1))
        }
        for (month in listOf(lastMonth, thisMonth)) {
            val first = month.range.start
            if (first <= today) {
                expenses += Expense(first, ExpenseCategory.PHONE, 1500, null)
                expenses += Expense(first.plus(DatePeriod(days = 4)).coerceAtMost(today), ExpenseCategory.ACCOUNTANT, 4500, null)
            }
        }
        expenses += Expense(lastMonth.range.start.plus(DatePeriod(days = 11)), ExpenseCategory.MAINTENANCE, 12_900, null)
        expenses += Expense(lastMonth.range.start.plus(DatePeriod(days = 17)), ExpenseCategory.INSURANCE, 8_500, null)
        return Month(days, expenses.filter { it.date <= today }, mapOf(lastMonth to 280_000, thisMonth to 300_000))
    }

    /** Bolt's daily breakdown: fares in the app and in cash, a campaign some days, tips, 20% commission. */
    private fun boltDay(date: LocalDate, random: Random, busy: Double): Day {
        val fares = cents(random, 85.0 * busy, 165.0 * busy)
        val cash = (fares * random.nextDouble(0.15, 0.35)).roundToLong()
        val lines = buildList {
            add(ParsedLine(IncomeLineKind.FARE, fares - cash, false, "Ride Payments"))
            add(ParsedLine(IncomeLineKind.FARE, cash, true, "Ride Payments"))
            if (random.nextDouble() < 0.3) add(ParsedLine(IncomeLineKind.BONUS, cents(random, 10.0, 25.0), false, "Campaigns"))
            if (random.nextDouble() < 0.6) add(ParsedLine(IncomeLineKind.TIP, cents(random, 1.0, 8.0), false, "Tips"))
            add(ParsedLine(IncomeLineKind.COMMISSION, -(fares * 0.2).roundToLong(), false, "Bolt commission"))
        }
        val day = ParsedDay(date, lines.sumOf { it.amountMinor }, cash, lines, addsUp = true)
        val count = (fares / 900).toInt().coerceAtLeast(4)
        return Day(BOLT, day, onlineMinutes = random.nextInt(300, 560), trips = count, rides = rides(BOLT, date, count, fares, cash, random))
    }

    /** Uber's day: net fare (after Uber's fee) and sometimes tips. */
    private fun uberDay(date: LocalDate, random: Random, busy: Double): Day {
        val fare = cents(random, 25.0 * busy, 75.0 * busy)
        val lines = buildList {
            add(ParsedLine(IncomeLineKind.FARE, fare, false, "Net Fare"))
            if (random.nextDouble() < 0.4) add(ParsedLine(IncomeLineKind.TIP, cents(random, 1.0, 5.0), false, "Tips"))
        }
        val day = ParsedDay(date, lines.sumOf { it.amountMinor }, null, lines, addsUp = true)
        val count = (fare / 800).toInt().coerceAtLeast(2)
        return Day(UBER, day, onlineMinutes = random.nextInt(90, 220), trips = count, rides = rides(UBER, date, count, fare, 0, random))
    }

    /**
     * [count] rides through a shift (morning or afternoon start, 10–35 minutes each with gaps), their fares adding up to
     * [faresMinor] and the first ones paid in cash up to about [cashMinor]; 2–15 km each.
     */
    private fun rides(app: String, date: LocalDate, count: Int, faresMinor: Long, cashMinor: Long, random: Random): List<ParsedTrip> {
        val weights = List(count) { random.nextDouble(0.6, 1.6) }
        val total = weights.sum()
        val fares = weights.map { (faresMinor * it / total).roundToLong() }.toMutableList()
        fares[fares.lastIndex] += faresMinor - fares.sum() // exact total
        var minute = if (random.nextBoolean()) random.nextInt(7 * 60, 10 * 60) else random.nextInt(14 * 60, 17 * 60)
        var cashLeft = cashMinor
        return fares.mapIndexed { i, fare ->
            val duration = random.nextInt(10, 36)
            val cash = cashLeft > 0 && fare <= cashLeft + 300
            if (cash) cashLeft -= fare
            ParsedTrip(
                externalId = "demo-$app-$date-$i",
                date = date,
                startMinute = minute.coerceAtMost(23 * 60 + 50),
                fareMinor = fare,
                paymentMethod = if (cash) PaymentMethod.CASH else PaymentMethod.IN_APP,
                distanceMeters = random.nextLong(2_000, 15_000),
                durationSeconds = duration * 60L,
            ).also { minute += duration + random.nextInt(3, 20) }
        }
    }

    private fun cents(random: Random, from: Double, until: Double): Long = (random.nextDouble(from, until) * 100).roundToLong()

    /** Replaces the app's data (not its settings) with the demo month and switches the currency to euro. */
    suspend fun fill(
        today: LocalDate,
        nowEpochMillis: Long,
        backup: BackupService,
        settings: SettingsRepository,
        income: IncomeRepository,
        imports: ImportRepository,
        expenses: ExpenseRepository,
    ) {
        val demo = generate(today)
        backup.eraseData()
        settings.setCountry(Country.OTHER, CURRENCY, settings.current().drivingType)
        val platforms = income.observePlatforms().first()
        fun id(name: String) = platforms.first { it.name.equals(name, ignoreCase = true) }.id
        for (d in demo.days) {
            val kind = if (d.app == BOLT) ImportKind.BOLT_DAILY_SCREENSHOT else ImportKind.UBER_DAILY_SCREENSHOT
            imports.saveDay(
                id(d.app), kind, IncomeSource.SCREENSHOT, "demo-${d.app}-${d.day.date}", d.day, nowEpochMillis,
                onlineMinutes = d.onlineMinutes, tripCount = d.trips,
            )
        }
        // Their rides, per app, as the rider-invoice (Bolt) and trip activity (Uber) imports would save them.
        for ((app, days) in demo.days.groupBy { it.app }) {
            val kind = if (app == BOLT) ImportKind.BOLT_RIDER_INVOICES_CSV else ImportKind.UBER_TRIPS_CSV
            imports.saveTrips(id(app), kind, "demo-$app-trips", days.flatMap { it.rides }, nowEpochMillis)
        }
        for (e in demo.expenses) expenses.save(null, e.amountMinor, e.date, e.category, e.note, nowEpochMillis)
        demo.targets.forEach { (month, amount) -> settings.setTarget(month, amount) }
    }

    private const val BOLT = "Bolt"
    private const val UBER = "Uber"
}
