package gnosis.keyboard.dictionary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class UserDictionaryRepository(private val baseDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val dictFile = File(baseDir, "user_dictionary.json")

    private val customWords = LinkedHashSet<String>()
    private val shortcuts = LinkedHashMap<String, String>()

    init {
        load()
    }

    @Synchronized
    fun addWord(word: String): Boolean {
        val clean = word.trim()
        if (clean.length < 2) return false
        val added = customWords.add(clean.lowercase(Locale.ROOT))
        if (added) save()
        return added
    }

    @Synchronized
    fun removeWord(word: String): Boolean {
        val removed = customWords.remove(word.trim().lowercase(Locale.ROOT))
        if (removed) save()
        return removed
    }

    @Synchronized
    fun isCustomWord(word: String): Boolean {
        return customWords.contains(word.trim().lowercase(Locale.ROOT))
    }

    @Synchronized
    fun getAllWords(): List<String> = customWords.toList()

    @Synchronized
    fun addShortcut(shortcut: String, expansion: String): Boolean {
        val s = shortcut.trim().lowercase(Locale.ROOT)
        val exp = expansion.trim()
        if (s.isEmpty() || exp.isEmpty()) return false
        shortcuts[s] = exp
        save()
        return true
    }

    @Synchronized
    fun removeShortcut(shortcut: String): Boolean {
        val s = shortcut.trim().lowercase(Locale.ROOT)
        val removed = shortcuts.remove(s) != null
        if (removed) save()
        return removed
    }

    @Synchronized
    fun getExpansion(shortcut: String): String? {
        val trimmed = shortcut.trim()
        val base = shortcuts[trimmed.lowercase(Locale.ROOT)] ?: return null
        return adaptCasing(trimmed, base)
    }

    private fun adaptCasing(typed: String, base: String): String {
        if (typed.isEmpty() || base.isEmpty()) return base
        val letters = typed.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
            return base.uppercase(Locale.ROOT)
        }
        if (typed[0].isUpperCase()) {
            return base.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return base
    }

    @Synchronized
    fun getAllShortcuts(): Map<String, String> = LinkedHashMap(shortcuts)

    @Synchronized
    fun clearAll() {
        customWords.clear()
        shortcuts.clear()
        if (dictFile.exists()) dictFile.delete()
    }

    @Synchronized
    fun exportJson(): String {
        val root = JSONObject()
        val wordsArr = JSONArray()
        for (w in customWords) wordsArr.put(w)
        root.put("words", wordsArr)

        val scObj = JSONObject()
        for ((k, v) in shortcuts) scObj.put(k, v)
        root.put("shortcuts", scObj)

        return root.toString(2)
    }

    @Synchronized
    fun importJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            val wordsArr = root.optJSONArray("words")
            if (wordsArr != null) {
                for (i in 0 until wordsArr.length()) {
                    val w = wordsArr.optString(i)?.trim()?.lowercase(Locale.ROOT)
                    if (!w.isNullOrEmpty()) customWords.add(w)
                }
            }

            val scObj = root.optJSONObject("shortcuts")
            if (scObj != null) {
                val keys = scObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = scObj.optString(k)?.trim()
                    if (!v.isNullOrEmpty()) shortcuts[k.lowercase(Locale.ROOT)] = v
                }
            }
            save()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun load() {
        if (!dictFile.exists()) {
            shortcuts["omw"] = "on my way"
            shortcuts["brb"] = "be right back"
            shortcuts["ty"] = "thank you"
            shortcuts["np"] = "no problem"
            shortcuts["idk"] = "I don't know"
            return
        }

        try {
            val text = dictFile.readText()
            importJson(text)
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val json = exportJson()
            dictFile.writeText(json)
        } catch (_: Exception) {
        }
    }
}
