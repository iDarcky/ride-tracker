package app.ridetracker.shared.domain

/**
 * Where the driver works. Decides the currency and, later, which report formats the importer expects.
 * [id] is persisted: never rename an existing one.
 */
enum class Country(val id: String, val currencyCode: String?) {
    ROMANIA("RO", "RON"),

    /** Any other country: the user picks the currency. */
    OTHER("OTHER", null),
    ;

    companion object {
        fun fromId(id: String?): Country? = entries.firstOrNull { it.id == id }
    }
}
