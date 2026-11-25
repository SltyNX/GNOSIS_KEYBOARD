package gnosis.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

data class SuggestionPackInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val sizeBytes: Long,
    val version: String,
    val file: String
)

object SuggestionExtensionManager {

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/suggestions_prediction/"
    private const val GITHUB_RAW_MANIFEST =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/suggestions.json"

    private const val PREFS_NAME = "gnosis_suggestion_extensions"
    private const val KEY_ENABLED_PREFIX = "pack_enabled_"

    val STATIC_CATALOG: List<SuggestionPackInfo> = emptyList()

    private var cachedNeuralReranker: List<Pair<String, List<String>>>? = null
    private var cachedSpatialCalibration: Map<String, Float>? = null
    private var cachedKneserNeyContinuations: Map<String, List<String>>? = null
    private var cachedSmartReplies: List<Pair<List<String>, List<String>>>? = null
    private var cachedNgrams: Map<String, List<String>>? = null
    private var cachedHinglishTransitions: Map<String, List<String>>? = null
    private var cachedHinglishTranslit: Map<String, String>? = null
    private var cachedSemanticEmojis: List<Pair<List<String>, List<String>>>? = null
    private var cachedKoreanTransitions: Map<String, List<String>>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSuggestionsDir(context: Context): File {
        val dir = File(context.filesDir, "suggestions_prediction")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isInstalled(context: Context, id: String): Boolean {
        val file = File(getSuggestionsDir(context), "$id.json")
        return file.exists() && file.length() > 0
    }

    fun isEnabled(context: Context, id: String): Boolean {
        if (!isInstalled(context, id)) return false
        return prefs(context).getBoolean(KEY_ENABLED_PREFIX + id, true)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED_PREFIX + id, enabled).apply()
        invalidateCache()
    }

    fun isLanguageSpecific(id: String): Boolean =
        id == "korean_neural_pack_ko" || id == "hinglish_intelligence_pack"

    fun getCorePredictionEngines(context: Context): List<SuggestionPackInfo> =
        STATIC_CATALOG.filter { !isLanguageSpecific(it.id) && isInstalled(context, it.id) }

    fun deletePack(context: Context, id: String): Boolean {
        val dir = getSuggestionsDir(context)
        val file = File(dir, "$id.json")
        val tmpFile = File(dir, "$id.json.tmp")
        val d1 = if (file.exists()) file.delete() else true
        val d2 = if (tmpFile.exists()) tmpFile.delete() else true
        prefs(context).edit().remove(KEY_ENABLED_PREFIX + id).apply()
        val p = Skins.prefs(context)
        if (p.getString("setting_active_prediction_model_id", "") == id) {
            val remaining = STATIC_CATALOG.filter { !isLanguageSpecific(it.id) && it.id != id && isInstalled(context, it.id) }
            p.edit().putString("setting_active_prediction_model_id", remaining.firstOrNull()?.id ?: "").apply()
        }
        invalidateCache()
        return d1 || d2
    }

    fun invalidateCache() {
        synchronized(this) {
            cachedNeuralReranker = null
            cachedSpatialCalibration = null
            cachedKneserNeyContinuations = null
            cachedSmartReplies = null
            cachedNgrams = null
            cachedHinglishTransitions = null
            cachedHinglishTranslit = null
            cachedSemanticEmojis = null
            cachedKoreanTransitions = null
        }
    }

    fun fetchCatalog(context: Context, onComplete: (List<SuggestionPackInfo>) -> Unit) { onComplete(emptyList()) }

    fun downloadPack(
        context: Context,
        pack: SuggestionPackInfo,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        thread {
            var success = false
            try {
                val filename = if (pack.file.contains("/")) pack.file.substringAfterLast('/') else "${pack.id}.json"
                val downloadUrl = if (pack.file.startsWith("http")) pack.file else "$GITHUB_RAW_BASE$filename"

                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val rawLength = conn.contentLength.toLong()
                    val totalLength = if (rawLength > 0) rawLength else if (pack.sizeBytes > 0) pack.sizeBytes else 50000L
                    val targetFile = File(getSuggestionsDir(context), "${pack.id}.json")
                    val tempFile = File(getSuggestionsDir(context), "${pack.id}.json.tmp")

                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            var downloaded = 0L
                            var lastPct = -1
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                val progress = ((downloaded * 100) / totalLength).toInt().coerceIn(0, 99)
                                if (progress != lastPct) {
                                    lastPct = progress
                                    handler.post { onProgress(progress) }
                                }
                            }
                        }
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        setEnabled(context, pack.id, true)
                        invalidateCache()
                        handler.post { onProgress(100) }
                        success = true
                    }
                }
            } catch (_: Exception) {
                success = false
            }

            handler.post { onComplete(success) }
        }
    }

    fun getKoreanContinuations(context: Context, lastWord: String): List<String> = emptyList()

    private fun loadKorean(context: Context) {
        val file = File(getSuggestionsDir(context), "korean_neural_pack_ko.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "transitions") {
                        val map = mutableMapOf<String, List<String>>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            val list = mutableListOf<String>()
                            reader.beginArray()
                            while (reader.hasNext()) list.add(reader.nextString())
                            reader.endArray()
                            map[key] = list
                        }
                        reader.endObject()
                        cachedKoreanTransitions = map
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getSmartReplies(context: Context, fullContext: String): List<String> = emptyList()

    private fun loadSmartReplies(context: Context) {
        val file = File(getSuggestionsDir(context), "smart_reply_vector_en.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "intents") {
                        val list = mutableListOf<Pair<List<String>, List<String>>>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            val kws = mutableListOf<String>()
                            val reps = mutableListOf<String>()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "keywords" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) kws.add(reader.nextString().lowercase(Locale.ROOT))
                                        reader.endArray()
                                    }
                                    "replies" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) reps.add(reader.nextString())
                                        reader.endArray()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            list.add(Pair(kws, reps))
                        }
                        reader.endArray()
                        cachedSmartReplies = list
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getExpandedNgrams(context: Context, phrase: String): List<String> = emptyList()

    private fun loadExpandedNgrams(context: Context) {
        val file = File(getSuggestionsDir(context), "ngrams_expanded_en.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "transitions") {
                        val map = mutableMapOf<String, List<String>>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName().lowercase(Locale.ROOT)
                            val list = mutableListOf<String>()
                            reader.beginArray()
                            while (reader.hasNext()) list.add(reader.nextString())
                            reader.endArray()
                            map[key] = list
                        }
                        reader.endObject()
                        cachedNgrams = map
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getHinglishContinuations(context: Context, lastWord: String): List<String> {
        if (!isEnabled(context, "hinglish_intelligence_pack")) return emptyList()
        val w = lastWord.trim().lowercase(Locale.ROOT)
        if (w.isEmpty()) return emptyList()

        if (cachedHinglishTransitions == null) {
            loadHinglish(context)
        }
        return cachedHinglishTransitions?.get(w) ?: emptyList()
    }

    fun getHinglishTransliteration(context: Context, word: String): String? {
        if (!isEnabled(context, "hinglish_intelligence_pack")) return null
        val w = word.trim().lowercase(Locale.ROOT)
        if (w.isEmpty()) return null

        if (cachedHinglishTranslit == null) {
            loadHinglish(context)
        }
        return cachedHinglishTranslit?.get(w)
    }

    private fun loadHinglish(context: Context) {
        val file = File(getSuggestionsDir(context), "hinglish_intelligence_pack.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "transitions" -> {
                            val map = mutableMapOf<String, List<String>>()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName().lowercase(Locale.ROOT)
                                val list = mutableListOf<String>()
                                reader.beginArray()
                                while (reader.hasNext()) list.add(reader.nextString())
                                reader.endArray()
                                map[key] = list
                            }
                            reader.endObject()
                            cachedHinglishTransitions = map
                        }
                        "transliterations" -> {
                            val map = mutableMapOf<String, String>()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val key = reader.nextName().lowercase(Locale.ROOT)
                                val value = reader.nextString()
                                map[key] = value
                            }
                            reader.endObject()
                            cachedHinglishTranslit = map
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getSemanticEmojis(context: Context, query: String): List<String> = emptyList()

    private fun loadSemanticEmojis(context: Context) {
        val file = File(getSuggestionsDir(context), "emoji_semantic_vectors.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "semantic_clusters") {
                        val list = mutableListOf<Pair<List<String>, List<String>>>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            val concepts = mutableListOf<String>()
                            val emojis = mutableListOf<String>()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "concepts" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) concepts.add(reader.nextString().lowercase(Locale.ROOT))
                                        reader.endArray()
                                    }
                                    "emojis" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) emojis.add(reader.nextString())
                                        reader.endArray()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            list.add(Pair(concepts, emojis))
                        }
                        reader.endArray()
                        cachedSemanticEmojis = list
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getNeuralRerankBoost(context: Context, fullContext: String): List<String> = emptyList()

    private fun loadNeuralStack(context: Context) {
        val file = File(getSuggestionsDir(context), "neural_transformer_stack_en.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "neural_reranker_rules") {
                        val list = mutableListOf<Pair<String, List<String>>>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var ctx = ""
                            val words = mutableListOf<String>()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "context" -> ctx = reader.nextString().lowercase(Locale.ROOT)
                                    "boost_words" -> {
                                        reader.beginArray()
                                        while (reader.hasNext()) words.add(reader.nextString())
                                        reader.endArray()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (ctx.isNotEmpty() && words.isNotEmpty()) {
                                list.add(Pair(ctx, words))
                            }
                        }
                        reader.endArray()
                        cachedNeuralReranker = list
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }

    fun getAlgorithmicKneserNey(context: Context, lastWord: String): List<String> = emptyList()

    fun getSpatialCalibration(context: Context, sensitivity: Int = 1): Map<String, Float>? {
        if (!isEnabled(context, "pure_algorithmic_engine_pack")) return null
        if (cachedSpatialCalibration == null) {
            loadAlgorithmicEngine(context)
        }
        val base = cachedSpatialCalibration ?: return null
        val scale = when (sensitivity) {
            0 -> 0.72f
            2 -> 1.36f
            else -> 1.0f
        }
        return base.mapValues { (_, v) -> v * scale }
    }

    private fun loadAlgorithmicEngine(context: Context) {
        val file = File(getSuggestionsDir(context), "pure_algorithmic_engine_pack.json")
        if (!file.exists()) return
        try {
            file.bufferedReader().use { br ->
                val reader = JsonReader(br)
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "spatial_touch_gaussian" -> {
                            val map = mutableMapOf<String, Float>()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val field = reader.nextName()
                                when (field) {
                                    "sigma_x_normal" -> map[field] = reader.nextDouble().toFloat()
                                    "sigma_y_normal" -> map[field] = reader.nextDouble().toFloat()
                                    "sigma_x_compact" -> map[field] = reader.nextDouble().toFloat()
                                    "sigma_y_compact" -> map[field] = reader.nextDouble().toFloat()
                                    "touch_ellipse_aspect_ratio" -> map[field] = reader.nextDouble().toFloat()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            cachedSpatialCalibration = map
                        }
                        "kneser_ney_language_model" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "continuation_probabilities") {
                                    val map = mutableMapOf<String, List<String>>()
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val word = reader.nextName().lowercase(Locale.ROOT)
                                        val innerMap = mutableMapOf<String, Double>()
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            innerMap[reader.nextName()] = reader.nextDouble()
                                        }
                                        reader.endObject()
                                        val sorted = innerMap.entries.sortedByDescending { it.value }.map { it.key }
                                        map[word] = sorted
                                    }
                                    reader.endObject()
                                    cachedKneserNeyContinuations = map
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (_: Exception) {}
    }
}
