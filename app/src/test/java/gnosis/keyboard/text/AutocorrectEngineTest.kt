package gnosis.keyboard.text

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutocorrectEngineTest {

    private lateinit var autocorrectEngine: AutocorrectEngine

    @Before
    fun setUp() {
        autocorrectEngine = AutocorrectEngine()
    }

    @Test
    fun testRejectionCases() {
        val validCandidate = Candidate("the", 0.95, CandidateKind.CORRECTION)

        assertFalse(autocorrectEngine.shouldAutoCorrect(null, "teh", false, false))

        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "teh", isPasswordField = true, isUrlOrEmail = false))

        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "teh", isPasswordField = false, isUrlOrEmail = true))

        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "", false, false))

        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "t3h", false, false))

        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "the", false, false))
        assertFalse(autocorrectEngine.shouldAutoCorrect(validCandidate, "THE", false, false))
    }

    @Test
    fun testConfidenceThresholds() {
        val correctionPass = Candidate("the", 0.72, CandidateKind.CORRECTION)
        assertTrue(autocorrectEngine.shouldAutoCorrect(correctionPass, "teh", false, false))

        val contractionPass = Candidate("don't", 0.75, CandidateKind.CONTRACTION)
        assertTrue(autocorrectEngine.shouldAutoCorrect(contractionPass, "dont", false, false))

        val correctionFail = Candidate("the", 0.65, CandidateKind.CORRECTION)
        assertFalse(autocorrectEngine.shouldAutoCorrect(correctionFail, "teh", false, false))

        val completionPass = Candidate("tomorrow", 0.88, CandidateKind.COMPLETION)
        assertTrue(autocorrectEngine.shouldAutoCorrect(completionPass, "tomorr", false, false))

        val completionFail = Candidate("tomorrow", 0.82, CandidateKind.COMPLETION)
        assertFalse(autocorrectEngine.shouldAutoCorrect(completionFail, "tomorr", false, false))
    }

    @Test
    fun testAutocorrectUndoLifecycle() {
        assertFalse(autocorrectEngine.canUndo())
        assertNull(autocorrectEngine.consumeUndo())

        autocorrectEngine.recordCorrection("teh", "the")
        assertTrue(autocorrectEngine.canUndo())

        val record = autocorrectEngine.consumeUndo()
        assertNotNull(record)
        assertEquals("teh", record?.original)
        assertEquals("the", record?.corrected)

        assertFalse(autocorrectEngine.canUndo())
        assertNull(autocorrectEngine.consumeUndo())

        autocorrectEngine.recordCorrection("waht", "what")
        autocorrectEngine.clear()
        assertFalse(autocorrectEngine.canUndo())
        assertNull(autocorrectEngine.consumeUndo())
    }

    @Test
    fun testSingleLetterNeverAutocorrectedExceptI() {
        val candI = Candidate("I", 0.99, CandidateKind.CORRECTION)
        assertTrue(autocorrectEngine.shouldAutoCorrect(candI, "i", false, false))

        val candR = Candidate("are", 0.95, CandidateKind.CORRECTION)
        assertFalse(autocorrectEngine.shouldAutoCorrect(candR, "r", false, false))

        val candPeriod = Candidate(".", 0.95, CandidateKind.CORRECTION)
        assertFalse(autocorrectEngine.shouldAutoCorrect(candPeriod, "r", false, false))
    }
}
