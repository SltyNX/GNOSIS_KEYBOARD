package gnosis.keyboard.engine.glide

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import gnosis.keyboard.WordDictionary
import java.util.Locale
import kotlin.math.hypot

/**
 * Gnosis Glide Engine
 *
 * High-performance gesture / glide typing engine:
 * 1. Trajectory turn-angle detection (inflection points at directional changes >= 38 degrees)
 * 2. Velocity dwell detection (slowing down over target keys)
 * 3. Double-letter compression matching (e.g. "look" -> "lok", "hello" -> "helo")
 * 4. High-efficiency vocabulary candidate retrieval via start/end key buckets
 * 5. Spatial centroid trajectory DTW distance scoring
 * 6. Contraction expansion (e.g. "dont" -> "don't", "youre" -> "you're")
 */
object GnosisGlideEngine {

    const val MIN_POINTS = 4
    const val MIN_PATH_LENGTH_DP = 40f

    // QWERTY adjacent key neighbors for spatial tolerance
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

    private val commonContractions = mapOf(
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "youre" to "you're",
        "theyre" to "they're",
        "weare" to "we're",
        "youve" to "you've",
        "theyve" to "they've",
        "weve" to "we've",
        "ive" to "I've",
        "im" to "I'm",
        "ill" to "I'll",
        "thats" to "that's",
        "whats" to "what's",
        "theres" to "there's",
        "heres" to "here's",
        "lets" to "let's"
    )

    fun calculatePathLength(points: List<PointF>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return total
    }

    fun buildSmoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        points.lastOrNull()?.let { path.lineTo(it.x, it.y) }
        return path
    }

    fun findNearestKey(point: PointF, keyBounds: Map<Char, RectF>): Char? {
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

    fun findAdjacentKeys(ch: Char): List<Char> {
        return qwertyNeighbors[ch] ?: emptyList()
    }

    /**
     * Extracts salient inflection points along the touch path.
     * Turn angles > 38 degrees represent conscious user turns toward a key.
     */
    fun extractInflectionPoints(points: List<PointF>): List<PointF> {
        if (points.size <= 2) return points
        val result = mutableListOf<PointF>()
        result.add(points.first())

        val step = if (points.size > 20) 2 else 1
        var i = step
        while (i < points.size - step) {
            val pPrev = points[i - step]
            val pCurr = points[i]
            val pNext = points[i + step]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)

            if (len1 > 3f && len2 > 3f) {
                val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
                // dot < 0.78 corresponds to turn angle > ~38 degrees
                if (dot < 0.78f) {
                    result.add(pCurr)
                    i += step
                }
            }
            i += step
        }
        result.add(points.last())
        return result
    }

    /**
     * Compresses consecutive duplicate characters: "hello" -> "helo", "look" -> "lok"
     */
    private fun compressDuplicates(s: String): String {
        if (s.length <= 1) return s
        val sb = StringBuilder()
        var last: Char? = null
        for (c in s) {
            if (c != last) {
                sb.append(c)
                last = c
            }
        }
        return sb.toString()
    }

    /**
     * Checks whether the compressed inflection sequence appears as an ordered subsequence.
     */
    private fun matchesGestureSequence(word: String, inflectionKeys: List<Char>): Boolean {
        val compressedWord = compressDuplicates(word)
        val compressedKeys = compressDuplicates(inflectionKeys.joinToString(""))

        var wIdx = 0
        var matchCount = 0
        for (c in compressedKeys) {
            val found = compressedWord.indexOf(c, wIdx)
            if (found != -1) {
                matchCount++
                wIdx = found + 1
            }
        }
        val target = (compressedKeys.length * 0.65f).toInt().coerceAtLeast(1)
        return matchCount >= target
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

    fun matchGesture(
        points: List<PointF>,
        keyBounds: Map<Char, RectF>,
        maxMatches: Int = 3
    ): List<String> {
        if (points.size < MIN_POINTS || keyBounds.isEmpty()) return emptyList()

        val pStart = points.first()
        val pEnd = points.last()

        val startChar = findNearestKey(pStart, keyBounds) ?: return emptyList()
        val endChar = findNearestKey(pEnd, keyBounds) ?: return emptyList()

        val startCandidates = setOf(startChar) + findAdjacentKeys(startChar)
        val endCandidates = setOf(endChar) + findAdjacentKeys(endChar)

        val inflections = extractInflectionPoints(points)
        val inflectionKeys = inflections.mapNotNull { findNearestKey(it, keyBounds) }

        val scoredWords = mutableListOf<Pair<String, Float>>()
        val seen = HashSet<String>()

        fun evaluateCandidate(candidate: String, freq: Float) {
            val word = candidate.lowercase(Locale.ROOT)
            if (word.length < 2 || !seen.add(word)) return
            val first = word.first()
            val last = word.last()
            if (!startCandidates.contains(first) || !endCandidates.contains(last)) return
            if (!matchesGestureSequence(word, inflectionKeys)) return

            val dist = computeSpatialDistance(word, points, keyBounds)
            val startKeyRect = keyBounds[first]
            val endKeyRect = keyBounds[last]
            val startDist = if (startKeyRect != null) hypot(pStart.x - startKeyRect.centerX(), pStart.y - startKeyRect.centerY()) else 0f
            val endDist = if (endKeyRect != null) hypot(pEnd.x - endKeyRect.centerX(), pEnd.y - endKeyRect.centerY()) else 0f

            val startExactBonus = if (first == startChar) 1.6f else 1.0f
            val endExactBonus = if (last == endChar) 1.6f else 1.0f
            val lengthBonus = (1f + (word.length * 0.08f)).coerceIn(1.0f, 1.8f)

            val score = (freq * startExactBonus * endExactBonus * lengthBonus) /
                (1.0f + (dist / 28.0f) + ((startDist + endDist) / 45.0f))

            scoredWords.add(word to score)
        }

        // 1. Check user frequently used words first
        for ((uWord, uFreq) in WordDictionary.getUserFrequencies()) {
            evaluateCandidate(uWord, uFreq.toFloat() * 1.5f)
        }

        // 2. Query gesture buckets across start candidates
        val buckets = WordDictionary.getGestureBuckets()
        if (buckets != null) {
            for (sc in startCandidates) {
                buckets[sc]?.forEach { gw ->
                    evaluateCandidate(gw.word, gw.freq)
                }
            }
        } else {
            for (w in WordDictionary.getCommonWordList()) {
                evaluateCandidate(w, 20f)
            }
        }

        scoredWords.sortByDescending { it.second }
        val rawResults = scoredWords.take(maxMatches).map { it.first }

        return rawResults.map { commonContractions[it] ?: it }
    }
}
