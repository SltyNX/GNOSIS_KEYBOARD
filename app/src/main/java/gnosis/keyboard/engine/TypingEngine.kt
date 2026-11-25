package gnosis.keyboard.engine

interface TypingEngine {
    fun onInputUpdate(
        typedPrefix: String,
        context: List<String>
    ): TypingResult

    fun onWordCommitted(word: String, context: List<String>)

    fun checkGrammar(text: String): List<GrammarIssue>
}

data class TypingResult(
    val suggestions: List<Candidate>,
    val autoCorrect: Candidate?,
    val spellIssues: List<SpellIssue>
)

data class Candidate(
    val word: String,
    val score: Double,
    val kind: CandidateKind
)

enum class CandidateKind {
    COMPLETION,
    CORRECTION,
    PREDICTION,
    CONTRACTION,
    USER_LEARNED,
    EXPANSION
}

data class SpellIssue(
    val offset: Int,
    val length: Int,
    val message: String,
    val suggestions: List<String>
)

data class GrammarIssue(
    val start: Int,
    val end: Int,
    val message: String,
    val suggestions: List<String>,
    val ruleId: String
)
