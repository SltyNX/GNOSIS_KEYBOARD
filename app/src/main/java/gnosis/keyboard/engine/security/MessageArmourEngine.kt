package gnosis.keyboard.engine.security

import java.util.regex.Pattern

object MessageArmourEngine {

    enum class SensitiveType {
        CREDIT_CARD,
        OTP_CODE,
        PRIVATE_KEY_OR_TOKEN
    }

    data class SensitiveMatch(
        val type: SensitiveType,
        val maskedValue: String,
        val description: String
    )

    private val CC_PATTERN = Pattern.compile("""\b(?:\d[ -]*?){13,19}\b""")
    private val OTP_PATTERN = Pattern.compile("""(?i)\b(?:otp|code|verification|pin)\b[:\s]*([0-9]{4,8})\b""")
    private val API_KEY_PATTERN = Pattern.compile("""\b(?:sk_[a-zA-Z0-9]{24,}|ghp_[a-zA-Z0-9]{36}|AIza[0-9A-Za-z-_]{35})\b""")

    private fun passesLuhnCheck(numberStr: String): Boolean {
        val digits = numberStr.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    fun scan(text: String): List<SensitiveMatch> {
        val matches = mutableListOf<SensitiveMatch>()
        if (text.isBlank()) return matches

        val ccMatcher = CC_PATTERN.matcher(text)
        while (ccMatcher.find()) {
            val raw = ccMatcher.group()
            val digits = raw.filter { it.isDigit() }
            if (passesLuhnCheck(digits)) {
                val masked = if (digits.length >= 8) {
                    digits.take(4) + " **** **** " + digits.takeLast(4)
                } else "****"
                matches.add(SensitiveMatch(SensitiveType.CREDIT_CARD, masked, "Credit card number detected"))
            }
        }

        val otpMatcher = OTP_PATTERN.matcher(text)
        while (otpMatcher.find()) {
            val code = otpMatcher.group(1) ?: ""
            if (code.length in 4..8) {
                matches.add(SensitiveMatch(SensitiveType.OTP_CODE, "******", "One-Time Password (OTP) detected"))
            }
        }

        val tokenMatcher = API_KEY_PATTERN.matcher(text)
        while (tokenMatcher.find()) {
            matches.add(SensitiveMatch(SensitiveType.PRIVATE_KEY_OR_TOKEN, "sk_...***", "API Secret key / Token detected"))
        }

        return matches
    }
}
