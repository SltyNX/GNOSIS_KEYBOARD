package gnosis.keyboard.engine.spell

import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.SpellIssue
import gnosis.keyboard.engine.prediction.Trie
import java.util.Locale

class SpellChecker(
    private val trie: Trie,
    private val isUserKnown: (String) -> Boolean = { false }
) {

    fun isCorrect(word: String): Boolean {
        if (word.isEmpty()) return true
        val lower = word.lowercase(Locale.ROOT)
        if (trie.contains(lower)) return true
        if (isUserKnown(lower)) return true
        if (ContractionEngine.isKnownContraction(lower)) return true
        if (lower.length == 1 && (lower == "a" || lower == "i")) return true
        return false
    }

    fun getSpellingCandidates(typed: String, limit: Int = 6): List<Candidate> {
        if (typed.isEmpty()) return emptyList()

        val results = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()

        TypoDictionary.getCorrection(typed)?.let { fix ->
            val formatted = TypoDictionary.matchCasing(typed, fix)
            if (seen.add(formatted.lowercase(Locale.ROOT))) {
                results.add(Candidate(formatted, score = 0.99, kind = CandidateKind.CORRECTION))
            }
        }

        ContractionEngine.getContractionCandidates(typed)?.forEach { contraction ->
            val formatted = ContractionEngine.matchCasing(typed, contraction)
            if (seen.add(formatted.lowercase(Locale.ROOT))) {
                results.add(Candidate(formatted, score = 0.95, kind = CandidateKind.CONTRACTION))
            }
        }

        val fuzzyMaxCost = when {
            typed.length <= 4 -> 1.2
            typed.length <= 7 -> 2.0
            else -> 3.0
        }
        val fuzzyMatches = trie.searchFuzzy(typed, maxCost = fuzzyMaxCost, limit = limit * 3)
        for (m in fuzzyMatches) {
            val formatted = ContractionEngine.matchCasing(typed, m.word)
            if (seen.add(formatted.lowercase(Locale.ROOT))) {
                val normalizedCostScore = (2.2 - m.cost).coerceAtLeast(0.1) / 2.2
                val freqBonus = 0.4 * Math.log1p(m.frequency.toDouble()) / Math.log1p(60000.0)
                val finalScore = (normalizedCostScore * 0.7) + freqBonus
                results.add(Candidate(formatted, score = finalScore, kind = CandidateKind.CORRECTION))
            }
            if (results.size >= limit + 3) break
        }

        return results.sortedByDescending { it.score }.take(limit)
    }

    fun checkSpelling(text: String): List<SpellIssue> {
        if (text.isBlank()) return emptyList()

        val issues = mutableListOf<SpellIssue>()
        val regex = Regex("\\b[a-zA-Z']+\\b")
        val matches = regex.findAll(text)

        for (match in matches) {
            val token = match.value
            val clean = token.trim('\'')
            if (clean.length <= 1 && clean != "a" && clean != "i" && clean != "A" && clean != "I") {
                continue
            }

            if (!isCorrect(clean)) {
                val suggestions = getSpellingCandidates(clean, limit = 3).map { it.word }
                issues.add(
                    SpellIssue(
                        offset = match.range.first,
                        length = token.length,
                        message = "Possible spelling mistake: \"$token\"",
                        suggestions = suggestions
                    )
                )
            }
        }

        return issues
    }
}
