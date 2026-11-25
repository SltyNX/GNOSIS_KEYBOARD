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

    private val REVERSE_SMALL_CAPS_MAP = SMALL_CAPS_MAP.entries.associate { (k, v) -> v to k.toString() }
    private val REVERSE_SCRIPT_UPPER = SCRIPT_UPPER_SPECIAL.entries.associate { (k, v) -> v to k.toString() }
    private val REVERSE_SCRIPT_LOWER = SCRIPT_LOWER_SPECIAL.entries.associate { (k, v) -> v to k.toString() }
    private val REVERSE_GOTHIC_UPPER = GOTHIC_UPPER_SPECIAL.entries.associate { (k, v) -> v to k.toString() }

    fun normalizeCodePoint(cp: Int): String? {
        return when (cp) {
            in 0x1D400..0x1D419 -> ('A'.code + (cp - 0x1D400)).toChar().toString()
            in 0x1D41A..0x1D433 -> ('a'.code + (cp - 0x1D41A)).toChar().toString()
            in 0x1D7CE..0x1D7D7 -> ('0'.code + (cp - 0x1D7CE)).toChar().toString()
            0x210E -> "h"
            in 0x1D434..0x1D44D -> ('A'.code + (cp - 0x1D434)).toChar().toString()
            in 0x1D44E..0x1D467 -> ('a'.code + (cp - 0x1D44E)).toChar().toString()
            in 0x1D468..0x1D481 -> ('A'.code + (cp - 0x1D468)).toChar().toString()
            in 0x1D482..0x1D49B -> ('a'.code + (cp - 0x1D482)).toChar().toString()
            in 0x1D49C..0x1D4B5 -> ('A'.code + (cp - 0x1D49C)).toChar().toString()
            in 0x1D4B6..0x1D4CF -> ('a'.code + (cp - 0x1D4B6)).toChar().toString()
            in 0x1D670..0x1D689 -> ('A'.code + (cp - 0x1D670)).toChar().toString()
            in 0x1D68A..0x1D6A3 -> ('a'.code + (cp - 0x1D68A)).toChar().toString()
            in 0x1D7F6..0x1D7FF -> ('0'.code + (cp - 0x1D7F6)).toChar().toString()
            in 0x24B6..0x24CF -> ('A'.code + (cp - 0x24B6)).toChar().toString()
            in 0x24D0..0x24E9 -> ('a'.code + (cp - 0x24D0)).toChar().toString()
            in 0x2460..0x2468 -> ('1'.code + (cp - 0x2460)).toChar().toString()
            0x24EA -> "0"
            in 0x1D504..0x1D51D -> ('A'.code + (cp - 0x1D504)).toChar().toString()
            in 0x1D51E..0x1D537 -> ('a'.code + (cp - 0x1D51E)).toChar().toString()
            in 0xFF01..0xFF5E -> (cp - 0xFEE0).toChar().toString()
            0x3000 -> " "
            else -> null
        }
    }

    fun normalize(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val charCount = Character.charCount(cp)
            val mapped = normalizeCodePoint(cp)
            if (mapped != null) {
                sb.append(mapped)
            } else {
                val str = text.substring(i, i + charCount)
                val special = REVERSE_SMALL_CAPS_MAP[str]
                    ?: REVERSE_SCRIPT_UPPER[str]
                    ?: REVERSE_SCRIPT_LOWER[str]
                    ?: REVERSE_GOTHIC_UPPER[str]
                if (special != null) {
                    sb.append(special)
                } else {
                    sb.append(str)
                }
            }
            i += charCount
        }
        return sb.toString()
    }

    fun getTransformedLength(asciiText: String, style: FontStyle): Int {
        if (style == FontStyle.NORMAL || asciiText.isEmpty()) return asciiText.length
        return transform(asciiText, style).length
    }

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
