package gnosis.keyboard.suggest

import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.prediction.CompactNgramModel
import java.util.Locale

class GnosisNgramPredictor(
    private val ngramModel: CompactNgramModel?,
    private val userLexicon: UserLexicon
) {

    private val commonFollowers: Map<String, List<String>> = mapOf(
        "how are" to listOf("you", "things", "we", "they"),
        "how is" to listOf("it", "your", "the", "everything", "work"),
        "how do" to listOf("you", "we", "i"),
        "what are" to listOf("you", "we", "they", "the"),
        "what is" to listOf("the", "your", "this", "that", "it"),
        "what do" to listOf("you", "we", "they"),
        "where are" to listOf("you", "we", "they"),
        "where is" to listOf("the", "it", "my", "your"),
        "thank you" to listOf("so", "very", "much", "for", "again"),
        "thanks for" to listOf("the", "your", "helping", "all", "everything"),
        "i am" to listOf("going", "not", "so", "glad", "here", "ready", "at"),
        "i will" to listOf("be", "call", "send", "come", "see", "let"),
        "i have" to listOf("been", "a", "no", "to", "the", "already"),
        "i would" to listOf("like", "love", "be", "have"),
        "i think" to listOf("it", "so", "we", "that", "you"),
        "i want" to listOf("to", "a", "the", "you"),
        "i need" to listOf("to", "a", "the", "your"),
        "i can" to listOf("do", "help", "see", "send", "come"),
        "you are" to listOf("welcome", "the", "so", "right", "great"),
        "you can" to listOf("do", "call", "send", "check", "see"),
        "you have" to listOf("a", "to", "been", "the"),
        "we are" to listOf("going", "ready", "here", "at", "not"),
        "we will" to listOf("be", "see", "have", "let"),
        "we can" to listOf("do", "meet", "go", "talk", "see"),
        "they are" to listOf("coming", "going", "here", "not"),
        "he is" to listOf("going", "a", "at", "not", "very"),
        "she is" to listOf("going", "a", "at", "not", "very"),
        "it is" to listOf("a", "the", "not", "very", "so", "good"),
        "it was" to listOf("a", "very", "great", "so", "not"),
        "let me" to listOf("know", "see", "check", "tell", "think"),
        "see you" to listOf("soon", "tomorrow", "later", "there", "tonight"),
        "looking forward" to listOf("to", "for"),
        "good" to listOf("morning", "night", "day", "luck", "job", "afternoon"),
        "great" to listOf("job", "work", "to", "news", "idea"),
        "have a" to listOf("great", "good", "nice", "wonderful", "safe"),
        "have a great" to listOf("day", "time", "weekend", "evening", "trip"),
        "have a good" to listOf("one", "day", "night", "time", "morning"),
        "please let" to listOf("me", "us"),
        "can you" to listOf("please", "send", "call", "help", "do", "check"),
        "could you" to listOf("please", "send", "check", "help", "let"),
        "do you" to listOf("have", "want", "know", "need", "think"),
        "are you" to listOf("ready", "sure", "free", "there", "coming"),

        "i" to listOf("am", "will", "have", "think", "want", "need", "can"),
        "you" to listOf("are", "can", "have", "will", "know", "want"),
        "we" to listOf("are", "can", "will", "have", "need"),
        "they" to listOf("are", "were", "will", "have"),
        "he" to listOf("is", "was", "will", "has"),
        "she" to listOf("is", "was", "will", "has"),
        "it" to listOf("is", "was", "will", "would"),
        "how" to listOf("are", "is", "was", "do", "can"),
        "what" to listOf("is", "are", "do", "did", "time"),
        "where" to listOf("are", "is", "did", "were"),
        "when" to listOf("is", "will", "are"),
        "why" to listOf("are", "is", "did", "do"),
        "can" to listOf("you", "i", "we"),
        "could" to listOf("you", "we", "i"),
        "do" to listOf("you", "not", "we"),
        "did" to listOf("you", "not", "he"),
        "are" to listOf("you", "they", "we"),
        "is" to listOf("it", "there", "that", "this"),
        "have" to listOf("a", "to", "you", "been"),
        "the" to listOf("best", "new", "first", "same"),
        "this" to listOf("is", "was", "will"),
        "that" to listOf("is", "was", "would"),
        "my" to listOf("way", "phone", "name", "friend"),
        "your" to listOf("welcome", "help", "time", "phone"),
        "to" to listOf("the", "be", "you", "do", "get"),
        "for" to listOf("the", "you", "your", "this"),
        "in" to listOf("the", "a", "my"),
        "on" to listOf("the", "my", "your"),
        "at" to listOf("the", "home", "work"),
        "please" to listOf("let", "send", "call", "help"),
        "hello" to listOf("how", "there", "everyone"),
        "hi" to listOf("how", "there", "everyone"),
        "hey" to listOf("how", "there", "what"),
        "yes" to listOf("i", "we", "please", "sure"),
        "no" to listOf("problem", "worries", "i"),
        "okay" to listOf("i", "sounds", "thanks"),
        "sure" to listOf("i", "thing", "will"),
        "sorry" to listOf("for", "about", "i"),

        "kya" to listOf("hai", "kar", "rahe", "hua", "baat"),
        "kaise" to listOf("ho", "hai", "karein", "batao"),
        "kahan" to listOf("ho", "hai", "ja", "gaye"),
        "aap" to listOf("kaise", "kahan", "kya", "batao"),
        "theek" to listOf("hai", "tha", "hoga"),
        "main" to listOf("bhi", "theek", "aa", "ghar"),
        "accha" to listOf("hai", "laga", "theek", "yaar"),
        "badhiya" to listOf("hai", "tha", "bhai", "yaar"),
        "kab" to listOf("aaoge", "tak", "chalna", "mile")
    )

    fun predictNextWords(context: List<String>, limit: Int = 3): List<GnosisCandidate> {
        if (context.isEmpty()) {
            return listOf(
                GnosisCandidate("How", GnosisCandidateType.PREDICTION, 0.90, "unigram_seed"),
                GnosisCandidate("I", GnosisCandidateType.PREDICTION, 0.85, "unigram_seed"),
                GnosisCandidate("The", GnosisCandidateType.PREDICTION, 0.80, "unigram_seed")
            )
        }

        val cleanCtx = context.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        if (cleanCtx.isEmpty()) return emptyList()

        val scoredMap = LinkedHashMap<String, Double>()

        if (cleanCtx.size >= 2) {
            val trigramKey = "${cleanCtx[cleanCtx.size - 2]} ${cleanCtx.last()}"
            commonFollowers[trigramKey]?.forEachIndexed { idx, word ->
                scoredMap[word] = 0.95 - (idx * 0.05)
            }
        }

        val lastWord = cleanCtx.last()
        commonFollowers[lastWord]?.forEachIndexed { idx, word ->
            if (!scoredMap.containsKey(word)) {
                scoredMap[word] = 0.85 - (idx * 0.05)
            }
        }

        val topLearned = userLexicon.getTopWords(20)
        for (w in topLearned) {
            val userScore = userLexicon.getUserScore(w, cleanCtx)
            if (userScore > 0.0) {
                val existing = scoredMap[w] ?: 0.0
                scoredMap[w] = existing + (userScore * 0.15)
            }
        }

        if (ngramModel != null) {
            for (key in scoredMap.keys.toList()) {
                val prob = ngramModel.nextWordProb(cleanCtx, key)
                scoredMap[key] = (scoredMap[key] ?: 0.5) + (prob * 0.20)
            }
        }

        return scoredMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (word, score) ->
                GnosisCandidate(
                    text = word,
                    type = GnosisCandidateType.PREDICTION,
                    score = score,
                    source = "ngram_predictor"
                )
            }
    }
}
