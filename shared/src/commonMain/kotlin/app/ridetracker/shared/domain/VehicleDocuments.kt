package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A car document's type. [id] is stored in the database and backups: never rename. */
enum class DocumentKind(val id: String, val expenseCategory: ExpenseCategory, val romaniaOnly: Boolean = false) {
    RCA("rca", ExpenseCategory.INSURANCE),
    ITP("itp", ExpenseCategory.INSPECTION),
    ROVINIETA("rovinieta", ExpenseCategory.ROAD_TAX, romaniaOnly = true),
    CASCO("casco", ExpenseCategory.INSURANCE),
    DRIVING_LICENCE("driving_licence", ExpenseCategory.OTHER_BUSINESS),

    /** The ARR copy of the transport licence that rides in the car (Romania). */
    COPIE_CONFORMA("copie_conforma", ExpenseCategory.OTHER_BUSINESS, romaniaOnly = true),

    /** The platforms' windscreen badges (ecusoane), Romania. */
    BADGE_UBER("badge_uber", ExpenseCategory.OTHER_BUSINESS, romaniaOnly = true),
    BADGE_BOLT("badge_bolt", ExpenseCategory.OTHER_BUSINESS, romaniaOnly = true),

    /** Anything else, with the driver's own name (an Uber or Bolt document, say). */
    OTHER("other", ExpenseCategory.OTHER_BUSINESS),
    ;

    companion object {
        fun fromId(id: String?): DocumentKind = entries.firstOrNull { it.id == id } ?: OTHER

        /** The kinds offered when adding a document; Romanian ones only in Romania (one already saved stays offered). */
        fun offered(country: Country?, current: DocumentKind? = null): List<DocumentKind> =
            entries.filter { !it.romaniaOnly || country == Country.ROMANIA || it == current }
    }
}

/** Renewing a document: paying for it again moves its expiry on. */
object DocumentRenewal {
    /** The suggested new last valid day: a year after the current one, or a year from today once it has expired. */
    fun suggestedExpiry(expiryEpochDay: Long, today: LocalDate): LocalDate {
        val from = maxOf(LocalDate.fromEpochDays(expiryEpochDay), today)
        return from.plus(DatePeriod(years = 1))
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
