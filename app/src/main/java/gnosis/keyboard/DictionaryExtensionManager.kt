package gnosis.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

data class DictionaryPackInfo(
    val id: String,
    val name: String,
    val language: String,
    val remoteFile: String,
    val fallbackFile: String? = null
)

object DictionaryExtensionManager {

    const val GITHUB_REPO = "https://github.com/SltyNX/Gnosis-Keyboard-Data.git"
    private const val BASE_RAW_URL = "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/Autocorrect/"

    val AVAILABLE_PACKS = listOf(
        DictionaryPackInfo("hinglish", "Hinglish Dictionary", "Hindi-English Hybrid", "words_hinglish.txt", "hinglish.txt"),
        DictionaryPackInfo("english", "English Expanded", "English (US / UK)", "words.txt", "words_english.txt"),
        DictionaryPackInfo("hindi", "Hindi Dictionary", "Hindi (Devanagari / Romanized)", "words_hindi.txt"),
        DictionaryPackInfo("german", "German Dictionary", "German (Deutsch)", "words_german.txt"),
        DictionaryPackInfo("spanish", "Spanish Dictionary", "Spanish (Espa\u00f1ol)", "words_spanish.txt"),
        DictionaryPackInfo("malayalam", "Malayalam Dictionary", "Malayalam", "words_malayalam.txt"),
        DictionaryPackInfo("manglish", "Manglish Dictionary", "Malayalam-English Hybrid", "words_manglish.txt"),
        DictionaryPackInfo("tamil", "Tamil Dictionary", "Tamil", "words_tamil.txt"),
        DictionaryPackInfo("french", "French Dictionary", "French (Fran\u00e7ais)", "words_french.txt"),
        DictionaryPackInfo("italian", "Italian Dictionary", "Italian (Italiano)", "words_italian.txt"),
        DictionaryPackInfo("portuguese", "Portuguese Dictionary", "Portuguese", "words_portuguese.txt"),
        DictionaryPackInfo("russian", "Russian Dictionary", "Russian (\u0420\u0443\u0441\u0441\u043a\u0438\u0439)", "words_russian.txt")
    )

    private val wordsCache = mutableMapOf<String, Set<String>>()

    fun getDictionaryDir(context: Context): File {
        val dir = File(context.filesDir, "dictionaries")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPackFile(context: Context, packId: String): File {
        val packDir = File(getDictionaryDir(context), packId)
        if (!packDir.exists()) packDir.mkdirs()
        return File(packDir, "words.txt")
    }

    fun isInstalled(context: Context, packId: String): Boolean {
        if (packId == "english") return true
        if (packId == "hinglish" && HinglishDictionaryManager.isInstalled(context)) {
            return true
        }
        val f = getPackFile(context, packId)
        return f.exists() && f.length() > 0
    }

    fun isEnabled(context: Context, packId: String): Boolean {
        if (!isInstalled(context, packId)) return false
        val p = Skins.prefs(context)
        return p.getBoolean("dict_pack_enabled_$packId", true)
    }

    fun setEnabled(context: Context, packId: String, enabled: Boolean) {
        val p = Skins.prefs(context)
        p.edit().putBoolean("dict_pack_enabled_$packId", enabled).apply()
        if (packId == "hinglish") {
            HinglishDictionaryManager.setEnabled(context, enabled)
        }
        clearCache()
    }

    fun getWordCount(context: Context, packId: String): Int {
        if (packId == "english") {
            val f = getPackFile(context, packId)
            if (f.exists()) {
                var c = 0
                try { f.forEachLine { if (it.isNotBlank()) c++ } } catch (_: Exception) {}
                if (c > 0) return c
            }
            return 48375
        }
        if (packId == "hinglish" && HinglishDictionaryManager.isInstalled(context)) {
            return HinglishDictionaryManager.getWordCount(context)
        }
        val f = getPackFile(context, packId)
        if (!f.exists()) return 0
        return try {
            var count = 0
            f.forEachLine { if (it.isNotBlank()) count++ }
            count
        } catch (_: Exception) {
            0
        }
    }

    fun loadPackWords(context: Context, packId: String): Set<String> {
        wordsCache[packId]?.let { return it }
        val result = LinkedHashSet<String>()

        if (packId == "hinglish") {
            result.addAll(HinglishDictionaryManager.loadWords(context))
            wordsCache[packId] = result
            return result
        }

        val f = getPackFile(context, packId)
        if (f.exists()) {
            try {
                f.forEachLine { line ->
                    val w = line.trim().lowercase(Locale.ROOT)
                    if (w.isNotEmpty()) result.add(w)
                }
            } catch (_: Exception) {}
        }

        val info = AVAILABLE_PACKS.firstOrNull { it.id == packId }
        val assetName = info?.remoteFile ?: "words_$packId.txt"
        try {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val w = line.trim().lowercase(Locale.ROOT)
                    if (w.isNotEmpty()) result.add(w)
                }
            }
        } catch (_: Exception) {}

        wordsCache[packId] = result
        return result
    }

    fun loadAllActiveWords(context: Context): Set<String> {
        val combined = LinkedHashSet<String>()
        for (pack in AVAILABLE_PACKS) {
            if (isEnabled(context, pack.id)) {
                combined.addAll(loadPackWords(context, pack.id))
            }
        }
        return combined
    }

    fun clearCache() {
        wordsCache.clear()
        HinglishDictionaryManager.clearCache()
    }

    fun deletePack(context: Context, packId: String): Boolean {
        clearCache()
        val p = Skins.prefs(context)
        p.edit().remove("dict_pack_enabled_$packId").apply()
        if (packId == "english") {
            val f = getPackFile(context, packId)
            if (f.exists()) f.delete()
            return true
        }
        if (packId == "hinglish") {
            return HinglishDictionaryManager.deleteExtension(context)
        }
        val f = getPackFile(context, packId)
        return if (f.exists()) f.delete() else true
    }

    fun downloadPack(
        context: Context,
        pack: DictionaryPackInfo,
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean, Int, String) -> Unit
    ) {
        if (pack.id == "hinglish") {
            HinglishDictionaryManager.downloadHinglishExtension(context, onProgress) { success, count, err ->
                if (success) {
                    setEnabled(context, pack.id, true)
                }
                onComplete(success, count, err)
            }
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            var downloaded = false
            var wordsFound = 0
            var errorMsg = ""
            val targetFile = getPackFile(context, pack.id)

            val urlsToTry = mutableListOf(
                BASE_RAW_URL + pack.remoteFile,
                "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/autocorrect/" + pack.remoteFile
            )
            if (pack.fallbackFile != null) {
                urlsToTry.add(BASE_RAW_URL + pack.fallbackFile)
                urlsToTry.add("https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/autocorrect/" + pack.fallbackFile)
            }

            for (remoteUrl in urlsToTry) {
                try {
                    val url = URL(remoteUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 12000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode == 200) {
                        val tmpFile = File(getDictionaryDir(context), "${pack.id}.tmp")
                        var count = 0
                        conn.inputStream.bufferedReader().use { reader ->
                            FileOutputStream(tmpFile).bufferedWriter().use { writer ->
                                reader.forEachLine { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.isNotEmpty()) {
                                        writer.write(trimmed)
                                        writer.newLine()
                                        count++
                                    }
                                }
                            }
                        }
                        if (count > 20) {
                            if (targetFile.exists()) targetFile.delete()
                            tmpFile.renameTo(targetFile)
                            downloaded = true
                            wordsFound = count
                            break
                        } else {
                            tmpFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Connection error"
                }
            }

            if (!downloaded) {
                try {
                    var count = 0
                    context.assets.open(pack.remoteFile).bufferedReader().use { reader ->
                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            reader.forEachLine { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty()) {
                                    writer.write(trimmed)
                                    writer.newLine()
                                    count++
                                }
                            }
                        }
                    }
                    if (count > 0) {
                        downloaded = true
                        wordsFound = count
                        errorMsg = ""
                    }
                } catch (_: Exception) {}
            }

            if (downloaded) {
                clearCache()
                setEnabled(context, pack.id, true)
            }

            mainHandler.post {
                onComplete(downloaded, wordsFound, errorMsg)
            }
        }
    }
}
