package gnosis.keyboard.engine.grammar

import java.util.Locale
import java.util.regex.Pattern

object GnosisGrammarEngine {

    data class GrammarIssue(
        val originalText: String,
        val correctedText: String,
        val explanation: String,
        val startIndex: Int,
        val length: Int
    )

    private data class GrammarRule(
        val id: String,
        val language: String,
        val pattern: Pattern,
        val explanation: String,
        val replacer: (String, java.util.regex.Matcher) -> String?
    )

    private val rules = mutableListOf<GrammarRule>()

    init {
        registerUniversalRules()
        registerEnglishRules()
        registerSpanishRules()
        registerFrenchRules()
        registerGermanRules()
        registerHindiRules()
    }

    fun checkGrammar(textBeforeCursor: CharSequence?, language: String = "en"): GrammarIssue? {
        if (textBeforeCursor == null || textBeforeCursor.length < 3) return null
        val text = textBeforeCursor.toString()

        val lastSentenceBoundary = maxOf(
            text.lastIndexOf('.'),
            text.lastIndexOf('!'),
            text.lastIndexOf('?'),
            text.lastIndexOf('\n')
        )
        val clauseStart = if (lastSentenceBoundary >= 0) lastSentenceBoundary + 1 else 0
        val clause = text.substring(clauseStart)
        if (clause.trim().isEmpty()) return null

        val lang = language.lowercase(Locale.ROOT)

        for (rule in rules) {
            if (rule.language != "universal" && rule.language != lang && !(rule.language == "hi" && lang == "hinglish")) {
                continue
            }

            val matcher = rule.pattern.matcher(clause)
            if (matcher.find()) {
                val orig = matcher.group(0) ?: continue
                val corrected = rule.replacer(orig, matcher) ?: continue
                if (orig != corrected) {
                    val issueStart = clauseStart + matcher.start()
                    return GrammarIssue(
                        originalText = orig,
                        correctedText = corrected,
                        explanation = rule.explanation,
                        startIndex = issueStart,
                        length = orig.length
                    )
                }
            }
        }
        return null
    }

    private fun registerUniversalRules() {
        rules.add(GrammarRule(
            id = "SPACE_BEFORE_PUNCT",
            language = "universal",
            pattern = Pattern.compile("([a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u0900-\u097F]+)\\s+([,.:;!?])"),
            explanation = "Remove unnecessary space before punctuation.",
            replacer = { _, m ->
                val w = m.group(1) ?: ""
                val p = m.group(2) ?: ""
                "$w$p"
            }
        ))

        rules.add(GrammarRule(
            id = "UNIVERSAL_DUPLICATE_WORDS",
            language = "universal",
            pattern = Pattern.compile("(?i)\\b([a-zA-Z\u00C0-\u024F\u0400-\u04FF\u0900-\u097F]{2,})\\s+\\1\\b"),
            explanation = "Redundant duplicate word.",
            replacer = { orig, m ->
                val w = m.group(1) ?: ""
                if (orig[0].isUpperCase()) w.replaceFirstChar { it.uppercase() } else w.lowercase()
            }
        ))
    }

    private fun registerEnglishRules() {
        rules.add(GrammarRule(
            id = "SUBJ_VERB_DONT",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(he|she|it|that|this|everyone|everybody|someone|somebody)\\s+(don't|dont)\\b"),
            explanation = "Subject-verb agreement: use 'doesn't' with third-person singular.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "he"
                val cap = orig[0].isUpperCase()
                if (cap) "${subj.replaceFirstChar { it.uppercase() }} doesn't" else "${subj.lowercase()} doesn't"
            }
        ))

        rules.add(GrammarRule(
            id = "SUBJ_VERB_WAS",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(they|we|you)\\s+was\\b"),
            explanation = "Subject-verb agreement: use 'were' with plural subjects.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "they"
                val cap = orig[0].isUpperCase()
                if (cap) "${subj.replaceFirstChar { it.uppercase() }} were" else "${subj.lowercase()} were"
            }
        ))

        rules.add(GrammarRule(
            id = "SUBJ_VERB_HAVE",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(he|she|it)\\s+have\\b"),
            explanation = "Subject-verb agreement: use 'has' with third-person singular.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "he"
                val cap = orig[0].isUpperCase()
                if (cap) "${subj.replaceFirstChar { it.uppercase() }} has" else "${subj.lowercase()} has"
            }
        ))

        rules.add(GrammarRule(
            id = "SUBJ_VERB_I_IS",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(i)\\s+is\\b"),
            explanation = "Subject-verb agreement: use 'I am'.",
            replacer = { _, _ -> "I am" }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_YOUR_WELCOME",
            language = "en",
            pattern = Pattern.compile("(?i)\\byour\\s+welcome\\b"),
            explanation = "Did you mean 'You're welcome'?",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "You're welcome" else "you're welcome"
            }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_YOURE_NOUN",
            language = "en",
            pattern = Pattern.compile("(?i)\\byou're\\s+(car|house|phone|turn|name|friend|job|email|idea|family|book|time|room|work)\\b"),
            explanation = "Use possessive 'your' before a noun.",
            replacer = { orig, m ->
                val noun = m.group(1) ?: ""
                val cap = orig[0].isUpperCase()
                if (cap) "Your $noun" else "your $noun"
            }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_THEIR_PRED",
            language = "en",
            pattern = Pattern.compile("(?i)\\btheir\\s+(coming|here|going|ready|waiting|not|right)\\b"),
            explanation = "Did you mean 'they're' (they are)?",
            replacer = { orig, m ->
                val word = m.group(1) ?: ""
                val cap = orig[0].isUpperCase()
                if (cap) "They're $word" else "they're $word"
            }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_OVER_THEIR",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(went|over|right|out|in|from|to)\\s+their\\b"),
            explanation = "Use 'there' to refer to a location.",
            replacer = { orig, m ->
                val prep = m.group(1) ?: "over"
                val cap = orig[0].isUpperCase()
                if (cap) "${prep.replaceFirstChar { it.uppercase() }} there" else "${prep.lowercase()} there"
            }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_THEN_THAN",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(better|more|less|greater|smaller|faster|slower|earlier|later|rather)\\s+then\\b"),
            explanation = "Use 'than' for comparisons.",
            replacer = { orig, m ->
                val comp = m.group(1) ?: "more"
                val cap = orig[0].isUpperCase()
                if (cap) "${comp.replaceFirstChar { it.uppercase() }} than" else "${comp.lowercase()} than"
            }
        ))

        rules.add(GrammarRule(
            id = "COULD_OF",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(could|should|would|must|might)\\s+of\\b"),
            explanation = "Use 'have' instead of 'of' with modal verbs.",
            replacer = { orig, m ->
                val modal = m.group(1) ?: "could"
                val cap = orig[0].isUpperCase()
                if (cap) "${modal.replaceFirstChar { it.uppercase() }} have" else "${modal.lowercase()} have"
            }
        ))

        rules.add(GrammarRule(
            id = "HOMOPHONE_ITS_PRED",
            language = "en",
            pattern = Pattern.compile("(?i)\\bits\\s+(ok|okay|fine|cold|hot|raining|done|good|great|bad|time|too|already|always|hard|easy)\\b"),
            explanation = "Use 'it's' (contraction of it is).",
            replacer = { orig, m ->
                val adj = m.group(1) ?: ""
                val cap = orig[0].isUpperCase()
                if (cap) "It's $adj" else "it's $adj"
            }
        ))

        rules.add(GrammarRule(
            id = "STANDALONE_I",
            language = "en",
            pattern = Pattern.compile("(^|\\s)i(\\s|$)"),
            explanation = "The pronoun 'I' must always be capitalized.",
            replacer = { orig, _ ->
                orig.replace('i', 'I')
            }
        ))

        rules.add(GrammarRule(
            id = "SUBJ_VERB_ARE",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(he|she|it|everyone|everybody|someone|somebody)\\s+are\\b"),
            explanation = "Subject-verb agreement: use 'is' with third-person singular.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "he"
                val cap = orig[0].isUpperCase()
                if (cap) "${subj.replaceFirstChar { it.uppercase() }} is" else "${subj.lowercase()} is"
            }
        ))

        rules.add(GrammarRule(
            id = "SUBJ_VERB_IS",
            language = "en",
            pattern = Pattern.compile("(?i)\\b(they|we|you)\\s+is\\b"),
            explanation = "Subject-verb agreement: use 'are' with plural subjects.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "they"
                val cap = orig[0].isUpperCase()
                if (cap) "${subj.replaceFirstChar { it.uppercase() }} are" else "${subj.lowercase()} are"
            }
        ))

        rules.add(GrammarRule(
            id = "ARTICLE_A_AN",
            language = "en",
            pattern = Pattern.compile("(?i)\\ba\\s+(apple|orange|egg|elephant|idea|hour|email|umbrella|order|option)\\b"),
            explanation = "Use 'an' before vowel sounds.",
            replacer = { orig, m ->
                val noun = m.group(1) ?: ""
                val cap = orig[0].isUpperCase()
                if (cap) "An $noun" else "an $noun"
            }
        ))
    }

    private fun registerSpanishRules() {
        rules.add(GrammarRule(
            id = "ES_DE_EL",
            language = "es",
            pattern = Pattern.compile("(?i)\\bde\\s+el\\b"),
            explanation = "Use the contraction 'del'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "Del" else "del"
            }
        ))

        rules.add(GrammarRule(
            id = "ES_A_EL",
            language = "es",
            pattern = Pattern.compile("(?i)\\ba\\s+el\\b"),
            explanation = "Use the contraction 'al'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "Al" else "al"
            }
        ))

        rules.add(GrammarRule(
            id = "ES_HAIGA",
            language = "es",
            pattern = Pattern.compile("(?i)\\bhaiga\\b"),
            explanation = "Incorrect subjunctive: use 'haya'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "Haya" else "haya"
            }
        ))

        rules.add(GrammarRule(
            id = "ES_LA_PROBLEMA",
            language = "es",
            pattern = Pattern.compile("(?i)\\bla\\s+problema\\b"),
            explanation = "'Problema' is a masculine noun: use 'el problema'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "El problema" else "el problema"
            }
        ))

        rules.add(GrammarRule(
            id = "ES_LA_SISTEMA",
            language = "es",
            pattern = Pattern.compile("(?i)\\bla\\s+sistema\\b"),
            explanation = "'Sistema' is a masculine noun: use 'el sistema'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "El sistema" else "el sistema"
            }
        ))

        rules.add(GrammarRule(
            id = "ES_EL_FOTO",
            language = "es",
            pattern = Pattern.compile("(?i)\\bel\\s+foto\\b"),
            explanation = "'Foto' is a feminine noun: use 'la foto'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "La foto" else "la foto"
            }
        ))
    }

    private fun registerFrenchRules() {
        rules.add(GrammarRule(
            id = "FR_C_EST",
            language = "fr",
            pattern = Pattern.compile("(?i)\\bc\\s+est\\b"),
            explanation = "Required French elision: 'c'est'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "C'est" else "c'est"
            }
        ))

        rules.add(GrammarRule(
            id = "FR_J_AI",
            language = "fr",
            pattern = Pattern.compile("(?i)\\bj\\s+ai\\b"),
            explanation = "Required French elision: 'j'ai'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "J'ai" else "j'ai"
            }
        ))

        rules.add(GrammarRule(
            id = "FR_D_ACCORD",
            language = "fr",
            pattern = Pattern.compile("(?i)\\bd\\s+accord\\b"),
            explanation = "Required French elision: 'd'accord'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "D'accord" else "d'accord"
            }
        ))

        rules.add(GrammarRule(
            id = "FR_L_AMOUR",
            language = "fr",
            pattern = Pattern.compile("(?i)\\bl\\s+(amour|homme|eau|ami|heure|ecole|histoire)\\b"),
            explanation = "Required French elision before vowel/mute h.",
            replacer = { orig, m ->
                val n = m.group(1) ?: ""
                if (orig[0].isUpperCase()) "L'$n" else "l'$n"
            }
        ))

        rules.add(GrammarRule(
            id = "FR_ILS_EST",
            language = "fr",
            pattern = Pattern.compile("(?i)\\b(ils|elles)\\s+est\\b"),
            explanation = "Subject-verb agreement: use 'sont' with plural third-person.",
            replacer = { orig, m ->
                val s = m.group(1) ?: "ils"
                if (orig[0].isUpperCase()) "${s.replaceFirstChar { it.uppercase() }} sont" else "${s.lowercase()} sont"
            }
        ))
    }

    private fun registerGermanRules() {
        rules.add(GrammarRule(
            id = "DE_SEID_SEIT",
            language = "de",
            pattern = Pattern.compile("(?i)\\bseit\\s+ihr\\b"),
            explanation = "Verb form: use 'seid ihr'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "Seid ihr" else "seid ihr"
            }
        ))

        rules.add(GrammarRule(
            id = "DE_NOUN_CAPITALIZATION",
            language = "de",
            pattern = Pattern.compile("(?i)\\b(ein|eine|einen|einem|der|die|das|den|dem)\\s+(tag|zeit|jahr|haus|morgen|abend|arbeit|woche|geld)\\b"),
            explanation = "German nouns must be capitalized.",
            replacer = { orig, m ->
                val art = m.group(1) ?: ""
                val noun = m.group(2) ?: ""
                val capNoun = noun.replaceFirstChar { it.uppercase() }
                "$art $capNoun"
            }
        ))
    }

    private fun registerHindiRules() {
        rules.add(GrammarRule(
            id = "HI_MAIN_KARTA_HAI",
            language = "hi",
            pattern = Pattern.compile("(?i)\\b(main|mai)\\s+([a-zA-Z]+)\\s+hai\\b"),
            explanation = "In first person, use 'hoon' instead of 'hai'.",
            replacer = { orig, m ->
                val subj = m.group(1) ?: "main"
                val verb = m.group(2) ?: ""
                val cap = orig[0].isUpperCase()
                val res = "$subj $verb hoon"
                if (cap) res.replaceFirstChar { it.uppercase() } else res
            }
        ))

        rules.add(GrammarRule(
            id = "HI_AAP_KAISE_HO",
            language = "hi",
            pattern = Pattern.compile("(?i)\\baap\\s+kaise\\s+ho\\b"),
            explanation = "Honorific agreement: use 'aap kaise hain'.",
            replacer = { orig, _ ->
                if (orig[0].isUpperCase()) "Aap kaise hain" else "aap kaise hain"
            }
        ))
    }
}
