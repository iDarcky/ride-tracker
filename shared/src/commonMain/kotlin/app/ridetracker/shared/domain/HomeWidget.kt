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
    /** Pinned at the top of Home since 0.11.0, no longer a movable card; the id stays for older saved layouts. */
    NEEDS_ATTENTION("needs_attention"),

    /** In the app vs cash, cash in hand and what reached the bank. */
    CASH_CARD("cash_card"),

    /** The month's target: progress, what's left per driving day, pace. */
    TARGET("target"),

    /** This period against the previous one: income, kept, hours, per hour, trips, each platform. */
    COMPARISON("comparison"),
    ;

    companion object {
        /** What a new install shows, in order. "When you earn" is off until the driver adds it. */
        val DEFAULT: List<HomeWidget> = listOf(TARGET, BREAKDOWN, COMPARISON, METRICS, SPLIT, CASH_CARD, ACTIVITY, EXPENSE_GROUPS)

        /** The cards the driver can move, add or remove in Customise. */
        val MOVABLE: List<HomeWidget> get() = entries - NEEDS_ATTENTION

        fun fromId(id: String): HomeWidget? = entries.firstOrNull { it.id == id }

        /** Shown widgets from their stored ids, in order; unknown ids (from newer versions) are skipped. */
        fun parse(stored: String?): List<HomeWidget> =
            stored?.split(',')?.mapNotNull { fromId(it.trim()) }?.distinct()?.filter { it in MOVABLE } ?: DEFAULT

        fun format(widgets: List<HomeWidget>): String = widgets.joinToString(",") { it.id }

        /**
         * A saved layout plus default cards the driver has never seen (added in a later version), each placed after the
         * default card before it, so new cards show up once instead of hiding until added. [seen] are ids the driver
         * had when they last saved a layout; null = before this was tracked (the cards up to the monthly target).
         */
        fun withNew(layout: List<HomeWidget>, seen: Set<String>?): List<HomeWidget> {
            val known = seen ?: (entries.takeWhile { it != COMPARISON }.map { it.id }.toSet())
            val result = layout.toMutableList()
            DEFAULT.forEachIndexed { index, widget ->
                if (widget in result || widget.id in known) return@forEachIndexed
                val after = DEFAULT.take(index).lastOrNull { it in result }
                result.add(if (after == null) 0 else result.indexOf(after) + 1, widget)
            }
            return result
        }
    }
}
