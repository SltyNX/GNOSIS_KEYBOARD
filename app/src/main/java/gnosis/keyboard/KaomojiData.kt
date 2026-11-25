package gnosis.keyboard

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object KaomojiData {

    data class KaomojiCategory(
        val title: String,
        val items: List<String>
    )

    data class ComboItem(
        val art: String,
        val keywords: String
    )

    data class ComboCategory(
        val title: String,
        val items: List<ComboItem>
    )

    private val KAOMOJI_TITLES = listOf(
        "Popular",
        "Happy",
        "Love",
        "Sad",
        "Emoticons",
        "Cats & Signs",
        "Bears",
        "Angry",
        "Surprise",
        "Cute"
    )

    @Volatile
    private var cachedKaomoji: List<KaomojiCategory>? = null

    @Volatile
    private var cachedCombos: List<ComboCategory>? = null

    fun getKaomojiCategories(ctx: Context): List<KaomojiCategory> {
        cachedKaomoji?.let { return it }
        return synchronized(this) {
            cachedKaomoji ?: run {
                val list = mutableListOf<KaomojiCategory>()
                try {
                    ctx.assets.open("kaomoji.json").use { stream ->
                        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                        val json = reader.readText()
                        val root = JSONArray(json)
                        for (i in 0 until root.length()) {
                            val arr = root.getJSONArray(i)
                            val items = mutableListOf<String>()
                            for (j in 0 until arr.length()) {
                                val s = arr.optString(j, "")
                                if (s.isNotEmpty()) items.add(s)
                            }
                            val title = KAOMOJI_TITLES.getOrElse(i) { "Category ${i + 1}" }
                            if (items.isNotEmpty()) {
                                list.add(KaomojiCategory(title, items))
                            }
                        }
                    }
                } catch (_: Exception) {}
                cachedKaomoji = list
                list
            }
        }
    }

    fun getEmojiCombos(ctx: Context): List<ComboCategory> {
        cachedCombos?.let { return it }
        return synchronized(this) {
            cachedCombos ?: run {
                val list = mutableListOf<ComboCategory>()
                try {
                    ctx.assets.open("emoji_combos.json").use { stream ->
                        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                        val json = reader.readText()
                        val root = JSONArray(json)
                        for (i in 0 until root.length()) {
                            val catObj = root.getJSONObject(i)
                            val rawTag = catObj.optString("tag", "Combos")
                            val title = formatTagTitle(rawTag)
                            val itemsArr = catObj.optJSONArray("list") ?: JSONArray()
                            val items = mutableListOf<ComboItem>()
                            for (j in 0 until itemsArr.length()) {
                                val itemObj = itemsArr.getJSONObject(j)
                                val word = itemObj.optString("word", "").trim()
                                val text = itemObj.optString("text", "")
                                if (word.isNotEmpty()) {
                                    items.add(ComboItem(word, text))
                                }
                            }
                            if (items.isNotEmpty()) {
                                list.add(ComboCategory(title, items))
                            }
                        }
                    }
                } catch (_: Exception) {}
                cachedCombos = list
                list
            }
        }
    }

    fun prewarm(ctx: Context) {
        Thread({
            try {
                getKaomojiCategories(ctx)
                getEmojiCombos(ctx)
            } catch (_: Throwable) {}
        }, "KaomojiPrewarm").start()
    }

    private fun formatTagTitle(tag: String): String = when (tag) {
        "Hot_Rank" -> "Trending"
        "Kaomoji_Art" -> "Kaomoji Art"
        "Game_Art" -> "Gaming"
        "Animal_Art" -> "Animals"
        "Love" -> "Love"
        "Greetings" -> "Greetings"
        "Holiday" -> "Holidays"
        "Season" -> "Seasons"
        "Food" -> "Food"
        "Lifestyle" -> "Lifestyle"
        "Fun" -> "Fun & Memes"
        else -> tag.replace('_', ' ')
    }
}
