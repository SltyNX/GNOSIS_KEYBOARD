package gnosis.keyboard.engine.delight

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

class GnosisSpatialModel {

    data class KeyGeometry(
        val char: Char,
        val row: Int,
        val col: Float,
        val normX: Float,
        val normY: Float
    )

    data class TouchPoint(
        val x: Float,
        val y: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val char: Char? = null
    )

    companion object {
        private const val SIGMA_X = 0.055f
        private const val SIGMA_Y = 0.080f
        private const val TWO_SIGMA_X_SQ = 2f * SIGMA_X * SIGMA_X
        private const val TWO_SIGMA_Y_SQ = 2f * SIGMA_Y * SIGMA_Y

        private const val ROW_0 = "qwertyuiop"
        private const val ROW_1 = "asdfghjkl"
        private const val ROW_2 = "zxcvbnm"

        private val KEY_MAP = HashMap<Char, KeyGeometry>(32)
        private val NEIGHBOR_MAP = HashMap<Char, List<Pair<Char, Double>>>(32)

        init {
            for (i in ROW_0.indices) {
                val c = ROW_0[i]
                val normX = (i + 0.5f) / 10f
                val normY = 0.20f
                KEY_MAP[c] = KeyGeometry(c, 0, i.toFloat(), normX, normY)
            }

            for (i in ROW_1.indices) {
                val c = ROW_1[i]
                val normX = (i + 1.0f) / 10f
                val normY = 0.50f
                KEY_MAP[c] = KeyGeometry(c, 1, i + 0.5f, normX, normY)
            }

            for (i in ROW_2.indices) {
                val c = ROW_2[i]
                val normX = (i + 2.0f) / 10f
                val normY = 0.80f
                KEY_MAP[c] = KeyGeometry(c, 2, i + 1.5f, normX, normY)
            }

            for (c1 in KEY_MAP.keys) {
                val geom1 = KEY_MAP[c1]!!
                val neighbors = mutableListOf<Pair<Char, Double>>()
                for (c2 in KEY_MAP.keys) {
                    if (c1 == c2) continue
                    val geom2 = KEY_MAP[c2]!!
                    val dRow = abs(geom1.row - geom2.row)
                    val dCol = abs(geom1.col - geom2.col)
                    val dist = hypot(dCol.toDouble(), (dRow * 1.1))
                    if (dist <= 1.45) {
                        val prob = exp(- (dist * dist) / 0.8)
                        neighbors.add(Pair(c2, prob))
                    }
                }
                NEIGHBOR_MAP[c1] = neighbors.sortedByDescending { it.second }
            }
        }
    }

    fun getTouchProbability(normX: Float, normY: Float, targetChar: Char): Double {
        val geom = KEY_MAP[targetChar.lowercaseChar()] ?: return 1e-4
        val dx = normX - geom.normX
        val dy = normY - geom.normY
        val exponent = -((dx * dx) / TWO_SIGMA_X_SQ + (dy * dy) / TWO_SIGMA_Y_SQ)
        return exp(exponent.toDouble()).coerceIn(1e-4, 1.0)
    }

    fun getCharProximityScore(c1: Char, c2: Char): Double {
        val l1 = c1.lowercaseChar()
        val l2 = c2.lowercaseChar()
        if (l1 == l2) return 1.0

        val geom1 = KEY_MAP[l1] ?: return 0.05
        val geom2 = KEY_MAP[l2] ?: return 0.05

        val dx = (geom1.normX - geom2.normX)
        val dy = (geom1.normY - geom2.normY)
        val distSq = (dx * dx) + (dy * dy)

        return exp(-distSq / 0.015).coerceIn(0.02, 0.88)
    }

    fun getNeighbors(c: Char): List<Pair<Char, Double>> {
        return NEIGHBOR_MAP[c.lowercaseChar()] ?: emptyList()
    }

    fun scoreWordSpatial(typedSequence: String, word: String): Double {
        val cleanTyped = typedSequence.lowercase(Locale.ROOT)
        val cleanWord = word.lowercase(Locale.ROOT)

        if (cleanTyped == cleanWord) return 1.0
        if (cleanTyped.isEmpty() || cleanWord.isEmpty()) return 0.0

        val lenTyped = cleanTyped.length
        val lenWord = cleanWord.length

        if (cleanWord.startsWith(cleanTyped)) {
            val completionRatio = lenTyped.toDouble() / lenWord.toDouble()
            return 0.80 + (0.18 * completionRatio)
        }

        val m = lenTyped
        val n = lenWord
        val dp = Array(m + 1) { DoubleArray(n + 1) }

        for (i in 0..m) dp[i][0] = i * 0.40
        for (j in 0..n) dp[j][0] = j * 0.40

        for (i in 1..m) {
            val tChar = cleanTyped[i - 1]
            for (j in 1..n) {
                val wChar = cleanWord[j - 1]
                val subCost = 1.0 - getCharProximityScore(tChar, wChar)
                val costMatch = dp[i - 1][j - 1] + subCost
                val costDel = dp[i - 1][j] + 0.45
                val costIns = dp[i][j - 1] + 0.45
                dp[i][j] = minOf(costMatch, costDel, costIns)
            }
        }

        val totalCost = dp[m][n]
        val maxLen = maxOf(m, n)
        val normalizedCost = totalCost / maxLen

        return (1.0 - normalizedCost).coerceIn(0.0, 0.95)
    }
}

typealias DelightSpatialModel = GnosisSpatialModel
