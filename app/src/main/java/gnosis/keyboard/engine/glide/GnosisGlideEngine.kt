package gnosis.keyboard.engine.glide

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import gnosis.keyboard.WordDictionary
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Gboard-Grade Glide / Gesture Typing Engine
 *
 * Implements Google Keyboard's core glide typing architecture:
 * 1. Dual Lexicon Indexing: Spatial start-end key hashing and prefix trie.
 * 2. Equidistant Arc-Length Trajectory Resampling (uniform spatial quantization).
 * 3. Polyline-to-Polyline DTW (Dynamic Time Warping) with Sakoe-Chiba band.
 * 4. Corner / Inflection angle detection and dwell pause point extraction.
 * 5. Bayesian ranking combining unigram/user frequency, start/end key proximity, and shape cost.
 * 6. Automatic Contraction Expansion (e.g. "dont" -> "don't", "cant" -> "can't").
 * 7. Continuous Multi-Word Glide Decoding across Spacebar without lifting finger.
 */
object GnosisGlideEngine {

    const val MIN_POINTS = 4
    const val MIN_PATH_LENGTH_DP = 36f
    private const val RESAMPLE_STEP_DP = 7f
    private const val DTW_POINTS = 32
    private const val DTW_WINDOW = 10

    // --- 1. TRIE & START-END BUCKETS DATA STRUCTURE ---

    class TrieNode(val char: Char) {
        var isWord: Boolean = false
        var frequency: Float = 0f
        val children: Array<TrieNode?> = arrayOfNulls(26)

        fun getChild(c: Char): TrieNode? {
            val idx = c - 'a'
            return if (idx in 0..25) children[idx] else null
        }

        fun getOrCreateChild(c: Char): TrieNode {
            val idx = c - 'a'
            if (idx in 0..25) {
                var child = children[idx]
                if (child == null) {
                    child = TrieNode(c)
                    children[idx] = child
                }
                return child
            }
            return this
        }
    }

    private val root = TrieNode('^')
    @Volatile
    private var isVocabularyLoaded = false

    // Quick lookup: (startChar - 'a') * 26 + (endChar - 'a') -> List of (word, freq)
    private val startEndBuckets = HashMap<Int, MutableList<Pair<String, Float>>>(676)

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
        "lets" to "let's",
        "its" to "it's"
    )

    // QWERTY adjacent key neighbors for spatial tolerance
    private val qwertyNeighbors = mapOf(
        'q' to listOf('w', 'a', 's'),
        'w' to listOf('q', 'e', 's', 'a', 'd'),
        'e' to listOf('w', 'r', 'd', 's', 'f'),
        'r' to listOf('e', 't', 'f', 'd', 'g'),
        't' to listOf('r', 'y', 'g', 'f', 'h'),
        'y' to listOf('t', 'u', 'h', 'g', 'j'),
        'u' to listOf('y', 'i', 'j', 'h', 'k'),
        'i' to listOf('u', 'o', 'k', 'j', 'l'),
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

    private fun bucketKey(start: Char, end: Char): Int {
        val s = start - 'a'
        val e = end - 'a'
        return if (s in 0..25 && e in 0..25) s * 26 + e else -1
    }

    /**
     * Initializes or updates the Trie and Start-End Buckets vocabulary.
     */
    fun initVocabulary(words: Collection<String>) {
        val total = words.size.toFloat().coerceAtLeast(1f)
        var rank = 0
        synchronized(root) {
            startEndBuckets.clear()
            for (raw in words) {
                rank++
                val word = raw.lowercase(Locale.ROOT).trim()
                if (word.length < 2 || !word.all { it in 'a'..'z' }) continue
                val freq = max(1f, 100f * (1f - (rank / total)))
                insertWordInternal(word, freq)
            }
            // Register common contraction uncontracted forms as well
            for ((shortcut, _) in commonContractions) {
                insertWordInternal(shortcut, 65f)
            }
            isVocabularyLoaded = true
        }
    }

    fun insertWord(word: String, freq: Float = 50f) {
        val clean = word.lowercase(Locale.ROOT).trim()
        if (clean.length < 2 || !clean.all { it in 'a'..'z' }) return
        synchronized(root) {
            insertWordInternal(clean, freq)
        }
    }

    private fun insertWordInternal(word: String, freq: Float) {
        var curr = root
        for (ch in word) {
            curr = curr.getOrCreateChild(ch)
        }
        curr.isWord = true
        curr.frequency = max(curr.frequency, freq)

        val key = bucketKey(word.first(), word.last())
        if (key >= 0) {
            val list = startEndBuckets.getOrPut(key) { ArrayList() }
            if (list.none { it.first == word }) {
                list.add(word to freq)
            }
        }
    }

    // --- 2. TRAJECTORY RESAMPLING & ANALYSIS ---

    class ResampledPoint(
        val x: Float,
        val y: Float,
        val distanceAlongPath: Float,
        var isCorner: Boolean = false
    )

    fun calculatePathLength(points: List<PointF>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return total
    }

    /**
     * Equidistant Arc-Length Resampling:
     * Converts raw touch samples into uniformly spaced points along the curve.
     */
    fun resampleTrajectory(points: List<PointF>, stepPx: Float): List<ResampledPoint> {
        if (points.size < 2) return points.map { ResampledPoint(it.x, it.y, 0f) }

        val totalLength = calculatePathLength(points)
        if (totalLength <= stepPx) {
            return listOf(
                ResampledPoint(points.first().x, points.first().y, 0f),
                ResampledPoint(points.last().x, points.last().y, totalLength)
            )
        }

        val resampled = mutableListOf<ResampledPoint>()
        resampled.add(ResampledPoint(points[0].x, points[0].y, 0f))

        var accumulatedDist = 0f
        var currentTarget = stepPx
        var prevP = points[0]

        for (i in 1 until points.size) {
            val currP = points[i]
            val segLen = hypot(currP.x - prevP.x, currP.y - prevP.y)
            if (segLen <= 0.001f) continue

            while (accumulatedDist + segLen >= currentTarget) {
                val t = (currentTarget - accumulatedDist) / segLen
                val nx = prevP.x + t * (currP.x - prevP.x)
                val ny = prevP.y + t * (currP.y - prevP.y)
                resampled.add(ResampledPoint(nx, ny, currentTarget))
                currentTarget += stepPx
            }
            accumulatedDist += segLen
            prevP = currP
        }

        val lastPoint = points.last()
        val lastResampled = resampled.last()
        if (hypot(lastPoint.x - lastResampled.x, lastPoint.y - lastResampled.y) > stepPx * 0.4f) {
            resampled.add(ResampledPoint(lastPoint.x, lastPoint.y, totalLength))
        }

        // Detect corners / inflection points using directional angle changes
        markInflectionPoints(resampled)
        return resampled
    }

    /**
     * Resamples any sequence of 2D points into an exact number of equidistant points.
     */
    fun resampleFixedCount(points: List<PointF>, count: Int): List<PointF> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(count) { points[0] }
        val totalLen = calculatePathLength(points)
        if (totalLen <= 0.001f) return List(count) { points[0] }

        val step = totalLen / (count - 1).toFloat()
        val result = ArrayList<PointF>(count)
        result.add(points[0])

        var acc = 0f
        var target = step
        var prev = points[0]

        for (i in 1 until points.size) {
            val curr = points[i]
            val seg = hypot(curr.x - prev.x, curr.y - prev.y)
            if (seg <= 0.0001f) continue
            while (acc + seg >= target && result.size < count) {
                val t = ((target - acc) / seg).coerceIn(0f, 1f)
                result.add(PointF(prev.x + t * (curr.x - prev.x), prev.y + t * (curr.y - prev.y)))
                target += step
            }
            acc += seg
            prev = curr
        }

        while (result.size < count) {
            result.add(points.last())
        }
        return result
    }

    /**
     * Identifies sharp turn angles (> 32 degrees) where user changed direction toward a letter.
     */
    private fun markInflectionPoints(points: List<ResampledPoint>) {
        if (points.size < 3) return
        points.first().isCorner = true
        points.last().isCorner = true

        val span = if (points.size > 24) 2 else 1
        for (i in span until points.size - span) {
            val pPrev = points[i - span]
            val pCurr = points[i]
            val pNext = points[i + span]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val l1 = hypot(v1x, v1y)
            val l2 = hypot(v2x, v2y)
            if (l1 > 1f && l2 > 1f) {
                val dot = (v1x * v2x + v1y * v2y) / (l1 * l2)
                // dot < 0.848 corresponds to turn angle > 32 degrees
                if (dot < 0.848f) {
                    points[i].isCorner = true
                }
            }
        }
    }

    // --- 3. SPATIAL GEOMETRY & UTILITIES ---

    fun findNearestKey(point: PointF, keyBounds: Map<Char, RectF>): Char? {
        var closest: Char? = null
        var minDist = Float.MAX_VALUE
        for ((ch, rect) in keyBounds) {
            if (ch !in 'a'..'z') continue
            val dist = hypot(point.x - rect.centerX(), point.y - rect.centerY())
            if (dist < minDist) {
                minDist = dist
                closest = ch
            }
        }
        return closest
    }

    private fun findAdjacentKeys(ch: Char): List<Char> {
        return qwertyNeighbors[ch] ?: emptyList()
    }

    private fun getKeyCenter(ch: Char, keyBounds: Map<Char, RectF>): PointF? {
        val rect = keyBounds[ch] ?: return null
        return PointF(rect.centerX(), rect.centerY())
    }

    /**
     * Converts a candidate word into its ideal spatial polyline across key centroids.
     * Adjacent duplicate letters (e.g. 'll' in 'hello') are collapsed to a single centroid.
     */
    private fun getWordPolyline(word: String, keyBounds: Map<Char, RectF>): List<PointF>? {
        if (word.isEmpty()) return null
        val pts = ArrayList<PointF>(word.length)
        var prevChar = ' '
        for (c in word) {
            if (c == prevChar) continue
            val center = getKeyCenter(c, keyBounds) ?: return null
            pts.add(center)
            prevChar = c
        }
        return pts
    }

    // --- 4. CONSTRAINED DYNAMIC TIME WARPING (DTW) ---

    /**
     * Computes the normalized DTW shape distance between resampled user trajectory P
     * and candidate word polyline Q (both normalized to DTW_POINTS).
     */
    private fun computeCurveDtw(
        pPoints: List<PointF>,
        qPoints: List<PointF>,
        avgKeyWidth: Float
    ): Float {
        val n = pPoints.size
        val m = qPoints.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        val dtw = Array(n) { FloatArray(m) { Float.MAX_VALUE } }
        val window = max(DTW_WINDOW, (n / 3))

        for (i in 0 until n) {
            val p = pPoints[i]
            val centerJ = (i.toFloat() / n.toFloat() * m).toInt()
            val minJ = max(0, centerJ - window)
            val maxJ = min(m - 1, centerJ + window)

            for (j in minJ..maxJ) {
                val q = qPoints[j]
                val cost = hypot(p.x - q.x, p.y - q.y) / avgKeyWidth

                if (i == 0 && j == 0) {
                    dtw[i][j] = cost
                } else {
                    val prev1 = if (i > 0) dtw[i - 1][j] else Float.MAX_VALUE
                    val prev2 = if (j > 0) dtw[i][j - 1] else Float.MAX_VALUE
                    val prev3 = if (i > 0 && j > 0) dtw[i - 1][j - 1] else Float.MAX_VALUE
                    val minPrev = min(prev1, min(prev2, prev3))
                    if (minPrev != Float.MAX_VALUE) {
                        dtw[i][j] = cost + minPrev
                    }
                }
            }
        }

        val finalCost = dtw[n - 1][m - 1]
        return if (finalCost == Float.MAX_VALUE) Float.MAX_VALUE else (finalCost / n.toFloat())
    }

    // --- 5. GBOARD-GRADE GESTURE DECODER ---

    data class TimedPoint(val x: Float, val y: Float, val time: Long = 0L) {
        fun toPointF(): PointF = PointF(x, y)
    }

    fun matchGesture(
        points: List<PointF>,
        keyBounds: Map<Char, RectF>,
        maxMatches: Int = 3
    ): List<String> = matchGestureTimed(points.map { TimedPoint(it.x, it.y, 0L) }, keyBounds, maxMatches)

    fun matchGestureTimed(
        points: List<TimedPoint>,
        keyBounds: Map<Char, RectF>,
        maxMatches: Int = 3
    ): List<String> {
        if (points.size < MIN_POINTS || keyBounds.isEmpty()) return emptyList()

        // 1. Ensure vocabulary is available in memory
        if (!isVocabularyLoaded) {
            val common = WordDictionary.getCommonWordList()
            if (common.isNotEmpty()) {
                initVocabulary(common)
            }
        }

        val avgKeyWidth = keyBounds.values.firstOrNull { it.width() > 0 }?.width() ?: 48f
        val pointFs = points.map { PointF(it.x, it.y) }
        val totalLength = calculatePathLength(pointFs)
        if (totalLength < MIN_PATH_LENGTH_DP) return emptyList()

        val pStart = pointFs.first()
        val pEnd = pointFs.last()

        val startChar = findNearestKey(pStart, keyBounds) ?: return emptyList()
        val endChar = findNearestKey(pEnd, keyBounds) ?: return emptyList()

        // 2. Detect True Dwell / Pause Points (where user lingered on a key for >= 110ms)
        val dwellKeys = mutableListOf<Char>()
        if (points.any { it.time > 0L }) {
            var clusterStart = 0
            val dwellRadius = avgKeyWidth * 0.38f
            for (i in 1 until points.size) {
                val p1 = points[clusterStart]
                val p2 = points[i]
                val d = hypot(p2.x - p1.x, p2.y - p1.y)
                val dt = p2.time - p1.time
                if (d <= dwellRadius) {
                    if (dt >= 110L) { // Intentional linger on key
                        val midX = (p1.x + p2.x) / 2f
                        val midY = (p1.y + p2.y) / 2f
                        findNearestKey(PointF(midX, midY), keyBounds)?.let { k ->
                            if (k != startChar && k != endChar && (dwellKeys.isEmpty() || dwellKeys.last() != k)) {
                                dwellKeys.add(k)
                            }
                        }
                    }
                } else {
                    clusterStart = i
                }
            }
        }

        // 3. Trajectory resampling for DTW matching and corner extraction
        val stepPx = max(12f, avgKeyWidth * 0.25f)
        val arcResampled = resampleTrajectory(pointFs, stepPx)
        val pResampledFixed = resampleFixedCount(pointFs, DTW_POINTS)
        val cornerLetters = arcResampled.filter { it.isCorner }.mapNotNull {
            findNearestKey(PointF(it.x, it.y), keyBounds)
        }.toSet()

        // 4. Gather high-confidence candidate words matching start and end key neighborhoods
        val startCandidates = (setOf(startChar) + findAdjacentKeys(startChar)).toList()
        val endCandidates = (setOf(endChar) + findAdjacentKeys(endChar)).toSet()

        val candidateWords = mutableMapOf<String, Float>()
        val userFreqs = WordDictionary.getUserFrequencies()

        synchronized(root) {
            for (sc in startCandidates) {
                for (ec in endCandidates) {
                    val key = bucketKey(sc, ec)
                    if (key >= 0) {
                        startEndBuckets[key]?.forEach { (w, freq) ->
                            candidateWords[w] = max(candidateWords[w] ?: 0f, freq)
                        }
                    }
                }
            }
        }

        // Also test user vocabulary words that match start/end neighborhoods
        for ((uWord, uCount) in userFreqs) {
            if (uWord.length >= 2 && uWord.all { it in 'a'..'z' }) {
                if (startCandidates.contains(uWord.first()) && endCandidates.contains(uWord.last())) {
                    candidateWords[uWord] = max(candidateWords[uWord] ?: 0f, uCount.toFloat() * 15f)
                }
            }
        }

        // Fallback: Check fallback gesture buckets if candidates are too few
        if (candidateWords.size < 12) {
            val buckets = WordDictionary.getGestureBuckets()
            for (sc in startCandidates) {
                buckets?.get(sc)?.take(80)?.forEach { gw ->
                    if (gw.word.length >= 2 && endCandidates.contains(gw.word.last())) {
                        candidateWords[gw.word] = max(candidateWords[gw.word] ?: 0f, gw.freq)
                    }
                }
            }
        }

        if (candidateWords.isEmpty()) return emptyList()

        // 5. Evaluate and score candidates with DTW shape matching
        val scoredCandidates = ArrayList<Pair<String, Float>>(candidateWords.size)

        for ((word, baseFreq) in candidateWords) {
            val polyline = getWordPolyline(word, keyBounds) ?: continue
            val qResampled = resampleFixedCount(polyline, DTW_POINTS)

            val dtwCost = computeCurveDtw(pResampledFixed, qResampled, avgKeyWidth)
            if (dtwCost > 2.85f) continue // Discard geometrically incompatible shapes

            val userFreq = userFreqs[word]?.toFloat() ?: 0f
            val isCommon = WordDictionary.isCommonWord(word)
            val effectiveFreq = baseFreq + (userFreq * 3.0f) + (if (isCommon) 40f else 0f)

            // Start & End Proximity Bonuses
            val firstCenter = getKeyCenter(word.first(), keyBounds) ?: continue
            val lastCenter = getKeyCenter(word.last(), keyBounds) ?: continue

            val dStart = hypot(pStart.x - firstCenter.x, pStart.y - firstCenter.y) / avgKeyWidth
            val dEnd = hypot(pEnd.x - lastCenter.x, pEnd.y - lastCenter.y) / avgKeyWidth

            val startBonus = (1.5f - dStart).coerceIn(0f, 1.5f) * 3.5f
            val endBonus = (1.5f - dEnd).coerceIn(0f, 1.5f) * 4.0f

            // Corner / Inflection match bonus
            var cornerBonus = 0f
            for (cl in cornerLetters) {
                if (word.contains(cl)) {
                    cornerBonus += 2.0f
                }
            }
            cornerBonus = cornerBonus.coerceAtMost(6.0f)

            // Dwell / Pause Bonus: reward candidates containing dwelled keys in order
            var dwellBonus = 0f
            if (dwellKeys.isNotEmpty()) {
                var dIdx = 0
                for (c in word) {
                    if (dIdx < dwellKeys.size && c == dwellKeys[dIdx]) {
                        dIdx++
                    }
                }
                dwellBonus = dIdx * 5.5f
            }

            // Word length prior
            val lengthFactor = (1.0f + (word.length * 0.05f)).coerceIn(1.0f, 1.4f)

            val finalScore = (ln(effectiveFreq + 1f) * 2.5f) +
                startBonus + endBonus + cornerBonus + dwellBonus +
                (lengthFactor * 1.5f) - (dtwCost * 5.2f)

            scoredCandidates.add(word to finalScore)
        }

        if (scoredCandidates.isEmpty()) return emptyList()

        scoredCandidates.sortByDescending { it.second }
        val rawResults = scoredCandidates.take(maxMatches).map { it.first }

        // Expand contractions (e.g. "dont" -> "don't", "cant" -> "can't", "youre" -> "you're")
        return rawResults.map { commonContractions[it] ?: it }
    }

    // --- 6. CONTINUOUS MULTI-WORD GLIDE TYPING (GBOARD SIGNATURE) ---

    /**
     * Splits continuous gesture trajectory at spacebar crossings.
     * Allows typing full phrases ("how are you") in a single uninterrupted stroke.
     */
    fun splitMultiWordTrajectory(
        points: List<PointF>,
        spaceBounds: RectF?
    ): List<List<PointF>> {
        if (spaceBounds == null || points.size < MIN_POINTS * 2) return listOf(points)

        val segments = mutableListOf<List<PointF>>()
        var currentSegment = mutableListOf<PointF>()
        var inSpace = false
        val spaceTop = spaceBounds.top
        val spaceLeft = spaceBounds.left
        val spaceRight = spaceBounds.right

        for (p in points) {
            val isInsideSpace = (p.y >= spaceTop && p.x >= spaceLeft && p.x <= spaceRight)
            if (isInsideSpace) {
                if (!inSpace) {
                    if (currentSegment.size >= MIN_POINTS && calculatePathLength(currentSegment) >= MIN_PATH_LENGTH_DP) {
                        segments.add(currentSegment)
                        currentSegment = mutableListOf()
                        inSpace = true
                    } else {
                        currentSegment.add(p)
                    }
                }
            } else {
                if (inSpace) {
                    inSpace = false
                }
                currentSegment.add(p)
            }
        }

        if (currentSegment.size >= MIN_POINTS && calculatePathLength(currentSegment) >= MIN_PATH_LENGTH_DP) {
            segments.add(currentSegment)
        }

        return if (segments.size > 1 && segments.all { it.size >= MIN_POINTS }) segments else listOf(points)
    }

    /**
     * Decodes a potentially continuous multi-word glide trajectory.
     * Returns: (List of committed words, Alternatives for the final word)
     */
    fun matchMultiWordGesture(
        points: List<PointF>,
        keyBounds: Map<Char, RectF>,
        spaceBounds: RectF?,
        maxMatches: Int = 3
    ): Pair<List<String>, List<String>> = matchMultiWordGestureTimed(points.map { TimedPoint(it.x, it.y, 0L) }, keyBounds, spaceBounds, maxMatches)

    fun matchMultiWordGestureTimed(
        points: List<TimedPoint>,
        keyBounds: Map<Char, RectF>,
        spaceBounds: RectF?,
        maxMatches: Int = 3
    ): Pair<List<String>, List<String>> {
        val pointFs = points.map { PointF(it.x, it.y) }
        val segments = splitMultiWordTrajectory(pointFs, spaceBounds)
        if (segments.size <= 1) {
            val single = matchGestureTimed(points, keyBounds, maxMatches)
            return if (single.isNotEmpty()) {
                listOf(single.first()) to single.drop(1)
            } else {
                emptyList<String>() to emptyList()
            }
        }

        val decodedWords = mutableListOf<String>()
        var lastAlternates = emptyList<String>()

        var pointCursor = 0
        for (i in segments.indices) {
            val seg = segments[i]
            val segTimed = if (pointCursor + seg.size <= points.size) {
                points.subList(pointCursor, pointCursor + seg.size).also { pointCursor += seg.size }
            } else {
                seg.map { TimedPoint(it.x, it.y, 0L) }
            }
            val isLast = (i == segments.size - 1)
            val matches = matchGestureTimed(segTimed, keyBounds, if (isLast) maxMatches else 1)
            if (matches.isEmpty()) {
                val fallback = matchGestureTimed(points, keyBounds, maxMatches)
                return if (fallback.isNotEmpty()) {
                    listOf(fallback.first()) to fallback.drop(1)
                } else {
                    emptyList<String>() to emptyList()
                }
            }
            decodedWords.add(matches.first())
            if (isLast) {
                lastAlternates = matches.drop(1)
            }
        }

        return decodedWords to lastAlternates
    }

    /**
     * Builds smooth Catmull-Rom or cubic spline Path for trail visualization.
     */
    fun buildSmoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size < 3) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
            return path
        }

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
}
