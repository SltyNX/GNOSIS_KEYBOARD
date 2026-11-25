package gnosis.keyboard.engine.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyFrequencyPolicyTest {
    @Test
    fun primaryFrequencyOrderIsPreserved() {
        val frequencies = VocabularyFrequencyPolicy.build(
            baseWords = listOf("the", "you", "because"),
            additionalWords = emptyList()
        )

        assertTrue(frequencies.getValue("the") > frequencies.getValue("you"))
        assertTrue(frequencies.getValue("you") > frequencies.getValue("because"))
    }

    @Test
    fun alphabeticAdditionalWordsDoNotReceiveFakeDescendingFrequencies() {
        val frequencies = VocabularyFrequencyPolicy.build(
            baseWords = listOf("the", "you"),
            additionalWords = listOf("aardvark", "zebra", "Aachen")
        )

        assertEquals(1, frequencies.getValue("aardvark"))
        assertEquals(1, frequencies.getValue("zebra"))
        assertEquals(1, frequencies.getValue("aachen"))
    }

    @Test
    fun duplicateAdditionalWordDoesNotOverrideBaseFrequency() {
        val frequencies = VocabularyFrequencyPolicy.build(
            baseWords = listOf("the", "you"),
            additionalWords = listOf("the", "aachen")
        )

        assertEquals(2, frequencies.getValue("the"))
        assertEquals(1, frequencies.getValue("aachen"))
    }
}
