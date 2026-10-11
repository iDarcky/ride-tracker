package app.ridetracker.shared.domain

import kotlinx.datetime.LocalDate

/** A car document's type. [id] is stored in the database and backups: never rename. */
enum class DocumentKind(val id: String, val expenseCategory: ExpenseCategory) {
    RCA("rca", ExpenseCategory.INSURANCE),
    ITP("itp", ExpenseCategory.INSPECTION),
    ROVINIETA("rovinieta", ExpenseCategory.ROAD_TAX),
    CASCO("casco", ExpenseCategory.INSURANCE),
    DRIVING_LICENCE("driving_licence", ExpenseCategory.OTHER_BUSINESS),

    /** Anything else, with the driver's own name (an Uber or Bolt document, say). */
    OTHER("other", ExpenseCategory.OTHER_BUSINESS),
    ;

    companion object {
        fun fromId(id: String?): DocumentKind = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** When documents remind: 30 and 7 days before they expire, and on the day. */
object DocumentReminders {
    /** Days before expiry, furthest first. */
    val STAGES = listOf(30, 7, 0)

    /** From this many days before, a document shows under Needs attention. */
    const val ATTENTION_DAYS = 30

    /** Days from [today] to the last valid day; 0 on the day, negative once expired. */
    fun daysLeft(expiryEpochDay: Long, today: LocalDate): Int = (expiryEpochDay - today.toEpochDays()).toInt()

    fun needsAttention(expiryEpochDay: Long, today: LocalDate): Boolean = daysLeft(expiryEpochDay, today) <= ATTENTION_DAYS

    /**
     * The reminder to send today, or null. Only the latest stage reached counts (a document added 5 days before
     * expiry gets the 7-day one, not the 30-day one too), and each stage is sent once per expiry date.
     */
    fun stageDue(expiryEpochDay: Long, notifiedStage: Int?, today: LocalDate): Int? {
        val left = daysLeft(expiryEpochDay, today)
        val stage = STAGES.lastOrNull { left <= it } ?: return null
        return stage.takeIf { notifiedStage == null || it < notifiedStage }
    }
}
