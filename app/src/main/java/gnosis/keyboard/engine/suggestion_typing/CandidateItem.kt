package gnosis.keyboard.engine.suggestion_typing

enum class CandidateType {
    AUTO_CORRECT,
    COMPLETION,
    PREDICTION,
    LITERAL,
    EMOJI,
    GRAMMAR
}

data class CandidateItem(
    val text: String,
    val type: CandidateType = CandidateType.LITERAL,
    val confidence: Float = 1.0f
)
