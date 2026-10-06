package app.ridetracker.shared.domain

/**
 * How a driver in Romania works. Decides which Romanian tax features are offered later
 * (tax estimate, Z report, CUI receipts are for PFA only). [id] is persisted: never rename.
 */
enum class DrivingType(val id: String) {
    /** Own business (persoană fizică autorizată). */
    PFA("pfa"),

    /** Through a fleet partner company that pays the driver. */
    FLEET("fleet"),
    ;

    companion object {
        fun fromId(id: String?): DrivingType? = entries.firstOrNull { it.id == id }
    }
}
