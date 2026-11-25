package gnosis.keyboard.engine.spell

import gnosis.keyboard.engine.prediction.QwertyDistance

object EditDistance {

    fun damerauLevenshteinWithCost(
        s1: String,
        s2: String,
        cost: (Char, Char) -> Double = { a, b -> QwertyDistance.substitutionCost(a, b) }
    ): Double {
        val n = s1.length
        val m = s2.length

        if (n == 0) return m.toDouble()
        if (m == 0) return n.toDouble()

        val d = Array(n + 1) { DoubleArray(m + 1) }

        for (i in 0..n) d[i][0] = i.toDouble()
        for (j in 0..m) d[0][j] = j.toDouble()

        for (i in 1..n) {
            for (j in 1..m) {
                val subCost = if (s1[i - 1] == s2[j - 1]) 0.0 else cost(s1[i - 1], s2[j - 1])
                val sub = d[i - 1][j - 1] + subCost
                val del = d[i - 1][j] + 1.0
                val ins = d[i][j - 1] + 1.0

                var minCost = minOf(sub, del, ins)

                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    minCost = minOf(minCost, d[i - 2][j - 2] + 0.8)
                }

                d[i][j] = minCost
            }
        }

        return d[n][m]
    }
}
