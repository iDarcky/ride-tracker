package app.ridetracker.shared.domain

/** The three expense groups shown on Home. */
enum class ExpenseGroup { VEHICLE, BUSINESS, OTHER }

/**
 * Expense categories. [id] is what the database and backups store: never rename an existing one.
 * New categories can be added freely; unknown ids from newer backups fall back to [OTHER].
 */
enum class ExpenseCategory(val id: String, val group: ExpenseGroup) {
    FUEL("fuel", ExpenseGroup.VEHICLE),
    CHARGING("charging", ExpenseGroup.VEHICLE),
    MAINTENANCE("maintenance", ExpenseGroup.VEHICLE),
    REPAIRS("repairs", ExpenseGroup.VEHICLE),
    INSURANCE("insurance", ExpenseGroup.VEHICLE),
    INSPECTION("inspection", ExpenseGroup.VEHICLE),
    ROAD_TAX("road_tax", ExpenseGroup.VEHICLE),
    PARKING("parking", ExpenseGroup.VEHICLE),
    CAR_WASH("car_wash", ExpenseGroup.VEHICLE),
    RENT("rent", ExpenseGroup.VEHICLE),
    ACCOUNTANT("accountant", ExpenseGroup.BUSINESS),
    BANK_FEES("bank_fees", ExpenseGroup.BUSINESS),
    PHONE("phone", ExpenseGroup.BUSINESS),
    FLEET_FEES("fleet_fees", ExpenseGroup.BUSINESS),

    /** VAT a Romanian PFA pays on the platforms' commission invoices (reverse charge, form 301). */
    INTRA_EU_VAT("intra_eu_vat", ExpenseGroup.BUSINESS),
    OTHER_BUSINESS("other_business", ExpenseGroup.BUSINESS),
    OTHER("other", ExpenseGroup.OTHER),
    ;

    companion object {
        fun fromId(id: String): ExpenseCategory = entries.firstOrNull { it.id == id } ?: OTHER
        fun inGroup(group: ExpenseGroup): List<ExpenseCategory> = entries.filter { it.group == group }

        /** The categories offered when adding an expense: intra-EU VAT only for Romanian drivers on their own (PFA). */
        fun offered(group: ExpenseGroup, country: Country?, drivingType: DrivingType?): List<ExpenseCategory> =
            inGroup(group).filter { it != INTRA_EU_VAT || (country == Country.ROMANIA && drivingType != DrivingType.FLEET) }
    }
}
