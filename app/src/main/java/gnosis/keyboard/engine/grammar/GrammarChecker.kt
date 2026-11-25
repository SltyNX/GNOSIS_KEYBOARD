package gnosis.keyboard.engine.grammar

import gnosis.keyboard.engine.GrammarIssue

interface GrammarChecker {
    fun check(text: String): List<GrammarIssue>
}
