package gnosis.keyboard.engine.delight

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind

data class GnosisEngineCandidate(
    val word: String,
    val score: Double,
    val spatialScore: Double = 0.0,
    val lmScore: Double = 0.0,
    val userScore: Double = 0.0,
    val kind: CandidateKind = CandidateKind.COMPLETION,
    val source: String = "gnosis_engine",
    val isAutoCorrect: Boolean = false
) {
    fun toCandidate(): Candidate {
        return Candidate(
            word = word,
            score = score,
            kind = kind
        )
    }
}

typealias DelightCandidate = GnosisEngineCandidate
