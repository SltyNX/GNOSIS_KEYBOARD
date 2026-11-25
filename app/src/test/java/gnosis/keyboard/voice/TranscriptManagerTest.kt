package gnosis.keyboard.voice

import gnosis.keyboard.voice.engine.TranscriptManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TranscriptManagerTest {

    private lateinit var transcriptManager: TranscriptManager

    @Before
    fun setUp() {
        transcriptManager = TranscriptManager()
    }

    @Test
    fun testPartialReplacementWithoutDuplication() {
        val p1 = transcriptManager.onPartial("hello")
        assertEquals("hello", p1)
        assertEquals("hello", transcriptManager.getCurrentPartial())

        val p2 = transcriptManager.onPartial("hello world")
        assertEquals("hello world", p2)
        assertEquals("hello world", transcriptManager.getCurrentPartial())

        assertEquals("", transcriptManager.getFullCommittedTranscript())
    }

    @Test
    fun testFinalCommitAndDeduplication() {
        val f1 = transcriptManager.onFinal("hello world")
        assertEquals("hello world", f1)
        assertEquals("", transcriptManager.getCurrentPartial())
        assertEquals("hello world", transcriptManager.getFullCommittedTranscript())

        val dup = transcriptManager.onFinal("hello world")
        assertNull(dup)
        assertEquals("hello world", transcriptManager.getFullCommittedTranscript())

        val f2 = transcriptManager.onFinal("this is offline voice")
        assertEquals("this is offline voice", f2)
        assertEquals("hello world this is offline voice", transcriptManager.getFullCommittedTranscript())
    }

    @Test
    fun testCancellationClearsPartialWithoutCommitting() {
        transcriptManager.onFinal("start of sentence")
        transcriptManager.onPartial("discarded partial text")
        assertEquals("discarded partial text", transcriptManager.getCurrentPartial())

        transcriptManager.onCancel()
        assertEquals("", transcriptManager.getCurrentPartial())
        assertEquals("start of sentence", transcriptManager.getFullCommittedTranscript())
    }

    @Test
    fun testStopFlushesUnfinalizedPartial() {
        transcriptManager.onPartial("flushed on stop")
        val flushed = transcriptManager.onStop()
        assertEquals("flushed on stop", flushed)
        assertEquals("", transcriptManager.getCurrentPartial())
        assertEquals("flushed on stop", transcriptManager.getFullCommittedTranscript())

        assertNull(transcriptManager.onStop())
    }

    @Test
    fun testTrailingAndLeadingSpacing() {
        val res1 = TranscriptManager.formatWithTrailingSpace("hello", "previous")
        assertEquals(" hello ", res1)

        val res2 = TranscriptManager.formatWithTrailingSpace("hello", "previous ")
        assertEquals("hello ", res2)

        val res3 = TranscriptManager.formatWithTrailingSpace("hello", null)
        assertEquals("hello ", res3)
    }
}
