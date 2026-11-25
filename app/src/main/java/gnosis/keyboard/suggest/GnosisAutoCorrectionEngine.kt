package gnosis.keyboard.suggest

import java.util.Locale

class GnosisAutoCorrectionEngine {

    data class AutoCorrectionRecord(
        val original: String,
        val corrected: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private var lastRecord: AutoCorrectionRecord? = null
    private val suppressedWords = mutableSetOf<String>()

    fun shouldAutoCorrect(
        candidate: GnosisCandidate?,
        typedWord: String,
        isPasswordField: Boolean,
        isUrlOrEmail: Boolean
    ): Boolean {
        if (candidate == null) return false
        if (isPasswordField || isUrlOrEmail) return false

        val clean = typedWord.trim()
        if (clean.isEmpty() || clean.length > 25) return false

        if (clean.any { it.isDigit() }) return false

        if (clean.any { !it.isLetter() && it != '\'' }) return false

        if (clean.equals(candidate.text, ignoreCase = true)) return false

        if (isSuppressed(clean)) return false

        if (candidate.type == GnosisCandidateType.EXPANSION) return true

        if (clean.length == 1) {
            return (clean == "i" || clean == "I") && candidate.text == "I"
        }

        val contractionThreshold = 0.65
        val typoThreshold = 0.70
        val completionThreshold = 0.85
        val minPrefixLen = 3

        if (candidate.type == GnosisCandidateType.AUTO_CORRECT && candidate.text.contains('\'')) {
            return candidate.score >= contractionThreshold
        }

        if (candidate.type == GnosisCandidateType.EXPANSION) return true
        if (candidate.type == GnosisCandidateType.AUTO_CORRECT || candidate.source == "typo") {
            return candidate.score >= typoThreshold
        }

        if (candidate.score >= completionThreshold && clean.length >= minPrefixLen) {
            return true
        }

        return false
    }

    fun recordCorrection(original: String, corrected: String) {
        lastRecord = AutoCorrectionRecord(original, corrected)
    }

    fun canUndo(): Boolean {
        val record = lastRecord ?: return false
        return (System.currentTimeMillis() - record.timestamp) < REVERT_WINDOW_MS
    }

    fun consumeUndo(): AutoCorrectionRecord? {
        val record = lastRecord ?: return null
        lastRecord = null
        val isValid = (System.currentTimeMillis() - record.timestamp) < REVERT_WINDOW_MS
        if (isValid) {
            suppressWord(record.original)
            return record
        }
        return null
    }

    fun suppressWord(word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.isNotEmpty()) {
            suppressedWords.add(clean)
        }
    }

    fun isSuppressed(word: String): Boolean {
        return suppressedWords.contains(word.trim().lowercase(Locale.ROOT))
    }

    fun clearSuppression() {
        suppressedWords.clear()
    }

    fun clear() {
        lastRecord = null
        suppressedWords.clear()
    }

    companion object {
        private const val REVERT_WINDOW_MS = 6000L
    }
}

typealias GboardAutoCorrectionEngine = GnosisAutoCorrectionEngine
