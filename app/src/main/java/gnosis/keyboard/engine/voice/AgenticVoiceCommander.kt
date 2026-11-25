package gnosis.keyboard.engine.voice

import java.util.regex.Pattern

data class AgenticVoiceResult(
    val processedText: String,
    val command: VoiceCommand?,
    val isCommandOnly: Boolean
)

enum class VoiceCommand {
    DELETE_THAT,
    DELETE_WORD,
    CLEAR_ALL,
    UNDO
}

object AgenticVoiceCommander {

    private val PUNCTUATION_REPLACEMENTS = listOf(
        Pattern.compile("""(?i)\s*\bcomma\b""") to ",",
        Pattern.compile("""(?i)\s*\b(period|full stop|dot)\b""") to ".",
        Pattern.compile("""(?i)\s*\bquestion mark\b""") to "?",
        Pattern.compile("""(?i)\s*\b(exclamation mark|exclamation point)\b""") to "!",
        Pattern.compile("""(?i)\s*\bcolon\b""") to ":",
        Pattern.compile("""(?i)\s*\b(semi colon|semicolon)\b""") to ";",
        Pattern.compile("""(?i)\s*\b(new line|next line|enter)\b""") to "\n",
        Pattern.compile("""(?i)\s*\bnew paragraph\b""") to "\n\n"
    )

    private val CMD_DELETE_THAT = Pattern.compile("""(?i)^(delete that|scratch that|remove that)$""")
    private val CMD_DELETE_WORD = Pattern.compile("""(?i)^(delete word|delete last word|backspace)$""")
    private val CMD_CLEAR_ALL = Pattern.compile("""(?i)^(clear all|delete all|clear text)$""")
    private val CMD_UNDO = Pattern.compile("""(?i)^(undo|undo that)$""")

    fun process(spokenText: String): AgenticVoiceResult {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) {
            return AgenticVoiceResult("", null, false)
        }

        if (CMD_DELETE_THAT.matcher(trimmed).matches()) {
            return AgenticVoiceResult("", VoiceCommand.DELETE_THAT, true)
        }
        if (CMD_DELETE_WORD.matcher(trimmed).matches()) {
            return AgenticVoiceResult("", VoiceCommand.DELETE_WORD, true)
        }
        if (CMD_CLEAR_ALL.matcher(trimmed).matches()) {
            return AgenticVoiceResult("", VoiceCommand.CLEAR_ALL, true)
        }
        if (CMD_UNDO.matcher(trimmed).matches()) {
            return AgenticVoiceResult("", VoiceCommand.UNDO, true)
        }

        var punctuated = trimmed
        for ((pattern, replacement) in PUNCTUATION_REPLACEMENTS) {
            val matcher = pattern.matcher(punctuated)
            if (matcher.find()) {
                punctuated = matcher.replaceAll(replacement)
            }
        }

        punctuated = punctuated
            .replace(Regex("""\s+([.,!?:;])"""), "$1")
            .replace(Regex("""([.,!?:;])([A-Za-z0-9])"""), "$1 $2")
            .replace(Regex("""\s{2,}"""), " ")

        return AgenticVoiceResult(punctuated, null, false)
    }
}
