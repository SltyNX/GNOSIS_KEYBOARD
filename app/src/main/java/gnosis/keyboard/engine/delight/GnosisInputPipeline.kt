package gnosis.keyboard.engine.delight

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.GrammarIssue
import gnosis.keyboard.engine.SpellIssue
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.TypingResult
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.grammar.GrammarChecker
import gnosis.keyboard.engine.spell.SpellChecker
import java.util.Locale

class GnosisInputPipeline(
    private val beamDecoder: GnosisBeamDecoder,
    private val userLexicon: UserLexicon,
    private val spellChecker: SpellChecker,
    private val grammarChecker: GrammarChecker,
    val autoCorrectionPipeline: GnosisAutoCorrectionPipeline = GnosisAutoCorrectionPipeline()
) : TypingEngine {

    override fun onInputUpdate(
        typedPrefix: String,
        context: List<String>
    ): TypingResult {
        val clean = typedPrefix.trim()

        if (clean.isEmpty()) {
            return TypingResult(
                suggestions = emptyList(),
                autoCorrect = null,
                spellIssues = emptyList()
            )
        }

        val decodeResult = beamDecoder.decode(clean, context, limit = 5)

        val autoCorrect = decodeResult.autoCorrectCandidate?.let { cand ->
            if (autoCorrectionPipeline.shouldAutoCorrect(cand, clean)) {
                cand.toCandidate()
            } else null
        }

        val spellIssues = emptyList<SpellIssue>()

        return TypingResult(
            suggestions = decodeResult.candidates.map { it.toCandidate() },
            autoCorrect = autoCorrect,
            spellIssues = spellIssues
        )
    }

    override fun onWordCommitted(word: String, context: List<String>) {
        val clean = word.trim()
        if (clean.isNotEmpty()) {
            userLexicon.recordWord(clean)
            if (context.isNotEmpty()) {
                val lastContextWord = context.last().trim()
                if (lastContextWord.isNotEmpty()) {
                    userLexicon.recordTransition(lastContextWord, clean)
                }
            }
        }
    }

    override fun checkGrammar(text: String): List<GrammarIssue> {
        return grammarChecker.check(text)
    }

    fun get3SlotCandidates(
        typedPrefix: String,
        context: List<String>
    ): Triple<Candidate?, Candidate?, Candidate?> {
        val res = onInputUpdate(typedPrefix, context)
        val clean = typedPrefix.trim()

        val center = res.autoCorrect ?: res.suggestions.firstOrNull()
        val others = res.suggestions.filter { it.word != center?.word }

        val left = if (center != null && !clean.equals(center.word, ignoreCase = true)) {
            Candidate(clean, 0.50, gnosis.keyboard.engine.CandidateKind.COMPLETION)
        } else {
            others.getOrNull(0)
        }

        val right = if (left != null) {
            others.firstOrNull { it.word != left.word }
        } else {
            others.getOrNull(1)
        }

        return Triple(left, center, right)
    }
}

typealias Delight5InputPipeline = GnosisInputPipeline
