package gnosis.keyboard

import android.content.Context

object EmojiSkinTones {

    val MODIFIERS = listOf(
        "",
        "\uD83C\uDFFB",
        "\uD83C\uDFFC",
        "\uD83C\uDFFD",
        "\uD83C\uDFFE",
        "\uD83C\uDFFF"
    )

    private val MODIFIER_REGEX = Regex("[\uD83C\uDFFB-\uD83C\uDFFF]")

    fun hasSkinTone(emoji: String): Boolean = MODIFIER_REGEX.containsMatchIn(emoji)

    fun getBaseEmoji(emoji: String): String = emoji.replace(MODIFIER_REGEX, "")

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
        Skins.prefs(context).edit().putString("emoji_pref_$base", chosenEmoji).apply()
    }

    fun getPreferredEmoji(context: Context, baseEmoji: String): String {
        val base = getBaseEmoji(baseEmoji)
        return Skins.prefs(context).getString("emoji_pref_$base", base) ?: base
    }
}
