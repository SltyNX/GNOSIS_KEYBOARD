package gnosis.keyboard.engine

import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.grammar.GrammarChecker
import gnosis.keyboard.engine.prediction.BeamDecoder
import gnosis.keyboard.engine.spell.SpellChecker

class TypingEngineImpl(
    private val beamDecoder: BeamDecoder,
    private val userLexicon: UserLexicon,
    private val spellChecker: SpellChecker,
    private val grammarChecker: GrammarChecker
) : TypingEngine {

    override fun onInputUpdate(
        typedPrefix: String,
        context: List<String>
    ): TypingResult {
        val (suggestions, autoCorrect) = beamDecoder.decode(typedPrefix, context, limit = 5)

        val spellIssues = if (typedPrefix.isNotBlank() && !spellChecker.isCorrect(typedPrefix)) {
            spellChecker.checkSpelling(typedPrefix)
        } else {
            emptyList()
        }

        return TypingResult(
            suggestions = suggestions,
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
}
