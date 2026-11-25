package gnosis.keyboard.engine.security

import java.util.regex.Pattern

object MessageArmourEngine {

    enum class SensitiveType {
        CREDIT_CARD,
        OTP_CODE,
        PRIVATE_KEY_OR_TOKEN,
        PASSWORD_OR_CREDENTIAL
    }

    data class SensitiveMatch(
        val type: SensitiveType,
        val maskedValue: String,
        val description: String
    )

    private val CC_PATTERN = Pattern.compile("""\b(?:\d[ -]*?){13,19}\b""")
    private val OTP_LABELED_PATTERN = Pattern.compile("""(?i)\b(?:otp|code|verification|verify|pin|passcode|secret)\b[:\s#-]*([0-9]{4,8})\b""")
    private val OTP_STANDALONE_PATTERN = Pattern.compile("""^\s*([0-9]{4,8}|[0-9]{3}[- ][0-9]{3})\s*$""")
    private val OTP_PHRASE_PATTERN = Pattern.compile("""(?i)\b(?:is|use|enter)\s+([0-9]{4,8})\b""")
    private val API_KEY_PATTERN = Pattern.compile(
        """\b(?:sk_[a-zA-Z0-9_\-]{16,}|ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{22,}|AIza[0-9A-Za-z-_]{35}|AKIA[0-9A-Z]{16}|Bearer\s+[a-zA-Z0-9_\-\.]{20,}|eyJ[a-zA-Z0-9_\-]{10,}\.[a-zA-Z0-9_\-]{10,}\.[a-zA-Z0-9_\-]{10,})\b"""
    )
    private val PRIVATE_KEY_PATTERN = Pattern.compile("""-----BEGIN [A-Z ]*PRIVATE KEY-----""")
    private val PASSWORD_PATTERN = Pattern.compile("""(?i)\b(?:password|passwd|pwd|pass)\b[:=\s]+([^\s,;]+)""")

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

    fun isSensitive(text: String): Boolean = scan(text).isNotEmpty()

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

        val otpLabeledMatcher = OTP_LABELED_PATTERN.matcher(text)
        while (otpLabeledMatcher.find()) {
            val code = otpLabeledMatcher.group(1) ?: ""
            if (code.length in 4..8) {
                matches.add(SensitiveMatch(SensitiveType.OTP_CODE, "******", "One-Time Password (OTP) detected"))
            }
        }

        if (matches.isEmpty() && OTP_STANDALONE_PATTERN.matcher(text).matches()) {
            matches.add(SensitiveMatch(SensitiveType.OTP_CODE, "******", "One-Time Password (OTP) detected"))
        }

        val otpPhraseMatcher = OTP_PHRASE_PATTERN.matcher(text)
        while (otpPhraseMatcher.find()) {
            val code = otpPhraseMatcher.group(1) ?: ""
            if (code.length in 4..8 && !matches.any { it.type == SensitiveType.OTP_CODE }) {
                matches.add(SensitiveMatch(SensitiveType.OTP_CODE, "******", "Verification code detected"))
            }
        }

        val tokenMatcher = API_KEY_PATTERN.matcher(text)
        while (tokenMatcher.find()) {
            val raw = tokenMatcher.group()
            val masked = if (raw.length > 8) raw.take(4) + "...***" else "***"
            matches.add(SensitiveMatch(SensitiveType.PRIVATE_KEY_OR_TOKEN, masked, "API Secret key / Token detected"))
        }

        if (PRIVATE_KEY_PATTERN.matcher(text).find()) {
            matches.add(SensitiveMatch(SensitiveType.PRIVATE_KEY_OR_TOKEN, "-----BEGIN PRIVATE KEY----- [MASKED]", "Private cryptographic key detected"))
        }

        val pwdMatcher = PASSWORD_PATTERN.matcher(text)
        while (pwdMatcher.find()) {
            matches.add(SensitiveMatch(SensitiveType.PASSWORD_OR_CREDENTIAL, "Password: ••••••••", "Account password detected"))
        }

        return matches
    }

    fun getMaskedSnippet(text: String): String {
        val matches = scan(text)
        if (matches.isEmpty()) return text
        val first = matches.first()
        return when (first.type) {
            SensitiveType.CREDIT_CARD -> "Card: ${first.maskedValue}"
            SensitiveType.OTP_CODE -> "OTP: ••••••"
            SensitiveType.PRIVATE_KEY_OR_TOKEN -> "Token: ${first.maskedValue}"
            SensitiveType.PASSWORD_OR_CREDENTIAL -> "Protected Credential: ••••••••"
        }
    }
}
