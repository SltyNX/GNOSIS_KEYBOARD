package gnosis.keyboard

object CoolFontEngine {

    enum class FontStyle(val id: String, val displayName: String, val previewText: String) {
        NORMAL("normal", "Normal", "Default"),
        BOLD("bold", "Bold", "𝐁𝐨𝐥𝐝"),
        ITALIC("italic", "Italic", "𝐼𝑡𝑎𝑙𝑖𝑐"),
        BOLD_ITALIC("bold_italic", "Bold Italic", "𝑩𝒐𝒍𝒅 𝑰𝒕𝒂𝒍𝒊𝒄"),
        SCRIPT("script", "Script", "𝒮𝒸𝓇𝒾𝓅𝓉"),
        MONOSPACE("monospace", "Monospace", "𝙼𝚘𝚗𝚘"),
        CIRCLED("circled", "Circled", "Ⓒⓘⓡⓒⓛⓔ"),
        GOTHIC("gothic", "Gothic", "𝔊𝔬𝔱𝔥𝔦𝔠"),
        SMALL_CAPS("small_caps", "Small Caps", "ꜱᴍᴀʟʟ"),
        FULLWIDTH("fullwidth", "Wide", "Ｗｉｄｅ")
    }

    private val SCRIPT_UPPER_SPECIAL = mapOf(
        'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131", 'H' to "\u210B",
        'I' to "\u2110", 'L' to "\u2112", 'M' to "\u2133", 'R' to "\u211B"
    )
    private val SCRIPT_LOWER_SPECIAL = mapOf(
        'e' to "\u212F", 'g' to "\u210A", 'o' to "\u2134"
    )

    private val GOTHIC_UPPER_SPECIAL = mapOf(
        'C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111", 'R' to "\u211C", 'Z' to "\u2128"
    )

    private val SMALL_CAPS_MAP = mapOf(
        'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ꜰ",
        'g' to "ɢ", 'h' to "ʜ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ",
        'm' to "ᴍ", 'n' to "ɴ", 'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ",
        's' to "ꜱ", 't' to "ᴛ", 'u' to "ᴜ", 'v' to "ᴠ", 'w' to "ᴡ", 'x' to "x",
        'y' to "ʏ", 'z' to "ᴢ"
    )

    fun transform(text: String, style: FontStyle): String {
        if (style == FontStyle.NORMAL || text.isEmpty()) return text
        val sb = StringBuilder()
        for (i in 0 until text.length) {
            sb.append(transformChar(text[i], style))
        }
        return sb.toString()
    }

    fun transformChar(c: Char, style: FontStyle): String {
        return when (style) {
            FontStyle.NORMAL -> c.toString()
            FontStyle.BOLD -> when {
                c in 'A'..'Z' -> String(Character.toChars(0x1D400 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D41A + (c - 'a')))
                c in '0'..'9' -> String(Character.toChars(0x1D7CE + (c - '0')))
                else -> c.toString()
            }
            FontStyle.ITALIC -> when {
                c == 'h' -> "\u210E"
                c in 'A'..'Z' -> String(Character.toChars(0x1D434 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D44E + (c - 'a')))
                else -> c.toString()
            }
            FontStyle.BOLD_ITALIC -> when {
                c in 'A'..'Z' -> String(Character.toChars(0x1D468 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D482 + (c - 'a')))
                else -> c.toString()
            }
            FontStyle.SCRIPT -> when {
                SCRIPT_UPPER_SPECIAL.containsKey(c) -> SCRIPT_UPPER_SPECIAL[c]!!
                SCRIPT_LOWER_SPECIAL.containsKey(c) -> SCRIPT_LOWER_SPECIAL[c]!!
                c in 'A'..'Z' -> String(Character.toChars(0x1D49C + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D4B6 + (c - 'a')))
                else -> c.toString()
            }
            FontStyle.MONOSPACE -> when {
                c in 'A'..'Z' -> String(Character.toChars(0x1D670 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D68A + (c - 'a')))
                c in '0'..'9' -> String(Character.toChars(0x1D7F6 + (c - '0')))
                else -> c.toString()
            }
            FontStyle.CIRCLED -> when {
                c in 'A'..'Z' -> String(Character.toChars(0x24B6 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x24D0 + (c - 'a')))
                c in '1'..'9' -> String(Character.toChars(0x2460 + (c - '1')))
                c == '0' -> "\u24EA"
                else -> c.toString()
            }
            FontStyle.GOTHIC -> when {
                GOTHIC_UPPER_SPECIAL.containsKey(c) -> GOTHIC_UPPER_SPECIAL[c]!!
                c in 'A'..'Z' -> String(Character.toChars(0x1D504 + (c - 'A')))
                c in 'a'..'z' -> String(Character.toChars(0x1D51E + (c - 'a')))
                else -> c.toString()
            }
            FontStyle.SMALL_CAPS -> {
                val lower = c.lowercaseChar()
                SMALL_CAPS_MAP[lower] ?: c.toString()
            }
            FontStyle.FULLWIDTH -> when {
                c in '!'..'~' -> String(Character.toChars(0xFF01 + (c - '!')))
                c == ' ' -> "\u3000"
                else -> c.toString()
            }
        }
    }
}
