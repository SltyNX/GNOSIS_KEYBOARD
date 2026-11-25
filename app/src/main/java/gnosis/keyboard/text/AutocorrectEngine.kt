package gnosis.keyboard.text

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.suggest.GnosisAutoCorrectionEngine
import gnosis.keyboard.suggest.GnosisCandidate
import gnosis.keyboard.suggest.GnosisCandidateType

class AutocorrectEngine {

    private val delegate = GnosisAutoCorrectionEngine()

    data class AutoCorrectRecord(
        val original: String,
        val corrected: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun shouldAutoCorrect(
        candidate: Candidate?,
        typedWord: String,
        isPasswordField: Boolean,
        isUrlOrEmail: Boolean
    ): Boolean {
        if (candidate == null) return false
        val gCandidate = GnosisCandidate(
            text = candidate.word,
            type = when (candidate.kind) {
                CandidateKind.EXPANSION -> GnosisCandidateType.EXPANSION
                CandidateKind.CORRECTION, CandidateKind.CONTRACTION -> GnosisCandidateType.AUTO_CORRECT
                CandidateKind.PREDICTION -> GnosisCandidateType.PREDICTION
                CandidateKind.USER_LEARNED -> GnosisCandidateType.P13N
                else -> GnosisCandidateType.RAW
            },
            score = candidate.score
        )
        return delegate.shouldAutoCorrect(gCandidate, typedWord, isPasswordField, isUrlOrEmail)
    }

    fun recordCorrection(original: String, corrected: String) {
        delegate.recordCorrection(original, corrected)
    }

    fun canUndo(): Boolean = delegate.canUndo()

    fun consumeUndo(): AutoCorrectRecord? {
        val r = delegate.consumeUndo() ?: return null
        return AutoCorrectRecord(r.original, r.corrected, r.timestamp)
    }

    fun suppressWord(word: String) {
        delegate.suppressWord(word)
    }

    fun clearSuppression() {
        delegate.clearSuppression()
    }

    fun clear() {
        delegate.clear()
    }
}
