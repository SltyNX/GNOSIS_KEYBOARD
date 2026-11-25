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
    private var initialized = false
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    class GestureWord(val word: String, val freq: Float)

    @Volatile
    private var gestureBuckets: Map<Char, List<GestureWord>>? = null

    fun getGestureBuckets(): Map<Char, List<GestureWord>>? = gestureBuckets
    fun getUserFrequencies(): Map<String, Int> = userWordFreq
    fun getCommonWordList(): List<String> = commonWords

    fun setGestureVocabulary(words: List<String>) {
        val n = words.size.coerceAtLeast(1).toFloat()
        val map = HashMap<Char, MutableList<GestureWord>>()
        for ((i, w) in words.withIndex()) {
            if (w.length < 2 || !w.all { it in 'a'..'z' }) continue
            map.getOrPut(w[0]) { ArrayList() }.add(GestureWord(w, 1f + 19f * (1f - i / n)))
        }
        gestureBuckets = map
        try {
            gnosis.keyboard.engine.glide.GnosisGlideEngine.initVocabulary(words)
        } catch (_: Throwable) {}
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
        "iam" to listOf("I'm"),
        "ive" to listOf("I've"),
        "ill" to listOf("I'll", "ill"),
        "id" to listOf("I'd"),
        "theyre" to listOf("they're", "there", "their"),
        "theyare" to listOf("they're"),
        "youare" to listOf("you're"),
        "weare" to listOf("we're"),
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
        initialized = true
        try {
            Thread {
                getCommonWords(ctx)
            }.start()
        } catch (_: Throwable) {}
    }

    fun learn(ctx: Context, word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length < 2) return

        init(ctx)
        synchronized(userWordFreq) {
            val current = userWordFreq[clean] ?: 0
            userWordFreq[clean] = (current + 1).coerceAtMost(9999)
            try {
                gnosis.keyboard.engine.glide.GnosisGlideEngine.insertWord(clean, (current + 1).toFloat() * 10f)
            } catch (_: Throwable) {}
        }

        val appContext = ctx.applicationContext
        backgroundExecutor.execute {
            try {
                val serialized = synchronized(userWordFreq) {
                    userWordFreq.entries
                        .sortedByDescending { it.value }
                        .take(500)
                        .joinToString(";") { "${it.key}:${it.value}" }
                }

                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LEARNED, serialized)
                    .apply()
            } catch (_: Throwable) {}
        }
    }

    fun clearLearnedWords(ctx: Context) {
        init(ctx)
        synchronized(userWordFreq) {
            userWordFreq.clear()
        }
        synchronized(userTransitions) {
            userTransitions.clear()
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences(PREFS_TRANSITIONS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun learnTransition(ctx: Context, prevWord: String, nextWord: String) {
        val p = prevWord.trim().lowercase(Locale.ROOT)
        val n = nextWord.trim().lowercase(Locale.ROOT)
        if (p.isEmpty() || n.isEmpty() || p == n) return

        init(ctx)
        val copyMap = synchronized(userTransitions) {
            val map = userTransitions.getOrPut(p) { mutableMapOf() }
            val currentCount = map[n] ?: 0
            map[n] = (currentCount + 1).coerceAtMost(999)
            map.toMap()
        }

        val appContext = ctx.applicationContext
        backgroundExecutor.execute {
            try {
                val serialized = copyMap.entries
                    .sortedByDescending { it.value }
                    .take(20)
                    .joinToString(";") { "${it.key}:${it.value}" }

                appContext.getSharedPreferences(PREFS_TRANSITIONS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(p, serialized)
                    .apply()
            } catch (_: Throwable) {}
        }
    }

    @Volatile
    private var assetDictionaryWords: Set<String>? = null

    fun reload(context: Context? = null) {
        assetDictionaryWords = null
        if (context != null) {
            getCommonWords(context)
        }
    }

    fun getCommonWords(context: Context? = null): Set<String> {
        val cached = assetDictionaryWords
        if (cached != null) return cached

        val combined = HashSet<String>(commonWords)
        if (context != null) {
            val binaryLoaded = loadBinaryDictionary(context, combined)
            if (!binaryLoaded) {
                try {
                    context.assets.open("words_en.txt").bufferedReader().useLines { lines ->
                        for (line in lines) {
                            val w = line.trim().lowercase(Locale.ROOT)
                            if (w.isNotEmpty()) combined.add(w)
                        }
                    }
                } catch (_: Exception) {
                    try {
                        context.assets.open("words/words(10000).txt").bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val w = line.trim().lowercase(Locale.ROOT)
                                if (w.isNotEmpty()) combined.add(w)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            try {
                context.assets.open("words_hinglish.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val w = line.trim().lowercase(Locale.ROOT)
                        if (w.isNotEmpty()) combined.add(w)
                    }
                }
            } catch (_: Exception) {}

            // Words loaded from asset dictionary
        }
        assetDictionaryWords = combined
        wordsByLengthCache = combined.groupBy { it.length }
        setGestureVocabulary(combined.toList())
        try {
            Thread {
                gnosis.keyboard.engine.suggestion_typing.GnosisRadixFstEngine.instance.build(combined)
            }.start()
        } catch (_: Throwable) {}
        return combined
    }

    private fun loadBinaryDictionary(context: Context, target: MutableSet<String>): Boolean {
        return try {
            val buffer: java.nio.ByteBuffer = try {
                val fd = context.assets.openFd("words_en.bin")
                val channel = java.io.FileInputStream(fd.fileDescriptor).channel
                val mapped = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
                mapped.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                mapped
            } catch (_: Exception) {
                context.assets.open("words_en.bin").use { input ->
                    val bytes = input.readBytes()
                    val b = java.nio.ByteBuffer.wrap(bytes)
                    b.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    b
                }
            }
            if (buffer.remaining() < 10) return false
            val m0 = buffer.get()
            val m1 = buffer.get()
            val m2 = buffer.get()
            val m3 = buffer.get()
            if (m0 != 0x47.toByte() || m1 != 0x4E.toByte() || m2 != 0x44.toByte() || m3 != 0x42.toByte()) {
                return false
            }
            buffer.short // version
            val count = buffer.int
            val wordBytes = ByteArray(128)
            for (i in 0 until count) {
                if (!buffer.hasRemaining()) break
                val len = buffer.get().toInt() and 0xFF
                if (len in 1..127) {
                    buffer.get(wordBytes, 0, len)
                    val word = String(wordBytes, 0, len, Charsets.UTF_8)
                    target.add(word)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Volatile
    private var wordsByLengthCache: Map<Int, List<String>>? = null

    fun getCandidateWordsByLength(targetLength: Int): List<String> {
        var cache = wordsByLengthCache
        if (cache == null) {
            val vocab = getCommonWords()
            cache = vocab.groupBy { it.length }
            wordsByLengthCache = cache
        }
        val res = ArrayList<String>()
        cache[targetLength - 1]?.let { res.addAll(it) }
        cache[targetLength]?.let { res.addAll(it) }
        cache[targetLength + 1]?.let { res.addAll(it) }
        return res
    }

    private val commonWordsSet by lazy { commonWords.toSet() }

    fun isCommonWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT)
        return assetDictionaryWords?.contains(lower) ?: commonWordsSet.contains(lower)
    }

    fun getVocab(): Set<String> {
        return assetDictionaryWords ?: commonWordsSet
    }

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()

        val rawLower = prefix.lowercase(Locale.ROOT)
        val cleanLower = rawLower.replace("'", "")
        val isFirstCapital = prefix[0].isUpperCase()
        val isAllCaps = prefix.length > 1 && prefix.all { it.isUpperCase() }

        val candidateMatches = LinkedHashSet<String>()
        val vocab = getVocab()

        val isKnown = vocab.contains(rawLower) || (userWordFreq[rawLower] ?: 0) >= 3
        if (isKnown) {
            candidateMatches.add(formatCasing(rawLower, isFirstCapital, isAllCaps))
        }

        contractionShortcuts[cleanLower]?.let { list ->
            list.forEach { candidateMatches.add(formatCasing(it, isFirstCapital, isAllCaps)) }
        }

        val learnedRanked = userWordFreq.entries
            .filter { it.value >= 3 || vocab.contains(it.key) }
            .sortedByDescending { it.value }
            .map { it.key }
        for (w in learnedRanked) {
            if (candidateMatches.size >= 8) break
            if (w.startsWith(rawLower) || w.replace("'", "").startsWith(cleanLower)) {
                candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
            }
        }

        for (w in vocab) {
            if (candidateMatches.size >= 8) break
            if (w.startsWith(rawLower) || w.replace("'", "").startsWith(cleanLower)) {
                candidateMatches.add(formatCasing(w, isFirstCapital, isAllCaps))
            }
        }

        if (rawLower.length >= 3 && candidateMatches.size < limit) {
            val fuzzyMatches = mutableListOf<Pair<String, Int>>()
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
        return gnosis.keyboard.engine.glide.GnosisGlideEngine.matchGesture(points, keyBounds, limit)
    }

    fun matchGestureTimed(
        points: List<gnosis.keyboard.engine.glide.GnosisGlideEngine.TimedPoint>,
        keyBounds: Map<Char, RectF>,
        limit: Int = 3
    ): List<String> {
        return gnosis.keyboard.engine.glide.GnosisGlideEngine.matchGestureTimed(points, keyBounds, limit)
    }

    fun matchMultiWordGesture(
        points: List<PointF>,
        keyBounds: Map<Char, RectF>,
        spaceBounds: RectF?,
        limit: Int = 3
    ): Pair<List<String>, List<String>> {
        return gnosis.keyboard.engine.glide.GnosisGlideEngine.matchMultiWordGesture(points, keyBounds, spaceBounds, limit)
    }

    fun matchMultiWordGestureTimed(
        points: List<gnosis.keyboard.engine.glide.GnosisGlideEngine.TimedPoint>,
        keyBounds: Map<Char, RectF>,
        spaceBounds: RectF?,
        limit: Int = 3
    ): Pair<List<String>, List<String>> {
        return gnosis.keyboard.engine.glide.GnosisGlideEngine.matchMultiWordGestureTimed(points, keyBounds, spaceBounds, limit)
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
