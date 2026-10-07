package app.ridetracker.shared.domain

/** Conversions between user-typed amounts and minor units (e.g. cents). */
object Money {

    /**
     * Parses a user-typed amount such as "120", "120.5" or "120,50" into minor units.
     * Returns null for empty, negative, malformed input or too many decimals.
     */
    fun parseToMinor(input: String, fractionDigits: Int): Long? {
        val text = input.trim().replace(" ", "").replace(',', '.')
        if (text.isEmpty() || !text.matches(Regex("""\d+(\.\d*)?|\.\d+"""))) return null
        val whole = text.substringBefore('.').ifEmpty { "0" }
        val fraction = text.substringAfter('.', "")
        if (fraction.length > fractionDigits) return null
        val wholeValue = whole.toLongOrNull() ?: return null
        val scale = pow10(fractionDigits)
        if (wholeValue > Long.MAX_VALUE / scale) return null
        val fractionValue = fraction.padEnd(fractionDigits, '0').ifEmpty { "0" }.toLong()
        return wholeValue * scale + fractionValue
    }

    /** Plain editable text for an amount, e.g. 12050 with 2 digits -> "120.50". */
    fun toPlainString(minor: Long, fractionDigits: Int): String {
        if (fractionDigits == 0) return minor.toString()
        val scale = pow10(fractionDigits)
        val fraction = (minor % scale).toString().padStart(fractionDigits, '0')
        return "${minor / scale}.$fraction"
    }

    /** Like [toPlainString] but keeps the sign, e.g. -2500 with 2 digits -> "-25.00". */
    fun toSignedString(minor: Long, fractionDigits: Int): String =
        if (minor < 0) "-" + toPlainString(-minor, fractionDigits) else toPlainString(minor, fractionDigits)

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
