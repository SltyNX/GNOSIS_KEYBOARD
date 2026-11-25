package gnosis.keyboard.engine.delight

import java.util.Locale

class GnosisAutoCorrectionPipeline {

    data class RevertRecord(
        val original: String,
        val corrected: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        const val REVERT_WINDOW_MS = 6000L
        const val MIN_CORRECTION_PREFIX = 2
    }

    private var lastRecord: RevertRecord? = null
    private val suppressedWords = HashSet<String>()

    fun shouldAutoCorrect(
        candidate: GnosisEngineCandidate?,
        typedPrefix: String,
        isPasswordField: Boolean = false,
        isUrlOrEmail: Boolean = false
    ): Boolean {
        if (candidate == null) return false
        if (isPasswordField || isUrlOrEmail) return false

        val clean = typedPrefix.trim()
        if (clean.isEmpty() || clean.length > 30) return false

        if (clean.any { it.isDigit() }) return false

        if (clean.any { !it.isLetter() && it != '\'' }) return false

        val lowerTyped = clean.lowercase(Locale.ROOT)
        val lowerCandidate = candidate.word.lowercase(Locale.ROOT)

        if (lowerTyped == lowerCandidate) return false

        if (isSuppressed(lowerTyped)) return false

        if (clean.length == 1) {
            return (clean == "i" || clean == "I") && candidate.word == "I"
        }

        if (candidate.word.contains('\'') && !clean.contains('\'')) {
            if (candidate.score >= 0.70) return true
        }

        if (candidate.isAutoCorrect || candidate.source == "typo" || candidate.source == "contraction" || candidate.source == "shortcut" || candidate.source == "grammar") {
            return candidate.score >= 0.70
        }

        if (candidate.score >= 0.85 && clean.length >= MIN_CORRECTION_PREFIX) {
            return true
        }

        return false
    }

    fun recordCorrection(original: String, corrected: String) {
        lastRecord = RevertRecord(original, corrected)
    }

    fun canRevert(): Boolean {
        val record = lastRecord ?: return false
        return (System.currentTimeMillis() - record.timestamp) < REVERT_WINDOW_MS
    }

    fun consumeRevert(): RevertRecord? {
        val record = lastRecord ?: return null
        if ((System.currentTimeMillis() - record.timestamp) >= REVERT_WINDOW_MS) {
            lastRecord = null
            return null
        }
        suppressedWords.add(record.original.lowercase(Locale.ROOT))
        lastRecord = null
        return record
    }

    fun isSuppressed(word: String): Boolean {
        return suppressedWords.contains(word.lowercase(Locale.ROOT))
    }

    fun suppressWord(word: String) {
        suppressedWords.add(word.lowercase(Locale.ROOT))
    }

    fun clearSuppressed() {
        suppressedWords.clear()
        lastRecord = null
    }
}

typealias DelightAutoCorrectionPipeline = GnosisAutoCorrectionPipeline
