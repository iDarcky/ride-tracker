package app.ridetracker.shared.domain

/**
 * The movable cards on Home (money kept and the period selector stay fixed at the top).
 * [id] is stored in settings and backups: never rename.
 */
enum class HomeWidget(val id: String) {
    BREAKDOWN("breakdown"),
    METRICS("metrics"),
    SPLIT("split"),
    ACTIVITY("activity"),
    WHEN_YOU_EARN("when_you_earn"),
    EXPENSE_GROUPS("expense_groups"),
    NEEDS_ATTENTION("needs_attention"),

    /** In the app vs cash, cash in hand and what reached the bank. */
    CASH_CARD("cash_card"),

    /** The month's target: progress, what's left per driving day, pace. */
    TARGET("target"),
    ;

    companion object {
        /** What a new install shows, in order. "When you earn" is off until the driver adds it. */
        val DEFAULT: List<HomeWidget> = listOf(TARGET, BREAKDOWN, METRICS, SPLIT, CASH_CARD, ACTIVITY, EXPENSE_GROUPS, NEEDS_ATTENTION)

        fun fromId(id: String): HomeWidget? = entries.firstOrNull { it.id == id }

        /** Shown widgets from their stored ids, in order; unknown ids (from newer versions) are skipped. */
        fun parse(stored: String?): List<HomeWidget> =
            stored?.split(',')?.mapNotNull { fromId(it.trim()) }?.distinct() ?: DEFAULT

        fun format(widgets: List<HomeWidget>): String = widgets.joinToString(",") { it.id }
    }
}
