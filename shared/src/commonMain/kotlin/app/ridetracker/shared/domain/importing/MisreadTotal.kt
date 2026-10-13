package app.ridetracker.shared.domain.importing

import kotlin.math.abs

/**
 * A report's total that text recognition read with extra digits: "lei 161.40" came through as 161,400 (owner,
 * 13 Oct 2026) while its own lines added up to 161.40. When the lines check out, they are the better number.
 */
object MisreadTotal {
    /**
     * True when [totalMinor] is far bigger than [linesMinor] (10 times or more) and starts with the same digits,
     * or is 100 times bigger or more: a misread total, not a real difference.
     */
    fun isMisread(totalMinor: Long, linesMinor: Long): Boolean {
        if (linesMinor <= 0 || totalMinor == linesMinor) return false
        val total = abs(totalMinor)
        if (total < linesMinor * 10) return false
        return total >= linesMinor * 100 || total.toString().startsWith(linesMinor.toString().trimEnd('0'))
    }
}
