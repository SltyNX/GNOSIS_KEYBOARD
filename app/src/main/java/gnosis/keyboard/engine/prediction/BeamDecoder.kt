package gnosis.keyboard.engine.prediction

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.data.UserLexicon
import gnosis.keyboard.engine.grammar.GrammarChecker
import gnosis.keyboard.engine.spell.ContractionEngine
import gnosis.keyboard.engine.spell.SpellChecker
import gnosis.keyboard.engine.spell.TypoDictionary
import gnosis.keyboard.engine.spell.WordCasing
import java.util.Locale

class BeamDecoder(
    private val trie: Trie,
    private val ngramModel: CompactNgramModel?,
    private val userLexicon: UserLexicon,
    private val spellChecker: SpellChecker,
    private val grammarChecker: GrammarChecker? = null
) {

    data class ScoredCandidate(
        val word: String,
        val score: Double,
        val kind: CandidateKind
    )

    fun decode(
        typedPrefix: String,
        context: List<String>,
        limit: Int = 3
    ): Pair<List<Candidate>, Candidate?> {
        val cleanPrefix = typedPrefix.trim()

        if (cleanPrefix.isEmpty()) {
            return Pair(emptyList(), null)
        }

        val lowerPrefix = cleanPrefix.lowercase(Locale.ROOT)
        val isFirstCapital = cleanPrefix[0].isUpperCase()
        val isAllCaps = cleanPrefix.length > 1 && cleanPrefix.all { it.isUpperCase() }

        val scoredMap = LinkedHashMap<String, ScoredCandidate>()

        TypoDictionary.getCorrection(cleanPrefix)?.let { fix ->
            val formatted = TypoDictionary.matchCasing(cleanPrefix, fix)
            scoredMap[formatted.lowercase(Locale.ROOT)] = ScoredCandidate(
                word = formatted,
                score = 0.99,
                kind = CandidateKind.CORRECTION
            )
        }

        val contractions = ContractionEngine.getContractionCandidates(cleanPrefix)
        contractions?.forEach { contraction ->
            val formatted = ContractionEngine.matchCasing(cleanPrefix, contraction)
            val baseScore = 0.95
            val lmBonus = ngramModel?.nextWordProb(context, formatted.lowercase(Locale.ROOT)) ?: 0.05
            val totalScore = baseScore + (lmBonus * 0.04)
            scoredMap[formatted.lowercase(Locale.ROOT)] = ScoredCandidate(
                word = formatted,
                score = totalScore,
                kind = CandidateKind.CONTRACTION
            )
        }

        if (grammarChecker != null && context.isNotEmpty()) {
            val phrase = (context.takeLast(2) + cleanPrefix).joinToString(" ")
            val issues = grammarChecker.check(phrase)
            issues.lastOrNull()?.let { issue ->
                if (issue.suggestions.isNotEmpty()) {
                    val fix = issue.suggestions.first()
                    val formattedFix = if (fix.contains(" ") && fix.split(" ").size == phrase.split(" ").size) {
                        fix.split(" ").last()
                    } else fix

                    scoredMap[formattedFix.lowercase(Locale.ROOT)] = ScoredCandidate(
                        word = formattedFix,
                        score = 0.98,
                        kind = CandidateKind.CORRECTION
                    )
                }
            }
        }

        val isDictionaryWord = trie.contains(lowerPrefix)
        val isUserKnown = userLexicon.isKnown(lowerPrefix)
        if (isDictionaryWord || isUserKnown) {
            val exactWord = formatWord(cleanPrefix, isFirstCapital, isAllCaps)
            val userScore = normUser(userLexicon.getUserScore(lowerPrefix, context))
            val lmProb = ngramModel?.nextWordProb(context, lowerPrefix) ?: 0.1
            val exactScore = 0.88 + (userScore * 0.08) + (lmProb * 0.10)
            scoredMap[lowerPrefix] = ScoredCandidate(
                word = exactWord,
                score = exactScore,
                kind = if (isUserKnown) CandidateKind.USER_LEARNED else CandidateKind.COMPLETION
            )
        }

        for (learned in userLexicon.getTopWords(50)) {
            if (learned.startsWith(lowerPrefix) && !scoredMap.containsKey(learned)) {
                val formatted = formatWord(learned, isFirstCapital, isAllCaps)
                val uScore = normUser(userLexicon.getUserScore(learned, context))
                scoredMap[learned] = ScoredCandidate(
                    word = formatted,
                    score = 0.78 + (uScore * 0.15),
                    kind = CandidateKind.USER_LEARNED
                )
            }
        }

        val completions = trie.searchPrefix(lowerPrefix, limit = 10)
        for ((idx, comp) in completions.withIndex()) {
            if (!scoredMap.containsKey(comp)) {
                val formatted = formatWord(comp, isFirstCapital, isAllCaps)
                val ctx = ngramModel?.contextBoost(context, comp) ?: 0.0
                val prefixRatio = (lowerPrefix.length.toDouble() / comp.length.toDouble()).coerceIn(0.1, 0.95)
                val rankBonus = 0.06 * (1.0 - idx.toDouble() / completions.size)
                val score = 0.65 + (prefixRatio * 0.08) + rankBonus + (ctx * 0.08)
                scoredMap[comp] = ScoredCandidate(
                    word = formatted,
                    score = score,
                    kind = CandidateKind.COMPLETION
                )
            }
        }

        if (!isDictionaryWord || scoredMap.size < limit) {
            val spellCandidates = spellChecker.getSpellingCandidates(cleanPrefix, limit = 5)
            for (sc in spellCandidates) {
                val key = sc.word.lowercase(Locale.ROOT)
                if (!scoredMap.containsKey(key)) {
                    val ctx = ngramModel?.contextBoost(context, key) ?: 0.0
                    val totalScore = (sc.score * 0.75) + (ctx * 0.10)
                    scoredMap[key] = ScoredCandidate(
                        word = sc.word,
                        score = totalScore,
                        kind = sc.kind
                    )
                }
            }
        }

        val ranked = scoredMap.values.sortedByDescending { it.score }
        val suggestions = ranked.take(limit).map { Candidate(it.word, it.score, it.kind) }

        var autoCorrectCandidate: Candidate? = null

        val topTypo = ranked.firstOrNull { it.score >= 0.98 }
        val topContraction = ranked.firstOrNull { it.kind == CandidateKind.CONTRACTION }

        if (topTypo != null && !topTypo.word.equals(cleanPrefix, ignoreCase = true)) {
            autoCorrectCandidate = Candidate(topTypo.word, topTypo.score, topTypo.kind)
        } else if (topContraction != null && !cleanPrefix.contains("'")) {
            autoCorrectCandidate = Candidate(topContraction.word, topContraction.score, CandidateKind.CONTRACTION)
        } else if (!isDictionaryWord && !isUserKnown && ranked.isNotEmpty()) {
            val top = ranked.first()
            if (top.score >= 0.65 && !top.word.equals(cleanPrefix, ignoreCase = true)) {
                autoCorrectCandidate = Candidate(top.word, top.score, top.kind)
            }
        }

        if (autoCorrectCandidate == null && !isAllCaps) {
            WordCasing.proper(lowerPrefix)?.let { proper ->
                if (proper != cleanPrefix) autoCorrectCandidate = Candidate(proper, 0.9, CandidateKind.CORRECTION)
            }
        }

        return Pair(suggestions, autoCorrectCandidate)
    }

    private fun normUser(raw: Double): Double = (Math.log1p(raw) / 5.0).coerceIn(0.0, 1.0)

    private fun formatWord(word: String, isFirstCapital: Boolean, isAllCaps: Boolean): String {
        if (!isAllCaps) WordCasing.proper(word)?.let { return it }
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
            word == "i" || word.startsWith("i'") -> {
                word.replaceFirstChar { it.uppercaseChar() }
            }
            else -> word
        }
    }
}
