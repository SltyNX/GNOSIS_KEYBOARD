package gnosis.keyboard.engine.grammar

import gnosis.keyboard.engine.grammar_and_spellcheck.writingassist.GnosisGrammarIssue

/**
 * Gnosis Sentence Explorer & Grammar Checker
 *
 * Implements Gnosis on-device Sentence Explorer grammar checking engine:
 * - Detects syntactic mismatches and homophone confusions (their/there/they're, your/you're, its/it's, to/too, then/than)
 * - Identifies missing apostrophes in English and multilingual contractions
 * - Fixes modal verb confusions ("would of" -> "would have")
 * - Evaluates subject-verb agreement and pronoun capitalization
 */
object GnosisSentenceExplorer {

    private val homophoneRules = listOf(
        // their / there / they're
        Pair(Regex("(?i)\\btheir\\s+(going|coming|doing|here|not|are|were|was|will|can|so|very|nice|happy|ready|right|wrong|late|early|waiting|eating|working|trying|saying)\\b"), "they're $1"),
        Pair(Regex("(?i)\\b(over|out|in|stay|go|went|sit|stand|up|down|back|from|put\\s+it)\\s+their\\b"), "$1 there"),
        Pair(Regex("(?i)\\b(over|out|in|stay|go|went|sit|stand|up|down|back|from|put\\s+it)\\s+they're\\b"), "$1 there"),
        Pair(Regex("(?i)\\bthere\\s+(car|house|phone|family|dog|cat|friend|friends|kids|children|parents|mother|father|work|job|team|office|school|problem|idea|name|life|lives|room|money|fault)\\b"), "their $1"),

        // your / you're
        Pair(Regex("(?i)\\byour\\s+(welcome|right|wrong|doing|going|so|very|too|great|awesome|beautiful|nice|late|early|here|amazing|perfect|best|cute|funny|smart|kind|crazy|tired|ready|finished|done|kidding|joking|lying|telling)\\b"), "you're $1"),
        Pair(Regex("(?i)\\byou're\\s+(car|house|phone|email|name|job|work|family|dog|cat|friend|friends|fault|turn|time|life|day|mind|idea|opinion|bag|shoes|eyes)\\b"), "your $1"),

        // its / it's
        Pair(Regex("(?i)\\bits\\s+(raining|ok|okay|time|cold|hot|good|bad|not|a|an|the|been|got|too|so|very|hard|easy|done|working|happening|going|coming|true|false|clear|obvious|simple|fine|over|mine|yours|ours)\\b"), "it's $1"),
        Pair(Regex("(?i)\\bit's\\s+(color|size|shape|name|price|battery|screen|camera|weight|tail|wheels|engine|design|purpose)\\b"), "its $1"),

        // to / too / two
        Pair(Regex("(?i)\\b(me|you|them|us|love\\s+you|see\\s+you)\\s+to\\b"), "$1 too"),
        Pair(Regex("(?i)\\bto\\s+(much|many|late|bad|far|slow|fast|big|small|hard|easy|early|cold|hot|high|low|expensive|cheap|long|short|close)\\b"), "too $1"),
        Pair(Regex("(?i)\\b(want|need|like|have|going|able|try|hope|love|wish|ready|start|stop)\\s+too\\b"), "$1 to"),
        Pair(Regex("(?i)\\btoo\\s+(go|see|do|be|have|make|get|know|take|give|find)\\b"), "to $1"),

        // then / than
        Pair(Regex("(?i)\\b(better|worse|more|less|bigger|smaller|faster|slower|rather|other|taller|shorter|older|younger|easier|harder|further|higher|lower|sooner|later|stronger|weaker|greater)\\s+then\\b"), "$1 than"),

        // loose / lose
        Pair(Regex("(?i)\\bloose\\s+weight\\b"), "lose weight"),
        Pair(Regex("(?i)\\b(dont|can't|cannot|cant|won't|wont|will|to|gonna|might|did)\\s+loose\\b"), "$1 lose"),

        // weather / whether
        Pair(Regex("(?i)\\bweather\\s+(or\\s+not|you|they|it|we)\\b"), "whether $1"),
        Pair(Regex("(?i)\\b(nice|bad|cold|hot|rainy|warm)\\s+whether\\b"), "$1 weather"),

        // effect / affect
        Pair(Regex("(?i)\\b(side|the|no|any)\\s+affect\\b"), "$1 effect"),
        Pair(Regex("(?i)\\b(will|can|how\\s+does\\s+it)\\s+effect\\b"), "$1 affect"),

        // passed / past
        Pair(Regex("(?i)\\bin\\s+the\\s+passed\\b"), "in the past"),
        Pair(Regex("(?i)\\b(walked|drove|ran|flew|went)\\s+passed\\b"), "$1 past"),
        Pair(Regex("(?i)\\btime\\s+has\\s+past\\b"), "time has passed"),

        // Modal "would of" -> "would have"
        Pair(Regex("(?i)\\bwould\\s+of\\b"), "would have"),
        Pair(Regex("(?i)\\bcould\\s+of\\b"), "could have"),
        Pair(Regex("(?i)\\bshould\\s+of\\b"), "should have"),
        Pair(Regex("(?i)\\bmust\\s+of\\b"), "must have"),
        Pair(Regex("(?i)\\bmight\\s+of\\b"), "might have"),

        // Subject-verb agreement
        Pair(Regex("(?i)\\b(he|she|it)\\s+have\\b"), "$1 has"),
        Pair(Regex("(?i)\\b(they|we|you)\\s+has\\b"), "$1 have"),
        Pair(Regex("(?i)\\b(he|she|it)\\s+dont\\b"), "$1 doesn't"),

        // Spacing and compounds
        Pair(Regex("(?i)\\balot\\b"), "a lot"),
        Pair(Regex("(?i)\\baswell\\b"), "as well"),
        Pair(Regex("(?i)\\binfront\\b"), "in front"),
        Pair(Regex("(?i)\\bthankyou\\b"), "thank you"),
        Pair(Regex("(?i)\\beachother\\b"), "each other"),
        Pair(Regex("(?i)\\bnevermind\\b"), "never mind"),
        Pair(Regex("(?i)\\batleast\\b"), "at least")
    )

    val CONTRACTION_MAP = mapOf(
        // Common negations
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
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
        "mustnt" to "mustn't",
        "neednt" to "needn't",
        "darent" to "daren't",
        "aint" to "ain't",

        // First-person contractions
        "im" to "I'm",
        "iam" to "I'm",
        "ive" to "I've",
        "ill" to "I'll",
        "id" to "I'd",

        // Second-person contractions
        "youre" to "you're",
        "youare" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "youd" to "you'd",

        // Third-person contractions
        "hes" to "he's",
        "hed" to "he'd",
        "hell" to "he'll",
        "shes" to "she's",
        "shed" to "she'd",
        "itll" to "it'll",
        "theyre" to "they're",
        "theyare" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "theyd" to "they'd",

        // Plural first-person contractions
        "weve" to "we've",
        "weare" to "we're",
        "wed" to "we'd",
        "weall" to "we'll",

        // Demonstratives & Wh-words
        "thats" to "that's",
        "thatll" to "that'll",
        "thatd" to "that'd",
        "whats" to "what's",
        "whatll" to "what'll",
        "theres" to "there's",
        "therell" to "there'll",
        "heres" to "here's",
        "wheres" to "where's",
        "wherell" to "where'll",
        "hows" to "how's",
        "howd" to "how'd",
        "whos" to "who's",
        "whove" to "who've",
        "wholl" to "who'll",
        "whod" to "who'd",
        "lets" to "let's",
        "cmon" to "c'mon",
        "yall" to "y'all",

        // Modal perfects
        "couldve" to "could've",
        "shouldve" to "should've",
        "wouldve" to "would've",
        "mightve" to "might've",
        "mustve" to "must've",

        // Indefinite pronouns possessive
        "someones" to "someone's",
        "everyones" to "everyone's",
        "anyones" to "anyone's",
        "nobodys" to "nobody's",
        "somebodys" to "somebody's"
    )

    val COMMON_SHORT_TYPOS = mapOf(
        "teh" to "the",
        "adn" to "and",
        "taht" to "that",
        "waht" to "what",
        "thsi" to "this",
        "wiht" to "with",
        "form" to "from",
        "fro" to "for",
        "oyu" to "you",
        "yuo" to "you",
        "hve" to "have",
        "ahve" to "have",
        "becuase" to "because",
        "beacuse" to "because",
        "definately" to "definitely",
        "seperate" to "separate",
        "recieve" to "receive",
        "untill" to "until",
        "wierd" to "weird",
        "alot" to "a lot",
        "thier" to "their",
        "freind" to "friend",
        "freinds" to "friends",
        "peice" to "piece",
        "lenght" to "length",
        "widht" to "width",
        "heigth" to "height",
        "calender" to "calendar",
        "recomended" to "recommended",
        "begining" to "beginning",
        "embarass" to "embarrass",
        "succesful" to "successful",
        "unfortunatly" to "unfortunately",
        "intrest" to "interest",
        "intresting" to "interesting",
        "diffrent" to "different",
        "familar" to "familiar",
        "goverment" to "government",
        "beleive" to "believe",
        "tommorow" to "tomorrow",
        "tommorrow" to "tomorrow",
        "neccessary" to "necessary",
        "occured" to "occurred",
        "truely" to "truly"
    )

    fun preserveCase(source: String, target: String): String {
        return when {
            source.all { it.isUpperCase() } -> target.uppercase()
            source.firstOrNull()?.isUpperCase() == true -> target.replaceFirstChar { it.uppercase() }
            else -> target
        }
    }

    fun checkGrammar(text: String): GnosisGrammarIssue? {
        if (text.length < 2) return null

        // 1. High-Performance Zero-Allocation DFA Evaluation (<0.02ms)
        val dfaIssue = GnosisGrammarDfaEngine.evaluate(text)
        if (dfaIssue != null) return dfaIssue

        // 2. Fallback regex evaluation if sliding window needs wider scope
        for ((regex, fixTemplate) in homophoneRules) {
            val match = regex.findAll(text).lastOrNull()
            if (match != null) {
                val orig = match.value
                val fix = if (fixTemplate.contains("$1")) {
                    val groupVal = match.groupValues.getOrNull(1) ?: ""
                    fixTemplate.replace("$1", groupVal)
                } else {
                    fixTemplate
                }

                val formattedFix = if (orig.firstOrNull()?.isUpperCase() == true) {
                    fix.replaceFirstChar { it.uppercase() }
                } else fix

                return GnosisGrammarIssue(orig, formattedFix, "Grammar agreement fix")
            }
        }

        // 2. Check solitary pronoun 'i'
        val rawWords = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (w in rawWords) {
            val clean = w.trim('?', '.', '!', ',', '"', '\'')
            if (clean == "i") {
                return GnosisGrammarIssue("i", "I", "Capitalize pronoun 'I'")
            }
        }

        // 3. Check missing apostrophes in contractions across recent words
        for (w in rawWords.takeLast(4).reversed()) {
            val clean = w.trim('?', '.', '!', ',', '"', '\'', ' ')
            val lower = clean.lowercase()
            val fix = CONTRACTION_MAP[lower]
            if (fix != null && !clean.equals(fix, ignoreCase = false)) {
                val formatted = preserveCase(clean, fix)
                return GnosisGrammarIssue(clean, formatted, "Missing apostrophe")
            }
            val typoFix = COMMON_SHORT_TYPOS[lower]
            if (typoFix != null && !clean.equals(typoFix, ignoreCase = true)) {
                val formatted = preserveCase(clean, typoFix)
                return GnosisGrammarIssue(clean, formatted, "Spelling correction")
            }
        }

        return null
    }
}
