package app.ridetracker.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceUnitTest {
    @Test
    fun milesConvertBothWays() {
        assertTrue(abs(DistanceUnit.MILES.fromKm(160.9344) - 100.0) < 1e-9)
        assertTrue(abs(DistanceUnit.MILES.toKm(100.0) - 160.9344) < 1e-9)
        assertEquals(42.0, DistanceUnit.KM.fromKm(42.0))
        // 1.00 per km is about 1.61 per mile.
        assertEquals(161, DistanceUnit.MILES.perUnit(100))
        assertEquals(DistanceUnit.KM, DistanceUnit.fromId(null))
        assertEquals(listOf("km", "mi"), DistanceUnit.entries.map { it.id })
    }
}
