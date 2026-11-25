package gnosis.keyboard.text

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.prediction.CompactNgramModel
import gnosis.keyboard.suggest.GnosisNgramPredictor

class NextWordPredictor(
    ngramModel: CompactNgramModel?,
    userLexicon: UserLexicon
) {

    private val delegate = GnosisNgramPredictor(ngramModel, userLexicon)

    fun predictNextWords(context: List<String>, limit: Int = 3): List<Candidate> {
        return delegate.predictNextWords(context, limit).map {
            Candidate(
                word = it.text,
                score = it.score,
                kind = CandidateKind.PREDICTION
            )
        }
    }
}
