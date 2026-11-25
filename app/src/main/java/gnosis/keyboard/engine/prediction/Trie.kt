package gnosis.keyboard.engine.prediction

import java.util.Locale

class Trie {

    val root = Node()

    data class FuzzyMatch(
        val word: String,
        val cost: Double,
        val frequency: Int
    )

    fun insert(word: String, frequency: Int = 1) {
        if (word.isEmpty()) return
        var node = root
        val lower = word.lowercase(Locale.ROOT)
        for (c in lower) {
            node = node.children.getOrPut(c) { Node(c, node) }
        }
        node.isEnd = true
        node.word = lower
        node.frequency = frequency
    }

    fun contains(word: String): Boolean {
        if (word.isEmpty()) return false
        var node = root
        val lower = word.lowercase(Locale.ROOT)
        for (c in lower) {
            node = node.children[c] ?: return false
        }
        return node.isEnd
    }

    fun searchPrefix(prefix: String, limit: Int = 20): List<String> {
        if (prefix.isEmpty()) return emptyList()
        var node = root
        val lower = prefix.lowercase(Locale.ROOT)
        for (c in lower) {
            node = node.children[c] ?: return emptyList()
        }

        val heap = java.util.PriorityQueue<Node>(limit + 1, compareBy<Node> { it.frequency })
        collectTop(node, heap, limit)
        return heap.sortedByDescending { it.frequency }.mapNotNull { it.word }
    }

    private fun collectTop(node: Node, heap: java.util.PriorityQueue<Node>, limit: Int) {
        if (node.isEnd && node.word != null) {
            if (heap.size < limit) {
                heap.add(node)
            } else if (node.frequency > heap.peek()!!.frequency) {
                heap.poll()
                heap.add(node)
            }
        }
        for (child in node.children.values) collectTop(child, heap, limit)
    }

    fun searchFuzzy(target: String, maxCost: Double = 2.2, limit: Int = 15): List<FuzzyMatch> {
        if (target.length < 3) return emptyList()
        val lower = target.lowercase(Locale.ROOT)
        val columns = lower.length + 1
        val currentRow = DoubleArray(columns) { it.toDouble() }

        val results = mutableListOf<FuzzyMatch>()
        var visitedCount = 0

        val sortedRoots = if (root.children.size <= 1) {
            root.children.entries
        } else {
            root.children.entries.sortedBy {
                QwertyDistance.substitutionCost(it.key, lower.first())
            }
        }
        for ((char, child) in sortedRoots) {
            visitedCount = searchRecursive(
                node = child,
                char = char,
                target = lower,
                previousRow = currentRow,
                twoRowsBack = null,
                depth = 1,
                results = results,
                maxCost = maxCost,
                visitedCount = visitedCount,
                maxVisited = 5000
            )
            if (visitedCount >= 5000) break
        }

        return results
            .sortedWith(compareBy({ it.cost }, { -it.frequency }))
            .take(limit)
    }

    private fun searchRecursive(
        node: Node,
        char: Char,
        target: String,
        previousRow: DoubleArray,
        twoRowsBack: DoubleArray?,
        depth: Int,
        results: MutableList<FuzzyMatch>,
        maxCost: Double,
        visitedCount: Int,
        maxVisited: Int
    ): Int {
        var count = visitedCount + 1
        if (count > maxVisited) return count

        val columns = target.length + 1
        val currentRow = DoubleArray(columns)
        currentRow[0] = previousRow[0] + 1.0

        for (col in 1 until columns) {
            val targetChar = target[col - 1]
            val insertCost = currentRow[col - 1] + 1.0
            val deleteCost = previousRow[col] + 1.0
            val subCost = previousRow[col - 1] + QwertyDistance.substitutionCost(char, targetChar)

            var minCost = minOf(insertCost, deleteCost, subCost)

            if (
                twoRowsBack != null &&
                col > 1 &&
                char == target[col - 2] &&
                node.parent?.char == targetChar
            ) {
                minCost = minOf(minCost, twoRowsBack[col - 2] + 0.8)
            }

            currentRow[col] = minCost
        }

        if (currentRow.last() <= maxCost && node.isEnd && node.word != null) {
            results.add(FuzzyMatch(node.word!!, currentRow.last(), node.frequency))
        }

        var canContinue = false
        for (i in currentRow.indices) {
            if (currentRow[i] <= maxCost) {
                canContinue = true
                break
            }
        }

        if (canContinue && count <= maxVisited) {
            val expectedChar = target.getOrElse(depth) { target.last() }
            val sortedChildren = if (node.children.size <= 1) {
                node.children.entries
            } else {
                node.children.entries.sortedBy {
                    QwertyDistance.substitutionCost(it.key, expectedChar)
                }
            }
            for ((nextChar, child) in sortedChildren) {
                count = searchRecursive(
                    node = child,
                    char = nextChar,
                    target = target,
                    previousRow = currentRow,
                    twoRowsBack = previousRow,
                    depth = depth + 1,
                    results = results,
                    maxCost = maxCost,
                    visitedCount = count,
                    maxVisited = maxVisited
                )
                if (count > maxVisited) break
            }
        }
        return count
    }

    class Node(
        val char: Char? = null,
        val parent: Node? = null
    ) {
        val children = mutableMapOf<Char, Node>()
        var isEnd = false
        var word: String? = null
        var frequency: Int = 1
    }
}
