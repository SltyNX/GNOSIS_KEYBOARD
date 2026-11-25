package gnosis.keyboard.engine.suggestion_typing

import android.content.Context
import gnosis.keyboard.WordDictionary
import gnosis.keyboard.engine.ai.GnosisAiConfig
import gnosis.keyboard.engine.ai.GnosisUniversalAiEngine
import gnosis.keyboard.engine.contractions.GnosisContractionEngine
import gnosis.keyboard.engine.delight.GnosisDynamicLm
import gnosis.keyboard.engine.delight.GnosisSpatialTouchModel
import gnosis.keyboard.engine.grammar.GnosisSentenceExplorer
import gnosis.keyboard.engine.homophones.GnosisHomophoneEngine
import gnosis.keyboard.engine.prediction.GnosisBigramPredictionEngine
import kotlin.math.abs

/**
 * Gnosis Candidate Manager
 *
 * Implements Gnosis 3-slot suggestion strip architecture:
 * - Center slot: Primary auto-correct candidate (bold accent highlight), or exact word
 * - Left slot: Literal typed word (allowing immediate rejection), or top completion
 * - Right slot: Next-word prediction or second candidate
 *
 * Integrates on-device Gnosis Universal AI Intelligence for contextual predictions,
 * smart autocomplete, and accurate typo recovery.
 */
class CandidateManager(
    private val context: Context? = null,
    private val dynamicLm: GnosisDynamicLm = GnosisDynamicLm(context),
    private val spatialModel: GnosisSpatialTouchModel = GnosisSpatialTouchModel()
) {

    private val universalAiEngine = context?.let { GnosisUniversalAiEngine.getInstance(it) }

    private val commonShortTypos = mapOf(
        "teh" to "the", "adn" to "and", "yu" to "you", "hw" to "how",
        "wld" to "would", "shd" to "should", "cld" to "could",
        "plz" to "please", "thx" to "thanks", "bc" to "because",
        "ur" to "your", "taht" to "that", "wiht" to "with",
        "fro" to "for", "fo" to "of", "ot" to "to",
        "waht" to "what", "hwo" to "how", "yuo" to "you",
        "whta" to "what", "thsi" to "this", "ti" to "it",
        "si" to "is", "aer" to "are", "hvae" to "have",
        "im" to "I'm", "id" to "I'd", "ill" to "I'll", "iv" to "I've",
        "dont" to "don't", "cant" to "can't", "wont" to "won't"
    )

    private val commonTransitions = mapOf(
        "how" to listOf("are", "is", "was", "about"),
        "what" to listOf("is", "are", "do", "time"),
        "where" to listOf("are", "is", "did", "can"),
        "thank" to listOf("you", "so", "very"),
        "i" to listOf("am", "have", "will", "can", "think", "love"),
        "you" to listOf("are", "have", "can", "will", "know"),
        "we" to listOf("are", "can", "will", "have"),
        "it" to listOf("is", "was", "will", "looks"),
        "good" to listOf("morning", "night", "day", "luck"),
        "see" to listOf("you", "what", "how"),
        "let" to listOf("me", "us", "go"),
        "to" to listOf("the", "be", "do", "get"),
        "in" to listOf("the", "my", "this"),
        "on" to listOf("the", "my", "time"),
        "for" to listOf("you", "the", "me"),
        "with" to listOf("you", "me", "the")
    )

    fun predictNextWords(contextBefore: String, limit: Int = 3): List<String> {
        val trimmed = contextBefore.trim()
        if (trimmed.isEmpty()) return listOf("I", "The", "How").take(limit)

        // 1. If Universal AI Intelligence is active, prioritize neural contextual predictions
        if (context != null && GnosisAiConfig.isAiEnabled(context) && GnosisAiConfig.isContextualPredictionsEnabled(context)) {
            val aiPreds = universalAiEngine?.predictNextWords(contextBefore, limit)
            if (!aiPreds.isNullOrEmpty()) return aiPreds
        }

        val lastWord = trimmed.split(Regex("\\s+")).lastOrNull()?.trim('?', '.', '!', ',', '"', '\'')?.lowercase() ?: return emptyList()

        val results = mutableListOf<String>()

        // 2. Dynamic LM transition (user personalized learning)
        val dynamicNext = dynamicLm.predictNext(lastWord)
        if (!dynamicNext.isNullOrEmpty()) {
            results.add(dynamicNext)
        }

        // 3. High-Coverage Statistical Bigram & Trigram Prediction Engine
        val statisticalPreds = GnosisBigramPredictionEngine.predictNext(contextBefore, limit)
        for (w in statisticalPreds) {
            if (results.size >= limit) break
            if (!results.contains(w)) results.add(w)
        }

        // 4. Predefined high-confidence fallback transitions
        val list = commonTransitions[lastWord] ?: emptyList()
        for (w in list) {
            if (results.size >= limit) break
            if (!results.contains(w)) results.add(w)
        }

        return results.take(limit)
    }

    fun generateSlots(
        activeWord: String,
        contextBefore: String
    ): List<CandidateItem> {
        val clean = activeWord.trim()

        // 1. Next-word prediction mode (idle / after space)
        if (clean.isEmpty()) {
            val nextWords = predictNextWords(contextBefore, limit = 3)
            return nextWords.map { CandidateItem(it, CandidateType.PREDICTION, 0.90f) }
        }

        // 2. Active typing mode
        val lower = clean.lowercase()
        val isSuppressed = dynamicLm.isSuppressed(lower)
        val contraction = GnosisContractionEngine.getContraction(clean) ?: GnosisSentenceExplorer.CONTRACTION_MAP[lower]
        val shortTypo = commonShortTypos[lower]
        val vocab = WordDictionary.getCommonWords(context)
        val isValid = WordDictionary.isCommonWord(lower)

        // Previous token extraction for contextual homophone & contraction ambiguity resolution
        val prevToken = contextBefore.trim().split(Regex("\\s+")).lastOrNull()?.trim('?', '.', '!', ',', '"', '\'')

        // Homophone disambiguation check (e.g. typing "there" when context requires "their" or vice versa)
        val homophoneCorrection = if (!isSuppressed && GnosisHomophoneEngine.isHomophone(lower)) {
            val disambiguated = GnosisHomophoneEngine.disambiguate(clean, prevWord = prevToken)
            if (disambiguated != null && !disambiguated.equals(clean, ignoreCase = true)) {
                disambiguated
            } else null
        } else null

        // Contraction ambiguity check (e.g. "were" vs "we're", "well" vs "we'll")
        val contractionAmbiguity = if (!isSuppressed) {
            GnosisContractionEngine.resolveAmbiguity(clean, prevWord = prevToken)
        } else null

        val aiEnabled = context != null && GnosisAiConfig.isAiEnabled(context)
        val aiSpellEnabled = aiEnabled && GnosisAiConfig.isAiSpellCorrectionEnabled(context)

        // Center candidate resolution
        val centerCandidate = if (homophoneCorrection != null) {
            CandidateItem(homophoneCorrection, CandidateType.AUTO_CORRECT, 1.20f)
        } else if (contractionAmbiguity != null) {
            CandidateItem(contractionAmbiguity, CandidateType.AUTO_CORRECT, 1.18f)
        } else if (contraction != null && !isSuppressed) {
            val formatted = preserveCase(clean, contraction)
            CandidateItem(formatted, CandidateType.AUTO_CORRECT, 1.15f)
        } else if (shortTypo != null && !isSuppressed) {
            val formatted = preserveCase(clean, shortTypo)
            CandidateItem(formatted, CandidateType.AUTO_CORRECT, 1.10f)
        } else if (isValid) {
            CandidateItem(clean, CandidateType.LITERAL, 1.0f)
        } else if (clean.length <= 3) {
            // Unrecognized short particle protection (avoid corrupting intentional abbreviations)
            CandidateItem(clean, CandidateType.LITERAL, 1.0f)
        } else {
            // Find typo corrections using spatial proximity, edit distance & contextual scoring
            val aiCorrection = if (aiSpellEnabled && universalAiEngine != null) {
                universalAiEngine.findContextualCorrection(clean, contextBefore, vocab)
            } else null

            val correction = aiCorrection ?: run {
                val fstMatches = GnosisRadixFstEngine.instance.findFuzzyMatches(clean, maxDistance = 1, limit = 2)
                val topFst = fstMatches.maxByOrNull {
                    it.second * GnosisBigramPredictionEngine.getContextualScore(it.first, contextBefore)
                }
                topFst ?: findBestCorrection(clean, vocab, contextBefore)
            }
            if (correction != null && !isSuppressed && correction.second >= 0.75f) {
                CandidateItem(correction.first, CandidateType.AUTO_CORRECT, correction.second + 0.15f)
            } else {
                CandidateItem(clean, CandidateType.LITERAL, 1.0f)
            }
        }

        val alternates = mutableListOf<CandidateItem>()

        // Check if this word has homophones: offer them as alternates!
        if (GnosisHomophoneEngine.isHomophone(lower)) {
            val homophones = GnosisHomophoneEngine.getHomophones(clean)
            for (h in homophones) {
                if (!h.equals(centerCandidate.text, ignoreCase = true) && alternates.none { it.text.equals(h, ignoreCase = true) }) {
                    alternates.add(CandidateItem(h, CandidateType.COMPLETION, 0.85f))
                }
            }
        }

        // Prefix completions via O(k) FST Radix Trie with N-Gram Contextual Rescoring
        val fstPrefixes = GnosisRadixFstEngine.instance.findPrefixMatches(lower, 6)
        val prefixMatches = (if (fstPrefixes.isNotEmpty()) fstPrefixes else vocab.filter { it.startsWith(lower) && it != lower }.take(6))
            .sortedByDescending { GnosisBigramPredictionEngine.getContextualScore(it, contextBefore) }
            .take(3)
        for (p in prefixMatches) {
            val formatted = preserveCase(clean, p)
            if (!formatted.equals(centerCandidate.text, ignoreCase = true) && alternates.none { it.text.equals(formatted, ignoreCase = true) }) {
                val cScore = GnosisBigramPredictionEngine.getContextualScore(p, contextBefore)
                alternates.add(CandidateItem(formatted, CandidateType.COMPLETION, 0.70f * cScore))
            }
        }

        val slots = mutableListOf<CandidateItem>()
        if (centerCandidate.type == CandidateType.AUTO_CORRECT) {
            slots.add(CandidateItem(clean, CandidateType.LITERAL, 0.95f))
            slots.add(centerCandidate)
            alternates.firstOrNull()?.let { slots.add(it) }
        } else {
            if (alternates.size >= 2) {
                slots.add(alternates[0])
                slots.add(centerCandidate)
                slots.add(alternates[1])
            } else if (alternates.size == 1) {
                slots.add(alternates[0])
                slots.add(centerCandidate)
            } else {
                slots.add(centerCandidate)
            }
        }

        // If AI is enabled, run contextual enhancement on candidate ordering
        return if (aiEnabled && universalAiEngine != null) {
            universalAiEngine.enhanceCandidates(clean, contextBefore, slots)
        } else {
            slots
        }
    }

    private val correctionCache = android.util.LruCache<String, Pair<String, Float>>(300)

    private fun findBestCorrection(rawWord: String, vocab: Set<String>, contextBefore: String = ""): Pair<String, Float>? {
        val lower = rawWord.lowercase()
        if (lower.length < 3) return null

        val cacheKey = if (contextBefore.isEmpty()) lower else "$lower|${contextBefore.takeLast(20)}"
        synchronized(correctionCache) {
            correctionCache.get(cacheKey)?.let { return it }
        }

        val firstChar = lower.firstOrNull() ?: return null
        val secondChar = if (lower.length > 1) lower[1] else null

        var bestCand: String? = null
        var bestScore = 0f

        val pool = ArrayList<String>()
        pool.addAll(WordDictionary.getCandidateWordsByLength(lower.length))
        if (lower.length > 3) {
            pool.addAll(WordDictionary.getCandidateWordsByLength(lower.length - 1))
            pool.addAll(WordDictionary.getCandidateWordsByLength(lower.length + 1))
        }

        for (cand in pool) {
            val candFirst = cand.firstOrNull() ?: continue
            val matchesFirst = (candFirst == firstChar) ||
                    (secondChar != null && candFirst == secondChar) ||
                    spatialModel.findAdjacentCharacters(candFirst).contains(firstChar)
            if (!matchesFirst) continue

            val dist = fastEditDistance(lower, cand, maxDist = 2)
            if (dist > 1) {
                if (lower.length >= 6 && dist == 2) {
                    val userFreq = dynamicLm.getUnigramFrequency(cand)
                    if (userFreq <= 0) continue
                } else {
                    continue
                }
            }

            val isAdjacent = if (cand.length == lower.length) {
                isAdjacentKeySubstitution(lower, cand)
            } else false

            val userFreq = dynamicLm.getUnigramFrequency(cand)
            val contextMultiplier = GnosisBigramPredictionEngine.getContextualScore(cand, contextBefore)
            val baseScore = 0.60f + (if (isAdjacent) 0.25f else 0.10f) + (userFreq * 0.05f).coerceAtMost(0.15f) - (if (dist > 1) 0.15f else 0.0f)
            val score = baseScore * contextMultiplier

            if (score > bestScore) {
                bestScore = score
                bestCand = cand
            }
        }

        val result = bestCand?.let { Pair(preserveCase(rawWord, it), bestScore) }
        if (result != null) {
            synchronized(correctionCache) {
                correctionCache.put(cacheKey, result)
            }
        }
        return result
    }

    private fun isAdjacentKeySubstitution(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diffCount = 0
        var charA = ' '
        var charB = ' '
        for (i in a.indices) {
            if (a[i] != b[i]) {
                diffCount++
                if (diffCount > 1) return false
                charA = a[i]
                charB = b[i]
            }
        }
        if (diffCount != 1) return false
        return spatialModel.findAdjacentCharacters(charB).contains(charA)
    }

    private fun fastEditDistance(a: String, b: String, maxDist: Int = 2): Int {
        val aLen = a.length
        val bLen = b.length
        if (Math.abs(aLen - bLen) > maxDist) return maxDist + 1

        var prev = IntArray(bLen + 1) { it }
        var curr = IntArray(bLen + 1)

        for (i in 1..aLen) {
            curr[0] = i
            var minInRow = curr[0]
            val aChar = a[i - 1]
            for (j in 1..bLen) {
                val cost = if (aChar == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < minInRow) minInRow = curr[j]
            }
            if (minInRow > maxDist) return maxDist + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[bLen]
    }

    private fun preserveCase(source: String, target: String): String {
        return when {
            source.all { it.isUpperCase() } -> target.uppercase()
            source.firstOrNull()?.isUpperCase() == true -> target.replaceFirstChar { it.uppercase() }
            else -> target.lowercase()
        }
    }
}
