package gnosis.keyboard.engine.prediction

import gnosis.keyboard.engine.data.LongIntHashMap
import java.util.Locale

class CompactNgramModel(
    private val wordsToId: Map<String, Int>,
    private val unigramCounts: IntArray,
    private val bigramCounts: LongIntHashMap,
    private val trigramCounts: LongIntHashMap? = null
) {
    private val totalUnigramTokens: Long = unigramCounts.sumOf { it.toLong() }.coerceAtLeast(1L)
    private val vocabSize = wordsToId.size.coerceAtLeast(1)

    fun wordId(word: String): Int? = wordsToId[word.lowercase(Locale.ROOT)]

    fun unigramProb(word: String): Double {
        val id = wordId(word) ?: return 1e-8
        val count = if (id in unigramCounts.indices) unigramCounts[id] else 0
        return (count + 1.0) / (totalUnigramTokens + vocabSize)
    }

    fun bigramProb(w1: String, w2: String): Double {
        val id1 = wordId(w1) ?: return 0.4 * unigramProb(w2)
        val id2 = wordId(w2) ?: return 1e-8

        val biKey = pack2(id1, id2)
        val countBigram = bigramCounts[biKey]
        val countUnigram = if (id1 in unigramCounts.indices) unigramCounts[id1] else 0

        return if (countBigram <= 0) {
            0.4 * unigramProb(w2)
        } else {
            (countBigram + 1.0) / (countUnigram + vocabSize)
        }
    }

    fun trigramProb(w1: String, w2: String, w3: String): Double {
        val id1 = wordId(w1) ?: return bigramProb(w2, w3)
        val id2 = wordId(w2) ?: return bigramProb(w2, w3)
        val id3 = wordId(w3) ?: return 1e-8

        val triKey = pack3(id1, id2, id3)
        val countTri = trigramCounts?.get(triKey) ?: 0
        val countBi = bigramCounts[pack2(id1, id2)]

        return if (countTri <= 0) {
            0.4 * bigramProb(w2, w3)
        } else {
            (countTri + 1.0) / (countBi + vocabSize)
        }
    }

    fun contextBoost(context: List<String>, candidate: String): Double {
        val c = wordId(candidate) ?: return 0.0
        val n = context.size
        if (n >= 1) {
            val b = wordId(cleanToken(context[n - 1])) ?: return 0.0
            if (n >= 2) {
                val a = wordId(cleanToken(context[n - 2]))
                if (a != null) {
                    val t = trigramCounts?.get(pack3(a, b, c)) ?: 0
                    if (t > 0) return (0.7 + t / 100.0).coerceAtMost(1.0)
                }
            }
            val bi = bigramCounts[pack2(b, c)]
            if (bi > 0) return (0.2 + bi / 40.0).coerceAtMost(0.7)
        }
        return 0.0
    }

    private fun cleanToken(w: String): String =
        w.trim { !it.isLetter() && it != '\'' }

    fun nextWordProb(context: List<String>, candidate: String): Double {
        val size = context.size
        return when {
            size >= 2 -> trigramProb(context[size - 2], context[size - 1], candidate)
            size == 1 -> bigramProb(context[0], candidate)
            else -> unigramProb(candidate)
        }
    }

    companion object {
        fun pack2(id1: Int, id2: Int): Long {
            return (id1.toLong() shl 32) or (id2.toLong() and 0xFFFFFFFFL)
        }

        fun pack3(id1: Int, id2: Int, id3: Int): Long {
            return ((id1.toLong() and 0xFFFFF) shl 40) or
                   ((id2.toLong() and 0xFFFFF) shl 20) or
                   (id3.toLong() and 0xFFFFF)
        }
    }
}
