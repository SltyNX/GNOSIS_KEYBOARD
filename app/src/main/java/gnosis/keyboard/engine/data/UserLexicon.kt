package gnosis.keyboard.engine.data

import android.content.Context
import java.util.Locale

interface UserLexicon {
    fun recordWord(word: String)
    fun recordTransition(w1: String, w2: String)
    fun getUserScore(word: String, context: List<String>): Double
    fun isKnown(word: String): Boolean
    fun getTopWords(limit: Int = 100): List<String>
    fun clearAll()
}

class InMemoryUserLexicon(context: Context? = null) : UserLexicon {

    private val words = mutableMapOf<String, Int>()
    private val transitions = mutableMapOf<String, MutableMap<String, Int>>()

    private val prefs = context?.getSharedPreferences("gnosis_engine_user_lexicon", Context.MODE_PRIVATE)
    private var dirty = 0
    private var topCache: List<String>? = null

    init {
        loadFromDisk()
    }

    override fun recordWord(word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (!isSafeToLearn(clean)) return

        val previous = words[clean] ?: 0
        val count = previous + 1
        words[clean] = count.coerceAtMost(9999)
        topCache = null

        if (previous == 0 || ++dirty >= 5) {
            dirty = 0
            persist()
        }
    }

    override fun recordTransition(w1: String, w2: String) {
        val p = w1.trim().lowercase(Locale.ROOT)
        val n = w2.trim().lowercase(Locale.ROOT)
        if (!isSafeToLearn(p) || !isSafeToLearn(n) || p == n) return

        val map = transitions.getOrPut(p) { mutableMapOf() }
        val count = (map[n] ?: 0) + 1
        map[n] = count.coerceAtMost(999)
    }

    override fun getUserScore(word: String, context: List<String>): Double {
        val w = word.lowercase(Locale.ROOT)
        val wordFreq = (words[w] ?: 0).toDouble()

        val transitionFreq = if (context.isNotEmpty()) {
            val last = context.last().lowercase(Locale.ROOT)
            (transitions[last]?.get(w) ?: 0).toDouble()
        } else 0.0

        return (wordFreq * 0.4) + (transitionFreq * 0.8)
    }

    override fun isKnown(word: String): Boolean {
        return (words[word.lowercase(Locale.ROOT)] ?: 0) >= 2
    }

    override fun getTopWords(limit: Int): List<String> {
        val sorted = topCache ?: words.entries.sortedByDescending { it.value }.map { it.key }.also { topCache = it }
        return sorted.take(limit)
    }

    override fun clearAll() {
        words.clear()
        transitions.clear()
        topCache = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun isSafeToLearn(w: String): Boolean {
        if (w.length < 2 || w.length > 30) return false
        if (w.none { it.isLetter() }) return false
        if (w.any { !(it.isLetterOrDigit() || it in "_-'.") }) return false
        if (w.contains('@') || w.contains('/') || w.contains('\\')) return false
        return true
    }

    private fun loadFromDisk() {
        val raw = prefs?.getString("learned_words", null) ?: return
        raw.split(";").forEach { entry ->
            val p = entry.split(":")
            if (p.size == 2) {
                val count = p[1].toIntOrNull() ?: 1
                words[p[0]] = count
            }
        }
    }

    private fun persist() {
        val p = prefs ?: return
        val serialized = words.entries
            .sortedByDescending { it.value }
            .take(5000)
            .joinToString(";") { "${it.key}:${it.value}" }
        p.edit().putString("learned_words", serialized).apply()
    }
}
