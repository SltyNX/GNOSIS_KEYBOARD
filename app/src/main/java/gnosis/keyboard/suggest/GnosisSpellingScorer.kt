package gnosis.keyboard.suggest

import gnosis.keyboard.engine.prediction.QwertyDistance
import gnosis.keyboard.engine.spell.ContractionEngine
import gnosis.keyboard.engine.spell.TypoDictionary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object GnosisSpellingScorer {

    fun findCorrection(typedWord: String): Pair<String, Double>? {
        val clean = typedWord.trim()
        if (clean.isEmpty() || clean.length > 25) return null

        val lower = clean.lowercase(Locale.ROOT)

        if (lower == "i") {
            return Pair("I", 0.99)
        }

        val contraction: String? = if (!ContractionEngine.isAmbiguous(lower)) ContractionEngine.getContraction(lower) else null
        if (contraction != null) {
            val formatted = applyCasing(clean, contraction)
            return Pair(formatted, 0.96)
        }

        val directCorrection = TypoDictionary.getCorrection(lower)
        if (directCorrection != null) {
            val formatted = applyCasing(clean, directCorrection)
            return Pair(formatted, 0.95)
        }

        return null
    }

    fun computeFuzzyScore(typed: String, candidate: String): Double {
        val s1 = typed.lowercase(Locale.ROOT)
        val s2 = candidate.lowercase(Locale.ROOT)

        if (s1 == s2) return 1.0
        val lenMax = max(s1.length, s2.length)
        if (lenMax == 0) return 0.0

        val dist = damerauLevenshteinDistance(s1, s2)
        val score = (1.0 - (dist / lenMax.toDouble())).coerceIn(0.0, 1.0)
        return score
    }

    private fun damerauLevenshteinDistance(source: String, target: String): Double {
        val n = source.length
        val m = target.length
        if (n == 0) return m.toDouble()
        if (m == 0) return n.toDouble()

        val d = Array(n + 1) { DoubleArray(m + 1) }

        for (i in 0..n) d[i][0] = i.toDouble()
        for (j in 0..m) d[0][j] = j.toDouble()

        for (i in 1..n) {
            val c1 = source[i - 1]
            for (j in 1..m) {
                val c2 = target[j - 1]
                val subCost = if (c1 == c2) 0.0 else QwertyDistance.substitutionCost(c1, c2)

                var cost = min(
                    d[i - 1][j] + 1.0,
                    min(
                        d[i][j - 1] + 1.0,
                        d[i - 1][j - 1] + subCost
                    )
                )

                if (i > 1 && j > 1 && c1 == target[j - 2] && source[i - 2] == c2) {
                    cost = min(cost, d[i - 2][j - 2] + 0.5)
                }

                d[i][j] = cost
            }
        }

        return d[n][m]
    }

    fun applyCasing(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target

        if (target.equals("i", ignoreCase = true)) return "I"
        if (target.startsWith("i'", ignoreCase = true) || target.startsWith("I'")) {
            return "I" + target.substring(1)
        }

        val isAllUpper = source.all { !it.isLetter() || it.isUpperCase() } && source.any { it.isLetter() }
        if (isAllUpper) {
            return target.uppercase(Locale.ROOT)
        }

        val isTitleCase = source[0].isUpperCase() && (source.length == 1 || source.substring(1).all { !it.isLetter() || it.isLowerCase() })
        if (isTitleCase) {
            return target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        if (target.any { it.isUpperCase() } && !source[0].isUpperCase()) {
            return target
        }
        return target.lowercase(Locale.ROOT)
    }
}

typealias GboardSpellingScorer = GnosisSpellingScorer
