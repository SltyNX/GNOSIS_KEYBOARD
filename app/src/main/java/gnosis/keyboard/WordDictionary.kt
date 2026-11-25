package gnosis.keyboard

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

object WordDictionary {

    private const val PREFS_NAME = "gnosis_kb_dict"
    private const val KEY_LEARNED = "user_learned_words"
    private const val PREFS_TRANSITIONS = "gnosis_kb_transitions"

    private val userWordFreq = mutableMapOf<String, Int>()
    private val userTransitions = mutableMapOf<String, MutableMap<String, Int>>()
    private val hinglishWords = LinkedHashSet<String>()
    private var initialized = false

    private class GestureWord(val word: String, val freq: Float)

    @Volatile
    private var gestureBuckets: Map<Char, List<GestureWord>>? = null

    fun setGestureVocabulary(words: List<String>) {
        val n = words.size.coerceAtLeast(1).toFloat()
        val map = HashMap<Char, MutableList<GestureWord>>()
        for ((i, w) in words.withIndex()) {
            if (w.length < 2 || !w.all { it in 'a'..'z' }) continue
            map.getOrPut(w[0]) { ArrayList() }.add(GestureWord(w, 1f + 19f * (1f - i / n)))
        }
        gestureBuckets = map
    }

    private val contractionShortcuts = mapOf(
        "it" to listOf("it's", "it"),
        "its" to listOf("it's", "its", "itself"),
        "dont" to listOf("don't"),
        "don" to listOf("don't"),
        "youre" to listOf("you're", "your"),
        "your" to listOf("you're", "your", "yours"),
        "cant" to listOf("can't", "cannot"),
        "can" to listOf("can", "can't"),
        "wont" to listOf("won't"),
        "im" to listOf("I'm"),
        "ive" to listOf("I've"),
        "ill" to listOf("I'll", "ill"),
        "id" to listOf("I'd"),
        "theyre" to listOf("they're", "there", "their"),
        "were" to listOf("we're", "were"),
        "thats" to listOf("that's", "that"),
        "that" to listOf("that's", "that"),
        "whats" to listOf("what's", "what"),
        "what" to listOf("what's", "what"),
        "lets" to listOf("let's", "lets"),
        "theres" to listOf("there's", "there"),
        "heres" to listOf("here's", "here"),
        "didnt" to listOf("didn't"),
        "doesnt" to listOf("doesn't"),
        "isnt" to listOf("isn't"),
        "arent" to listOf("aren't"),
        "wasnt" to listOf("wasn't"),
        "werent" to listOf("weren't"),
        "havent" to listOf("haven't"),
        "hasnt" to listOf("hasn't"),
        "hadnt" to listOf("hadn't"),
        "couldnt" to listOf("couldn't"),
        "shouldnt" to listOf("shouldn't"),
        "wouldnt" to listOf("wouldn't"),
        "mustnt" to listOf("mustn't"),
        "neednt" to listOf("needn't"),
        "mightnt" to listOf("mightn't"),
        "shant" to listOf("shan't"),
        "aint" to listOf("ain't"),
        "shes" to listOf("she's", "she"),
        "hes" to listOf("he's", "he"),
        "hows" to listOf("how's"),
        "whos" to listOf("who's", "whose"),
        "wheres" to listOf("where's", "where"),
        "whens" to listOf("when's"),
        "whys" to listOf("why's"),
        "wouldve" to listOf("would've"),
        "couldve" to listOf("could've"),
        "shouldve" to listOf("should've"),
        "mustve" to listOf("must've"),
        "mightve" to listOf("might've"),
        "yall" to listOf("y'all"),
        "yallre" to listOf("y'all're"),
        "yallve" to listOf("y'all've"),
        "cmon" to listOf("c'mon"),
        "maam" to listOf("ma'am"),
        "oclock" to listOf("o'clock"),
        "todays" to listOf("today's"),
        "tomorrows" to listOf("tomorrow's"),
        "yesterdays" to listOf("yesterday's"),
        "weeks" to listOf("week's"),
        "months" to listOf("month's"),
        "years" to listOf("year's"),
        "everyones" to listOf("everyone's"),
        "someones" to listOf("someone's"),
        "nobodys" to listOf("nobody's"),
        "dontt" to listOf("don't"),
        "cantt" to listOf("can't"),
        "woudnt" to listOf("wouldn't"),
        "isntt" to listOf("isn't"),
        "didntt" to listOf("didn't")
    )

    private val commonWords = listOf(
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
        "is", "are", "was", "were", "been", "has", "had", "did", "doing", "done",
        "am", "shall", "should", "may", "might", "must", "hello", "hi", "hey",
        "yes", "yeah", "ok", "okay", "sure", "fine", "cool", "great", "awesome",
        "wanna", "gonna", "gotta", "kinda", "sorta", "dunno", "lemme", "gimme",
        "thanks", "thank", "please", "sorry", "welcome", "happy", "today", "tomorrow",
        "yesterday", "tonight", "morning", "night", "love", "friend", "family", "home",
        "school", "work", "office", "phone", "email", "message", "call", "send",
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
        "again", "before", "next", "few", "many", "much", "more", "most", "very",
        "little", "own", "another", "such", "same", "different", "right",
        "left", "big", "small", "large", "long", "short", "high", "low", "old",
        "young", "fast", "slow", "easy", "hard", "simple", "complex", "true", "false",
        "ready", "able", "bad", "best", "better", "important", "clear",
        "clean", "free", "full", "late", "early", "real", "strong", "possible", "whole",
        "safe", "recent", "main", "major", "beautiful", "perfect",
        "quick", "smart", "super", "wonder", "wonderful", "amazing", "keyboard", "gnosis"
    )

    fun init(ctx: Context) {
        if (initialized) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LEARNED, null)
        if (!raw.isNullOrEmpty()) {
            raw.split(";").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val w = parts[0]
                    val count = parts[1].toIntOrNull() ?: 1
                    userWordFreq[w] = count
                }
            }
        }

        val transPrefs = ctx.getSharedPreferences(PREFS_TRANSITIONS, Context.MODE_PRIVATE)
        transPrefs.all.forEach { (key, value) ->
            if (value is String) {
                val nextMap = mutableMapOf<String, Int>()
                value.split(";").forEach { item ->
                    val pair = item.split(":")
                    if (pair.size == 2) {
                        nextMap[pair[0]] = pair[1].toIntOrNull() ?: 1
                    }
                }
                userTransitions[key] = nextMap
            }
        }
        try {
            if (HinglishDictionaryManager.isEnabled(ctx)) {
                hinglishWords.addAll(HinglishDictionaryManager.loadWords(ctx))
            }
            hinglishWords.addAll(DictionaryExtensionManager.loadAllActiveWords(ctx))
        } catch (_: Exception) {}
        initialized = true
    }

    fun learn(ctx: Context, word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length < 2) return

        init(ctx)
        val current = userWordFreq[clean] ?: 0
        userWordFreq[clean] = (current + 1).coerceAtMost(9999)

        val serialized = userWordFreq.entries
            .sortedByDescending { it.value }
            .take(500)
            .joinToString(";") { "${it.key}:${it.value}" }

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEARNED, serialized)
            .apply()
    }

    fun clearLearnedWords(ctx: Context) {
        init(ctx)
        userWordFreq.clear()
        userTransitions.clear()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences(PREFS_TRANSITIONS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun learnTransition(ctx: Context, prevWord: String, nextWord: String) {
        val p = prevWord.trim().lowercase(Locale.ROOT)
        val n = nextWord.trim().lowercase(Locale.ROOT)
        if (p.isEmpty() || n.isEmpty() || p == n) return

        init(ctx)
        val map = userTransitions.getOrPut(p) { mutableMapOf() }
        val currentCount = map[n] ?: 0
        map[n] = (currentCount + 1).coerceAtMost(999)

        val serialized = map.entries
            .sortedByDescending { it.value }
            .take(20)
            .joinToString(";") { "${it.key}:${it.value}" }

        ctx.getSharedPreferences(PREFS_TRANSITIONS, Context.MODE_PRIVATE)
            .edit()
            .putString(p, serialized)
            .apply()
    }

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()

        val rawLower = prefix.lowercase(Locale.ROOT)
        val cleanLower = rawLower.replace("'", "")
        val isFirstCapital = prefix[0].isUpperCase()
        val isAllCaps = prefix.length > 1 && prefix.all { it.isUpperCase() }

        val candidateMatches = LinkedHashSet<String>()

        if (commonWords.contains(rawLower) || userWordFreq.containsKey(rawLower) || hinglishWords.contains(rawLower)) {
            candidateMatches.add(formatCasing(rawLower, isFirstCapital, isAllCaps))
        }

        contractionShortcuts[cleanLower]?.let { list ->
            list.forEach { candidateMatches.add(formatCasing(it, isFirstCapital, isAllCaps)) }
        }

        val learnedRanked = userWordFreq.entries.sortedByDescending { it.value }.map { it.key }
        for (w in learnedRanked) {
            if (candidateMatches.size >= 8) break
            if (w.startsWith(rawLower) || w.replace("'", "").startsWith(cleanLower)) {
                candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
            }
        }

        for (w in commonWords) {
            if (candidateMatches.size >= 8) break
            if (w.startsWith(rawLower) || w.replace("'", "").startsWith(cleanLower)) {
                candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
            }
        }

        if (candidateMatches.size < 8 && hinglishWords.isNotEmpty()) {
            for (w in hinglishWords) {
                if (candidateMatches.size >= 8) break
                if (w.startsWith(rawLower) || w.replace("'", "").startsWith(cleanLower)) {
                    candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
                }
            }
        }

        if (rawLower.length >= 3 && candidateMatches.size < limit) {
            val fuzzyMatches = mutableListOf<Pair<String, Int>>()
            val vocab = if (hinglishWords.isNotEmpty()) commonWords + hinglishWords.take(500) else commonWords
            for (w in vocab) {
                if (w.isNotEmpty() && w[0] == rawLower[0]) {
                    val dist = editDistance(rawLower, w)
                    if (dist <= 2) {
                        fuzzyMatches.add(w to dist)
                    }
                }
            }
            fuzzyMatches.sortBy { it.second }
            for ((w, _) in fuzzyMatches) {
                if (candidateMatches.size >= 8) break
                candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
            }
        }

        val topCandidates = candidateMatches.toList()
        if (topCandidates.isEmpty()) {
            return listOf(prefix)
        }

        val primaryCorrection = topCandidates.first()
        val formattedTyped = prefix

        return if (topCandidates.size == 1) {
            if (primaryCorrection.equals(formattedTyped, ignoreCase = true)) {
                listOf(formattedTyped)
            } else {
                listOf(formattedTyped, primaryCorrection)
            }
        } else if (topCandidates.size == 2) {
            if (primaryCorrection.equals(formattedTyped, ignoreCase = true)) {
                listOf(formattedTyped, topCandidates[1])
            } else {
                listOf(formattedTyped, primaryCorrection, topCandidates[1])
            }
        } else {
            if (primaryCorrection.equals(formattedTyped, ignoreCase = true)) {
                listOf(topCandidates[1], primaryCorrection, topCandidates[2])
            } else {
                listOf(formattedTyped, primaryCorrection, topCandidates[1])
            }
        }
    }

    fun matchGesture(
        points: List<PointF>,
        keyBounds: Map<Char, RectF>,
        limit: Int = 3
    ): List<String> {
        if (points.size < 4 || keyBounds.isEmpty()) return emptyList()

        val pStart = points.first()
        val pEnd = points.last()

        val startChar = findNearestKey(pStart, keyBounds) ?: return emptyList()
        val endChar = findNearestKey(pEnd, keyBounds) ?: return emptyList()

        val startCandidates = setOf(startChar) + findAdjacentKeys(startChar)
        val endCandidates = setOf(endChar) + findAdjacentKeys(endChar)

        val visitedKeys = extractVisitedKeys(points, keyBounds)
        val scoredWords = mutableListOf<Pair<String, Float>>()
        val seen = HashSet<String>()
        fun consider(word: String, dictFreq: Float) {
            if (word.length < 2 || !seen.add(word)) return
            val first = word.first()
            val last = word.last()
            if (!startCandidates.contains(first) || !endCandidates.contains(last)) return
            if (!matchesSequence(word, visitedKeys)) return

            val distScore = computeSpatialDistance(word, points, keyBounds)
            val baseFreq = userWordFreq[word]?.toFloat() ?: dictFreq
            val startBonus = if (first == startChar) 2.0f else 1.0f
            val endBonus = if (last == endChar) 2.0f else 1.0f
            scoredWords.add(word to (baseFreq * startBonus * endBonus) / (1.0f + (distScore / 40.0f)))
        }

        for (w in userWordFreq.keys) consider(w, 1f)
        val buckets = gestureBuckets
        if (buckets != null) {
            for (c in startCandidates) buckets[c]?.forEach { consider(it.word, it.freq) }
        } else {
            for (w in commonWords) consider(w, 20f)
        }

        scoredWords.sortByDescending { it.second }
        return scoredWords.take(limit).map { it.first }
    }

    private fun findNearestKey(point: PointF, keyBounds: Map<Char, RectF>): Char? {
        var closestChar: Char? = null
        var minDist = Float.MAX_VALUE
        for ((ch, rect) in keyBounds) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val dist = hypot(point.x - cx, point.y - cy)
            if (dist < minDist) {
                minDist = dist
                closestChar = ch
            }
        }
        return closestChar
    }

    private val qwertyNeighbors = mapOf(
        'q' to listOf('w', 'a'),
        'w' to listOf('q', 'e', 's', 'a'),
        'e' to listOf('w', 'r', 'd', 's'),
        'r' to listOf('e', 't', 'f', 'd'),
        't' to listOf('r', 'y', 'g', 'f'),
        'y' to listOf('t', 'u', 'h', 'g'),
        'u' to listOf('y', 'i', 'j', 'h'),
        'i' to listOf('u', 'o', 'k', 'j'),
        'o' to listOf('i', 'p', 'l', 'k'),
        'p' to listOf('o', 'l'),
        'a' to listOf('q', 'w', 's', 'z'),
        's' to listOf('w', 'e', 'a', 'd', 'z', 'x'),
        'd' to listOf('e', 'r', 's', 'f', 'x', 'c'),
        'f' to listOf('r', 't', 'd', 'g', 'c', 'v'),
        'g' to listOf('t', 'y', 'f', 'h', 'v', 'b'),
        'h' to listOf('y', 'u', 'g', 'j', 'b', 'n'),
        'j' to listOf('u', 'i', 'h', 'k', 'n', 'm'),
        'k' to listOf('i', 'o', 'j', 'l', 'm'),
        'l' to listOf('o', 'p', 'k'),
        'z' to listOf('a', 's', 'x'),
        'x' to listOf('z', 's', 'd', 'c'),
        'c' to listOf('x', 'd', 'f', 'v'),
        'v' to listOf('c', 'f', 'g', 'b'),
        'b' to listOf('v', 'g', 'h', 'n'),
        'n' to listOf('b', 'h', 'j', 'm'),
        'm' to listOf('n', 'j', 'k')
    )

    private fun findAdjacentKeys(ch: Char): List<Char> {
        return qwertyNeighbors[ch] ?: emptyList()
    }

    private fun extractVisitedKeys(points: List<PointF>, keyBounds: Map<Char, RectF>): List<Char> {
        val visited = mutableListOf<Char>()
        var lastChar: Char? = null

        for (p in points) {
            val key = findNearestKey(p, keyBounds) ?: continue
            if (key != lastChar) {
                visited.add(key)
                lastChar = key
            }
        }
        return visited
    }

    private fun matchesSequence(word: String, visitedKeys: List<Char>): Boolean {
        var wordIdx = 0
        for (k in visitedKeys) {
            if (wordIdx < word.length && word[wordIdx] == k) {
                wordIdx++
            }
        }
        return wordIdx >= (word.length * 0.75f).toInt()
    }

    private fun computeSpatialDistance(
        word: String,
        points: List<PointF>,
        keyBounds: Map<Char, RectF>
    ): Float {
        var totalDist = 0f
        for (ch in word) {
            val rect = keyBounds[ch] ?: continue
            val cx = rect.centerX()
            val cy = rect.centerY()

            var minPdist = Float.MAX_VALUE
            for (p in points) {
                val d = hypot(p.x - cx, p.y - cy)
                if (d < minPdist) minPdist = d
            }
            totalDist += minPdist
        }
        return totalDist / word.length.toFloat()
    }

    private fun formatCasing(word: String, isFirstCapital: Boolean, isAllCaps: Boolean): String {
        return when {
            isAllCaps -> word.uppercase(Locale.ROOT)
            isFirstCapital -> {
                if (word.startsWith("i'", ignoreCase = true)) {
                    "I'" + word.substring(2)
                } else if (word.equals("i", ignoreCase = true)) {
                    "I"
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
            else -> {
                if (word.startsWith("i'", ignoreCase = true)) {
                    "I'" + word.substring(2)
                } else if (word.equals("i", ignoreCase = true)) {
                    "I"
                } else {
                    word
                }
            }
        }
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[a.length][b.length]
    }
}
