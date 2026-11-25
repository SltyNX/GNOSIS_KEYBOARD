package gnosis.keyboard.suggest

enum class GnosisCandidateType {
    RAW,
    AUTO_CORRECT,
    PREDICTION,
    EXPANSION,
    P13N,
    EMOJI
}

typealias GboardCandidateType = GnosisCandidateType

data class GnosisCandidate(
    val text: String,
    val type: GnosisCandidateType,
    val score: Double,
    val source: String = "",
    val rawTyped: String? = null
) {
    val isAutoCorrect: Boolean get() = type == GnosisCandidateType.AUTO_CORRECT || type == GnosisCandidateType.EXPANSION
    val isRaw: Boolean get() = type == GnosisCandidateType.RAW
    val isPrediction: Boolean get() = type == GnosisCandidateType.PREDICTION
    val isExpansion: Boolean get() = type == GnosisCandidateType.EXPANSION
    val isEmoji: Boolean get() = type == GnosisCandidateType.EMOJI
}

typealias GboardCandidate = GnosisCandidate
