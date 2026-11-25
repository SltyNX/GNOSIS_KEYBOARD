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

object HinglishDictionaryManager {

    const val PREFS_NAME = "gnosis_keyboard_prefs"
    const val KEY_HINGLISH_ENABLED = "hinglish_autocorrect_enabled"
    const val KEY_HINGLISH_INSTALLED = "hinglish_extension_installed"
    const val KEY_HINGLISH_WORD_COUNT = "hinglish_word_count"

    const val GITHUB_REPO = "https://github.com/SltyNX/Gnosis-Keyboard-Data.git"
    private const val PRIMARY_REMOTE_URL =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/Autocorrect/words_hinglish.txt"
    private const val SECONDARY_REMOTE_URL =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/Autocorrect/hinglish.txt"
    private const val TERTIARY_REMOTE_URL =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/Autocorrect/words.txt"

    @Volatile
    private var cachedWords: Set<String>? = null

    fun getDictionaryDir(context: Context): File {
        val dir = File(context.filesDir, "dictionaries/hinglish")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getHinglishWordFile(context: Context): File {
        return File(getDictionaryDir(context), "words_hinglish.txt")
    }

    fun isInstalled(context: Context): Boolean {
        val file = getHinglishWordFile(context)
        return file.exists() && file.length() > 0
    }

    fun isEnabled(context: Context): Boolean {
        val p = Skins.prefs(context)
        return p.getBoolean(KEY_HINGLISH_ENABLED, false) && isInstalled(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        Skins.prefs(context).edit().putBoolean(KEY_HINGLISH_ENABLED, enabled).apply()
    }

    fun getWordCount(context: Context): Int {
        val p = Skins.prefs(context)
        val saved = p.getInt(KEY_HINGLISH_WORD_COUNT, 0)
        if (saved > 0) return saved
        val file = getHinglishWordFile(context)
        if (file.exists()) {
            try {
                var count = 0
                file.forEachLine { if (it.isNotBlank()) count++ }
                p.edit().putInt(KEY_HINGLISH_WORD_COUNT, count).apply()
                return count
            } catch (_: Exception) {}
        }
        return 0
    }

    fun loadWords(context: Context): Set<String> {
        cachedWords?.let { return it }
        val words = LinkedHashSet<String>()
        val file = getHinglishWordFile(context)
        if (file.exists()) {
            try {
                file.forEachLine { line ->
                    val w = line.trim().lowercase(Locale.ROOT)
                    if (w.isNotEmpty()) words.add(w)
                }
            } catch (_: Exception) {}
        }

        try {
            context.assets.open("words_hinglish.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val w = line.trim().lowercase(Locale.ROOT)
                    if (w.isNotEmpty()) words.add(w)
                }
            }
        } catch (_: Exception) {}

        cachedWords = words
        return words
    }

    fun clearCache() {
        cachedWords = null
    }

    fun deleteExtension(context: Context): Boolean {
        clearCache()
        val p = Skins.prefs(context)
        p.edit()
            .putBoolean(KEY_HINGLISH_INSTALLED, false)
            .putInt(KEY_HINGLISH_WORD_COUNT, 0)
            .apply()
        val file = getHinglishWordFile(context)
        return if (file.exists()) file.delete() else true
    }

    fun downloadHinglishExtension(
        context: Context,
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean, Int, String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            var downloaded = false
            var wordsFound = 0
            var errorMsg = ""
            val targetFile = getHinglishWordFile(context)

            val remoteUrls = listOf(PRIMARY_REMOTE_URL, SECONDARY_REMOTE_URL, TERTIARY_REMOTE_URL)
            for (remoteUrl in remoteUrls) {
                try {
                    val url = URL(remoteUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 12000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode == 200) {
                        val tmpFile = File(getDictionaryDir(context), "words_hinglish.tmp")
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
                        if (count > 100) {
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
                    context.assets.open("words_hinglish.txt").bufferedReader().use { reader ->
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
                } catch (e: Exception) {
                    if (errorMsg.isEmpty()) errorMsg = e.message ?: "Failed to seed dictionary"
                }
            }

            if (downloaded) {
                clearCache()
                val p = Skins.prefs(context)
                val editor = p.edit()
                    .putBoolean(KEY_HINGLISH_INSTALLED, true)
                    .putInt(KEY_HINGLISH_WORD_COUNT, wordsFound)
                if (!p.contains(KEY_HINGLISH_ENABLED)) {
                    editor.putBoolean(KEY_HINGLISH_ENABLED, false)
                }
                editor.apply()
            }

            mainHandler.post {
                onComplete(downloaded, wordsFound, errorMsg)
            }
        }
    }
}
