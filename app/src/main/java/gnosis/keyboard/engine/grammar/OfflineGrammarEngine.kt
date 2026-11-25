package gnosis.keyboard.engine.grammar

import gnosis.keyboard.engine.GrammarIssue
import java.util.regex.Pattern

class OfflineGrammarEngine : GrammarChecker {

    private data class GrammarRule(
        val ruleId: String,
        val pattern: Pattern,
        val message: String,
        val replacementGenerator: (java.util.regex.Matcher) -> List<String>
    )

    private val rules = mutableListOf<GrammarRule>()

    init {
        registerRules()
    }

    private fun registerRules() {
        addRegexRule(
            id = "POSSESSIVE_THEIRS",
            regex = """(?i)\btheir's\b""",
            message = "Possessive pronoun 'theirs' never has an apostrophe.",
            replacements = listOf("theirs", "they're")
        )
        addRegexRule(
            id = "POSSESSIVE_YOURS",
            regex = """(?i)\byour's\b""",
            message = "Possessive pronoun 'yours' never has an apostrophe.",
            replacements = listOf("yours")
        )
        addRegexRule(
            id = "POSSESSIVE_OURS",
            regex = """(?i)\bour's\b""",
            message = "Possessive pronoun 'ours' never has an apostrophe.",
            replacements = listOf("ours")
        )
        addRegexRule(
            id = "POSSESSIVE_HERS",
            regex = """(?i)\bher's\b""",
            message = "Possessive pronoun 'hers' never has an apostrophe.",
            replacements = listOf("hers")
        )
        addRegexRule(
            id = "POSSESSIVE_ITS_TRAILING",
            regex = """(?i)\bits'\b""",
            message = "Did you mean 'its' or 'it's'?",
            replacements = listOf("its", "it's")
        )

        addDynamicRule(
            id = "HOMOPHONE_YOUR_WELCOME",
            regex = """(?i)\byour\s+welcome\b""",
            message = "Did you mean 'you're welcome'?",
            generator = { m ->
                val orig = m.group(0)!!
                val target = if (orig[0].isUpperCase()) "You're welcome" else "you're welcome"
                listOf(target)
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_YOURE_NOUN",
            regex = """(?i)\byou're\s+(car|house|phone|book|turn|name|time|family|dog|cat|computer|email|idea|friend|job|work|wallet|clothes|shoes)\b""",
            message = "Did you mean possessive 'your'?",
            generator = { m ->
                val noun = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "Your" else "your"
                listOf("$pref $noun")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_YOUR_RIGHT",
            regex = """(?i)\b(yes\s+|you\s+know\s+|i\s+think\s+)?your\s+right\b""",
            message = "Did you mean 'you're right'?",
            generator = { m ->
                val prefix = m.group(1) ?: ""
                val isUpper = m.group(0)!![0].isUpperCase()
                val target = if (isUpper) "You're right" else "you're right"
                listOf(prefix + target)
            }
        )

        addDynamicRule(
            id = "HOMOPHONE_THEIR_PREDICATE",
            regex = """(?i)\btheir\s+(going|coming|doing|here|not|very|always|also|so|still|probably|actually|getting|making)\b""",
            message = "Did you mean 'they're' (they are)?",
            generator = { m ->
                val word = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "They're" else "they're"
                listOf("$pref $word")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_THERE_NOUN",
            regex = """(?i)\bthere\s+(house|car|phone|dog|cat|mom|dad|family|friend|kid|kids|team|teacher|room|bag|job)\b""",
            message = "Did you mean possessive 'their'?",
            generator = { m ->
                val word = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "Their" else "their"
                listOf("$pref $word")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_THEYRE_NOUN",
            regex = """(?i)\bthey're\s+(house|car|phone|dog|cat|mom|dad|family|friend|kid|kids|team|teacher|room|bag|job)\b""",
            message = "Did you mean possessive 'their'?",
            generator = { m ->
                val word = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "Their" else "their"
                listOf("$pref $word")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_OVER_THEIR",
            regex = """(?i)\b(over|right|out|in|up|down)\s+their\b""",
            message = "Did you mean 'there' (location)?",
            generator = { m ->
                val prep = m.group(1)
                listOf("$prep there")
            }
        )

        addDynamicRule(
            id = "HOMOPHONE_ITS_PREDICATE",
            regex = """(?i)\bits\s+(a|an|the|not|been|ok|okay|cool|great|fine|good|true|hard|easy|time|going|working|about|worth|possible)\b""",
            message = "Did you mean 'it's' (it is / it has)?",
            generator = { m ->
                val follow = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "It's" else "it's"
                listOf("$pref $follow")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_ITS_CONTRACTION_NOUN",
            regex = """(?i)\bit's\s+(color|tail|size|name|shape|price|owner|feature|battery|screen|camera|weight|speed)\b""",
            message = "Did you mean possessive 'its' without apostrophe?",
            generator = { m ->
                val noun = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "Its" else "its"
                listOf("$pref $noun")
            }
        )

        val tooAdjectives = "much|many|late|early|fast|slow|far|hot|cold|hard|heavy|expensive|good|bad|close|high|low|little|easy|short|long|big|small"
        addDynamicRule(
            id = "HOMOPHONE_TOO_INTENSIFIER",
            regex = """(?i)\bto\s+($tooAdjectives)\b""",
            message = "Use 'too' when meaning 'excessively' or 'also'.",
            generator = { m ->
                val adj = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val pref = if (isUpper) "Too" else "too"
                listOf("$pref $adj")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_ME_TO",
            regex = """(?i)\bme\s+to\b""",
            message = "Did you mean 'me too'?",
            generator = { _ -> listOf("me too") }
        )

        addDynamicRule(
            id = "COMPARATIVE_THAN",
            regex = """(?i)\b(better|worse|more|less|faster|slower|bigger|smaller|greater|higher|lower|rather|other|easier|harder|older|younger)\s+then\b""",
            message = "Use 'than' for comparisons instead of 'then'.",
            generator = { m ->
                val comp = m.group(1)
                listOf("$comp than")
            }
        )

        val modals = listOf("should", "could", "would", "must", "might")
        for (modal in modals) {
            addDynamicRule(
                id = "MODAL_${modal.uppercase()}_HAVE",
                regex = """(?i)\b$modal\s+of\b""",
                message = "Use '$modal have' instead of '$modal of'.",
                generator = { m ->
                    val isUpper = m.group(0)!![0].isUpperCase()
                    val mod = if (isUpper) modal.replaceFirstChar { it.uppercaseChar() } else modal
                    listOf("$mod have", "$mod've")
                }
            )
        }

        addDynamicRule(
            id = "HOMOPHONE_LOOSE_LOSE",
            regex = """(?i)\b(don't|gonna|will|might|to|did|can't)\s+loose\b""",
            message = "Did you mean 'lose' (to be deprived of)?",
            generator = { m ->
                val aux = m.group(1)
                listOf("$aux lose")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_AFFECT_EFFECT",
            regex = """(?i)\b(side|special|sound|visual|after)\s+affect\b""",
            message = "Did you mean 'effect' (noun)?",
            generator = { m ->
                val adj = m.group(1)
                listOf("$adj effect")
            }
        )
        addDynamicRule(
            id = "HOMOPHONE_WEATHER_WHETHER",
            regex = """(?i)\bweather\s+or\s+not\b""",
            message = "Did you mean 'whether or not'?",
            generator = { _ -> listOf("whether or not") }
        )
        addDynamicRule(
            id = "HOMOPHONE_PEACE_OF_MIND",
            regex = """(?i)\bpiece\s+of\s+mind\b""",
            message = "Did you mean 'peace of mind'?",
            generator = { _ -> listOf("peace of mind") }
        )

        addDynamicRule(
            id = "SVA_HE_SHE_IT_ARE",
            regex = """(?i)\b(he|she|it)\s+are\b""",
            message = "Singular pronoun requires 'is'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub is")
            }
        )
        addDynamicRule(
            id = "SVA_THEY_WE_YOU_IS",
            regex = """(?i)\b(they|we|you)\s+is\b""",
            message = "Plural pronoun requires 'are'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub are")
            }
        )
        addDynamicRule(
            id = "SVA_HE_SHE_IT_HAVE",
            regex = """(?i)\b(he|she|it)\s+have\b""",
            message = "Third-person singular requires 'has'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub has")
            }
        )
        addDynamicRule(
            id = "SVA_THEY_WE_HAS",
            regex = """(?i)\b(they|we)\s+has\b""",
            message = "Plural pronoun requires 'have'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub have")
            }
        )
        addDynamicRule(
            id = "SVA_HE_SHE_DO",
            regex = """(?i)\b(he|she|it)\s+do\b""",
            message = "Third-person singular requires 'does'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub does")
            }
        )
        addDynamicRule(
            id = "SVA_HE_SHE_DONT",
            regex = """(?i)\b(he|she|it)\s+(don't|dont)\b""",
            message = "Third-person singular requires 'doesn't'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub doesn't")
            }
        )
        addDynamicRule(
            id = "SVA_THEY_WE_DOESNT",
            regex = """(?i)\b(they|we|you)\s+(doesn't|doesnt)\b""",
            message = "Plural pronoun requires 'don't'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub don't")
            }
        )
        addDynamicRule(
            id = "SVA_I_IS_ARE",
            regex = """(?i)\bI\s+(is|are)\b""",
            message = "First-person pronoun 'I' requires 'am'.",
            generator = { _ -> listOf("I am") }
        )
        addDynamicRule(
            id = "SVA_I_HAS",
            regex = """(?i)\bI\s+has\b""",
            message = "First-person pronoun 'I' requires 'have'.",
            generator = { _ -> listOf("I have") }
        )
        addDynamicRule(
            id = "SVA_THEY_WE_YOU_WAS",
            regex = """(?i)\b(they|we|you)\s+was\b""",
            message = "Plural pronoun requires 'were'.",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub were")
            }
        )

        addDynamicRule(
            id = "ARTICLE_A_BEFORE_VOWEL",
            regex = """(?i)\ba\s+(apple|orange|egg|elephant|idiot|island|umbrella|hour|honor|honest|uncle|answer|example|issue|opportunity|idea|action|animal|option|article|error|update)\b""",
            message = "Use 'an' before words starting with a vowel sound.",
            generator = { m ->
                val n = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val art = if (isUpper) "An" else "an"
                listOf("$art $n")
            }
        )
        addDynamicRule(
            id = "ARTICLE_AN_BEFORE_CONSONANT",
            regex = """(?i)\ban\s+(car|dog|cat|house|phone|book|person|table|pen|friend|computer|problem|game|user|year|keyboard|screen|camera|photo|video|message)\b""",
            message = "Use 'a' before words starting with a consonant sound.",
            generator = { m ->
                val n = m.group(1)
                val isUpper = m.group(0)!![0].isUpperCase()
                val art = if (isUpper) "A" else "a"
                listOf("$art $n")
            }
        )

        addDynamicRule(
            id = "PHRASE_SUPPOSE_TO",
            regex = """(?i)\b(is|are|was|were|am)\s+suppose\s+to\b""",
            message = "Did you mean 'supposed to'?",
            generator = { m ->
                val aux = m.group(1)
                listOf("$aux supposed to")
            }
        )
        addDynamicRule(
            id = "PHRASE_USE_TO",
            regex = """(?i)\b(i|we|they|he|she|you)\s+use\s+to\b""",
            message = "Did you mean 'used to'?",
            generator = { m ->
                val sub = m.group(1)
                listOf("$sub used to")
            }
        )
        addDynamicRule(
            id = "PHRASE_ALL_OF_SUDDEN",
            regex = """(?i)\ball\s+of\s+sudden\b""",
            message = "The standard idiom is 'all of a sudden'.",
            generator = { _ -> listOf("all of a sudden") }
        )
        addDynamicRule(
            id = "COMPOUND_ALOT",
            regex = """(?i)\balot\b""",
            message = "'a lot' is written as two words.",
            generator = { m ->
                val isUpper = m.group(0)!![0].isUpperCase()
                listOf(if (isUpper) "A lot" else "a lot")
            }
        )
        addDynamicRule(
            id = "COMPOUND_INFRONT",
            regex = """(?i)\binfront\b""",
            message = "'in front' is written as two words.",
            generator = { m ->
                val isUpper = m.group(0)!![0].isUpperCase()
                listOf(if (isUpper) "In front" else "in front")
            }
        )
        addDynamicRule(
            id = "COMPOUND_ASWELL",
            regex = """(?i)\baswell\b""",
            message = "'as well' is written as two words.",
            generator = { _ -> listOf("as well") }
        )
        addDynamicRule(
            id = "COMPOUND_EACHOTHER",
            regex = """(?i)\beachother\b""",
            message = "'each other' is written as two words.",
            generator = { _ -> listOf("each other") }
        )
        addDynamicRule(
            id = "COMPOUND_EVERYTIME",
            regex = """(?i)\beverytime\b""",
            message = "'every time' is written as two words.",
            generator = { _ -> listOf("every time") }
        )
        addDynamicRule(
            id = "COMPOUND_ATLEAST",
            regex = """(?i)\batleast\b""",
            message = "'at least' is written as two words.",
            generator = { _ -> listOf("at least") }
        )

        addDynamicRule(
            id = "REPEATED_WORD",
            regex = """(?i)\b([a-zA-Z]{2,})\s+\1\b""",
            message = "Duplicate word detected.",
            generator = { m ->
                val word = m.group(1)!!
                listOf(word)
            }
        )

        addDynamicRule(
            id = "PUNCT_COMMA_SPACE",
            regex = """([a-zA-Z]),([a-zA-Z])""",
            message = "Missing space after comma.",
            generator = { m ->
                val c1 = m.group(1)
                val c2 = m.group(2)
                listOf("$c1, $c2")
            }
        )
    }

    private fun addRegexRule(id: String, regex: String, message: String, replacements: List<String>) {
        rules.add(
            GrammarRule(
                ruleId = id,
                pattern = Pattern.compile(regex),
                message = message,
                replacementGenerator = { replacements }
            )
        )
    }

    private fun addDynamicRule(id: String, regex: String, message: String, generator: (java.util.regex.Matcher) -> List<String>) {
        rules.add(
            GrammarRule(
                ruleId = id,
                pattern = Pattern.compile(regex),
                message = message,
                replacementGenerator = generator
            )
        )
    }

    override fun check(text: String): List<GrammarIssue> {
        if (text.isBlank()) return emptyList()

        val issues = mutableListOf<GrammarIssue>()

        for (rule in rules) {
            val matcher = rule.pattern.matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val suggestions = rule.replacementGenerator(matcher)

                issues.add(
                    GrammarIssue(
                        start = start,
                        end = end,
                        message = rule.message,
                        suggestions = suggestions,
                        ruleId = rule.ruleId
                    )
                )
            }
        }

        return issues.sortedBy { it.start }
    }
}
