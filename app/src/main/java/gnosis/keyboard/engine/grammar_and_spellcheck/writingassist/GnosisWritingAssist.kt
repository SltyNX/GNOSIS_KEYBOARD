package gnosis.keyboard.engine.grammar_and_spellcheck.writingassist

data class GnosisGrammarIssue(
    val originalText: String,
    val correctedText: String,
    val explanation: String = "Grammar & Style Fix"
)

/**
 * Gnosis_Engine - Writing Assist & Sentence Explorer
 *
 * Real-time punctuation, contraction, apostrophe, capitalization,
 * and homophone grammar checking integrated with Gnosis Sentence Explorer.
 */
object GnosisWritingAssist {

    val contractionFixes = mapOf(
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "ive" to "I've",
        "im" to "I'm",
        "iam" to "I'm",
        "youre" to "you're",
        "youare" to "you're",
        "theyre" to "they're",
        "theyare" to "they're",
        "weare" to "we're",
        "didnt" to "didn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "doesnt" to "doesn't",
        "thats" to "that's",
        "whats" to "what's",
        "theres" to "there's",
        "heres" to "here's",
        "lets" to "let's",
        "ill" to "I'll",
        "id" to "I'd",
        "youll" to "you'll",
        "youve" to "you've",
        "theyll" to "they'll",
        "theyve" to "they've",
        "weve" to "we've",
        "cmon" to "c'mon",
        "yall" to "y'all",
        "mightnt" to "mightn't",
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "shant" to "shan't",
        "aint" to "ain't",
        "whos" to "who's",
        "hows" to "how's",
        "whens" to "when's",
        "wheres" to "where's",
        "whys" to "why's",
        "couldve" to "could've",
        "shouldve" to "should've",
        "wouldve" to "would've",
        "mightve" to "might've",
        "mustve" to "must've",
        "wholl" to "who'll",
        "whove" to "who've",
        "howll" to "how'll",
        "howd" to "how'd",
        "whod" to "who'd",
        "hes" to "he's",
        "shes" to "she's",
        "itll" to "it'll",
        "thatll" to "that'll"
    )

    private val phraseFixes = listOf(
        // Homophones: their / there / they're
        Pair(Regex("(?i)\\btheir\\s+going\\b"), "they're going"),
        Pair(Regex("(?i)\\btheir\\s+coming\\b"), "they're coming"),
        Pair(Regex("(?i)\\btheir\\s+doing\\b"), "they're doing"),
        Pair(Regex("(?i)\\btheir\\s+here\\b"), "they're here"),
        Pair(Regex("(?i)\\btheir\\s+not\\b"), "they're not"),
        Pair(Regex("(?i)\\bover\\s+their\\b"), "over there"),
        Pair(Regex("(?i)\\bout\\s+their\\b"), "out there"),
        Pair(Regex("(?i)\\bin\\s+their\\b"), "in there"),
        Pair(Regex("(?i)\\bstay\\s+their\\b"), "stay there"),
        Pair(Regex("(?i)\\bthere\\s+car\\b"), "their car"),
        Pair(Regex("(?i)\\bthere\\s+house\\b"), "their house"),
        Pair(Regex("(?i)\\bthere\\s+phone\\b"), "their phone"),
        Pair(Regex("(?i)\\bthere\\s+family\\b"), "their family"),

        // Homophones: your / you're
        Pair(Regex("(?i)\\byour\\s+welcome\\b"), "you're welcome"),
        Pair(Regex("(?i)\\byour\\s+right\\b"), "you're right"),
        Pair(Regex("(?i)\\byour\\s+doing\\b"), "you're doing"),
        Pair(Regex("(?i)\\byour\\s+going\\b"), "you're going"),
        Pair(Regex("(?i)\\byour\\s+so\\b"), "you're so"),
        Pair(Regex("(?i)\\byou're\\s+name\\b"), "your name"),
        Pair(Regex("(?i)\\byou're\\s+phone\\b"), "your phone"),
        Pair(Regex("(?i)\\byou're\\s+car\\b"), "your car"),
        Pair(Regex("(?i)\\byou're\\s+email\\b"), "your email"),

        // Homophones: its / it's
        Pair(Regex("(?i)\\bits\\s+raining\\b"), "it's raining"),
        Pair(Regex("(?i)\\bits\\s+ok\\b"), "it's ok"),
        Pair(Regex("(?i)\\bits\\s+okay\\b"), "it's okay"),
        Pair(Regex("(?i)\\bits\\s+time\\b"), "it's time"),
        Pair(Regex("(?i)\\bits\\s+cold\\b"), "it's cold"),
        Pair(Regex("(?i)\\bits\\s+hot\\b"), "it's hot"),
        Pair(Regex("(?i)\\bits\\s+good\\b"), "it's good"),
        Pair(Regex("(?i)\\bits\\s+not\\b"), "it's not"),

        // Homophones: to / too
        Pair(Regex("(?i)\\bme\\s+to\\b"), "me too"),
        Pair(Regex("(?i)\\bto\\s+much\\b"), "too much"),
        Pair(Regex("(?i)\\bto\\s+late\\b"), "too late"),
        Pair(Regex("(?i)\\bto\\s+bad\\b"), "too bad"),
        Pair(Regex("(?i)\\bto\\s+far\\b"), "too far"),

        // Homophones: then / than
        Pair(Regex("(?i)\\bbetter\\s+then\\b"), "better than"),
        Pair(Regex("(?i)\\bmore\\s+then\\b"), "more than"),
        Pair(Regex("(?i)\\bless\\s+then\\b"), "less than"),

        // Common spelling compounds
        Pair(Regex("(?i)\\balot\\b"), "a lot"),
        Pair(Regex("(?i)\\baswell\\b"), "as well"),
        Pair(Regex("(?i)\\binfront\\b"), "in front"),
        Pair(Regex("(?i)\\bthankyou\\b"), "thank you")
    )

    @Volatile
    private var installedContractions: Map<String, String> = emptyMap()

    fun setInstalledContractions(contractions: Map<String, String>) {
        installedContractions = contractions
    }

    fun getContraction(rawWord: String): String? {
        val lower = rawWord.lowercase()
        val fix = installedContractions[lower] ?: contractionFixes[lower] ?: return null
        return formatMatchCase(rawWord, fix)
    }

    private fun formatMatchCase(source: String, target: String): String {
        if (source.isEmpty()) return target
        val isFirstUpper = source.first().isUpperCase()
        val isAllUpper = source.length > 1 && source.all { it.isUpperCase() }
        return when {
            isAllUpper -> target.uppercase()
            isFirstUpper -> target.replaceFirstChar { it.uppercase() }
            else -> target
        }
    }

    fun analyzeText(text: String): GnosisGrammarIssue? {
        if (text.length < 3) return null

        // 1. Check phrase fixes (homophones and common grammatical patterns)
        for ((regex, fix) in phraseFixes) {
            val match = regex.find(text)
            if (match != null) {
                val orig = match.value
                val formattedFix = if (orig.firstOrNull()?.isUpperCase() == true) {
                    fix.replaceFirstChar { it.uppercase() }
                } else fix
                return GnosisGrammarIssue(orig, formattedFix, "Grammar agreement fix")
            }
        }

        // 2. Check lone lowercase 'i'
        val words = text.split(Regex("\\s+"))
        for (w in words) {
            val clean = w.trim('?', '.', '!', ',', '"', '\'')
            if (clean == "i") {
                return GnosisGrammarIssue("i", "I", "Capitalize pronoun 'I'")
            }
        }

        // 3. Check contractions for the last completed word
        val lastWord = words.lastOrNull()?.trim('?', '.', '!', ',', ' ') ?: ""
        val contraction = getContraction(lastWord)
        if (contraction != null && !lastWord.equals(contraction, ignoreCase = false)) {
            return GnosisGrammarIssue(lastWord, contraction, "Missing apostrophe")
        }

        return null
    }
}
