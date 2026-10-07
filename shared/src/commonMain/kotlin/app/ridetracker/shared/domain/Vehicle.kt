package app.ridetracker.shared.domain

/** [id] is stored in the database and backups: never rename. */
enum class FuelType(val id: String) {
    DIESEL("diesel"),
    PETROL("petrol"),
    HYBRID("hybrid"),
    LPG("lpg"),
    ELECTRIC("electric"),
    ;

    /** Electric cars use kWh per 100 km and a price per kWh; the rest use litres. */
    val isElectric: Boolean get() = this == ELECTRIC

    companion object {
        fun fromId(id: String?): FuelType = entries.firstOrNull { it.id == id } ?: DIESEL
    }
}

/** An odometer reading: the car showed [km] on [epochDay]. */
data class OdometerPoint(val epochDay: Long, val km: Long)

object VehicleMath {

    /**
     * Kilometres driven inside [range], from odometer readings. The start point is the last reading
     * before the range (or the first one inside it); the end point is the last reading inside it.
     * Returns null when there are not two usable readings ("Missing").
     */
    fun kmDriven(readings: List<OdometerPoint>, range: DateRange): Long? {
        val start = range.start.toEpochDays()
        val end = range.endInclusive.toEpochDays()
        val sorted = readings.sortedWith(compareBy({ it.epochDay }, { it.km }))
        val inRange = sorted.filter { it.epochDay in start..end }
        val last = inRange.lastOrNull() ?: return null
        val first = sorted.lastOrNull { it.epochDay < start } ?: inRange.first().takeIf { it !== last } ?: return null
        return (last.km - first.km).coerceAtLeast(0)
    }

    /** Estimated fuel cost in minor units: km × (consumption per 100 km) × price per unit. */
    fun estimatedFuelCostMinor(km: Long, consumptionCenti: Long, fuelPriceMinor: Long): Long =
        // consumptionCenti is litres (or kWh) per 100 km × 100, so divide by 100 twice.
        (km * consumptionCenti * fuelPriceMinor + 5_000) / 10_000

    /** Most a car can realistically drive in one day; larger jumps are treated as typos. */
    const val MAX_KM_PER_DAY = 2_000L

    /** True if going from [fromKm] on [fromDay] to [toKm] on [toDay] is drivable (same day counts as one day). */
    fun isPlausible(fromKm: Long, fromDay: Long, toKm: Long, toDay: Long): Boolean =
        toKm - fromKm <= MAX_KM_PER_DAY * maxOf(1L, toDay - fromDay)

    /** Cost per km in minor units, rounded; null when no km were driven. */
    fun costPerKmMinor(costMinor: Long, km: Long?): Long? =
        if (km == null || km <= 0) null else (costMinor + km / 2) / km
}
