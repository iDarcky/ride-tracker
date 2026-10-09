package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.PaymentMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Made-up drivers, ids and numbers in the layout of Uber's Supplier portal CSVs (Romanian columns). */
class UberReportsTest {
    private val september = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))

    @Test
    fun recognisesReportsByFileName() {
        assertEquals(UberReport.PAYMENTS_ORDER, UberFileName.report("20260901-20260930-payments_order-Ion_Popescu.csv"))
        assertEquals(UberReport.PAYMENTS_ORGANIZATION, UberFileName.report("20260901-20260930-payments_organization-Ion_Popescu.csv"))
        assertEquals(UberReport.DRIVER_TIME_AND_DISTANCE, UberFileName.report("20260901-20260930-driver_time_and_distance-Ion.csv"))
        assertEquals(UberReport.VEHICLE_TIME_AND_DISTANCE, UberFileName.report("20260901-20260930-vehicle_time_and_distance-Ion.csv"))
        assertEquals(UberReport.DRIVER_STATUS, UberFileName.report("driver_status-Ion_Popescu.csv"))
        assertNull(UberFileName.report("rider_invoices.csv"))
        assertEquals(september, UberFileName.period("20260901-20260930-trip_activity-Ion.csv"))
        assertNull(UberFileName.period("driver_status-Ion.csv"))
    }

    private val paymentsHeader = listOf(
        "Identificatorul universal unic (UUID) al tranzacției", "Identificatorul universal unic (UUID) al șoferului",
        "Prenumele șoferului", "Numele de familie al șoferului", "Identificatorul universal unic (UUID) al cursei", "Descriere",
        "Denumirea organizației", "Numele preferat al organizației", "față de raportare", "Câștiguri primite",
        "Câștiguri primite : Câștigurile tale", "Câștiguri primite : Sold cursă : Plăți : Numerar încasat",
        "Câștiguri primite : Câștigurile tale : Tarif", "Câștiguri primite : Câștigurile tale : Impozite",
        "Câștiguri primite:Câștigurile tale:Recompensa", "Câștiguri primite:Câștigurile tale:Tarif:Tarif",
        "Câștiguri primite:Sold cursă:Rambursări:Taxă de aeroport", "Câștiguri primite:Câștigurile tale:Tarif:Anulare",
        "Câștiguri primite:Sold cursă:Plăți:S-a transferat în contul bancar",
        "Câștiguri primite:Câștigurile tale:Tarif:Durată de așteptare la preluare",
        "Câștiguri primite:Câștigurile tale:Promoție:Quest",
    ).joinToString(",")

    /** id, trip, description, time, paid, earned, cash, fare, recompense, fare:fare, airport, cancel, bank, wait, quest */
    private fun paymentRow(
        n: Int, trip: String, time: String, earned: String, cash: String = "0", fare: String = "0", reward: String = "0",
        fareFare: String = "0", airport: String = "0", cancel: String = "0", bank: String = "0", wait: String = "0", quest: String = "0",
        description: String = "trip completed order",
    ): String {
        return listOf(
            "tx-$n", "driver-1", "Ion", "Popescu", trip, description, "Ion Popescu", "IonPopescu", time, "0",
            earned, cash, fare, "", reward, fareFare, airport, cancel, bank, wait, quest,
        ).joinToString(",")
    }

    private val payments = listOf(
        paymentsHeader,
        paymentRow(1, "trip-a", "2026-09-02 10:15:00.000 +0300 EEST", earned = "20.00", fare = "20.00", fareFare = "18.50", wait = "1.50"),
        paymentRow(2, "trip-b", "2026-09-02 11:40:00.000 +0300 EEST", earned = "15.00", cash = "-12.00", fare = "15.00", fareFare = "15.00", airport = "6.00"),
        paymentRow(3, "trip-c", "2026-09-02 12:05:00.000 +0300 EEST", earned = "3.00", fare = "3.00", cancel = "3.00"),
        paymentRow(4, "trip-a", "2026-09-03 09:00:00.000 +0300 EEST", earned = "2.00", reward = "2.00", description = "trip fare adjust order"),
        paymentRow(5, "", "2026-09-03 18:00:00.000 +0300 EEST", earned = "50.00", quest = "50.00", description = "Quest: 20 trips"),
        paymentRow(6, "", "2026-09-07 04:00:00.000 +0300 EEST", earned = "", bank = "-40.00", description = "so.payout"),
    ).joinToString("\n")

    @Test
    fun readsPaymentsAsDays() {
        val result = assertNotNull(UberReports.parsePayments(payments, september))
        assertEquals(september, result.period)
        assertEquals(listOf(LocalDate(2026, 9, 2), LocalDate(2026, 9, 3)), result.days.map { it.date }) // payout day left out

        val day = result.days[0]
        assertEquals(4400, day.earningsMinor) // 20 + 15 + 3 earned, plus the 6 airport fee refunded
        assertEquals(1200, day.cashCollectedMinor)
        assertTrue(day.addsUp)
        assertEquals(2000, day.lines.filter { it.kind == IncomeLineKind.FARE && !it.inCash }.sumOf { it.amountMinor })
        assertEquals(1500, day.lines.single { it.kind == IncomeLineKind.FARE && it.inCash }.amountMinor)
        assertEquals(300, day.lines.single { it.kind == IncomeLineKind.CANCELLATION_FEE }.amountMinor)
        assertEquals(600, day.lines.single { it.kind == IncomeLineKind.AIRPORT_FEE }.amountMinor)

        val bonuses = result.days[1]
        assertEquals(5200, bonuses.earningsMinor)
        assertEquals(5200, bonuses.lines.filter { it.kind == IncomeLineKind.BONUS }.sumOf { it.amountMinor })
        assertTrue(bonuses.addsUp)
    }

    @Test
    fun putsEarningsOnTrips() {
        val fares = assertNotNull(UberReports.parsePayments(payments, september)).tripFares.associateBy { it.tripId }
        assertEquals(setOf("trip-a", "trip-b"), fares.keys) // trip-c was only a cancellation fee
        assertEquals(2000, fares.getValue("trip-a").fareMinor) // the reward is income, not the trip's fare
        assertEquals(10 * 60 + 15, fares.getValue("trip-a").minute)
        assertTrue(fares.getValue("trip-b").inCash)
    }

    @Test
    fun takesThePeriodFromTheDaysWithoutAFileName() {
        assertEquals(DateRange(LocalDate(2026, 9, 2), LocalDate(2026, 9, 3)), UberReports.parsePayments(payments, null)?.period)
    }

    @Test
    fun readsEnglishPaymentColumns() {
        val csv = """
            Transaction UUID,Driver UUID,Driver first name,Driver surname,Trip UUID,Description,Organisation name,Organisation alias,vs reporting,Paid to you,Paid to you : Your earnings,Paid to you : Trip balance : Payouts : Cash collected,Paid to you : Your earnings : Fare,Paid to you:Your earnings:Fare:Fare,Paid to you:Your earnings:Tip,Paid to you:Your earnings:Fare:Service fee
            t1,d1,Ana,Ionescu,trip-1,trip completed order,Ana Ionescu,Ana,2026-09-05 20:00:00.000 +0300 EEST,25.00,25.00,0,22.00,30.00,3.00,-8.00
        """.trimIndent()
        val day = assertNotNull(UberReports.parsePayments(csv, null)).days.single()
        assertEquals(2500, day.earningsMinor)
        assertTrue(day.addsUp)
        assertEquals(-800, day.lines.single { it.kind == IncomeLineKind.COMMISSION }.amountMinor)
        assertEquals(300, day.lines.single { it.kind == IncomeLineKind.TIP }.amountMinor)
    }

    @Test
    fun readsOrganisationTotalsWithTheServiceFee() {
        val csv = listOf(
            "Organization UUID,Denumirea organizației,Numele preferat al organizației,Prenumele șoferului,Numele de familie al șoferului," +
                "Soldul la începutul perioadei selectate,Soldul la sfârșitul perioadei selectate,Câștiguri primite,Câștiguri primite : Câștigurile tale," +
                "Câștiguri primite : Sold cursă : Plăți : Numerar încasat,Câștiguri primite : Câștigurile tale : Tarif,Câștiguri primite : Câștigurile tale : Impozite," +
                "Câștiguri primite:Câștigurile tale:Tarif:Tarif,Câștiguri primite:Câștigurile tale:Tarif:Anulare,Câștiguri primite:Câștigurile tale:Taxă de servicii," +
                "Câștiguri primite:Câștigurile tale:Promoție:Quest,Câștiguri primite:Câștigurile tale:Recompensa,Câștiguri primite:Sold cursă:Rambursări:Taxă de aeroport," +
                "Câștiguri primite:Sold cursă:Plăți:S-a transferat în contul bancar",
            "org-1,Ion Popescu,,Ion,Popescu,0,100.00,100.00,1050.00,-50.00,1200.00,,1190.00,10.00,-300.00,100.00,50.00,6.00,-906.00",
        ).joinToString("\n")
        val s = assertNotNull(UberReports.parseTotals(csv, september))
        assertEquals(LocalDate(2026, 9, 1), s.periodStart)
        assertEquals(119000, s.grossFareMinor)
        assertEquals(1000, s.cancellationMinor)
        assertEquals(15000, s.bonusMinor)
        assertEquals(-30000, s.platformFeeMinor)
        assertEquals(105600, s.earningsMinor) // 1050 earned + 6 airport fee refunded
    }

    @Test
    fun readsCompletedTripsWithoutPersonalData() {
        val csv = """
            Identificatorul universal unic (UUID) al cursei,Identificatorul universal unic (UUID) al șoferului,Prenumele șoferului,Numele de familie al șoferului,Identificatorul universal unic (UUID) al vehiculului,Numărul de înmatriculare,Tipul serviciului,Ora la care a fost comandată cursa,Ora sosirii la destinație,Adresa de preluare,Adresa destinației,Distanța cursei,Starea cursei,Tipul produsului,Tip de plată
            trip-a,d1,Ion,Popescu,v1,B00XYZ,personal_transport,2026-09-02 10:01:30,2026-09-02 10:21:10,"Strada Exemplu 1, Oraș","Piața Test 2, Oraș",5.25,completed,UberX,braintree
            trip-b,d1,Ion,Popescu,v1,B00XYZ,personal_transport,2026-09-02 23:50:00,2026-09-03 00:10:00,"Strada Exemplu 3, Oraș","Strada Test 4, Oraș",7.10,completed,UberX,cash
            trip-x,d1,Ion,Popescu,v1,B00XYZ,personal_transport,2026-09-02 12:00:00,,"Strada Exemplu 5, Oraș","Strada Test 6, Oraș",0.00,rider_cancelled,UberX,apple_pay
        """.trimIndent()
        val trips = assertNotNull(UberReports.parseTrips(csv))
        assertEquals(listOf("trip-a", "trip-b"), trips.map { it.externalId })
        assertEquals(ParsedTrip("trip-a", LocalDate(2026, 9, 2), 10 * 60 + 1, 0, PaymentMethod.IN_APP, 5250, 20 * 60), trips[0])
        assertEquals(PaymentMethod.CASH, trips[1].paymentMethod)
        assertEquals(20L * 60, trips[1].durationSeconds) // over midnight
    }

    @Test
    fun readsTimeAndDistance() {
        val csv = """
            Identificatorul universal unic (UUID) al șoferului partener,Prenume șofer partener,Nume de familie șofer partener,Timp deschis,Timp pe traseu,Timp în cursă,Oră indisponibilă,Distanță deschisă,Distanță pe traseu,Distanță cursă,Distanță indisponibilă
            d1,Ion,Popescu,00:02:30,00:10:15,01:01:05,00:00:01,40.50,200.25,600.00,0.00
        """.trimIndent()
        val t = assertNotNull(UberReports.parseTimeAndDistance(csv, september))
        assertEquals(2 * 60 + 30 + 10 * 60 + 15 + 24 * 60 + 60 + 5, t.onlineMinutes) // 37 h 50 min, unavailable time left out
        assertEquals(840_750, t.distanceMeters)
    }

    @Test
    fun detectsRenamedFilesFromTheirColumns() {
        assertEquals(UberReport.PAYMENTS_ORDER, UberReports.detect("export.csv", paymentsHeader.split(",")))
        assertNull(UberReports.detect("export.csv", listOf("Invoice number", "Date of ride")))
    }
}
