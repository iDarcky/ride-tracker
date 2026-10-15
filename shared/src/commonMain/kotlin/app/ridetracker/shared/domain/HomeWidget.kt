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

    /**
     * The month's target: progress, what's left per driving day, pace. Inside the money-kept card since 0.21.0, no
     * longer a movable card; the id stays for older saved layouts.
     */
    TARGET("target"),

    /** This period against the previous one: income, kept, hours, per hour, trips, each platform. */
    COMPARISON("comparison"),

    /** This week's days, each app ticked once its day is in (owner, 15 Oct 2026: screenshots first, see it weekly). */
    WEEK_SCREENSHOTS("week_screenshots"),

    /** This week so far per app: earned, cash in hand, coming to the account (0.18.0). */
    WEEK_PAYOUT("week_payout"),

    /** Money per hour online, after fuel and after all expenses (0.18.0). */
    REAL_PER_HOUR("real_per_hour"),

    /** A few lines from the detail cards with their change, and "See all" for the full cards (0.21.0). */
    MONTH_DETAIL("month_detail"),
    ;

    companion object {
        /**
         * What Home shows, in order (owner, 16 Oct 2026: a short Home). The other cards are on the Details page, and
         * Customise can bring any of them back.
         */
        val DEFAULT: List<HomeWidget> = listOf(WEEK_SCREENSHOTS, WEEK_PAYOUT, MONTH_DETAIL)

        /** The full cards behind "See all", in order. */
        val DETAILS: List<HomeWidget> = listOf(
            BREAKDOWN, COMPARISON, METRICS, REAL_PER_HOUR, SPLIT, CASH_CARD, ACTIVITY, EXPENSE_GROUPS, WHEN_YOU_EARN,
        )

        /** The cards the driver can move, add or remove in Customise. */
        val MOVABLE: List<HomeWidget> get() = entries - NEEDS_ATTENTION - TARGET

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
            // A layout saved before the short Home switches to it once; saving a layout again keeps the driver's own.
            if (MONTH_DETAIL.id !in known) return DEFAULT
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
