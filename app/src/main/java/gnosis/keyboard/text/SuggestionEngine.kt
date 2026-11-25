package gnosis.keyboard.text

import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.prediction.CompactNgramModel
import gnosis.keyboard.suggest.GnosisCandidateType
import gnosis.keyboard.suggest.GnosisNgramPredictor
import gnosis.keyboard.suggest.GnosisSuggestionEngine

data class SuggestionResult(
    val words: List<Candidate>,
    val autoCorrectCandidate: Candidate?,
    val emojiSuggestions: List<String>,
    val shortcutExpansion: String? = null
)

class SuggestionEngine(
    private val typingEngine: TypingEngine,
    private val nextWordPredictor: NextWordPredictor,
    private val userDictionary: UserDictionaryRepository,
    ngramModel: CompactNgramModel? = null,
    userLexicon: UserLexicon? = null,
    private val context: android.content.Context? = null
) {

    private val gnosisPredictor = GnosisNgramPredictor(
        ngramModel = ngramModel,
        userLexicon = userLexicon ?: gnosis.keyboard.engine.data.InMemoryUserLexicon(null)
    )

    private val gnosisEngine = GnosisSuggestionEngine(
        typingEngine = typingEngine,
        ngramPredictor = gnosisPredictor,
        userDictionary = userDictionary,
        context = context
    )

    fun generateSuggestions(
        typedPrefix: String,
        contextWords: List<String>,
        isPasswordField: Boolean = false,
        isUrlOrEmail: Boolean = false,
        language: String = "en"
    ): SuggestionResult {
        val clean = typedPrefix.trim()

        val gnosisResult = gnosisEngine.generateSuggestions(clean, contextWords, isPasswordField, isUrlOrEmail, language = language)

        val words = gnosisResult.layout.toList().map {
            Candidate(
                word = it.text,
                score = it.score,
                kind = when (it.type) {
                    GnosisCandidateType.AUTO_CORRECT -> CandidateKind.CORRECTION
                    GnosisCandidateType.EXPANSION -> CandidateKind.EXPANSION
                    GnosisCandidateType.P13N -> CandidateKind.USER_LEARNED
                    GnosisCandidateType.PREDICTION -> CandidateKind.PREDICTION
                    else -> CandidateKind.COMPLETION
                }
            )
        }

        val autoCorrect = gnosisResult.autoCorrectCandidate?.let {
            Candidate(
                word = it.text,
                score = it.score,
                kind = when (it.type) {
                    GnosisCandidateType.EXPANSION -> CandidateKind.EXPANSION
                    GnosisCandidateType.P13N -> CandidateKind.USER_LEARNED
                    else -> CandidateKind.CORRECTION
                }
            )
        }

        return SuggestionResult(
            words = words,
            autoCorrectCandidate = autoCorrect,
            emojiSuggestions = gnosisResult.emojiSuggestions,
            shortcutExpansion = gnosisResult.shortcutExpansion
        )
    }

    fun predictZeroState(contextWords: List<String>): List<Candidate> {
        return gnosisPredictor.predictNextWords(contextWords, limit = 3).map {
            Candidate(
                word = it.text,
                score = it.score,
                kind = CandidateKind.PREDICTION
            )
        }
    }
}
