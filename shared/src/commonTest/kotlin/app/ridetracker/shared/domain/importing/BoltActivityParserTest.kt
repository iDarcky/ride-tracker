package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/** Made-up hours, laid out like Bolt's Activity screen on a 1080 px wide screenshot. */
class BoltActivityParserTest {
    private val today = LocalDate(2026, 10, 8) // a Thursday
    private fun w(text: String, x: Int, y: Int, width: Int = 120) = TextBox(text, x - width / 2, y - 20, x + width / 2, y + 20)

    private val chrome = listOf(
        w("Activitate", 510, 228, 200),
        w("Număr", 300, 2167), w("ore", 400, 2167), w("conduse", 520, 2167), w("cu", 640, 2167), w("Bolt", 720, 2167),
        w("ORE", 130, 2282), w("ONLINE", 230, 2282), w("CURSE", 540, 2282), w("ANULĂRI", 900, 2282),
    )

    @Test
    fun readsTheSelectedMonth() {
        val words = chrome + listOf(
            w("ămâna", 60, 362), w("în", 140, 362), w("curs", 200, 362),
            w("În", 420, 362, 40), w("ultimele", 520, 362), w("3", 600, 362, 20), w("luni", 650, 362, 60),
            w("50ore", 480, 548), w("10min", 610, 548),
            w("aug.", 195, 1490), w("sept.", 540, 1490), w("oct.", 880, 1490),
            w("31ore", 490, 1572), w("5min", 600, 1572),
        )
        assertEquals(
            listOf(OnlineTime(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), 31 * 60 + 5)),
            BoltActivityParser.parse(words, 1080, today),
        )
    }

    @Test
    fun readsTheWeekAndTheSelectedDay() {
        val words = chrome + listOf(
            w("28.09-04.10", 110, 362, 200), w("Săptămâna", 450, 362, 200), w("în", 560, 362, 40), w("curs", 620, 362, 80),
            w("12ore", 480, 548), w("30min", 610, 548),
            w("lun.", 96, 1490), w("mar.", 243, 1490), w("mie.", 390, 1490), w("joi", 537, 1490),
            w("vin.", 684, 1490), w("sâm.", 831, 1490), w("dum.", 978, 1490),
            w("Gore", 220, 1572), w("20min", 320, 1572), // OCR reads the bubble's 6 as G
        )
        val week = DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 11))
        assertEquals(
            listOf(OnlineTime(week, 12 * 60 + 30), OnlineTime(DateRange(LocalDate(2026, 10, 6), LocalDate(2026, 10, 6)), 6 * 60 + 20)),
            BoltActivityParser.parse(words, 1080, today),
        )
    }

    @Test
    fun readsAPastWeekTab() {
        val words = chrome + listOf(
            w("21.09-27.09", 300, 362, 200), w("28.09-04.10", 540, 362, 200), w("Săptămâna", 820, 362, 200),
            w("9ore", 490, 548), w("0min", 600, 548),
            w("lun.", 96, 1490), w("mar.", 243, 1490), w("mie.", 390, 1490), w("joi", 537, 1490),
            w("vin.", 684, 1490), w("sâm.", 831, 1490), w("dum.", 978, 1490),
        )
        assertEquals(
            listOf(OnlineTime(DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4)), 9 * 60)),
            BoltActivityParser.parse(words, 1080, today),
        )
    }

    @Test
    fun readsTheEnglishScreen() {
        val words = listOf(
            w("Activity", 423, 193, 136),
            w("Current", 60, 302), w("week", 160, 302), w("Past", 376, 302, 60), w("3", 426, 302, 16), w("months", 501, 302, 108),
            w("61h", 380, 455, 95), w("12m", 505, 455, 117),
            w("Aug", 162, 1244, 50), w("Sep", 448, 1244, 47), w("Oct", 735, 1244, 44),
            w("38h", 406, 1310, 61), w("7m", 480, 1310, 60),
            w("Waiting", 282, 1808, 82), w("and", 352, 1808, 39), w("driving", 419, 1808, 76), w("hours", 498, 1808, 61),
            w("HOURS", 149, 1901, 96), w("RIDES", 450, 1901, 77), w("CANCELS", 748, 1901, 130),
        )
        assertEquals(
            listOf(OnlineTime(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)), 38 * 60 + 7)),
            BoltActivityParser.parse(words, 898, today),
        )
    }

    @Test
    fun readsAnEnglishPastWeekTab() {
        val words = listOf(
            w("Activity", 423, 193, 136), w("HOURS", 149, 1901, 96), w("Waiting", 282, 1808, 82), w("and", 352, 1808, 39),
            w("driving", 419, 1808, 76), w("hours", 498, 1808, 61),
            w("Sep", 380, 302, 50), w("28", 420, 302, 30), w("-", 448, 302, 10), w("Oct", 480, 302, 50), w("4", 515, 302, 16),
            w("Current", 760, 302), w("week", 860, 302),
            w("20h", 380, 455, 95), w("0m", 505, 455, 117),
            w("Mon", 64, 1244, 50), w("Tue", 192, 1244, 50), w("Wed", 320, 1244, 50), w("Thu", 448, 1244, 50),
            w("Fri", 576, 1244, 50), w("Sat", 704, 1244, 50), w("Sun", 832, 1244, 50),
        )
        assertEquals(
            listOf(OnlineTime(DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4)), 20 * 60)),
            BoltActivityParser.parse(words, 898, today),
        )
    }

    @Test
    fun ignoresOtherScreens() {
        assertNull(BoltActivityParser.parse(listOf(w("Earnings breakdown", 500, 300, 400)), 1080, today))
    }
}
