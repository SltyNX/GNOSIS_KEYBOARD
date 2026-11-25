package gnosis.keyboard.engine.prediction

import org.junit.Assert.assertTrue
import org.junit.Test

class TrieFuzzyCorrectionTest {

    @Test
    fun findsAdjacentTranspositionsWithoutWordSpecificRules() {
        val trie = Trie()
        listOf("play", "receive", "keyboard", "android", "because", "tomorrow").forEachIndexed { index, word ->
            trie.insert(word, frequency = 100 - index)
        }

        assertTrue(trie.searchFuzzy("paly", maxCost = 1.2, limit = 10).any { it.word == "play" })
        assertTrue(trie.searchFuzzy("recieve", maxCost = 1.8, limit = 10).any { it.word == "receive" })
        assertTrue(trie.searchFuzzy("keybaord", maxCost = 1.8, limit = 10).any { it.word == "keyboard" })
    }

    @Test
    fun findsMultipleTranspositionsWhenCandidateIsInLexicon() {
        val trie = Trie()
        trie.insert("playboard", frequency = 20)

        val matches = trie.searchFuzzy("palybraod", maxCost = 3.0, limit = 10)
        assertTrue(matches.any { it.word == "playboard" && it.cost <= 3.0 })
    }
}
