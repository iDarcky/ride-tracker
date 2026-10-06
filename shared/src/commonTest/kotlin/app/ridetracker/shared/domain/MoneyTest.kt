package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun parsesCommonFormats() {
        assertEquals(12000, Money.parseToMinor("120", 2))
        assertEquals(12050, Money.parseToMinor("120.5", 2))
        assertEquals(12050, Money.parseToMinor("120,50", 2))
        assertEquals(50, Money.parseToMinor(".5", 2))
        assertEquals(12000, Money.parseToMinor(" 120. ", 2))
        assertEquals(1500, Money.parseToMinor("1500", 0))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(Money.parseToMinor("", 2))
        assertNull(Money.parseToMinor("-5", 2))
        assertNull(Money.parseToMinor("1.234", 2))
        assertNull(Money.parseToMinor("1.5", 0))
        assertNull(Money.parseToMinor("abc", 2))
        assertNull(Money.parseToMinor("1.2.3", 2))
    }

    @Test
    fun formatsPlainString() {
        assertEquals("120.50", Money.toPlainString(12050, 2))
        assertEquals("0.05", Money.toPlainString(5, 2))
        assertEquals("1500", Money.toPlainString(1500, 0))
    }
}
