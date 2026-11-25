package gnosis.keyboard.engine.grammar_and_spellcheck.spellchecker

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import gnosis.keyboard.WordDictionary

/**
 * Gnosis_Engine - Spell Checker Service
 *
 * Implements Android system SpellCheckerService contract backed by Gnosis_Engine vocabulary.
 */
class GnosisEngineSpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session {
        return GnosisSpellCheckerSession()
    }

    private inner class GnosisSpellCheckerSession : Session() {
        override fun onCreate() {}

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val text = textInfo?.text ?: return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, emptyArray())
            val common = WordDictionary.getCommonWords(applicationContext)

            val lower = text.lowercase()
            if (common.contains(lower)) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            val suggestions = common
                .filter { it.startsWith(lower.take(2)) }
                .take(suggestionsLimit.coerceAtLeast(1))
                .toTypedArray()

            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, suggestions)
        }
    }
}
