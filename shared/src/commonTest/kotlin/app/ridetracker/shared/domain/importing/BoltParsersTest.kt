package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.PaymentMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Made-up numbers in the layout of Bolt's reports. */
class BoltParsersTest {
    private val today = LocalDate(2026, 10, 8)

    @Test
    fun readsAmountRows() {
        assertEquals(ReportText.AmountRow("Ride Payments", 1490), ReportText.amountRow("Ride Payments ............ +lei 14.90"))
        assertEquals(ReportText.AmountRow("Bolt commission", -2015), ReportText.amountRow("Bolt commission -lei 20.15"))
        assertEquals(ReportText.AmountRow("Gross Fare", 314160), ReportText.amountRow("Gross Fare lei 3,141.60"))
        assertEquals(ReportText.AmountRow("Service Fee", -12340), ReportText.amountRow("Service Fee −RON 123.40"))
        assertEquals(ReportText.AmountRow("Tips", 1250), ReportText.amountRow("Tips 12,50 lei"))
        assertEquals(ReportText.AmountRow("Cash in hand", 8820), ReportText.amountRow("Cash in hand +lei 88.20 >"))
        assertNull(ReportText.amountRow("8 Oct"))
        assertNull(ReportText.amountRow("On Trip Mileage 123.45km"))
    }

    @Test
    fun infersTheYearFromTheScreenshotDate() {
        assertEquals(LocalDate(2026, 10, 8), ReportText.inferYear(8, kotlinx.datetime.Month.OCTOBER, today))
        assertEquals(LocalDate(2025, 12, 30), ReportText.inferYear(30, kotlinx.datetime.Month.DECEMBER, LocalDate(2026, 1, 2)))
    }

    private val dailyRows = listOf(
        "15:26",
        "Earnings breakdown",
        "Daily Weekly Monthly",
        "5 Oct",
        "In-app Income +lei 120.50",
        "Ride Payments ..................... +lei 100.50",
        "Campaigns ....................... +lei 20.00",
        "Cash Income +lei 81.00",
        "Ride Payments .................... +lei 75.00",
        "Toll road ....................... +lei 5.00",
        "Rider credits and promotions ....... +lei 1.00",
        "Cost and Fees lei 0.00",
        "Bolt commission -lei 40.10",
        "Your earnings lei 161.40",
        "Cash in hand +lei 80.00",
    )

    @Test
    fun readsBoltDailyBreakdown() {
        val day = assertIs<DailyParseResult.Day>(BoltDailyParser.parse(dailyRows, today)).day
        assertEquals(LocalDate(2026, 10, 5), day.date)
        assertEquals(16140, day.earningsMinor)
        assertEquals(8000, day.cashCollectedMinor)
        assertTrue(day.addsUp)
        assertEquals(
            listOf(
                ParsedLine(IncomeLineKind.FARE, 10050, false, "Ride Payments"),
                ParsedLine(IncomeLineKind.BONUS, 2000, false, "Campaigns"),
                ParsedLine(IncomeLineKind.FARE, 7500, true, "Ride Payments"),
                ParsedLine(IncomeLineKind.TOLL, 500, true, "Toll road"),
                ParsedLine(IncomeLineKind.PROMOTION, 100, true, "Rider credits and promotions"),
                ParsedLine(IncomeLineKind.COMMISSION, -4010, false, "Bolt commission"),
            ),
            day.lines,
        )
    }

    @Test
    fun flagsABreakdownThatDoesNotAddUp() {
        val misread = dailyRows.map { if (it.startsWith("Toll")) "Toll road +lei 8.00" else it }
        val day = assertIs<DailyParseResult.Day>(BoltDailyParser.parse(misread, today)).day
        assertEquals(false, day.addsUp)
    }

    @Test
    fun keepsAGroupTotalWhenItsRowsWereNotRead() {
        val rows = dailyRows.filterNot { it.startsWith("Ride Payments") || it.startsWith("Campaigns") }
        val day = assertIs<DailyParseResult.Day>(BoltDailyParser.parse(rows, today)).day
        assertEquals(12050, day.lines.single { !it.inCash && it.kind == IncomeLineKind.OTHER }.amountMinor)
    }

    @Test
    fun refusesWeeksAndOtherScreens() {
        val week = dailyRows.map { if (it == "5 Oct") "29 Sep - 5 Oct" else it }
        assertEquals(DailyParseResult.NotADay, BoltDailyParser.parse(week, today))
        assertEquals(DailyParseResult.NotRecognised, BoltDailyParser.parse(listOf("Total Earnings", "RON 29.08"), today))
    }

    @Test
    fun readsBoltMonthlySummary() {
        val rows = listOf(
            "NOT AN OFFICIAL INVOICE OR TAX DOCUMENT",
            "Monthly summary for the period",
            "01.08.2026 - 31.08.2026",
            "Test Driver",
            "FARE BREAKDOWN",
            "Gross Fare lei 1,234.50",
            "Cancellation fee lei 10.00",
            "TOTAL lei 1,244.50",
            "OTHER INCOME BREAKDOWN",
            "Tip lei 15.00",
            "TOTAL lei 15.00",
            "OTHER POTENTIAL DEDUCTIONS",
            "Bolt Fee lei 0.00",
            "On Trip Mileage 321.45km",
        )
        assertEquals(
            ParsedSummary(
                periodStart = LocalDate(2026, 8, 1),
                periodEnd = LocalDate(2026, 8, 31),
                grossFareMinor = 123450,
                cancellationMinor = 1000,
                tipsMinor = 1500,
                platformFeeMinor = 0,
                distanceMeters = 321450,
            ),
            BoltMonthlySummaryParser.parse(rows),
        )
    }

    @Test
    fun readsMonthlySummaryWhenColumnsAreJoined() {
        // How on-device text recognition reads the PDF: the two columns side by side.
        val rows = listOf(
            "Monthly summary for the period",
            "01.08.2026- 31.08.2026",
            "FARE BREAKDOWN OTHER INCOME BREAKDOWN",
            "Gross Fare lei 1,234.50 Tip lei 15.00",
            "Cancellation fee lei 10.00 TOTAL lei 15.00",
            "TOTAL lei 1,244.50",
            "Bolt Fee lei 0.00",
            "On Trip Mileage 321.45km",
        )
        val summary = BoltMonthlySummaryParser.parse(rows)!!
        assertEquals(123450, summary.grossFareMinor)
        assertEquals(1000, summary.cancellationMinor)
        assertEquals(1500, summary.tipsMinor)
        assertEquals(321450, summary.distanceMeters)
    }

    @Test
    fun readsBoltRiderInvoicesWithoutPersonalData() {
        val csv = """
            "Invoice Number","Date","Pickup address","Payment method","Date of ride","Recipient","Recipient Address","Recipient Registration Number","Recipient NIM / VAT Number","Company name (Driver)","Company address (Street, Number, ZIP, Country)","Company Registration Number","Company NIM / VAT number","Price (no VAT)","VAT","Price Total"
            "T-2","02.08.2026 21:49","Test Street 1, Test City","Bolt Payment","02.08.2026 20:11","Test Rider","","","","TEST PFA","Test address","1","1","14.9","0","14.9"
            "T-1","01.08.2026 00:15","Test Street 2, Test City","Cash","31.07.2026 23:58","Other Rider","","","","TEST PFA","Test address","1","1","11.7","0","11.7"
            "T-0","01.08.2026 10:00","Test Street 3","Business","01.08.2026 09:30","Test Company","Somewhere","9","RO9","TEST PFA","Test address","1","1","20.6","0","20.6"
        """.trimIndent()
        val trips = BoltRiderInvoicesParser.parse(csv)!!
        assertEquals(
            listOf(
                ParsedTrip("T-2", LocalDate(2026, 8, 2), 20 * 60 + 11, 1490, PaymentMethod.IN_APP),
                ParsedTrip("T-1", LocalDate(2026, 7, 31), 23 * 60 + 58, 1170, PaymentMethod.CASH),
                ParsedTrip("T-0", LocalDate(2026, 8, 1), 9 * 60 + 30, 2060, PaymentMethod.BUSINESS),
            ),
            trips,
        )
    }

    @Test
    fun csvHandlesQuotesAndNewlines() {
        val text = "a,\"b \"\"x\"\", c\"\r\n\"multi\nline\",2\n"
        assertEquals(listOf(listOf("a", "b \"x\", c"), listOf("multi\nline", "2")), Csv.parse(text))
    }
}
