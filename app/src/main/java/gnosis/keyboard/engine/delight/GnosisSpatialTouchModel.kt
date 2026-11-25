package gnosis.keyboard.engine.delight

import android.graphics.PointF
import kotlin.math.exp

/**
 * Gnosis Spatial Touch Model
 *
 * Implements Gnosis TNT (Touch-Neural-Targeting) Spatial Model.
 * Evaluates bivariate Gaussian probability distributions over key centroids
 * to accurately resolve finger fat-pad contact variance and adjacent key slips.
 * Highly optimized with static adjacency lookup tables and zero-allocation scoring.
 */
class GnosisSpatialTouchModel {

    // Normalized QWERTY key centroids (col: 0.0..1.0, row: 0.0..1.0)
    val keyCentroids = mapOf(
        'q' to PointF(0.05f, 0.2f), 'w' to PointF(0.15f, 0.2f), 'e' to PointF(0.25f, 0.2f),
        'r' to PointF(0.35f, 0.2f), 't' to PointF(0.45f, 0.2f), 'y' to PointF(0.55f, 0.2f),
        'u' to PointF(0.65f, 0.2f), 'i' to PointF(0.75f, 0.2f), 'o' to PointF(0.85f, 0.2f),
        'p' to PointF(0.95f, 0.2f),

        'a' to PointF(0.10f, 0.5f), 's' to PointF(0.20f, 0.5f), 'd' to PointF(0.30f, 0.5f),
        'f' to PointF(0.40f, 0.5f), 'g' to PointF(0.50f, 0.5f), 'h' to PointF(0.60f, 0.5f),
        'j' to PointF(0.70f, 0.5f), 'k' to PointF(0.80f, 0.5f), 'l' to PointF(0.90f, 0.5f),

        'z' to PointF(0.20f, 0.8f), 'x' to PointF(0.30f, 0.8f), 'c' to PointF(0.40f, 0.8f),
        'v' to PointF(0.50f, 0.8f), 'b' to PointF(0.60f, 0.8f), 'n' to PointF(0.70f, 0.8f),
        'm' to PointF(0.80f, 0.8f)
    )

    private val touchVariance = 0.085f
    private val varianceFactor = 1.0f / (2f * touchVariance * touchVariance)

    companion object {
        private val ADJACENT_MAP: Map<Char, List<Char>> = mapOf(
            'q' to listOf('w', 'a', 's'),
            'w' to listOf('q', 'e', 'a', 's', 'd'),
            'e' to listOf('w', 'r', 's', 'd', 'f'),
            'r' to listOf('e', 't', 'd', 'f', 'g'),
            't' to listOf('r', 'y', 'f', 'g', 'h'),
            'y' to listOf('t', 'u', 'g', 'h', 'j'),
            'u' to listOf('y', 'i', 'h', 'j', 'k'),
            'i' to listOf('u', 'o', 'j', 'k', 'l'),
            'o' to listOf('i', 'p', 'k', 'l'),
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
    }

    /**
     * Calculates spatial likelihood P(key | touchPoint) using Gaussian radial basis function.
     */
    fun calculateLikelihood(intendedChar: Char, touchPoint: PointF?): Float {
        if (touchPoint == null) return 1.0f
        val centroid = keyCentroids[intendedChar.lowercaseChar()] ?: return 0.5f
        val dx = touchPoint.x - centroid.x
        val dy = touchPoint.y - centroid.y
        val distSq = dx * dx + dy * dy
        return exp(-distSq * varianceFactor).coerceIn(0.01f, 1.0f)
    }

    /**
     * Returns adjacent physical neighbor keys within spatial proximity radius (instant static lookup).
     */
    fun findAdjacentCharacters(ch: Char): List<Char> {
        return ADJACENT_MAP[ch.lowercaseChar()] ?: emptyList()
    }
}
