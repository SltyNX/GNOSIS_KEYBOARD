package gnosis.keyboard.engine

import gnosis.keyboard.engine.grammar_and_spellcheck.writingassist.GnosisWritingAssist
import android.graphics.PointF
import gnosis.keyboard.engine.delight.GnosisSpatialTouchModel
import gnosis.keyboard.engine.suggestion_typing.textboard.CandidateItem
import gnosis.keyboard.engine.suggestion_typing.textboard.CandidateType
import org.junit.Assert.*
import org.junit.Test

class GnosisEngineTest {

    @Test
    fun testSpatialTouchModelCalculation() {
        val model = GnosisSpatialTouchModel()
        val directTouch = model.calculateLikelihood('q', PointF(0.05f, 0.2f))
        val distantTouch = model.calculateLikelihood('q', PointF(0.95f, 0.8f))
        assertTrue("Direct key hit should have higher likelihood than distant hit", directTouch > distantTouch)
    }

    @Test
    fun testWritingAssistContractionDetection() {
        val dontIssue = GnosisWritingAssist.analyzeText("I dont")
        assertNotNull("Should detect contraction missing apostrophe", dontIssue)
        assertEquals("don't", dontIssue?.correctedText)

        val cantIssue = GnosisWritingAssist.analyzeText("you cant go")
        assertNotNull(cantIssue)
        assertEquals("can't", cantIssue?.correctedText)

        val iIssue = GnosisWritingAssist.analyzeText("when i")
        assertNotNull(iIssue)
        assertEquals("I", iIssue?.correctedText)

        val clean = GnosisWritingAssist.analyzeText("This is perfect English.")
        assertNull("Clean text should yield no grammar issues", clean)
    }

    @Test
    fun testWritingAssistHomophonesAndApostrophes() {
        val yourWelcome = GnosisWritingAssist.analyzeText("your welcome")
        assertNotNull("Should detect your welcome homophone", yourWelcome)
        assertEquals("you're welcome", yourWelcome?.correctedText)

        val theirGoing = GnosisWritingAssist.analyzeText("their going")
        assertNotNull("Should detect their going homophone", theirGoing)
        assertEquals("they're going", theirGoing?.correctedText)

        assertEquals("don't", GnosisWritingAssist.getContraction("dont"))
        assertEquals("Don't", GnosisWritingAssist.getContraction("Dont"))
        assertEquals("I'm", GnosisWritingAssist.getContraction("im"))
        assertEquals("that's", GnosisWritingAssist.getContraction("thats"))
    }

    @Test
    fun testCandidateItemModel() {
        val cand = CandidateItem("hello", CandidateType.AUTO_CORRECT, 0.95f)
        assertEquals("hello", cand.text)
        assertEquals(CandidateType.AUTO_CORRECT, cand.type)
        assertEquals(0.95f, cand.confidence, 0.001f)
    }
}
