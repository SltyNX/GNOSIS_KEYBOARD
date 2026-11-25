package gnosis.keyboard.stats

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TypingStatisticsRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: TypingStatisticsRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("gnosis_stats_test").toFile()
        repository = TypingStatisticsRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testEmptyStats() {
        val stats = repository.getStats(StatsPeriod.DAYS_7)
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.totalKeystrokes)
        assertEquals(0L, stats.totalCorrections)
        assertEquals(0L, stats.suggestionsAccepted)
        assertEquals("0m", stats.timeSavedFormatted)
    }

    @Test
    fun testRecordKeystrokesAndCorrections() {
        repository.recordKeystroke(15)
        repository.recordCorrection()
        repository.recordSuggestionAccepted("hello")
        repository.recordSuggestionAccepted("world")
        repository.recordSuggestionAccepted("hello")

        val stats = repository.getStats(StatsPeriod.DAYS_7)
        assertFalse(stats.isEmpty)
        assertEquals(15L, stats.totalKeystrokes)
        assertEquals(1L, stats.totalCorrections)
        assertEquals(3L, stats.suggestionsAccepted)

        assertEquals(2, stats.topSuggestedWords.size)
        assertEquals("hello", stats.topSuggestedWords[0].first)
        assertEquals(2, stats.topSuggestedWords[0].second)
        assertEquals("world", stats.topSuggestedWords[1].first)
        assertEquals(1, stats.topSuggestedWords[1].second)
    }

    @Test
    fun testUnsafeWordFiltering() {
        repository.recordSuggestionAccepted("123password")
        repository.recordSuggestionAccepted("a")
        repository.recordSuggestionAccepted("validword")

        val stats = repository.getStats(StatsPeriod.ALL_TIME)
        assertEquals(1, stats.topSuggestedWords.size)
        assertEquals("validword", stats.topSuggestedWords[0].first)
    }

    @Test
    fun testPersistence() {
        repository.recordKeystroke(50)
        repository.recordCorrection()
        repository.flush()

        val repo2 = TypingStatisticsRepository(tempDir)
        val stats = repo2.getStats(StatsPeriod.ALL_TIME)
        assertEquals(50L, stats.totalKeystrokes)
        assertEquals(1L, stats.totalCorrections)
    }

    @Test
    fun testClearAllStats() {
        repository.recordKeystroke(100)
        repository.clearAllStats()

        val stats = repository.getStats(StatsPeriod.ALL_TIME)
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.totalKeystrokes)
    }
}
