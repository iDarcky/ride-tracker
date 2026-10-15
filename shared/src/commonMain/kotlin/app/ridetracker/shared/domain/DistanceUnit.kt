package app.ridetracker.shared.domain

/**
 * How distances are shown and typed (owner, 15 Oct 2026: miles for drivers abroad). Everything is stored in km or
 * metres; this only converts on screen. [id] is stored: never rename.
 */
enum class DistanceUnit(val id: String) {
    KM("km"),
    MILES("mi"),
    ;

    /** Km (or metres / 1000) in this unit. */
    fun fromKm(km: Double): Double = if (this == MILES) km * 1000.0 / METERS_PER_MILE else km

    /** A value typed in this unit, back to km. */
    fun toKm(value: Double): Double = if (this == MILES) value * METERS_PER_MILE / 1000.0 else value

    /** Money per km as money per this unit (a mile is longer, so it costs more). */
    fun perUnit(perKmMinor: Long): Long = if (this == MILES) Math.round(perKmMinor * METERS_PER_MILE / 1000.0) else perKmMinor

    companion object {
        const val METERS_PER_MILE = 1609.344

        fun fromId(id: String?): DistanceUnit = entries.firstOrNull { it.id == id } ?: KM
    }
}
