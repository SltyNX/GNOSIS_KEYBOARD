package gnosis.keyboard

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object EmojiSkinTones {

    val MODIFIERS = listOf(
        "",
        "\uD83C\uDFFB",
        "\uD83C\uDFFC",
        "\uD83C\uDFFD",
        "\uD83C\uDFFE",
        "\uD83C\uDFFF"
    )

    private val baseEmojiCache = ConcurrentHashMap<String, String>()
    private val preferredCache = ConcurrentHashMap<String, String>()
    @Volatile private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val all = Skins.prefs(context).all
            for ((key, value) in all) {
                if (key.startsWith("emoji_pref_") && value is String) {
                    val base = key.removePrefix("emoji_pref_")
                    preferredCache[base] = value
                }
            }
            isInitialized = true
        } catch (_: Exception) {}
    }

    fun hasSkinTone(emoji: String): Boolean {
        for (i in 0 until emoji.length - 1) {
            if (emoji[i] == '\uD83C' && emoji[i + 1] in '\uDFFB'..'\uDFFF') {
                return true
            }
        }
        return false
    }

    fun getBaseEmoji(emoji: String): String {
        baseEmojiCache[emoji]?.let { return it }
        if (!hasSkinTone(emoji)) {
            baseEmojiCache[emoji] = emoji
            return emoji
        }
        val sb = java.lang.StringBuilder(emoji.length)
        var i = 0
        while (i < emoji.length) {
            if (i < emoji.length - 1 && emoji[i] == '\uD83C' && emoji[i + 1] in '\uDFFB'..'\uDFFF') {
                i += 2
            } else {
                sb.append(emoji[i])
                i++
            }
        }
        val clean = sb.toString()
        baseEmojiCache[emoji] = clean
        return clean
    }

    private val SUPPORTS_SKIN_TONE: Set<String> = setOf(
        "👋", "🤚", "🖐️", "🖐", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "🫷", "🫸",
        "👌", "🤌", "🤏", "✌️", "✌", "🤞", "🫰", "🤟", "🤘", "🤙",
        "👈", "👉", "👆", "🖕", "👇", "☝️", "☝", "🫵",
        "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏",
        "✍️", "✍", "💅", "🤳", "💪", "🦵", "🦶", "👂", "🦻", "👃",
        "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "🧔‍♂️", "🧔‍♀️",
        "👨‍🦰", "👨‍🦱", "👨‍🦳", "👨‍🦲", "👩", "👩‍🦰", "🧑‍🦰", "👩‍🦱", "🧑‍🦱", "👩‍🦳", "🧑‍🦳", "👩‍🦲", "🧑‍🦲",
        "👱‍♀️", "👱‍♂️", "🧓", "👴", "👵",
        "🙍", "🙍‍♂️", "🙍‍♀️", "🙎", "🙎‍♂️", "🙎‍♀️", "🙅", "🙅‍♂️", "🙅‍♀️",
        "🙆", "🙆‍♂️", "🙆‍♀️", "💁", "💁‍♂️", "💁‍♀️", "🙋", "🙋‍♂️", "🙋‍♀️",
        "🧏", "🧏‍♂️", "🧏‍♀️", "🙇", "🙇‍♂️", "🙇‍♀️", "🤦", "🤦‍♂️", "🤦‍♀️",
        "🤷", "🤷‍♂️", "🤷‍♀️",
        "🧑‍⚕️", "👨‍⚕️", "👩‍⚕️", "🧑‍🎓", "👨‍🎓", "👩‍🎓", "🧑‍🏫", "👨‍🏫", "👩‍🏫",
        "🧑‍⚖️", "👨‍⚖️", "👩‍⚖️", "🧑‍🌾", "👨‍🌾", "👩‍🌾", "🧑‍🍳", "👨‍🍳", "👩‍🍳",
        "🧑‍🔧", "👨‍🔧", "👩‍🔧", "🧑‍🏭", "👨‍🏭", "👩‍🏭", "🧑‍💼", "👨‍💼", "👩‍💼",
        "🧑‍🔬", "👨‍🔬", "👩‍🔬", "👨‍💻", "👩‍💻", "🧑‍🎤", "👨‍🎤", "👩‍🎤",
        "🧑‍🎨", "👨‍🎨", "👩‍🎨", "🧑‍✈️", "👨‍✈️", "👩‍✈️", "🧑‍🚀", "👨‍🚀", "👩‍🚀",
        "🧑‍🚒", "👨‍🚒", "👩‍🚒",
        "👮", "👮‍♂️", "👮‍♀️", "🕵️", "🕵️‍♂️", "🕵️‍♀️", "💂", "💂‍♂️", "💂‍♀️",
        "🥷", "👷", "👷‍♂️", "👷‍♀️", "🫅", "🤴", "👸", "👳", "👳‍♂️", "👳‍♀️",
        "👲", "🧕", "🤵", "🤵‍♂️", "🤵‍♀️", "👰", "👰‍♂️", "👰‍♀️",
        "🤰", "🫃", "🫄", "🤱", "👩‍🍼", "👨‍🍼", "🧑‍🍼", "👼",
        "🎅", "🤶", "🧑‍🎄", "🦸", "🦸‍♂️", "🦸‍♀️", "🦹", "🦹‍♂️", "🦹‍♀️",
        "🧙", "🧙‍♂️", "🧙‍♀️", "🧚", "🧚‍♂️", "🧚‍♀️", "🧛", "🧛‍♂️", "🧛‍♀️",
        "🧜", "🧜‍♂️", "🧜‍♀️", "🧝", "🧝‍♂️", "🧝‍♀️",
        "💆", "💆‍♂️", "💆‍♀️", "💇", "💇‍♂️", "💇‍♀️",
        "🚶", "🚶‍♂️", "🚶‍♀️", "🧍", "🧍‍♂️", "🧍‍♀️", "🧎", "🧎‍♂️", "🧎‍♀️",
        "🧑‍🦯", "👨‍🦯", "👩‍🦯", "🧑‍🦼", "👨‍🦼", "👩‍🦼", "🧑‍🦽", "👨‍🦽", "👩‍🦽",
        "🏃", "🏃‍♂️", "🏃‍♀️", "💃", "🕺", "🕴️", "🕴",
        "🧖", "🧖‍♂️", "🧖‍♀️", "🧗", "🧗‍♂️", "🧗‍♀️", "🏇", "🏄", "🏄‍♂️", "🏄‍♀️",
        "🏊", "🏊‍♂️", "🏊‍♀️", "🏋️", "🏋️‍♂️", "🏋️‍♀️", "🚴", "🚴‍♂️", "🚴‍♀️",
        "🚵", "🚵‍♂️", "🚵‍♀️", "🤸", "🤸‍♂️", "🤸‍♀️", "🤽", "🤽‍♂️", "🤽‍♀️",
        "🤾", "🤾‍♂️", "🤾‍♀️", "🤹", "🤹‍♂️", "🤹‍♀️", "🧘", "🧘‍♂️", "🧘‍♀️",
        "🛀", "🛌"
    )

    fun supportsSkinTone(emoji: String): Boolean {
        if (hasSkinTone(emoji)) return true
        val base = getBaseEmoji(emoji)
        return SUPPORTS_SKIN_TONE.contains(base) || SUPPORTS_SKIN_TONE.contains(base.replace("\uFE0F", ""))
    }

    fun getVariants(emoji: String): List<String> {
        val base = getBaseEmoji(emoji)
        val cleanBase = base.replace("\uFE0F", "")
        return MODIFIERS.map { mod ->
            if (mod.isEmpty()) {
                base
            } else {
                if (cleanBase.contains('\u200D')) {
                    val parts = cleanBase.split('\u200D', limit = 2)
                    "${parts[0]}$mod\u200D${parts[1]}"
                } else {
                    "$cleanBase$mod"
                }
            }
        }
    }

    fun savePreferredTone(context: Context, baseEmoji: String, chosenEmoji: String) {
        val base = getBaseEmoji(baseEmoji)
        preferredCache[base] = chosenEmoji
        preferredCache[baseEmoji] = chosenEmoji
        try {
            Skins.prefs(context).edit().putString("emoji_pref_$base", chosenEmoji).apply()
        } catch (_: Exception) {}
    }

    fun getPreferredEmoji(context: Context, baseEmoji: String): String {
        preferredCache[baseEmoji]?.let { return it }
        val base = getBaseEmoji(baseEmoji)
        preferredCache[base]?.let {
            preferredCache[baseEmoji] = it
            return it
        }
        val pref = try {
            Skins.prefs(context).getString("emoji_pref_$base", base) ?: base
        } catch (_: Exception) {
            base
        }
        preferredCache[base] = pref
        preferredCache[baseEmoji] = pref
        return pref
    }
}
