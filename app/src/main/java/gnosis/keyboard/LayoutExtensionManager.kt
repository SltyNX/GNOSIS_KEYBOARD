package gnosis.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class KeyboardLayoutModel(
    val id: String,
    val name: String,
    val language: String = "English",
    val spaceLabel: String = "SPACE",
    val langLabel: String = "EN",
    val isNonLatin: Boolean = false,
    val rows: List<String>,
    val secondaries: Map<Char, String> = emptyMap(),
    val isCustom: Boolean = false
)

data class RemoteLayoutInfo(
    val id: String,
    val name: String,
    val description: String,
    val language: String,
    val file: String
)

object LayoutExtensionManager {

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/SltyNX/Gnosis-Keyboard-Data/main/layouts/"

    val DEFAULT_QWERTY = KeyboardLayoutModel(
        id = "qwerty",
        name = "QWERTY (Standard)",
        language = "English",
        spaceLabel = "SPACE",
        langLabel = "EN",
        isNonLatin = false,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        secondaries = SecondaryKeyMapping.QWERTY_SECONDARY,
        isCustom = false
    )

    val BUILTIN_LAYOUTS: Map<String, KeyboardLayoutModel> = mapOf(
        "qwerty" to DEFAULT_QWERTY
    )

    val STATIC_CATALOG = listOf(
        RemoteLayoutInfo("qwertz", "QWERTZ (German)", "Standard German keyboard layout (Deutsch)", "German", "qwertz.json"),
        RemoteLayoutInfo("azerty", "AZERTY (French)", "Standard French keyboard layout (Français)", "French", "azerty.json"),
        RemoteLayoutInfo("spanish", "Español (Spanish)", "Standard Spanish keyboard layout with ñ and punctuation", "Spanish", "spanish.json"),
        RemoteLayoutInfo("deva", "देवनागरी (Hindi Native)", "Native Hindi Devanagari script layout with matras and conjuncts", "Hindi", "deva.json"),
        RemoteLayoutInfo("russian", "Русский (Russian)", "Standard Russian Cyrillic layout (ЙЦУКЕН)", "Russian", "russian.json"),
        RemoteLayoutInfo("arabic", "العربية (Arabic)", "Standard Arabic keyboard layout", "Arabic", "arabic.json"),
        RemoteLayoutInfo("bengali", "বাংলা (Bengali)", "Standard Bengali script keyboard layout", "Bengali", "bengali.json"),
        RemoteLayoutInfo("tamil", "தமிழ் (Tamil)", "Standard Tamil script keyboard layout", "Tamil", "tamil.json"),
        RemoteLayoutInfo("telugu", "తెలుగు (Telugu)", "Standard Telugu script keyboard layout", "Telugu", "telugu.json"),
        RemoteLayoutInfo("portuguese", "Português (Portuguese)", "Standard Portuguese keyboard layout with ç", "Portuguese", "portuguese.json"),
        RemoteLayoutInfo("turkish", "Türkçe (Turkish)", "Standard Turkish Q keyboard layout with ğ, ü, ş, ı, ö, ç", "Turkish", "turkish.json"),
        RemoteLayoutInfo("dvorak", "Dvorak", "Ergonomic alternative English layout", "English", "dvorak.json"),
        RemoteLayoutInfo("colemak", "Colemak", "Modern ergonomic English layout preserving shortcuts", "English", "colemak.json")
    )

    fun getPreviewKeys(id: String): List<String> {
        return when (id.lowercase()) {
            "qwertz" -> listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P")
            "azerty" -> listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P")
            "dvorak" -> listOf("P", "Y", "F", "G", "C", "R", "L")
            "colemak" -> listOf("Q", "W", "F", "P", "G", "J", "L", "U", "Y")
            "spanish" -> listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            "deva" -> listOf("क", "ख", "ग", "घ", "च", "छ", "ज", "झ")
            "russian" -> listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш")
            "arabic" -> listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه")
            "bengali" -> listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ")
            "tamil" -> listOf("க", "ங", "ச", "ஞ", "ட", "ண")
            "telugu" -> listOf("క", "ఖ", "గ", "ఘ", "ఙ", "చ")
            "portuguese" -> listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            "turkish" -> listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            else -> listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        }
    }

    fun getLayoutDir(context: Context): File {
        val dir = File(context.filesDir, "layouts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstalledLayouts(context: Context): List<KeyboardLayoutModel> {
        val map = LinkedHashMap<String, KeyboardLayoutModel>(BUILTIN_LAYOUTS)
        val dir = getLayoutDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
        if (files != null) {
            for (file in files) {
                try {
                    val content = file.readText()
                    val model = parseLayoutJson(content, isCustom = true)
                    if (model != null) {
                        map[model.id] = model
                    }
                } catch (_: Exception) {}
            }
        }
        return map.values.toList()
    }

    fun getLayout(context: Context, id: String): KeyboardLayoutModel {
        val lowerId = id.lowercase()
        val dir = getLayoutDir(context)
        val target = File(dir, "$lowerId.json")
        if (target.exists()) {
            try {
                val model = parseLayoutJson(target.readText(), isCustom = true)
                if (model != null) return model
            } catch (_: Exception) {}
        }
        return BUILTIN_LAYOUTS[lowerId] ?: DEFAULT_QWERTY
    }

    fun isInstalled(context: Context, id: String): Boolean {
        if (id.equals("qwerty", ignoreCase = true)) return true
        return File(getLayoutDir(context), "$id.json").exists()
    }

    fun installLayout(context: Context, jsonStr: String): KeyboardLayoutModel? {
        val model = parseLayoutJson(jsonStr, isCustom = true) ?: return null
        val file = File(getLayoutDir(context), "${model.id}.json")
        file.writeText(jsonStr)
        return model
    }

    fun deleteLayout(context: Context, id: String): Boolean {
        if (id.equals("qwerty", ignoreCase = true)) return false
        val file = File(getLayoutDir(context), "$id.json")
        return file.exists() && file.delete()
    }

    fun parseLayoutJson(jsonStr: String, isCustom: Boolean = false): KeyboardLayoutModel? {
        return try {
            val obj = JSONObject(jsonStr)
            val id = obj.getString("id")
            val name = obj.optString("name", id)
            val language = obj.optString("language", "English")
            val spaceLabel = obj.optString("spaceLabel", "SPACE")
            val langLabel = obj.optString("langLabel", "EN")
            val isNonLatin = obj.optBoolean("isNonLatin", false)

            val rowsArr = obj.getJSONArray("rows")
            val rows = mutableListOf<String>()
            for (i in 0 until rowsArr.length()) {
                rows.add(rowsArr.getString(i))
            }

            val secMap = mutableMapOf<Char, String>()
            val secObj = obj.optJSONObject("secondaries")
            if (secObj != null) {
                val it = secObj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k.isNotEmpty()) {
                        secMap[k[0]] = secObj.getString(k)
                    }
                }
            }

            KeyboardLayoutModel(
                id = id,
                name = name,
                language = language,
                spaceLabel = spaceLabel,
                langLabel = langLabel,
                isNonLatin = isNonLatin,
                rows = rows,
                secondaries = secMap,
                isCustom = isCustom
            )
        } catch (_: Exception) {
            null
        }
    }

    fun fetchRemoteCatalog(onResult: (List<RemoteLayoutInfo>) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            val list = mutableListOf<RemoteLayoutInfo>()
            try {
                val url = URL("${GITHUB_RAW_BASE}manifest.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            RemoteLayoutInfo(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                description = o.optString("description", ""),
                                language = o.optString("language", ""),
                                file = o.optString("file", "${o.getString("id")}.json")
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            val finalList = if (list.isNotEmpty()) list else STATIC_CATALOG
            mainHandler.post { onResult(finalList) }
        }
    }

    fun downloadAndInstall(
        context: Context,
        info: RemoteLayoutInfo,
        onComplete: (Boolean, KeyboardLayoutModel?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        thread {
            var success = false
            var installedModel: KeyboardLayoutModel? = null
            try {
                val url = URL("${GITHUB_RAW_BASE}${info.file}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    installedModel = installLayout(context, text)
                    success = installedModel != null
                }
            } catch (_: Exception) {}

            mainHandler.post { onComplete(success, installedModel) }
        }
    }
}
