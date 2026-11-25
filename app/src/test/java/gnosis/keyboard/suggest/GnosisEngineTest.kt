package gnosis.keyboard.suggest

import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.GrammarIssue
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.TypingResult
import gnosis.keyboard.engine.data.InMemoryUserLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GnosisEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var userDictRepo: UserDictionaryRepository
    private lateinit var userLexicon: InMemoryUserLexicon
    private lateinit var ngramPredictor: GnosisNgramPredictor
    private lateinit var autoCorrectionEngine: GnosisAutoCorrectionEngine
    private lateinit var suggestionEngine: GnosisSuggestionEngine

    class FakeTypingEngine : TypingEngine {
        override fun onInputUpdate(typedPrefix: String, context: List<String>): TypingResult {
            val list = when (typedPrefix) {
                "hel" -> listOf(
                    Candidate("hello", 0.90, CandidateKind.COMPLETION),
                    Candidate("help", 0.85, CandidateKind.COMPLETION)
                )
                else -> listOf(
                    Candidate(typedPrefix, 0.50, CandidateKind.COMPLETION)
                )
            }
            return TypingResult(list, null, emptyList())
        }

        override fun onWordCommitted(word: String, context: List<String>) {}
        override fun checkGrammar(text: String): List<GrammarIssue> = emptyList()
    }

    @Before
    fun setUp() {
        userDictRepo = UserDictionaryRepository(tempFolder.root)
        userLexicon = InMemoryUserLexicon(context = null)
        ngramPredictor = GnosisNgramPredictor(ngramModel = null, userLexicon = userLexicon)
        autoCorrectionEngine = GnosisAutoCorrectionEngine()
        suggestionEngine = GnosisSuggestionEngine(
            typingEngine = FakeTypingEngine(),
            ngramPredictor = ngramPredictor,
            userDictionary = userDictRepo
        )
    }

    @Test
    fun testGnosisAutoCorrectionConstraints() {
        val cand = GnosisCandidate("the", GnosisCandidateType.AUTO_CORRECT, 0.95)

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(null, "teh", false, false))

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "teh", isPasswordField = true, isUrlOrEmail = false))

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "teh", isPasswordField = false, isUrlOrEmail = true))

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "", false, false))

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "teh2", false, false))

        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "the", false, false))
        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "THE", false, false))

        assertTrue(autoCorrectionEngine.shouldAutoCorrect(cand, "teh", false, false))
    }

    @Test
    fun testGnosisAutoCorrectionOneTapRevertAndSuppression() {
        assertFalse(autoCorrectionEngine.canUndo())
        assertNull(autoCorrectionEngine.consumeUndo())

        autoCorrectionEngine.recordCorrection("teh", "the")
        assertTrue(autoCorrectionEngine.canUndo())

        val record = autoCorrectionEngine.consumeUndo()
        assertNotNull(record)
        assertEquals("teh", record?.original)
        assertEquals("the", record?.corrected)

        assertTrue(autoCorrectionEngine.isSuppressed("teh"))
        val cand = GnosisCandidate("the", GnosisCandidateType.AUTO_CORRECT, 0.95)
        assertFalse(autoCorrectionEngine.shouldAutoCorrect(cand, "teh", false, false))

        autoCorrectionEngine.clear()
        assertFalse(autoCorrectionEngine.isSuppressed("teh"))
    }

    @Test
    fun testGnosisSpellingScorerAndCaseTransfer() {
        val typo = GnosisSpellingScorer.findCorrection("teh")
        assertNotNull(typo)
        assertEquals("the", typo?.first)

        val dont = GnosisSpellingScorer.findCorrection("dont")
        assertNotNull(dont)
        assertEquals("don't", dont?.first)

        val singleI = GnosisSpellingScorer.findCorrection("i")
        assertNotNull(singleI)
        assertEquals("I", singleI?.first)

        assertEquals("THE", GnosisSpellingScorer.applyCasing("TEH", "the"))
        assertEquals("The", GnosisSpellingScorer.applyCasing("Teh", "the"))
        assertEquals("the", GnosisSpellingScorer.applyCasing("teh", "the"))
        assertEquals("DON'T", GnosisSpellingScorer.applyCasing("DONT", "don't"))
        assertEquals("Don't", GnosisSpellingScorer.applyCasing("Dont", "don't"))
        assertEquals("I'm", GnosisSpellingScorer.applyCasing("im", "I'm"))
    }

    @Test
    fun testGnosisNgramZeroStatePredictions() {
        val howArePreds = ngramPredictor.predictNextWords(listOf("how", "are"))
        assertTrue(howArePreds.any { it.text.equals("you", ignoreCase = true) })

        val thankYouPreds = ngramPredictor.predictNextWords(listOf("thank", "you"))
        assertTrue(thankYouPreds.any { it.text.equals("so", ignoreCase = true) || it.text.equals("very", ignoreCase = true) })

        val hinglishPreds = ngramPredictor.predictNextWords(listOf("kya"))
        assertTrue(hinglishPreds.any { it.text.equals("hai", ignoreCase = true) || it.text.equals("kar", ignoreCase = true) })
    }

    @Test
    fun testGnosis3SlotLayoutGeneration() {
        val zeroState = suggestionEngine.generateSuggestions("", listOf("how", "are"))
        assertNotNull(zeroState.layout.slot0Left)
        assertNotNull(zeroState.layout.slot1Center)

        val typoResult = suggestionEngine.generateSuggestions("teh", emptyList())
        assertEquals("teh", typoResult.layout.slot0Left?.text)
        assertEquals("the", typoResult.layout.slot1Center.text)
        assertEquals(GnosisCandidateType.RAW, typoResult.layout.slot0Left?.type)
        assertEquals(GnosisCandidateType.AUTO_CORRECT, typoResult.layout.slot1Center.type)
        assertNotNull(typoResult.autoCorrectCandidate)
        assertEquals("the", typoResult.autoCorrectCandidate?.text)

        val scResult = suggestionEngine.generateSuggestions("omw", emptyList())
        assertEquals("on my way", scResult.layout.slot1Center.text)
        assertEquals(GnosisCandidateType.EXPANSION, scResult.layout.slot1Center.type)
    }
}
