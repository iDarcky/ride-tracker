package app.ridetracker.shared.domain

/** Where an income entry came from. [id] is stored: never rename. */
enum class IncomeSource(val id: String) {
    MANUAL("manual"),
    SCREENSHOT("screenshot"),
    CSV("csv"),
    PDF("pdf"),
    ;

    companion object {
        fun fromId(id: String?): IncomeSource = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/**
 * One line of an income breakdown, as the apps report it. Amounts are signed: [COMMISSION] and
 * [OTHER_FEE] are negative. [id] is stored: never rename; unknown ids fall back to [OTHER].
 */
enum class IncomeLineKind(val id: String) {
    /** Ride payments / fares. */
    FARE("fare"),
    TIP("tip"),
    /** Campaigns, quests, boosts. */
    BONUS("bonus"),
    /** Rider credits and promotions the platform pays on the rider's behalf. */
    PROMOTION("promotion"),
    TOLL("toll"),
    AIRPORT_FEE("airport_fee"),
    CANCELLATION_FEE("cancellation_fee"),
    /** Bolt commission, Uber service fee. */
    COMMISSION("commission"),
    /** "Cost and fees" and other deductions. */
    OTHER_FEE("other_fee"),
    OTHER("other"),
    ;

    companion object {
        fun fromId(id: String?): IncomeLineKind = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** How a rider paid for a trip. [id] is stored: never rename. */
enum class PaymentMethod(val id: String) {
    /** Card or wallet through the app. */
    IN_APP("in_app"),
    CASH("cash"),
    /** Business account (paid through the app). */
    BUSINESS("business"),
    ;

    companion object {
        fun fromId(id: String?): PaymentMethod = entries.firstOrNull { it.id == id } ?: IN_APP
    }
}

/** What kind of report an import read. [id] is stored: never rename. */
enum class ImportKind(val id: String) {
    BOLT_DAILY_SCREENSHOT("bolt_daily_screenshot"),
    BOLT_WEEKLY_SCREENSHOT("bolt_weekly_screenshot"),
    BOLT_MONTHLY_SCREENSHOT("bolt_monthly_screenshot"),
    BOLT_MONTHLY_PDF("bolt_monthly_pdf"),
    BOLT_RIDER_INVOICES_CSV("bolt_rider_invoices_csv"),
    UBER_DAILY_SCREENSHOT("uber_daily_screenshot"),
    UBER_WEEKLY_SCREENSHOT("uber_weekly_screenshot"),
    UBER_PAYMENTS_CSV("uber_payments_csv"),
    OTHER("other"),
    ;

    companion object {
        fun fromId(id: String?): ImportKind = entries.firstOrNull { it.id == id } ?: OTHER
    }
}
