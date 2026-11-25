package gnosis.keyboard.text

import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.GrammarIssue
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.TypingResult
import gnosis.keyboard.engine.data.InMemoryUserLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SuggestionEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var userDictRepo: UserDictionaryRepository
    private lateinit var userLexicon: InMemoryUserLexicon
    private lateinit var nextWordPredictor: NextWordPredictor
    private lateinit var fakeTypingEngine: FakeTypingEngine
    private lateinit var suggestionEngine: SuggestionEngine

    class FakeTypingEngine : TypingEngine {
        override fun onInputUpdate(typedPrefix: String, context: List<String>): TypingResult {
            val candidates = when (typedPrefix) {
                "tomorr" -> listOf(
                    Candidate("tomorrow", 0.92, CandidateKind.COMPLETION),
                    Candidate("tomorrows", 0.70, CandidateKind.COMPLETION)
                )
                "teh" -> listOf(
                    Candidate("the", 0.95, CandidateKind.CORRECTION),
                    Candidate("teh", 0.50, CandidateKind.COMPLETION)
                )
                else -> listOf(
                    Candidate(typedPrefix, 0.50, CandidateKind.COMPLETION)
                )
            }
            val autoCorrect = candidates.firstOrNull { it.kind == CandidateKind.CORRECTION }
            return TypingResult(candidates, autoCorrect, emptyList())
        }

        override fun onWordCommitted(word: String, context: List<String>) {}

        override fun checkGrammar(text: String): List<GrammarIssue> = emptyList()
    }

    @Before
    fun setUp() {
        userDictRepo = UserDictionaryRepository(tempFolder.root)
        userLexicon = InMemoryUserLexicon(context = null)
        nextWordPredictor = NextWordPredictor(ngramModel = null, userLexicon = userLexicon)
        fakeTypingEngine = FakeTypingEngine()
        suggestionEngine = SuggestionEngine(fakeTypingEngine, nextWordPredictor, userDictRepo)
    }

    @Test
    fun testEmptyPrefixReturnsEmptySuggestions() {
        val emptyCtxResult = suggestionEngine.generateSuggestions("", emptyList())
        assertNull(emptyCtxResult.autoCorrectCandidate)
        assertTrue(emptyCtxResult.emojiSuggestions.isEmpty())
        assertTrue(emptyCtxResult.words.isEmpty())

        val emptyWithContext = suggestionEngine.generateSuggestions("", listOf("how", "are"))
        assertTrue(emptyWithContext.words.isEmpty())
    }

    @Test
    fun testNextWordPredictorBigramsAndHinglish() {
        val engResult = nextWordPredictor.predictNextWords(listOf("thank", "you"))
        assertTrue(engResult.any { it.word.equals("so", ignoreCase = true) || it.word.equals("very", ignoreCase = true) })

        val hinglishResult = nextWordPredictor.predictNextWords(listOf("kya"))
        assertTrue(hinglishResult.any { it.word.equals("hai", ignoreCase = true) || it.word.equals("kar", ignoreCase = true) })
    }

    @Test
    fun testShortcutExpansionGeneratesTopCorrectionCandidate() {
        val result = suggestionEngine.generateSuggestions("omw", emptyList())
        assertEquals("on my way", result.shortcutExpansion)
        assertNotNull(result.autoCorrectCandidate)
        assertEquals("on my way", result.autoCorrectCandidate?.word)
        assertEquals(CandidateKind.CORRECTION, result.autoCorrectCandidate?.kind)

        assertTrue(result.words.any { it.word == "on my way" })
    }

    @Test
    fun testEmojiSuggestionsReturnedWhenKeywordMatches() {
        val fireResult = suggestionEngine.generateSuggestions("fire", emptyList())
        assertTrue(fireResult.emojiSuggestions.contains("🔥"))

        val coffeeResult = suggestionEngine.generateSuggestions("coffee", emptyList())
        assertTrue(coffeeResult.emojiSuggestions.contains("☕"))
    }

    @Test
    fun testCustomUserWordBoostedToCandidates() {
        userDictRepo.addWord("gnosistech")

        val result = suggestionEngine.generateSuggestions("gnosis", emptyList())
        val found = result.words.find { it.word == "gnosistech" }
        assertNotNull(found)
        assertEquals(CandidateKind.USER_LEARNED, found?.kind)
    }

    @Test
    fun testTypoCorrectionSlotFormatting() {
        val result = suggestionEngine.generateSuggestions("teh", emptyList())
        assertNotNull(result.autoCorrectCandidate)
        assertEquals("the", result.autoCorrectCandidate?.word)

        assertTrue(result.words.any { it.word == "the" })
        assertTrue(result.words.any { it.word == "teh" })
    }
}
