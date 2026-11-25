package gnosis.keyboard.engine

import android.content.Context
import gnosis.keyboard.DictionaryExtensionManager
import gnosis.keyboard.WordDictionary
import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.data.InMemoryUserLexicon
import gnosis.keyboard.engine.data.LongIntHashMap
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.delight.GnosisBeamDecoder
import gnosis.keyboard.engine.delight.GnosisInputPipeline
import gnosis.keyboard.engine.delight.GnosisSpatialModel
import gnosis.keyboard.engine.grammar.HarperBridge
import gnosis.keyboard.engine.language.GnosisLanguageManager
import gnosis.keyboard.engine.prediction.BeamDecoder
import gnosis.keyboard.engine.prediction.CompactNgramModel
import gnosis.keyboard.engine.prediction.Trie
import gnosis.keyboard.engine.prediction.VocabularyFrequencyPolicy
import gnosis.keyboard.engine.spell.ContractionEngine
import gnosis.keyboard.engine.spell.SpellChecker
import gnosis.keyboard.text.NextWordPredictor
import gnosis.keyboard.text.SuggestionEngine
import java.util.Locale

object EngineFactory {

    @Volatile
    private var cachedEngine: TypingEngine? = null
    @Volatile
    private var cachedNgramModel: CompactNgramModel? = null
    @Volatile
    private var cachedUserLexicon: UserLexicon? = null
    @Volatile
    private var cachedNextWordPredictor: NextWordPredictor? = null
    @Volatile
    private var cachedSuggestionEngine: SuggestionEngine? = null
    @Volatile
    private var cachedLanguage: String? = null

    fun create(
        context: Context,
        language: String = GnosisLanguageManager.resolveEffectiveLanguage(context).code
    ): TypingEngine {
        val lang = language.lowercase(Locale.ROOT)
        if (cachedEngine != null && cachedLanguage == lang) {
            return cachedEngine!!
        }

        synchronized(this) {
            if (cachedEngine != null && cachedLanguage == lang) {
                return cachedEngine!!
            }

            val trie = Trie()
            val wordsToId = mutableMapOf<String, Int>()

            val baseWords = LinkedHashSet<String>()
            val additionalWords = LinkedHashSet<String>()

            when (lang) {
                "es" -> {
                    SPANISH_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "spanish"))
                }
                "fr" -> {
                    FRENCH_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "french"))
                }
                "de" -> {
                    GERMAN_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "german"))
                }
                "hi" -> {
                    HINDI_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "hindi"))
                }
                "hinglish" -> {
                    val hinglishWords = if (gnosis.keyboard.HinglishDictionaryManager.isEnabled(context)) {
                        gnosis.keyboard.HinglishDictionaryManager.loadWords(context)
                    } else emptySet()
                    if (hinglishWords.isNotEmpty()) {
                        baseWords.addAll(hinglishWords)
                    } else {
                        try {
                            context.assets.open("words_hinglish.txt").bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    val word = line.trim().lowercase(Locale.ROOT)
                                    if (word.isNotEmpty()) baseWords.add(word)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    BASE_VOCABULARY.take(500).forEach { additionalWords.add(it.lowercase(Locale.ROOT)) }
                }
                "ru" -> {
                    RUSSIAN_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "russian"))
                }
                "pt" -> {
                    PORTUGUESE_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "portuguese"))
                }
                "it" -> {
                    ITALIAN_CORE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    additionalWords.addAll(DictionaryExtensionManager.loadPackWords(context, "italian"))
                }
                else -> {
                    try {
                        context.assets.open("words_en.txt").bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val word = line.trim().lowercase(Locale.ROOT)
                                if (word.isNotEmpty()) baseWords.add(word)
                            }
                        }
                    } catch (_: Exception) {
                        BASE_VOCABULARY.forEach { baseWords.add(it.lowercase(Locale.ROOT)) }
                    }

                    try {
                        context.assets.open("words_en_extended.txt").bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val word = line.trim().lowercase(Locale.ROOT)
                                if (word.length in 2..30 && word.all { it in 'a'..'z' }) {
                                    additionalWords.add(word)
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val hinglishWords = if (gnosis.keyboard.HinglishDictionaryManager.isEnabled(context)) {
                        gnosis.keyboard.HinglishDictionaryManager.loadWords(context)
                    } else emptySet()
                    if (hinglishWords.isNotEmpty()) {
                        additionalWords.addAll(hinglishWords)
                    } else {
                        try {
                            context.assets.open("words_hinglish.txt").bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    val word = line.trim().lowercase(Locale.ROOT)
                                    if (word.isNotEmpty()) additionalWords.add(word)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            additionalWords.addAll(DictionaryExtensionManager.loadAllActiveWords(context))

            for ((key, forms) in ContractionEngine.CONTRACTION_MAP) {
                additionalWords.add(key.lowercase(Locale.ROOT))
                additionalWords.addAll(forms.map { it.lowercase(Locale.ROOT) })
            }

            val wordFrequencies = VocabularyFrequencyPolicy.build(baseWords, additionalWords)
            val vocabulary = wordFrequencies.keys

            WordDictionary.setGestureVocabulary(vocabulary.toList())

            var idCounter = 0
            val unigramCounts = IntArray(vocabulary.size)

            for (w in vocabulary) {
                val lower = w.lowercase(Locale.ROOT)
                val id = idCounter++
                wordsToId[lower] = id

                val freq = wordFrequencies[lower] ?: 1
                unigramCounts[id] = freq
                trie.insert(lower, frequency = freq)
            }

            val bigramCounts = LongIntHashMap(initialCapacity = 2048)

            val bigramSource = when (lang) {
                "es" -> SPANISH_BIGRAMS
                "fr" -> FRENCH_BIGRAMS
                "de" -> GERMAN_BIGRAMS
                "hi", "hinglish" -> PRESEEDED_BIGRAMS
                "ru" -> RUSSIAN_BIGRAMS
                "pt" -> PORTUGUESE_BIGRAMS
                "it" -> ITALIAN_BIGRAMS
                else -> PRESEEDED_BIGRAMS + ENGLISH_COMMON_BIGRAMS
            }

            for ((w1, followers) in bigramSource) {
                val id1 = wordsToId[w1.lowercase(Locale.ROOT)] ?: continue
                for (w2 in followers) {
                    val id2 = wordsToId[w2.lowercase(Locale.ROOT)] ?: continue
                    val key = CompactNgramModel.pack2(id1, id2)
                    bigramCounts[key] = 20
                }
            }

            val trigramCounts = LongIntHashMap(initialCapacity = 1024)
            if (lang == "en") {
                try {
                    context.assets.open("ngrams_en.txt").bufferedReader().useLines { lines ->
                        val ws = Regex("\\s+")
                        for (line in lines) {
                            val colon = line.indexOf(':')
                            if (colon < 0 || line.startsWith("#")) continue
                            val keyTokens = line.substring(0, colon).trim().lowercase(Locale.ROOT).split(ws)
                            val ctxIds = keyTokens.mapNotNull { wordsToId[it] }
                            if (ctxIds.size != keyTokens.size || ctxIds.size > 2) continue
                            line.substring(colon + 1).trim().lowercase(Locale.ROOT).split(ws)
                                .forEachIndexed { rank, w ->
                                    val id = wordsToId[w] ?: return@forEachIndexed
                                    val count = (30 - rank * 3).coerceAtLeast(6)
                                    if (ctxIds.size == 1) {
                                        val key = CompactNgramModel.pack2(ctxIds[0], id)
                                        if (bigramCounts[key] < count) bigramCounts[key] = count
                                    } else {
                                        val key = CompactNgramModel.pack3(ctxIds[0], ctxIds[1], id)
                                        if (trigramCounts[key] < count) trigramCounts[key] = count
                                    }
                                }
                        }
                    }
                } catch (_: Exception) {}
            }

            val ngramModel = CompactNgramModel(
                wordsToId = wordsToId,
                unigramCounts = unigramCounts,
                bigramCounts = bigramCounts,
                trigramCounts = trigramCounts
            )

            val userLexicon = InMemoryUserLexicon(context.applicationContext)
            val spellChecker = SpellChecker(trie, isUserKnown = { userLexicon.isKnown(it) })
            val grammarChecker = HarperBridge()

            val gnosisDecoder = GnosisBeamDecoder(
                trie = trie,
                ngramModel = ngramModel,
                userLexicon = userLexicon,
                spatialModel = GnosisSpatialModel()
            )

            val engine = GnosisInputPipeline(
                beamDecoder = gnosisDecoder,
                userLexicon = userLexicon,
                spellChecker = spellChecker,
                grammarChecker = grammarChecker
            )

            cachedNgramModel = ngramModel
            cachedUserLexicon = userLexicon
            cachedNextWordPredictor = NextWordPredictor(ngramModel, userLexicon)
            cachedLanguage = lang

            cachedEngine = engine
            return engine
        }
    }

    fun getNextWordPredictor(
        context: Context,
        language: String = GnosisLanguageManager.resolveEffectiveLanguage(context).code
    ): NextWordPredictor {
        create(context, language)
        return cachedNextWordPredictor ?: NextWordPredictor(cachedNgramModel, cachedUserLexicon ?: InMemoryUserLexicon(context.applicationContext))
    }

    fun getSuggestionEngine(
        context: Context,
        userDictionary: UserDictionaryRepository,
        language: String = GnosisLanguageManager.resolveEffectiveLanguage(context).code
    ): SuggestionEngine {
        val lang = language.lowercase(Locale.ROOT)
        if (cachedSuggestionEngine != null && cachedLanguage == lang) {
            return cachedSuggestionEngine!!
        }
        synchronized(this) {
            if (cachedSuggestionEngine != null && cachedLanguage == lang) {
                return cachedSuggestionEngine!!
            }
            val engine = create(context, lang)
            val predictor = getNextWordPredictor(context, lang)
            val sugg = SuggestionEngine(engine, predictor, userDictionary, cachedNgramModel, cachedUserLexicon, context = context.applicationContext)
            cachedSuggestionEngine = sugg
            return sugg
        }
    }

    fun invalidateCache() {
        synchronized(this) {
            cachedEngine = null
            cachedNgramModel = null
            cachedUserLexicon = null
            cachedNextWordPredictor = null
            cachedSuggestionEngine = null
            cachedLanguage = null
        }
    }

    private val ENGLISH_COMMON_BIGRAMS = mapOf(
        "how" to listOf("are", "is", "was", "do", "can"),
        "how are" to listOf("you", "things", "we", "they"),
        "thank" to listOf("you", "so", "very"),
        "thank you" to listOf("so", "very", "for", "much"),
        "let" to listOf("me", "us", "it", "know"),
        "let me" to listOf("know", "check", "see", "think")
    )

    private val SPANISH_BIGRAMS = mapOf(
        "cómo" to listOf("estás", "te", "se", "vamos"),
        "gracias" to listOf("por", "amigo", "muchas"),
        "muchas" to listOf("gracias"),
        "por" to listOf("favor", "ejemplo", "eso", "qué"),
        "buenos" to listOf("días", "noches"),
        "buenas" to listOf("tardes", "noches"),
        "de" to listOf("nada", "acuerdo", "nuevo"),
        "te" to listOf("quiero", "amo", "veo"),
        "hasta" to listOf("luego", "mañana", "pronto")
    )

    private val FRENCH_BIGRAMS = mapOf(
        "comment" to listOf("vas-tu", "ça", "allez-vous"),
        "merci" to listOf("beaucoup", "infiniment", "pour"),
        "s'il" to listOf("vous", "te"),
        "bonjour" to listOf("comment", "tout", "à"),
        "je" to listOf("suis", "vais", "pense", "veux"),
        "c'est" to listOf("bon", "bien", "vrai", "super"),
        "à" to listOf("bientôt", "demain", "plus", "tard")
    )

    private val GERMAN_BIGRAMS = mapOf(
        "wie" to listOf("geht's", "geht", "war"),
        "vielen" to listOf("Dank", "Dank!"),
        "guten" to listOf("Morgen", "Tag", "Abend"),
        "gute" to listOf("Nacht", "Besserung", "Reise"),
        "ich" to listOf("bin", "habe", "denke", "will"),
        "bis" to listOf("später", "morgen", "bald"),
        "kein" to listOf("Problem", "Ding")
    )

    private val PORTUGUESE_BIGRAMS = mapOf(
        "como" to listOf("vai", "está", "você"),
        "muito" to listOf("obrigado", "obrigada", "bom"),
        "bom" to listOf("dia", "trabalho"),
        "boa" to listOf("tarde", "noite"),
        "por" to listOf("favor", "isso"),
        "tudo" to listOf("bem", "certo"),
        "até" to listOf("logo", "amanhã")
    )

    private val ITALIAN_BIGRAMS = mapOf(
        "come" to listOf("stai", "va", "posso"),
        "grazie" to listOf("mille", "di", "per"),
        "buon" to listOf("giorno", "pomeriggio", "lavoro"),
        "buona" to listOf("giornata", "sera", "notte"),
        "per" to listOf("favore", "piacere"),
        "ci" to listOf("vediamo", "sentiamo"),
        "a" to listOf("presto", "domani")
    )

    private val RUSSIAN_BIGRAMS = mapOf(
        "как" to listOf("дела", "ты", "вы"),
        "спасибо" to listOf("большое", "за"),
        "доброе" to listOf("утро"),
        "добрый" to listOf("день", "вечер"),
        "до" to listOf("свидания", "встречи"),
        "все" to listOf("хорошо", "понятно"),
        "пожалуйста" to listOf("ответь", "помоги")
    )

    private val PRESEEDED_BIGRAMS = mapOf(
        "kya" to listOf("hai", "kar", "hua", "baat", "chal", "bol", "raha", "rahi", "hoga"),
        "kaise" to listOf("ho", "hai", "karein", "batao"),
        "kaisi" to listOf("ho", "hai", "lagi"),
        "kaisa" to listOf("hai", "laga", "tha", "raha"),
        "kahan" to listOf("ho", "hai", "ja", "gaye", "se"),
        "kidhar" to listOf("ho", "hai", "ja", "gaye"),
        "kab" to listOf("aaoge", "tak", "aaye", "mile"),
        "kyun" to listOf("nahi", "kya", "bhai"),
        "kyu" to listOf("nahi", "kya", "bhai"),
        "theek" to listOf("hai", "tha", "hoga", "se"),
        "thik" to listOf("hai", "tha", "hoga", "se"),
        "accha" to listOf("hai", "laga", "theek", "bhai", "yaar"),
        "achha" to listOf("hai", "laga", "theek", "bhai", "yaar"),
        "badhiya" to listOf("hai", "tha", "bhai", "yaar"),
        "mast" to listOf("hai", "laga", "yaar", "bhai"),
        "koi" to listOf("baat", "nahi", "bhi"),
        "kuch" to listOf("nahi", "bhi", "khas", "bolo", "batao"),
        "bahut" to listOf("accha", "badhiya", "sundar", "kam", "zyada", "shukriya"),
        "bohot" to listOf("accha", "badhiya", "sundar", "kam", "zyada", "shukriya"),
        "sab" to listOf("badhiya", "theek", "kuch", "log", "sahi"),
        "main" to listOf("bhi", "theek", "aa", "kar", "nahi", "to", "ghar"),
        "mai" to listOf("bhi", "theek", "aa", "kar", "nahi", "to", "ghar"),
        "mera" to listOf("naam", "bhai", "dost", "kaam", "phone"),
        "meri" to listOf("baat", "behan", "mummy", "taraf"),
        "mere" to listOf("paas", "saath", "liye", "bhai"),
        "hum" to listOf("bhi", "karenge", "saath", "jayenge"),
        "aap" to listOf("kaise", "kahan", "kya", "batao", "bhi"),
        "aapka" to listOf("naam", "dhanyawad", "shukriya", "swagat"),
        "aapki" to listOf("kripa", "baat", "madad"),
        "shukriya" to listOf("bhai", "dost", "aapka", "sir"),
        "dhanyawad" to listOf("bhai", "aapka", "sir"),
        "namaste" to listOf("aap", "ji", "sir"),
        "namaskar" to listOf("aap", "ji", "sir"),
        "bhai" to listOf("kya", "kaise", "kahan", "suno", "sun"),
        "yaar" to listOf("kya", "kaise", "chal", "suno", "theek"),
        "dost" to listOf("kya", "kaise", "hai", "mera"),
        "sahi" to listOf("hai", "baat", "lag", "raha", "hoga"),
        "galat" to listOf("hai", "tha", "baat", "mat"),
        "chal" to listOf("theek", "koi", "bhai", "aaja"),
        "chalo" to listOf("theek", "aaj", "chalte", "milte"),
        "aaj" to listOf("kya", "shaam", "milte", "raat", "aaoge"),
        "kal" to listOf("milte", "aata", "subah", "pakka"),
        "ab" to listOf("kya", "chal", "niklo", "batao")
    )

    private val SPANISH_CORE_VOCABULARY = listOf(
        "de", "la", "que", "el", "en", "y", "a", "los", "del", "se", "las", "por", "un", "para", "con", "no", "una",
        "su", "al", "lo", "como", "más", "pero", "sus", "le", "ya", "o", "este", "sí", "porque", "esta", "entre",
        "cuando", "muy", "sin", "sobre", "también", "me", "hasta", "hay", "donde", "quien", "desde", "todo", "nos",
        "durante", "todos", "uno", "les", "ni", "contra", "otros", "ese", "eso", "ante", "ellos", "e", "esto", "mí",
        "antes", "algunos", "qué", "unos", "yo", "otro", "otras", "otra", "él", "tanto", "esa", "estos", "mucho",
        "quienes", "nada", "muchos", "cual", "sea", "poco", "ella", "estar", "haber", "hacer", "tener", "decir",
        "ir", "ver", "dar", "saber", "poder", "poner", "llegar", "pasar", "deber", "parecer", "quedar", "creer",
        "hablar", "llevar", "dejar", "seguir", "encontrar", "llamar", "venir", "pensar", "salir", "volver", "tomar",
        "conocer", "vivir", "sentir", "tratar", "mirar", "contar", "empezar", "esperar", "buscar", "existir", "entrar",
        "trabajar", "escribir", "perder", "producir", "ocurrir", "entender", "pedir", "recibir", "recordar", "terminar",
        "permitir", "aparecer", "conseguir", "comenzar", "servir", "sacar", "necesitar", "mantener", "resultar",
        "leer", "caer", "cambiar", "presentar", "crear", "abrir", "considerar", "oír", "acabar", "ganar", "formar",
        "traer", "partir", "morir", "aceptar", "realizar", "suponer", "comprender", "lograr", "explicar", "preguntar",
        "tocar", "reconocer", "estudiar", "alcanzar", "nacer", "dirigir", "correr", "utilizar", "pagar", "ayudar",
        "gustar", "jugar", "escuchar", "cumplir", "ofrecer", "descubrir", "levantar", "intentar", "hola", "gracias",
        "buenos", "días", "tardes", "noches", "amigo", "casa", "tiempo", "vida", "hoy", "mañana", "ayer"
    )

    private val FRENCH_CORE_VOCABULARY = listOf(
        "de", "la", "le", "et", "les", "des", "en", "un", "du", "une", "que", "est", "pour", "qui", "dans", "a",
        "par", "plus", "pas", "au", "sur", "ne", "se", "ce", "il", "sont", "avec", "tout", "faire", "son", "mettre",
        "autre", "on", "mais", "nous", "comme", "ou", "si", "leur", "y", "dire", "elle", "pouvoir", "vous", "deux",
        "bien", "sans", "voir", "vouloir", "savoir", "devoir", "prendre", "donner", "falloir", "aller", "venir",
        "passer", "parler", "aimer", "croire", "demander", "rester", "tenir", "trouver", "laisser", "arriver",
        "servir", "penser", "vivre", "attendre", "porter", "connaître", "paraître", "rendre", "comprendre",
        "sortir", "devenir", "entrer", "écrire", "perdre", "rappeler", "tomber", "ouvrir", "gagner", "payer",
        "travailler", "écouter", "regarder", "chercher", "manger", "dormir", "finir", "choisir", "partir", "aider",
        "jouer", "marcher", "arrêter", "envoyer", "montrer", "merci", "bonjour", "bonsoir", "salut", "oui", "non",
        "d'accord", "toujours", "jamais", "souvent", "ici", "maintenant", "aujourd'hui", "demain", "hier", "soir",
        "matin", "jour", "nuit", "temps", "homme", "femme", "enfant", "ami", "maison", "monde", "vie", "chose", "heure"
    )

    private val GERMAN_CORE_VOCABULARY = listOf(
        "der", "die", "das", "und", "in", "den", "von", "zu", "mit", "sich", "des", "auf", "für", "ist", "im",
        "dem", "nicht", "ein", "eine", "als", "auch", "es", "an", "werden", "aus", "er", "hat", "dass", "sie",
        "nach", "wird", "bei", "einer", "um", "am", "sind", "noch", "wie", "einem", "über", "einen", "so", "zum",
        "war", "haben", "nur", "oder", "aber", "vor", "zur", "bis", "mehr", "durch", "man", "sein", "wurde", "sei",
        "prozent", "hatte", "kann", "gegen", "vom", "können", "schon", "wenn", "habe", "seine", "ihre", "dann",
        "unter", "wir", "soll", "ich", "eines", "jahr", "zwei", "diese", "dieser", "wieder", "keine", "uhr", "seiner",
        "worden", "will", "zwischen", "immer", "was", "sagte", "gibt", "alle", "diesen", "diesem", "gut", "danke",
        "bitte", "hallo", "morgen", "abend", "heute", "gestern", "ja", "nein", "zeit", "haus", "mensch", "tag",
        "arbeit", "woche", "geld", "leben", "weg", "stadt", "freund", "sehen", "gehen", "machen", "kommen", "sagen"
    )

    private val HINDI_CORE_VOCABULARY = listOf(
        "है", "के", "में", "की", "तो", "का", "से", "हो", "nahi", "tha", "raha", "aur", "bhi", "yeh", "woh", "kya",
        "kaise", "kahan", "kab", "kyun", "aap", "tum", "hum", "main", "mera", "meri", "mere", "aapka", "aapki",
        "theek", "accha", "bahut", "shukriya", "dhanyawad", "namaste", "namaskar", "ghar", "kaam", "dost", "bhai",
        "aaj", "kal", "abhi", "kuch", "sahi", "chal", "aao", "jao", "bolo", "suno", "batana", "samajh", "aana"
    )

    private val RUSSIAN_CORE_VOCABULARY = listOf(
        "и", "в", "не", "на", "я", "что", "тот", "быть", "с", "он", "а", "весь", "это", "как", "она", "по", "но",
        "они", "к", "у", "ты", "из", "мы", "за", "вы", "который", "свой", "же", "сказать", "этот", "о", "от",
        "мочь", "один", "для", "такой", "знать", "только", "еще", "или", "так", "его", "говорить", "год", "при",
        "дело", "другой", "уже", "себя", "когда", "время", "вот", "сам", "кто", "день", "человек", "идти", "думать",
        "хотеть", "привет", "спасибо", "пожалуйста", "добрый", "утро", "вечер", "ночь", "до", "свидания", "друг",
        "хорошо", "да", "нет", "сегодня", "завтра", "вчера", "дом", "работа", "жизнь", "рука", "глаз"
    )

    private val PORTUGUESE_CORE_VOCABULARY = listOf(
        "de", "a", "o", "que", "e", "do", "da", "em", "um", "para", "é", "com", "não", "uma", "os", "no", "se",
        "na", "por", "mais", "as", "dos", "como", "mas", "foi", "ao", "ele", "das", "tem", "à", "seu", "sua", "ou",
        "ser", "quando", "muito", "há", "nos", "já", "está", "eu", "também", "só", "pelo", "pela", "até", "isso",
        "ela", "entre", "era", "depois", "sem", "mesmo", "ter", "seus", "quem", "me", "esse", "eles", "você",
        "meu", "minha", "bom", "dia", "obrigado", "obrigada", "olá", "tudo", "bem", "tarde", "noite", "favor",
        "amigo", "casa", "tempo", "vida", "hoje", "amanhã", "ontem", "fazer", "dizer", "ir", "ver", "dar", "saber"
    )

    private val ITALIAN_CORE_VOCABULARY = listOf(
        "di", "e", "il", "che", "la", "a", "per", "in", "un", "non", "sono", "è", "una", "del", "ho", "al", "da",
        "con", "le", "si", "i", "nel", "ma", "come", "ha", "se", "più", "lo", "della", "questo", "ci", "anche",
        "alla", "delle", "mi", "dei", "mio", "tutto", "bene", "grazie", "ciao", "buon", "giorno", "sera", "notte",
        "favore", "presto", "cosa", "dove", "quando", "perché", "amico", "tempo", "casa", "lavoro", "vedere",
        "andare", "fare", "dire", "sapere", "oggi", "domani", "ieri", "vita", "uomo", "donna", "stare", "volere"
    )

    private val BASE_VOCABULARY = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "are", "is", "was", "were", "been", "has", "had", "did", "does", "done",
        "am", "shall", "should", "may", "might", "must", "hello", "hi", "hey",
        "yes", "yeah", "ok", "okay", "sure", "fine", "cool", "great", "awesome",
        "thanks", "thank", "please", "sorry", "welcome", "happy", "today", "tomorrow",
        "yesterday", "tonight", "morning", "night", "love", "friend", "family", "home",
        "school", "office", "phone", "email", "message", "call", "send",
        "read", "write", "open", "close", "help", "need", "find", "tell", "ask",
        "feel", "try", "leave", "keep", "let", "begin", "start", "stop", "hear",
        "play", "run", "move", "live", "believe", "bring", "happen", "provide",
        "sit", "stand", "lose", "pay", "meet", "include", "continue", "set", "learn",
        "change", "lead", "understand", "watch", "follow", "create", "speak",
        "spend", "grow", "walk", "win", "offer", "remember",
        "consider", "appear", "buy", "wait", "serve", "die", "expect", "build",
        "stay", "fall", "cut", "reach", "kill", "remain", "suggest", "raise", "pass",
        "sell", "require", "report", "decide", "pull", "hope", "develop", "carry",
        "explain", "receive", "join", "agree", "pick", "wear", "drive",
        "always", "never", "sometimes", "often", "usually", "really", "almost", "together",
        "already", "enough", "either", "quite", "both", "between", "under",
        "again", "before", "next", "few", "many", "much", "more", "very",
        "little", "own", "another", "such", "same", "different", "right",
        "left", "big", "small", "large", "long", "short", "high", "low", "old",
        "young", "fast", "slow", "easy", "hard", "simple", "complex", "true", "false",
        "ready", "able", "bad", "best", "better", "important", "clear",
        "clean", "free", "full", "late", "early", "real", "strong", "possible", "whole",
        "safe", "recent", "main", "major", "beautiful", "perfect",
        "quick", "smart", "super", "wonder", "wonderful", "amazing", "keyboard", "gnosis",
        "aren't", "isn't", "wasn't", "weren't", "don't", "doesn't", "didn't", "can't",
        "couldn't", "won't", "wouldn't", "shouldn't", "haven't", "hasn't", "hadn't",
        "you're", "they're", "that's", "what's", "there's", "here's", "where's", "how's",
        "let's", "i'm", "i've", "i'll", "i'd", "you've", "you'll", "you'd", "we've",
        "we'll", "we're", "they'd", "they'll", "they've", "she's", "he's", "it'll",
        "who's", "whose", "theirs", "yours", "ours", "hers"
    )
}
