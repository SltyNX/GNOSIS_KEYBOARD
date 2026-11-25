package gnosis.keyboard.engine.delight

import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.data.InMemoryUserLexicon
import gnosis.keyboard.engine.grammar.HarperBridge
import gnosis.keyboard.engine.prediction.Trie
import gnosis.keyboard.engine.spell.SpellChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GnosisPipelineTest {

    private lateinit var trie: Trie
    private lateinit var userLexicon: InMemoryUserLexicon
    private lateinit var spatialModel: GnosisSpatialModel
    private lateinit var beamDecoder: GnosisBeamDecoder
    private lateinit var pipeline: GnosisInputPipeline

    @Before
    fun setUp() {
        trie = Trie()
        listOf(
            "hello", "help", "held", "helicopter",
            "the", "there", "their", "they're",
            "world", "work", "word", "working",
            "quick", "brown", "fox", "jumps", "lazy", "dog"
        ).forEachIndexed { i, w -> trie.insert(w, 100 - i) }

        userLexicon = InMemoryUserLexicon(context = null)
        spatialModel = GnosisSpatialModel()

        beamDecoder = GnosisBeamDecoder(
            trie = trie,
            ngramModel = null,
            userLexicon = userLexicon,
            spatialModel = spatialModel
        )

        val spellChecker = SpellChecker(trie, isUserKnown = { userLexicon.isKnown(it) })
        val grammarChecker = HarperBridge()

        pipeline = GnosisInputPipeline(
            beamDecoder = beamDecoder,
            userLexicon = userLexicon,
            spellChecker = spellChecker,
            grammarChecker = grammarChecker
        )
    }

    @Test
    fun testSpatialProximityScores() {
        val weProximity = spatialModel.getCharProximityScore('w', 'e')
        assertTrue("Adjacent keys should have high proximity score", weProximity > 0.60)

        val qpProximity = spatialModel.getCharProximityScore('q', 'p')
        assertTrue("Distant keys should have low proximity score", qpProximity < 0.20)
    }

    @Test
    fun testContractionDecoding() {
        val result = pipeline.onInputUpdate("dont", emptyList())
        assertNotNull("Should have candidates", result.suggestions)
        assertTrue(result.suggestions.any { it.word.equals("don't", ignoreCase = true) })
        assertEquals("don't", result.autoCorrect?.word)
    }

    @Test
    fun testAutoCorrectionRevertStateMachine() {
        val autoCorrect = pipeline.autoCorrectionPipeline
        autoCorrect.recordCorrection("helo", "hello")

        assertTrue("Should be able to revert immediately", autoCorrect.canRevert())
        val reverted = autoCorrect.consumeRevert()
        assertNotNull(reverted)
        assertEquals("helo", reverted?.original)
        assertEquals("hello", reverted?.corrected)

        assertTrue("Reverted word should be in suppression set", autoCorrect.isSuppressed("helo"))
    }

    @Test
    fun testGnosis3SlotCandidateLayout() {
        val (left, center, right) = pipeline.get3SlotCandidates("hel", emptyList())
        assertNotNull("Center candidate should exist", center)
        assertTrue("Center should be completion for hel", center?.word?.startsWith("hel", ignoreCase = true) == true)
    }

    @Test
    fun testSingleLetterNeverAutocorrectedExceptI() {
        val autoCorrect = pipeline.autoCorrectionPipeline
        val candI = GnosisEngineCandidate("I", 0.99, 1.0, 1.0, 1.0, isAutoCorrect = true)
        assertTrue(autoCorrect.shouldAutoCorrect(candI, "i"))

        val candR = GnosisEngineCandidate("are", 0.95, 1.0, 1.0, 1.0, isAutoCorrect = true)
        assertFalse(autoCorrect.shouldAutoCorrect(candR, "r"))

        val candPeriod = GnosisEngineCandidate(".", 0.95, 1.0, 1.0, 1.0, isAutoCorrect = true)
        assertFalse(autoCorrect.shouldAutoCorrect(candPeriod, "r"))
    }
}
