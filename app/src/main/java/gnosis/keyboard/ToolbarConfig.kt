package gnosis.keyboard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import org.json.JSONArray
import java.text.BreakIterator

object ToolbarConfig {
    const val MAX_QUICK = 3
    const val MAX_QUICK_LEN = 3

    const val IME_QUICK_EDIT = "gnosis.quickkey_edit"
    const val IME_PREVIEW = "gnosis.preview"

    val ALL_IDS = listOf("mic", "clip", "edit", "fonts", "float", "resize", "settings")

    val DEFAULT_QUICK = listOf(".", ",", "?")

    val QUICK_SUGGESTIONS = listOf(
        ".", ",", "?", "!", "'", "\"", "-", ":", ";", "@", "#", "&", "/", "(", ")", "…",
        "😀", "😂", "😊", "👍", "❤️", "🙏", "🔥", "✅"
    )

    fun loadQuick(ctx: Context): List<String> {
        val raw = Skins.prefs(ctx).getString(Skins.KEY_QUICK_KEYS, null) ?: return DEFAULT_QUICK
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { arr.optString(it, "") }
                .filter { it.isNotEmpty() }
                .take(MAX_QUICK)
        } catch (_: Exception) {
            DEFAULT_QUICK
        }
    }

    fun saveQuick(ctx: Context, keys: List<String>) {
        val clean = keys.map { clip(it) }.filter { it.isNotEmpty() }.take(MAX_QUICK)
        Skins.prefs(ctx).edit().putString(Skins.KEY_QUICK_KEYS, JSONArray(clean).toString()).apply()
    }

    fun resetQuick(ctx: Context) {
        Skins.prefs(ctx).edit().remove(Skins.KEY_QUICK_KEYS).apply()
    }

    fun clip(s: String, max: Int = MAX_QUICK_LEN): String {
        val t = s.trim()
        if (t.isEmpty()) return ""
        return try {
            val it = BreakIterator.getCharacterInstance()
            it.setText(t)
            var end = 0
            var n = 0
            while (n < max) {
                val next = it.next()
                if (next == BreakIterator.DONE) { end = t.length; break }
                end = next
                n++
            }
            t.substring(0, end)
        } catch (_: Exception) {
            t.take(max)
        }
    }

    fun firstGrapheme(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return ""
        return try {
            val it = BreakIterator.getCharacterInstance()
            it.setText(t)
            val end = it.next()
            if (end == BreakIterator.DONE || end <= 0) t else t.substring(0, end)
        } catch (_: Exception) {
            t.substring(0, t.offsetByCodePoints(0, 1))
        }
    }

    fun graphemeCount(s: CharSequence): Int {
        if (s.isEmpty()) return 0
        return try {
            val it = BreakIterator.getCharacterInstance()
            it.setText(s.toString())
            var n = 0
            while (it.next() != BreakIterator.DONE) n++
            n
        } catch (_: Exception) {
            s.toString().codePointCount(0, s.length)
        }
    }

    fun takeGraphemes(s: String, max: Int): String {
        if (graphemeCount(s) <= max) return s
        return try {
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            var end = 0
            var n = 0
            while (n < max) {
                val next = it.next()
                if (next == BreakIterator.DONE) { end = s.length; break }
                end = next
                n++
            }
            s.substring(0, end)
        } catch (_: Exception) {
            s.take(max)
        }
    }

    fun limitLength(et: EditText, max: Int = MAX_QUICK_LEN) {
        et.addTextChangedListener(object : TextWatcher {
            private var busy = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (busy || s == null || graphemeCount(s) <= max) return
                busy = true
                s.replace(0, s.length, takeGraphemes(s.toString(), max))
                busy = false
                et.setSelection(et.text.length)
            }
        })
    }
}
