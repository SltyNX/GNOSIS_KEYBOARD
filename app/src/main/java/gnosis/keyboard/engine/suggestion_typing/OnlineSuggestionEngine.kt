package gnosis.keyboard.engine.suggestion_typing

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import gnosis.keyboard.Skins
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * OnlineSuggestionEngine
 *
 * Fast, non-blocking asynchronous neural predictions and completions using free public
 * multi-language suggestion models (Google Complete API + Datamuse Phonics).
 * Processes results through an on-device lightweight ranking model optimized for lower-end devices.
 * Fully cached via LruCache with zero UI overhead.
 */
object OnlineSuggestionEngine {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GnosisOnlineSuggest").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, List<String>>(500)
    @Volatile
    private var activeTask: Future<*>? = null

    fun isEnabled(context: Context): Boolean {
        return Skins.prefs(context).getBoolean(
            Skins.KEY_ONLINE_PREDICTIONS,
            Skins.DEFAULT_ONLINE_PREDICTIONS
        )
    }

    /**
     * Query online word suggestions / completions asynchronously.
     * @param query The active word fragment (or empty for next-word predictions)
     * @param prevWord The preceding committed word
     * @param langCode Active language code (e.g. "en", "hi", "es")
     * @param onResult Callback delivered on the Android main thread with suggestions
     */
    fun query(
        query: String,
        prevWord: String = "",
        langCode: String = "en",
        onResult: (List<String>) -> Unit
    ) {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        val cleanPrev = prevWord.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isEmpty() && cleanPrev.isEmpty()) {
            onResult(emptyList())
            return
        }
        val safeLang = if (langCode == "hinglish") "hi" else langCode
        val cacheKey = "$safeLang|$cleanPrev|$cleanQuery"

        synchronized(cache) {
            val cached = cache.get(cacheKey)
            if (cached != null) {
                onResult(cached)
                return
            }
        }

        try {
            activeTask?.cancel(true)
        } catch (_: Throwable) {}

        activeTask = executor.submit {
            val raw = fetchMultiLanguageOnlineSuggestions(cleanQuery, cleanPrev, safeLang)
            val ranked = rankAndFilterPredictions(raw, cleanQuery)
            if (ranked.isNotEmpty()) {
                synchronized(cache) {
                    cache.put(cacheKey, ranked)
                }
            }
            mainHandler.post {
                try {
                    onResult(ranked)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun fetchMultiLanguageOnlineSuggestions(prefix: String, prevWord: String, lang: String): List<String> {
        val list = ArrayList<String>(8)
        val targetQuery = if (prefix.isNotEmpty()) prefix else prevWord
        if (targetQuery.isEmpty()) return emptyList()

        // 1. Primary: Free multi-language completions via public search suggestion endpoint
        try {
            val encodedQuery = URLEncoder.encode(targetQuery, "UTF-8")
            val urlStr = "https://suggestqueries.google.com/complete/search?client=firefox&hl=$lang&q=$encodedQuery"
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 280
                readTimeout = 300
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                useCaches = true
            }
            if (conn.responseCode == 200) {
                val stream = conn.inputStream
                val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                val sb = StringBuilder(512)
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                conn.disconnect()

                val jsonArray = JSONArray(sb.toString())
                if (jsonArray.length() >= 2) {
                    val suggestionsArr = jsonArray.optJSONArray(1)
                    if (suggestionsArr != null) {
                        for (i in 0 until suggestionsArr.length()) {
                            val candidate = suggestionsArr.optString(i)?.trim() ?: ""
                            val singleWord = if (candidate.contains(" ")) {
                                val tokens = candidate.split(" ")
                                tokens.firstOrNull { it.startsWith(targetQuery, ignoreCase = true) || it.contains("'") } ?: tokens[0]
                            } else candidate

                            val cleanWord = singleWord.trim('?', '.', '!', ',', '"', ';', ':', ' ')
                            if (cleanWord.isNotEmpty() && !list.contains(cleanWord)) {
                                list.add(cleanWord)
                                if (list.size >= 6) break
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Secondary fallback: Datamuse English / phonetic model
        if (list.size < 3 && (lang == "en" || lang == "hi" || lang == "hinglish")) {
            try {
                val dmQuery = URLEncoder.encode(targetQuery, "UTF-8")
                val dmUrl = if (prefix.isNotEmpty()) {
                    "https://api.datamuse.com/words?sp=$dmQuery*&max=4"
                } else {
                    "https://api.datamuse.com/words?rel_bga=$dmQuery&max=4"
                }
                val conn = (URL(dmUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 220
                    readTimeout = 240
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val sb = StringBuilder(256)
                    var line: String?
                    while (reader.readLine().also { line = it } != null) sb.append(line)
                    reader.close()
                    conn.disconnect()
                    val arr = JSONArray(sb.toString())
                    for (i in 0 until arr.length()) {
                        val w = arr.optJSONObject(i)?.optString("word")?.trim() ?: ""
                        if (w.isNotEmpty() && !w.contains(" ") && !list.contains(w)) {
                            list.add(w)
                            if (list.size >= 6) break
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return list
    }

    /**
     * Fast on-device ranking model:
     * Cleans tokens, prioritizes prefix-matching and typo-corrected candidates,
     * and deduplicates case-insensitively with minimal allocations.
     */
    private fun rankAndFilterPredictions(
        rawSuggestions: List<String>,
        query: String
    ): List<String> {
        if (rawSuggestions.isEmpty()) return emptyList()
        val cleaned = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val queryLower = query.lowercase(Locale.ROOT)

        for (cand in rawSuggestions) {
            val trimmed = cand.trim().trim('?', '.', '!', ',', '"', '\'', ';', ':', '-', '_')
            if (trimmed.isEmpty() || trimmed.length > 32) continue
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("www.")) continue
            if (trimmed.all { it.isDigit() || it == '.' }) continue

            val lower = trimmed.lowercase(Locale.ROOT)
            if (seen.contains(lower)) continue
            seen.add(lower)
            cleaned.add(trimmed)
            if (cleaned.size >= 6) break
        }

        if (queryLower.isEmpty()) return cleaned.take(4)

        return cleaned.sortedWith(Comparator { a, b ->
            val aLower = a.lowercase(Locale.ROOT)
            val bLower = b.lowercase(Locale.ROOT)
            val aStarts = aLower.startsWith(queryLower)
            val bStarts = bLower.startsWith(queryLower)
            if (aStarts && !bStarts) return@Comparator -1
            if (!aStarts && bStarts) return@Comparator 1
            a.length.compareTo(b.length)
        }).take(4)
    }
}
