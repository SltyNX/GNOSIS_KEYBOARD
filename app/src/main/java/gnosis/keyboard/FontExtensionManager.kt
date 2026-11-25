package gnosis.keyboard

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class RemoteFontInfo(
    val name: String,
    val file: String,
    val size: Long
)

data class InstalledFontInfo(
    val name: String,
    val filename: String,
    val file: File
)

object FontExtensionManager {

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/fonts/"

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    fun getFontsDir(context: Context): File {
        val dir = File(context.filesDir, "fonts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPreviewsDir(context: Context): File {
        val dir = File(context.cacheDir, "font_previews")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstalledFonts(context: Context): List<InstalledFontInfo> {
        val dir = getFontsDir(context)
        val files = dir.listFiles { _, name ->
            name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)
        } ?: return emptyList()

        return files.map { f ->
            val cleanName = f.nameWithoutExtension.replace('-', ' ').replace('_', ' ').trim()
            InstalledFontInfo(cleanName, f.name, f)
        }.sortedBy { it.name }
    }

    fun isInstalled(context: Context, filename: String): Boolean {
        return File(getFontsDir(context), filename).exists()
    }

    fun deleteFont(context: Context, filename: String): Boolean {
        val file = File(getFontsDir(context), filename)
        typefaceCache.remove(filename)
        typefaceCache.remove("$filename-bold")
        typefaceCache.remove("$filename-normal")
        return file.exists() && file.delete()
    }

    fun getTypeface(context: Context, fontFilename: String, bold: Boolean = false): Typeface? {
        val cacheKey = "$fontFilename-${if (bold) "b" else "n"}"
        typefaceCache[cacheKey]?.let { return it }

        val styleInt = if (bold) Typeface.BOLD else Typeface.NORMAL
        val dir = getFontsDir(context)
        val file = File(dir, fontFilename)
        if (file.exists()) {
            return try {
                val tf = Typeface.createFromFile(file)
                val styled = Typeface.create(tf, styleInt)
                if (styled != null) typefaceCache[cacheKey] = styled
                styled
            } catch (_: Exception) {
                null
            }
        }
        val withExt = File(dir, "$fontFilename.ttf")
        if (withExt.exists()) {
            return try {
                val tf = Typeface.createFromFile(withExt)
                val styled = Typeface.create(tf, styleInt)
                if (styled != null) typefaceCache[cacheKey] = styled
                styled
            } catch (_: Exception) {
                null
            }
        }
        val match = dir.listFiles()?.firstOrNull {
            it.name.equals(fontFilename, ignoreCase = true) ||
            it.nameWithoutExtension.equals(fontFilename.removeSuffix(".ttf").removeSuffix(".otf"), ignoreCase = true)
        }
        if (match != null && match.exists()) {
            return try {
                val tf = Typeface.createFromFile(match)
                val styled = Typeface.create(tf, styleInt)
                if (styled != null) typefaceCache[cacheKey] = styled
                styled
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun fetchRemoteCatalog(onResult: (List<RemoteFontInfo>?) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            val list = mutableListOf<RemoteFontInfo>()
            try {
                val url = URL("${GITHUB_RAW_BASE}manifest.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            RemoteFontInfo(
                                name = o.getString("name"),
                                file = o.getString("file"),
                                size = o.optLong("size", 0L)
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post {
                onResult(if (list.isNotEmpty()) list else null)
            }
        }
    }

    fun downloadFont(
        context: Context,
        font: RemoteFontInfo,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            var targetFile: File? = null
            try {
                val encodedFile = java.net.URLEncoder.encode(font.file, "UTF-8").replace("+", "%20")
                val url = URL("${GITHUB_RAW_BASE}${encodedFile}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val fileLength = conn.contentLength
                    val dest = File(getFontsDir(context), font.file)
                    val tmp = File(getFontsDir(context), "${font.file}.tmp")
                    conn.inputStream.use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            var totalRead = 0
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                totalRead += read
                                if (fileLength > 0 && onProgress != null) {
                                    val progress = (totalRead * 100L / fileLength).toInt()
                                    mainHandler.post { onProgress(progress) }
                                }
                            }
                        }
                    }
                    if (tmp.renameTo(dest) || (dest.delete() && tmp.renameTo(dest))) {
                        targetFile = dest
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post { onComplete(targetFile) }
        }
    }

    fun loadPreviewTypeface(
        context: Context,
        font: RemoteFontInfo,
        onComplete: (Typeface?) -> Unit
    ) {
        val installedFile = File(getFontsDir(context), font.file)
        if (installedFile.exists()) {
            try {
                val tf = Typeface.createFromFile(installedFile)
                onComplete(tf)
                return
            } catch (_: Exception) {}
        }

        val cachedFile = File(getPreviewsDir(context), font.file)
        if (cachedFile.exists()) {
            try {
                val tf = Typeface.createFromFile(cachedFile)
                onComplete(tf)
                return
            } catch (_: Exception) {}
        }

        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            var resultTf: Typeface? = null
            try {
                val encodedFile = java.net.URLEncoder.encode(font.file, "UTF-8").replace("+", "%20")
                val url = URL("${GITHUB_RAW_BASE}${encodedFile}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val tmp = File(getPreviewsDir(context), "${font.file}.tmp")
                    conn.inputStream.use { input ->
                        FileOutputStream(tmp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tmp.renameTo(cachedFile) || (cachedFile.delete() && tmp.renameTo(cachedFile))) {
                        resultTf = Typeface.createFromFile(cachedFile)
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post { onComplete(resultTf) }
        }
    }
}
