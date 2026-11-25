package gnosis.keyboard.engine.delight

import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.prediction.CompactNgramModel
import gnosis.keyboard.engine.prediction.Trie
import gnosis.keyboard.engine.spell.ContractionEngine
import gnosis.keyboard.engine.spell.EditDistance
import gnosis.keyboard.engine.prediction.QwertyDistance

import gnosis.keyboard.engine.spell.TypoDictionary
import gnosis.keyboard.engine.spell.WordCasing
import java.util.Locale
import kotlin.math.ln

class GnosisBeamDecoder(
    private val trie: Trie,
    private val ngramModel: CompactNgramModel?,
    private val userLexicon: UserLexicon,
    private val spatialModel: GnosisSpatialModel = GnosisSpatialModel()
) {

    data class DecodeResult(
        val candidates: List<GnosisEngineCandidate>,
        val autoCorrectCandidate: GnosisEngineCandidate?
    )

    fun decode(
        typedPrefix: String,
        context: List<String>,
        limit: Int = 5
    ): DecodeResult {
        val clean = typedPrefix.trim()
        if (clean.isEmpty()) {
            return DecodeResult(emptyList(), null)
        }

        val lowerPrefix = clean.lowercase(Locale.ROOT)
        val isFirstCapital = clean.first().isUpperCase()
        val isAllCaps = clean.length > 1 && clean.all { it.isUpperCase() }

        val candidateMap = LinkedHashMap<String, GnosisEngineCandidate>()

        TypoDictionary.getCorrection(clean)?.let { fix ->
            val formatted = TypoDictionary.matchCasing(clean, fix)
            val key = formatted.lowercase(Locale.ROOT)
            candidateMap[key] = GnosisEngineCandidate(
                word = formatted,
                score = 0.99,
                spatialScore = 0.99,
                kind = CandidateKind.CORRECTION,
                source = "typo",
                isAutoCorrect = true
            )
        }

        ContractionEngine.getContractionCandidates(clean)?.forEach { contraction ->
            val formatted = ContractionEngine.matchCasing(clean, contraction)
            val key = formatted.lowercase(Locale.ROOT)
            val lmBoost = ngramModel?.nextWordProb(context, key) ?: 0.05
            val score = 0.94 + (lmBoost * 0.05)
            candidateMap[key] = GnosisEngineCandidate(
                word = formatted,
                score = score,
                spatialScore = 0.95,
                lmScore = lmBoost,
                kind = CandidateKind.CONTRACTION,
                source = "contraction",
                isAutoCorrect = true
            )
        }

        val inDict = trie.contains(lowerPrefix)
        val inUser = userLexicon.isKnown(lowerPrefix)
        if (inDict || inUser) {
            val userScore = normUser(userLexicon.getUserScore(lowerPrefix, context))
            val lmProb = ngramModel?.nextWordProb(context, lowerPrefix) ?: 0.08
            val score = 0.88 + (userScore * 0.07) + (lmProb * 0.05)
            val formatted = formatCasing(clean, isFirstCapital, isAllCaps)
            candidateMap[lowerPrefix] = GnosisEngineCandidate(
                word = formatted,
                score = score,
                spatialScore = 1.0,
                lmScore = lmProb,
                userScore = userScore,
                kind = if (inUser) CandidateKind.USER_LEARNED else CandidateKind.COMPLETION,
                source = "exact"
            )
        }

        for (learnedWord in userLexicon.getTopWords(40)) {
            if (learnedWord.startsWith(lowerPrefix) && !candidateMap.containsKey(learnedWord)) {
                val uScore = normUser(userLexicon.getUserScore(learnedWord, context))
                val score = 0.78 + (uScore * 0.16)
                candidateMap[learnedWord] = GnosisEngineCandidate(
                    word = formatCasing(learnedWord, isFirstCapital, isAllCaps),
                    score = score,
                    spatialScore = 0.85,
                    userScore = uScore,
                    kind = CandidateKind.USER_LEARNED,
                    source = "user_lexicon"
                )
            }
        }

        if (lowerPrefix.length >= 3) {
            val learnedMaxCost = when {
                lowerPrefix.length <= 4 -> 1.2
                lowerPrefix.length <= 7 -> 2.0
                else -> 3.0
            }
            val maxLenDiff = learnedMaxCost.toInt()
            for (learnedWord in userLexicon.getTopWords(2000)) {
                if (candidateMap.containsKey(learnedWord)) continue
                if (kotlin.math.abs(learnedWord.length - lowerPrefix.length) > maxLenDiff) continue
                if (lowerPrefix.length <= 4 && learnedWord.isNotEmpty() &&
                    learnedWord[0] != lowerPrefix[0] &&
                    QwertyDistance.substitutionCost(lowerPrefix[0], learnedWord[0]) > learnedMaxCost) {
                    continue
                }
                val editCost = EditDistance.damerauLevenshteinWithCost(lowerPrefix, learnedWord)
                if (editCost > learnedMaxCost) continue

                val editScore = (1.0 - editCost / maxOf(lowerPrefix.length, learnedWord.length).coerceAtLeast(1))
                    .coerceIn(0.0, 1.0)
                val userScore = normUser(userLexicon.getUserScore(learnedWord, context))
                val lmProb = ngramModel?.contextBoost(context, learnedWord) ?: 0.0
                val score = (0.62 + editScore * 0.25 + userScore * 0.08 + lmProb * 0.05)
                    .coerceIn(0.0, 0.99)

                candidateMap[learnedWord] = GnosisEngineCandidate(
                    word = formatCasing(learnedWord, isFirstCapital, isAllCaps),
                    score = score,
                    spatialScore = editScore,
                    lmScore = lmProb,
                    userScore = userScore,
                    kind = CandidateKind.USER_LEARNED,
                    source = "fuzzy_user_lexicon",
                    isAutoCorrect = !inDict && editCost <= 1.8 && score >= 0.80
                )
            }
        }

        val completions = trie.searchPrefix(lowerPrefix, limit = 12)
        for ((index, comp) in completions.withIndex()) {
            if (!candidateMap.containsKey(comp)) {
                val completionRatio = (lowerPrefix.length.toDouble() / comp.length.toDouble()).coerceIn(0.1, 0.95)
                val lmProb = ngramModel?.contextBoost(context, comp) ?: 0.0
                val rankBonus = 0.05 * (1.0 - index.toDouble() / completions.size)
                val score = 0.65 + (completionRatio * 0.08) + rankBonus + (lmProb * 0.07)
                candidateMap[comp] = GnosisEngineCandidate(
                    word = formatCasing(comp, isFirstCapital, isAllCaps),
                    score = score,
                    spatialScore = 0.75 + (completionRatio * 0.20),
                    lmScore = lmProb,
                    kind = CandidateKind.COMPLETION,
                    source = "trie_prefix"
                )
            }
        }

        if ((!inDict || candidateMap.size < limit) && lowerPrefix.length >= 3) {
            try {
                val fuzzyMaxCost = when {
                    lowerPrefix.length <= 4 -> 1.2
                    lowerPrefix.length <= 7 -> 2.0
                    else -> 3.0
                }
                val fuzzyMatches = trie.searchFuzzy(lowerPrefix, maxCost = fuzzyMaxCost, limit = 12)
                for (match in fuzzyMatches) {
                    val key = match.word.lowercase(Locale.ROOT)
                    if (!candidateMap.containsKey(key)) {
                        val spatialScore = spatialModel.scoreWordSpatial(lowerPrefix, key)
                        val lmProb = ngramModel?.contextBoost(context, key) ?: 0.0
                        val freqScore = (ln(match.frequency.toDouble() + 1.0) / 10.0).coerceIn(0.0, 1.0)

                        val editScore = (
                            1.0 - match.cost / maxOf(lowerPrefix.length, key.length).coerceAtLeast(1)
                        ).coerceIn(0.0, 1.0)
                        val compositeScore = (
                            0.58 +
                            (editScore * 0.25) +
                            (spatialScore * 0.10) +
                            (lmProb * 0.05) +
                            (freqScore * 0.02)
                        ).coerceIn(0.0, 0.99)

                        val finalScore = if (inDict) minOf(compositeScore, 0.84) else compositeScore
                        candidateMap[key] = GnosisEngineCandidate(
                            word = formatCasing(match.word, isFirstCapital, isAllCaps),
                            score = finalScore,
                            spatialScore = spatialScore,
                            lmScore = lmProb,
                            kind = CandidateKind.CORRECTION,
                            source = "fuzzy_edit_decoder",
                            isAutoCorrect = !inDict && match.cost <= 1.8 && finalScore >= 0.80
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        val ranked = candidateMap.values.sortedByDescending { it.score }
        val topCandidates = ranked.take(limit)

        var autoCorrect: GnosisEngineCandidate? = null
        val best = ranked.firstOrNull()
        if (best != null && !best.word.equals(clean, ignoreCase = true)) {
            if (best.isAutoCorrect || best.score >= 0.72) {
                autoCorrect = best
            }
        }

        return DecodeResult(
            candidates = topCandidates,
            autoCorrectCandidate = autoCorrect
        )
    }

    private fun normUser(raw: Double): Double = (Math.log1p(raw) / 5.0).coerceIn(0.0, 1.0)

    private fun formatCasing(word: String, isFirstCapital: Boolean, isAllCaps: Boolean): String {
        if (!isAllCaps) WordCasing.proper(word)?.let { return it }
        return when {
            isAllCaps -> word.uppercase(Locale.ROOT)
            isFirstCapital -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            else -> word
        }
    }
}

typealias Delight5BeamDecoder = GnosisBeamDecoder
