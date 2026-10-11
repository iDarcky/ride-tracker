package app.ridetracker.shared.domain.importing

/** Why a file couldn't be read, so the Import screen can say what to do instead of just "not recognised". */
enum class UnrecognisedReason {
    /** Hardly any text: a photo, a blank or a dark screenshot. */
    NO_TEXT,

    /** Uber's Earnings screen without the week at the top ("Oct 5 - Oct 12"). */
    UBER_NO_WEEK,

    /** An Uber screen Ride Tracker reads, but the part with the total is cut off. */
    UBER_CROPPED,

    /** Another Uber screen (Home, a trip, Uber Pro…). */
    UBER_OTHER_SCREEN,

    /** Bolt's earnings breakdown without the date or period above it. */
    BOLT_NO_DATE,

    /** Bolt's earnings breakdown cut off before the commission and the total. */
    BOLT_CROPPED,

    /** Another Bolt screen. */
    BOLT_OTHER_SCREEN,

    /** A picture that isn't from Bolt or Uber. */
    OTHER_IMAGE,

    /** A PDF that isn't Bolt's monthly summary. */
    PDF_NOT_BOLT_MONTHLY,

    /** One of Uber's files whose period is only in the name Uber gave it, renamed since. */
    UBER_RENAMED,

    /** A spreadsheet that isn't Bolt's rider invoices or one of Uber's files. */
    CSV_UNKNOWN,
}

object ScreenshotDiagnosis {

    /** Why a screenshot's text ([rows], top to bottom) wasn't recognised as any screen Ride Tracker reads. */
    fun reason(rows: List<String>): UnrecognisedReason {
        val plain = rows.map(ReportText::plain)
        if (plain.sumOf { row -> row.split(' ').count { it.isNotBlank() } } < 5) return UnrecognisedReason.NO_TEXT
        if (UberEarningsParser.recognises(rows)) {
            val hasWeek = rows.any { UberWeekLabel.parse(it, kotlinx.datetime.LocalDate(2026, 1, 1)) != null }
            return if (hasWeek) UnrecognisedReason.UBER_CROPPED else UnrecognisedReason.UBER_NO_WEEK
        }
        if (UberPaymentsParser.recognises(rows)) return UnrecognisedReason.UBER_CROPPED
        // Bolt's titles start their row; a sentence that merely mentions "Earnings breakdown" isn't the screen.
        val breakdown = plain.any { row -> BREAKDOWN_TITLES.any { row.startsWith(it) } }
        if (breakdown) {
            val commission = plain.any { "commission" in it || "comision" in it }
            return if (commission) UnrecognisedReason.BOLT_NO_DATE else UnrecognisedReason.BOLT_CROPPED
        }
        // The app's name first, then its usual words (whole words: "requests" isn't "quest").
        val words = plain.flatMap { it.split(Regex("[^a-z]+")) }.toSet()
        if ("uber" in words) return UnrecognisedReason.UBER_OTHER_SCREEN
        if ("bolt" in words) return UnrecognisedReason.BOLT_OTHER_SCREEN
        if (UBER_WORDS.count { it in words } >= 2) return UnrecognisedReason.UBER_OTHER_SCREEN
        if (BOLT_WORDS.count { it in words } >= 2) return UnrecognisedReason.BOLT_OTHER_SCREEN
        return UnrecognisedReason.OTHER_IMAGE
    }

    private val BREAKDOWN_TITLES = listOf("earnings breakdown", "your earnings", "defalcarea", "castigurile tale")
    private val UBER_WORDS = listOf("online", "points", "quest", "trips", "payments", "boost")
    private val BOLT_WORDS = listOf("earnings", "castiguri", "activity", "activitate", "numerar", "acceptance", "rides", "curse")
}
