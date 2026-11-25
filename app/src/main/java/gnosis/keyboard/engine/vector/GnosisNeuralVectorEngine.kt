package gnosis.keyboard.engine.vector

import gnosis.keyboard.suggest.GnosisCandidate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GnosisNeuralVectorEngine {

    const val VECTOR_DIM = 96

    private val embeddingTable = HashMap<String, FloatArray>(2048)

    private val userPersonalEmbeddings = HashMap<String, FloatArray>(512)

    private val semanticTransitions = HashMap<String, List<String>>(1024)

    init {
        initializeSemanticVocabulary()
    }

    fun getVector(word: String): FloatArray {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return FloatArray(VECTOR_DIM)

        userPersonalEmbeddings[clean]?.let { return it }
        embeddingTable[clean]?.let { return it }

        val vec = computeSubwordProjection(clean)
        embeddingTable[clean] = vec
        return vec
    }

    fun encodeContext(contextWords: List<String>): FloatArray {
        if (contextWords.isEmpty()) return FloatArray(VECTOR_DIM)

        val h = FloatArray(VECTOR_DIM)
        val tokens = contextWords.takeLast(5)
        val k = tokens.size
        val alpha = 0.65f

        var weightSum = 0f
        for (i in tokens.indices) {
            val weight = Math.pow(alpha.toDouble(), (k - 1 - i).toDouble()).toFloat()
            val v = getVector(tokens[i])
            for (d in 0 until VECTOR_DIM) {
                h[d] += v[d] * weight
            }
            weightSum += weight
        }

        if (weightSum > 0f) {
            for (d in 0 until VECTOR_DIM) {
                h[d] /= weightSum
            }
        }
        normalizeInPlace(h)
        return h
    }

    fun cosineSimilarity(u: FloatArray, v: FloatArray): Float {
        if (u.size != VECTOR_DIM || v.size != VECTOR_DIM) return 0f
        var dot = 0f
        for (d in 0 until VECTOR_DIM) {
            dot += u[d] * v[d]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }

    fun rerankCandidates(contextWords: List<String>, candidates: List<GnosisCandidate>): List<GnosisCandidate> {
        if (candidates.isEmpty() || contextWords.isEmpty()) return candidates

        val contextVec = encodeContext(contextWords)

        return candidates.map { candidate ->
            val candVec = getVector(candidate.text)
            val sim = (cosineSimilarity(contextVec, candVec) + 1f) / 2f
            val fusedScore = (candidate.score * 0.65) + (sim * 0.35)
            candidate.copy(score = fusedScore)
        }.sortedByDescending { it.score }
    }

    fun predictNextWords(
        contextWords: List<String>,
        language: String = "en",
        maxResults: Int = 3
    ): List<Pair<String, Double>> {
        val lang = language.lowercase(Locale.ROOT)
        if (contextWords.isEmpty()) {
            return getDefaultStarters(lang).take(maxResults)
        }

        val lastWord = contextWords.last().lowercase(Locale.ROOT)
        val candidates = mutableListOf<String>()

        if (contextWords.size >= 2) {
            val bigram = "${contextWords[contextWords.size - 2].lowercase(Locale.ROOT)} $lastWord"
            semanticTransitions[bigram]?.let { candidates.addAll(it) }
        }

        semanticTransitions[lastWord]?.let { candidates.addAll(it) }

        if (candidates.isEmpty()) {
            candidates.addAll(getLanguageFallbacks(lang))
        }

        val contextVec = encodeContext(contextWords)
        val scored = candidates.distinct().map { word ->
            val v = getVector(word)
            val sim = ((cosineSimilarity(contextVec, v) + 1f) / 2f).toDouble()
            Pair(word, sim)
        }.sortedByDescending { it.second }

        return scored.take(maxResults)
    }

    private fun getDefaultStarters(lang: String): List<Pair<String, Double>> {
        return when (lang) {
            "es" -> listOf(Pair("Hola", 0.95), Pair("¿Cómo", 0.90), Pair("Gracias", 0.85), Pair("Qué", 0.80))
            "fr" -> listOf(Pair("Bonjour", 0.95), Pair("Comment", 0.90), Pair("Merci", 0.85), Pair("C'est", 0.80))
            "de" -> listOf(Pair("Hallo", 0.95), Pair("Wie", 0.90), Pair("Guten", 0.85), Pair("Danke", 0.80))
            "hi" -> listOf(Pair("नमस्ते", 0.95), Pair("क्या", 0.90), Pair("आप", 0.85), Pair("कैसे", 0.80))
            "hinglish" -> listOf(Pair("Kya", 0.95), Pair("Kaise", 0.90), Pair("Haan", 0.85), Pair("Aap", 0.80))
            "ru" -> listOf(Pair("Привет", 0.95), Pair("Как", 0.90), Pair("Спасибо", 0.85), Pair("Что", 0.80))
            "pt" -> listOf(Pair("Olá", 0.95), Pair("Como", 0.90), Pair("Obrigado", 0.85), Pair("Bom", 0.80))
            "it" -> listOf(Pair("Ciao", 0.95), Pair("Come", 0.90), Pair("Grazie", 0.85), Pair("Buon", 0.80))
            else -> listOf(Pair("How", 0.90), Pair("I", 0.85), Pair("The", 0.80))
        }
    }

    private fun getLanguageFallbacks(lang: String): List<String> {
        return when (lang) {
            "es" -> listOf("que", "de", "no", "a", "la", "el", "es", "y", "en", "lo", "un", "por", "qué", "me", "te", "los", "se")
            "fr" -> listOf("de", "je", "est", "pas", "le", "que", "un", "la", "tu", "et", "ce", "il", "en", "ça", "à", "dans", "pour")
            "de" -> listOf("ich", "nicht", "die", "das", "der", "es", "und", "sie", "ist", "in", "du", "wir", "was", "zu", "mit", "den")
            "hi" -> listOf("है", "के", "में", "की", "तो", "का", "से", "हो", "nahi", "tha", "raha", "aur", "bhi")
            "hinglish" -> listOf("hai", "nahi", "bhi", "tha", "ho", "kar", "kya", "toh", "se", "aur", "ko", "raha", "rahi", "hoga")
            "ru" -> listOf("и", "не", "что", "я", "в", "на", "с", "как", "это", "по", "но", "он", "к", "у", "ты", "же", "вы")
            "pt" -> listOf("que", "não", "de", "um", "para", "é", "com", "não", "uma", "os", "no", "se", "na", "por", "mais", "como")
            "it" -> listOf("di", "e", "il", "che", "la", "a", "per", "non", "in", "un", "ho", "sono", "è", "mi", "si", "lo", "ti")
            else -> listOf("the", "to", "and", "a", "in", "it", "is", "for", "you", "that", "with", "have")
        }
    }

    fun recordUserSelection(contextWords: List<String>, acceptedWord: String) {
        val clean = acceptedWord.trim().lowercase(Locale.ROOT)
        if (clean.length < 2 || contextWords.isEmpty()) return

        val prev = contextWords.last().lowercase(Locale.ROOT)
        val existing = semanticTransitions[prev]?.toMutableList() ?: mutableListOf()
        if (!existing.contains(clean)) {
            existing.add(0, clean)
            if (existing.size > 8) existing.removeAt(existing.size - 1)
            semanticTransitions[prev] = existing
        }

        val currentVec = getVector(clean).clone()
        val ctxVec = encodeContext(contextWords)
        for (d in 0 until VECTOR_DIM) {
            currentVec[d] = (currentVec[d] * 0.90f) + (ctxVec[d] * 0.10f)
        }
        normalizeInPlace(currentVec)
        userPersonalEmbeddings[clean] = currentVec
    }

    private fun computeSubwordProjection(word: String): FloatArray {
        val vec = FloatArray(VECTOR_DIM)
        val len = word.length

        val h1 = word.hashCode()
        val h2 = word.reversed().hashCode()

        for (d in 0 until VECTOR_DIM) {
            val angle1 = (h1.toDouble() * (d + 1)) / 1000.0
            val angle2 = (h2.toDouble() * (d + 1)) / 1000.0
            vec[d] = (sin(angle1) + cos(angle2)).toFloat()
        }

        val prefix = word.take(3)
        val suffix = word.takeLast(3)
        val pHash = prefix.hashCode()
        val sHash = suffix.hashCode()

        for (d in 0 until VECTOR_DIM step 2) {
            vec[d] += sin((pHash * (d + 3)).toDouble() / 1500.0).toFloat() * 0.5f
            if (d + 1 < VECTOR_DIM) {
                vec[d + 1] += cos((sHash * (d + 4)).toDouble() / 1500.0).toFloat() * 0.5f
            }
        }

        val normWeight = 1f / sqrt(len.toFloat().coerceAtLeast(1f))
        for (d in 0 until VECTOR_DIM) {
            vec[d] *= normWeight
        }

        normalizeInPlace(vec)
        return vec
    }

    private fun normalizeInPlace(v: FloatArray) {
        var sumSq = 0f
        for (d in 0 until VECTOR_DIM) {
            sumSq += v[d] * v[d]
        }
        val norm = sqrt(sumSq)
        if (norm > 1e-6f) {
            for (d in 0 until VECTOR_DIM) {
                v[d] /= norm
            }
        }
    }

    private fun initializeSemanticVocabulary() {
        fun createAnchor(clusterIdx: Int, word: String) {
            val v = FloatArray(VECTOR_DIM)
            val baseOffset = clusterIdx * 16
            for (i in 0 until 16) {
                val idx = (baseOffset + i) % VECTOR_DIM
                v[idx] = 1.0f / sqrt(16.0f)
            }
            val wHash = word.hashCode()
            for (d in 0 until VECTOR_DIM) {
                v[d] += sin((wHash * (d + 1)).toDouble() / 1000.0).toFloat() * 0.25f
            }
            normalizeInPlace(v)
            embeddingTable[word] = v
        }

        listOf("restaurant", "dinner", "lunch", "breakfast", "food", "eat", "pizza", "coffee", "comida", "cena", "almuerzo", "nourriture", "essen", "mittagessen", "khana", "chai", "cibo", "pranzo").forEach {
            createAnchor(0, it)
        }

        listOf("place", "home", "office", "hotel", "airport", "train", "flight", "car", "bus", "casa", "oficina", "aeropuerto", "maison", "gare", "flughafen", "bahnhof", "ghar", "gaadi", "stazione").forEach {
            createAnchor(1, it)
        }

        listOf("hello", "hi", "hey", "thanks", "thank", "welcome", "please", "sorry", "yes", "no", "hola", "gracias", "por favor", "merci", "bonjour", "danke", "bitte", "shukriya", "dhanyawad", "ciao", "obrigado").forEach {
            createAnchor(2, it)
        }

        listOf("phone", "email", "message", "call", "link", "meeting", "send", "app", "telefono", "correo", "mensaje", "trabajo", "travail", "arbeit", "kam", "telefon", "lavoro", "mensagem").forEach {
            createAnchor(3, it)
        }

        listOf("today", "tomorrow", "tonight", "now", "later", "soon", "morning", "hoy", "mañana", "ahora", "tarde", "demain", "aujourd'hui", "heute", "morgen", "kal", "aaj", "ab", "domani", "hoje").forEach {
            createAnchor(4, it)
        }

        listOf("i", "you", "he", "she", "we", "they", "it", "yo", "tú", "él", "ella", "nosotros", "je", "tu", "il", "nous", "ich", "du", "wir", "main", "tum", "aap", "hum", "eu", "você", "ele", "io", "noi").forEach {
            createAnchor(5, it)
        }

        seedTransitions()
    }

    private fun seedTransitions() {
        val pairs = mapOf(
            "how" to listOf("are", "is", "was", "do", "can", "about"),
            "how are" to listOf("you", "things", "we", "they"),
            "how is" to listOf("it", "your", "the", "everything", "work"),
            "how do" to listOf("you", "we", "i"),
            "how about" to listOf("you", "tomorrow", "tonight", "later"),
            "what" to listOf("is", "are", "do", "did", "time", "about"),
            "what is" to listOf("the", "your", "this", "that", "it"),
            "what are" to listOf("you", "we", "they", "the"),
            "what time" to listOf("are", "is", "will", "does"),
            "what do" to listOf("you", "we", "they"),
            "where" to listOf("are", "is", "did", "were", "can"),
            "where are" to listOf("you", "we", "they"),
            "where is" to listOf("the", "it", "my", "your"),
            "when" to listOf("is", "will", "are", "can"),
            "when is" to listOf("the", "your", "it"),
            "why" to listOf("are", "is", "did", "do"),
            "who" to listOf("is", "are", "was"),

            "thank" to listOf("you", "so", "very"),
            "thank you" to listOf("so", "very", "for", "much", "again"),
            "thanks" to listOf("for", "so", "again", "a"),
            "thanks for" to listOf("the", "your", "helping", "all", "everything"),
            "hello" to listOf("how", "there", "everyone"),
            "hi" to listOf("how", "there", "everyone"),
            "hey" to listOf("how", "there", "what"),
            "good" to listOf("morning", "night", "afternoon", "evening", "job", "luck"),
            "great" to listOf("job", "work", "news", "to", "idea"),
            "nice to" to listOf("meet", "hear", "see"),
            "see" to listOf("you", "them", "it", "soon", "later"),
            "see you" to listOf("soon", "tomorrow", "later", "tonight", "there"),
            "have a" to listOf("great", "good", "nice", "wonderful", "safe"),
            "have a great" to listOf("day", "time", "weekend", "evening", "trip"),
            "have a good" to listOf("one", "day", "night", "time", "morning"),

            "i" to listOf("am", "will", "have", "think", "want", "need", "can"),
            "i am" to listOf("going", "ready", "here", "on", "sorry", "at", "so", "not"),
            "i will" to listOf("be", "call", "send", "check", "do", "let"),
            "i have" to listOf("been", "a", "no", "to", "the", "already"),
            "i would" to listOf("like", "love", "be", "have"),
            "i think" to listOf("it", "so", "we", "that", "you", "i"),
            "i want" to listOf("to", "a", "the", "you"),
            "i need" to listOf("to", "a", "the", "your", "help"),
            "i can" to listOf("do", "help", "see", "send", "come", "call"),
            "i dont" to listOf("know", "think", "want", "have", "need", "like"),
            "i don't" to listOf("know", "think", "want", "have", "need", "like"),
            "you" to listOf("are", "can", "have", "will", "know", "want"),
            "you are" to listOf("welcome", "the", "so", "right", "great"),
            "we" to listOf("are", "can", "will", "have", "need"),
            "we are" to listOf("going", "ready", "here", "at", "not", "so"),
            "let" to listOf("me", "us", "it", "know"),
            "let me" to listOf("know", "check", "see", "think", "tell"),

            "hola" to listOf("cómo", "qué", "amigo", "buenos", "todos"),
            "cómo" to listOf("estás", "te", "se", "vamos", "hacer"),
            "cómo estás" to listOf("hoy", "amigo", "bien", "tú"),
            "gracias" to listOf("por", "amigo", "muchas", "de"),
            "muchas gracias" to listOf("por", "amigo", "de", "a"),
            "de nada" to listOf("amigo", "un", "placer", "para"),
            "buenos" to listOf("días", "noches", "momentos"),
            "buenas" to listOf("tardes", "noches", "días"),
            "por favor" to listOf("avísame", "mándame", "ayúdame", "ven"),
            "qué" to listOf("tal", "haces", "pasó", "hora", "quieres"),
            "qué tal" to listOf("todo", "estás", "el", "tu"),
            "voy a" to listOf("ir", "hacer", "llegar", "ver", "salir", "estar"),
            "te quiero" to listOf("mucho", "decir", "preguntar"),
            "dónde" to listOf("estás", "vamos", "queda", "está"),
            "hasta" to listOf("luego", "mañana", "pronto", "la"),
            "estoy" to listOf("bien", "en", "aquí", "listo", "yendo"),

            "bonjour" to listOf("comment", "tout", "à", "mon"),
            "bonsoir" to listOf("comment", "tout", "à"),
            "comment" to listOf("vas-tu", "ça", "allez-vous", "faire"),
            "comment vas-tu" to listOf("aujourd'hui", "mon", "bien"),
            "merci" to listOf("beaucoup", "infiniment", "pour", "d'avance"),
            "merci beaucoup" to listOf("pour", "mon", "à", "de"),
            "s'il vous" to listOf("plaît", "plait"),
            "s'il te" to listOf("plaît", "plait"),
            "je suis" to listOf("là", "prêt", "désolé", "en", "content"),
            "j'ai" to listOf("besoin", "compris", "fini", "envie", "un"),
            "c'est" to listOf("bon", "bien", "vrai", "parti", "super", "facile"),
            "à" to listOf("bientôt", "demain", "plus", "tard"),
            "bonne" to listOf("journée", "nuit", "soirée", "chance"),
            "je vais" to listOf("bien", "venir", "partir", "faire"),

            "hallo" to listOf("wie", "zusammen", "alle"),
            "wie" to listOf("geht's", "geht", "war", "kann", "spät"),
            "wie geht's" to listOf("dir", "euch", "heute", "denn"),
            "vielen" to listOf("Dank", "Dank!", "Dank für"),
            "guten" to listOf("Morgen", "Tag", "Abend"),
            "gute" to listOf("Nacht", "Besserung", "Reise", "Idee"),
            "ich bin" to listOf("hier", "bereit", "unterwegs", "gespannt"),
            "ich habe" to listOf("keine", "eine", "Zeit", "es", "Angst"),
            "bis" to listOf("später", "morgen", "bald", "dann"),
            "kein" to listOf("Problem", "Ding", "Thema"),
            "freut" to listOf("mich", "mich sehr"),
            "alles" to listOf("Gute", "klar", "bestens"),
            "was" to listOf("machst", "ist", "geht", "hast"),

            "kya" to listOf("kar", "hua", "baat", "chal", "bol", "hai"),
            "kaise" to listOf("ho", "hai", "karein", "batao"),
            "kaisi" to listOf("ho", "hai", "lagi"),
            "kaisa" to listOf("hai", "laga", "tha", "raha"),
            "kahan" to listOf("ho", "hai", "ja", "gaye", "se"),
            "theek" to listOf("hai", "tha", "hoga", "se"),
            "bahut" to listOf("accha", "badhiya", "sundar", "kam", "zyada", "shukriya"),
            "shukriya" to listOf("bhai", "dost", "aapka", "sir"),
            "aap" to listOf("kaise", "kahan", "kya", "batao"),
            "main" to listOf("theek", "aa", "ja", "kar", "hoon"),
            "kuch" to listOf("nahi", "bhi", "khas", "bolo", "batao"),
            "sahi" to listOf("hai", "baat", "lag", "raha"),
            "chal" to listOf("theek", "koi", "bhai"),
            "aaj" to listOf("kya", "shaam", "milte", "raat"),
            "kal" to listOf("milte", "aata", "subah", "pakka"),

            "olá" to listOf("como", "tudo", "amigo"),
            "como" to listOf("vai", "está", "você", "fazer"),
            "como vai" to listOf("você", "tudo", "amigo"),
            "muito" to listOf("obrigado", "obrigada", "bom", "bem"),
            "bom" to listOf("dia", "trabalho", "fim", "descanso"),
            "boa" to listOf("tarde", "noite", "sorte", "viagem"),
            "por" to listOf("favor", "isso", "enquanto", "exemplo"),
            "tudo" to listOf("bem", "certo", "bom", "ótimo"),
            "eu" to listOf("estou", "vou", "quero", "acho", "tenho"),
            "até" to listOf("logo", "amanhã", "mais", "breve"),
            "de" to listOf("nada", "acordo", "novo"),

            "ciao" to listOf("come", "tutti", "a"),
            "come" to listOf("stai", "va", "posso", "fare"),
            "come stai" to listOf("oggi", "bene", "amico"),
            "grazie" to listOf("mille", "di", "per", "mille!"),
            "buon" to listOf("giorno", "pomeriggio", "lavoro", "viaggio"),
            "buona" to listOf("giornata", "sera", "notte", "fortuna"),
            "per" to listOf("favore", "piacere", "sempre", "fortuna"),
            "ci" to listOf("vediamo", "sentiamo", "sono"),
            "a" to listOf("presto", "domani", "dopo"),
            "tutto" to listOf("bene", "posto", "chiaro"),
            "io" to listOf("sono", "vado", "penso", "posso"),

            "привет" to listOf("как", "всем", "друг"),
            "как" to listOf("дела", "ты", "вы", "насчет"),
            "как дела" to listOf("сегодня", "у", "хорошо"),
            "спасибо" to listOf("большое", "огромное", "за"),
            "доброе" to listOf("утро", "время"),
            "добрый" to listOf("день", "вечер"),
            "спокойной" to listOf("ночи"),
            "до" to listOf("свидания", "встречи", "скорого"),
            "все" to listOf("хорошо", "понятно", "отлично"),
            "пожалуйста" to listOf("ответь", "помоги", "скажи"),
            "я" to listOf("думаю", "знаю", "хочу", "буду")
        )
        semanticTransitions.putAll(pairs)
    }
}
