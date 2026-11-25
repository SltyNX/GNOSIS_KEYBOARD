package gnosis.keyboard.engine.prediction

import kotlin.math.abs

object QwertyDistance {

    private val KEY_COORDS = mapOf(
        'q' to Pair(0, 0), 'w' to Pair(0, 1), 'e' to Pair(0, 2), 'r' to Pair(0, 3), 't' to Pair(0, 4),
        'y' to Pair(0, 5), 'u' to Pair(0, 6), 'i' to Pair(0, 7), 'o' to Pair(0, 8), 'p' to Pair(0, 9),

        'a' to Pair(1, 0), 's' to Pair(1, 1), 'd' to Pair(1, 2), 'f' to Pair(1, 3), 'g' to Pair(1, 4),
        'h' to Pair(1, 5), 'j' to Pair(1, 6), 'k' to Pair(1, 7), 'l' to Pair(1, 8),

        'z' to Pair(2, 1), 'x' to Pair(2, 2), 'c' to Pair(2, 3), 'v' to Pair(2, 4), 'b' to Pair(2, 5),
        'n' to Pair(2, 6), 'm' to Pair(2, 7)
    )

    fun substitutionCost(c1: Char, c2: Char): Double {
        if (c1 == c2) return 0.0
        val p1 = KEY_COORDS[c1.lowercaseChar()] ?: return 1.0
        val p2 = KEY_COORDS[c2.lowercaseChar()] ?: return 1.0

        val dr = abs(p1.first - p2.first)
        val dc = abs(p1.second - p2.second)

        return when {
            dr == 0 && dc == 1 -> 0.40
            dr == 1 && dc == 0 -> 0.45
            dr == 1 && dc == 1 -> 0.50
            dr == 0 && dc == 2 -> 0.70
            else -> 1.0
        }
    }
}
