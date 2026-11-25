package gnosis.keyboard.engine.delight

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Gnosis Dynamic Language Model (Dynamic LM)
 *
 * Implements Gnosis DynamicLm personal history model.
 * Learns vocabulary frequencies and bigram context transitions dynamically
 * on-device with zero cloud telemetry and strict privacy boundaries.
 */
class GnosisDynamicLm(private val context: Context? = null) {

    private val unigramFrequencies = ConcurrentHashMap<String, Int>()
    private val bigramTransitions = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val suppressedCorrections = ConcurrentHashMap.newKeySet<String>()

    init {
        loadPersistedData()
    }

    fun learnWord(word: String, prevWord: String? = null) {
        val clean = word.trim().lowercase()
        if (clean.length < 2 || clean.any { it.isDigit() }) return

        unigramFrequencies.compute(clean) { _, v -> (v ?: 0) + 1 }

        if (!prevWord.isNullOrEmpty()) {
            val cleanPrev = prevWord.trim().lowercase()
            if (cleanPrev.isNotEmpty()) {
                bigramTransitions
                    .computeIfAbsent(cleanPrev) { ConcurrentHashMap() }
                    .compute(clean) { _, v -> (v ?: 0) + 1 }
            }
        }
    }

    fun getBigramProbability(prevWord: String, currentWord: String): Float {
        val cleanPrev = prevWord.trim().lowercase()
        val cleanCurr = currentWord.trim().lowercase()
        val map = bigramTransitions[cleanPrev] ?: return 0.0f
        val count = map[cleanCurr] ?: return 0.0f
        val total = map.values.sum().toFloat().coerceAtLeast(1.0f)
        return count / total
    }

    fun predictNext(prevWord: String): String? {
        val cleanPrev = prevWord.trim().lowercase()
        val map = bigramTransitions[cleanPrev] ?: return null
        return map.entries.maxByOrNull { it.value }?.key
    }

    fun getUnigramFrequency(word: String): Int {
        return unigramFrequencies[word.trim().lowercase()] ?: 0
    }

    fun suppressCorrection(word: String) {
        suppressedCorrections.add(word.trim().lowercase())
    }

    fun isSuppressed(word: String): Boolean {
        return suppressedCorrections.contains(word.trim().lowercase())
    }

    private fun loadPersistedData() {
        if (context == null) return
        try {
            val file = File(context.filesDir, "gnosis_dynamic_lexicon.json")
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val unigrams = json.optJSONObject("unigrams")
            unigrams?.keys()?.forEach { k ->
                unigramFrequencies[k] = unigrams.getInt(k)
            }
        } catch (_: Exception) {}
    }

    fun persist() {
        if (context == null) return
        try {
            val json = JSONObject()
            val unigrams = JSONObject()
            unigramFrequencies.forEach { (k, v) -> unigrams.put(k, v) }
            json.put("unigrams", unigrams)

            val file = File(context.filesDir, "gnosis_dynamic_lexicon.json")
            file.writeText(json.toString())
        } catch (_: Exception) {}
    }
}
