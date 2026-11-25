package gnosis.keyboard.engine.prediction

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

object VocabularyFrequencyPolicy {
    fun build(
        baseWords: Iterable<String>,
        additionalWords: Iterable<String>
    ): LinkedHashMap<String, Int> {
        val normalizedBase = LinkedHashSet<String>()
        baseWords.forEach { raw ->
            val word = raw.trim().lowercase(Locale.ROOT)
            if (word.isNotEmpty()) normalizedBase.add(word)
        }

        val frequencies = LinkedHashMap<String, Int>()
        val baseSize = normalizedBase.size
        normalizedBase.forEachIndexed { index, word ->
            frequencies[word] = (baseSize - index).coerceAtLeast(1)
        }

        additionalWords.forEach { raw ->
            val word = raw.trim().lowercase(Locale.ROOT)
            if (word.isNotEmpty() && !frequencies.containsKey(word)) {
                frequencies[word] = 1
            }
        }
        return frequencies
    }
}
