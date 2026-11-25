package gnosis.keyboard

object SecondaryKeyMapping {

    val QWERTY_SECONDARY: Map<Char, String> = mapOf(

        'q' to "1",
        'w' to "2",
        'e' to "3",
        'r' to "4",
        't' to "5",
        'y' to "6",
        'u' to "7",
        'i' to "8",
        'o' to "9",
        'p' to "0",

        'a' to "@",
        's' to "#",
        'd' to "$",
        'f' to "%",
        'g' to "&",
        'h' to "-",
        'j' to "+",
        'k' to "(",
        'l' to ")",

        'z' to "*",
        'x' to "\"",
        'c' to "'",
        'v' to ":",
        'b' to ";",
        'n' to "!",
        'm' to "?"
    )

    val QWERTZ_SECONDARY: Map<Char, String> = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'z' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
        'a' to "@", 's' to "#", 'd' to "$", 'f' to "%", 'g' to "&",
        'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'y' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'n' to "!", 'm' to "?"
    )

    val AZERTY_SECONDARY: Map<Char, String> = mapOf(
        'a' to "1", 'z' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
        'q' to "@", 's' to "#", 'd' to "$", 'f' to "%", 'g' to "&",
        'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")", 'm' to "/",
        'w' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'n' to "!"
    )

    val DVORAK_SECONDARY: Map<Char, String> = mapOf(
        'p' to "1", 'y' to "2", 'f' to "3", 'g' to "4", 'c' to "5",
        'r' to "6", 'l' to "7",
        'a' to "@", 'o' to "#", 'e' to "$", 'u' to "%", 'i' to "&",
        'd' to "-", 'h' to "+", 't' to "(", 'n' to ")", 's' to "/",
        'q' to "8", 'j' to "9", 'k' to "0", 'x' to "*", 'b' to "\"",
        'm' to "'", 'w' to ":", 'v' to ";", 'z' to "?"
    )

    val COLEMAK_SECONDARY: Map<Char, String> = mapOf(
        'q' to "1", 'w' to "2", 'f' to "3", 'p' to "4", 'g' to "5",
        'j' to "6", 'l' to "7", 'u' to "8", 'y' to "9",
        'a' to "@", 'r' to "#", 's' to "$", 't' to "%", 'd' to "&",
        'h' to "-", 'n' to "+", 'e' to "(", 'i' to ")", 'o' to "0",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'k' to "!", 'm' to "?"
    )

    fun getSecondaryChar(ch: Char, layoutName: String = "qwerty"): String? {
        val lower = ch.lowercaseChar()
        return when (layoutName.lowercase()) {
            "qwertz" -> QWERTZ_SECONDARY[lower] ?: QWERTY_SECONDARY[lower]
            "azerty" -> AZERTY_SECONDARY[lower] ?: QWERTY_SECONDARY[lower]
            "dvorak" -> DVORAK_SECONDARY[lower] ?: QWERTY_SECONDARY[lower]
            "colemak" -> COLEMAK_SECONDARY[lower] ?: QWERTY_SECONDARY[lower]
            "deva" -> null
            else -> QWERTY_SECONDARY[lower]
        }
    }

    fun getMappings(layoutName: String = "qwerty"): Map<Char, String> {
        return when (layoutName.lowercase()) {
            "qwertz" -> QWERTZ_SECONDARY
            "azerty" -> AZERTY_SECONDARY
            "dvorak" -> DVORAK_SECONDARY
            "colemak" -> COLEMAK_SECONDARY
            "deva" -> emptyMap()
            else -> QWERTY_SECONDARY
        }
    }
}
