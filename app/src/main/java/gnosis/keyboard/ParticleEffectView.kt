package gnosis.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleEffectView(context: Context) : View(context) {

    enum class Style(val id: String, val displayName: String) {
        OFF("off", "Off"),
        SPARKS("sparks", "Neon Sparks"),
        HEARTS("hearts", "Floating Hearts"),
        BUBBLES("bubbles", "Bubble Burst"),
        STARS("stars", "Glitter Stars"),
        NEON_TRAIL("trail", "Neon Glow Trail")
    }

    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var size = 0f
        var alpha = 1f
        var decay = 0.05f
        var color = Color.WHITE
        var rotation = 0f
        var vRot = 0f
        var extra = 0f // e.g. heart scale or bubble stroke
    }

    private val maxParticles = 160
    private val particlePool = Array(maxParticles) { Particle() }
    private var activeCount = 0

    private val trailPoints = ArrayList<TrailPoint>()
    private class TrailPoint(val x: Float, val y: Float, val time: Long, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val heartPath = Path()
    private val starPath = Path()

    var effectStyle: Style = Style.SPARKS
    var primaryColor: Int = Color.parseColor("#4C8DFF")
    var secondaryColor: Int = Color.parseColor("#00E5FF")

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val w = if (wMode != MeasureSpec.UNSPECIFIED) wSize else 0
        val h = if (hMode == MeasureSpec.EXACTLY) hSize else 0
        setMeasuredDimension(w, h)
    }

    fun onTouch(x: Float, y: Float, action: Int) {
        if (effectStyle == Style.OFF) return

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                spawnTapParticles(x, y)
                if (effectStyle == Style.NEON_TRAIL) {
                    addTrailPoint(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (effectStyle == Style.NEON_TRAIL) {
                    addTrailPoint(x, y)
                } else if (Random.nextFloat() < 0.25f) {
                    spawnTapParticles(x, y, count = 2)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (effectStyle == Style.NEON_TRAIL) {
                    addTrailPoint(x, y)
                }
            }
        }
    }

    private fun addTrailPoint(x: Float, y: Float) {
        val color = if (Random.nextBoolean()) primaryColor else secondaryColor
        trailPoints.add(TrailPoint(x, y, SystemClock.uptimeMillis(), color))
        if (trailPoints.size > 40) {
            trailPoints.removeAt(0)
        }
        postInvalidateOnAnimation()
    }

    private fun spawnTapParticles(x: Float, y: Float, count: Int = 10) {
        val spawnCount = when (effectStyle) {
            Style.SPARKS -> count.coerceAtMost(14)
            Style.HEARTS -> (count / 2).coerceIn(3, 7)
            Style.BUBBLES -> (count / 2).coerceIn(4, 8)
            Style.STARS -> (count / 2).coerceIn(4, 8)
            Style.NEON_TRAIL, Style.OFF -> 0
        }
        if (spawnCount <= 0) return

        val density = resources.displayMetrics.density
        var spawned = 0
        for (p in particlePool) {
            if (!p.active) {
                p.active = true
                p.x = x
                p.y = y
                p.alpha = 1f
                p.rotation = Random.nextFloat() * 360f
                p.vRot = (Random.nextFloat() - 0.5f) * 12f

                val colorPick = when (Random.nextInt(3)) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> ColorUtils.blendARGB(primaryColor, Color.WHITE, 0.45f)
                }
                p.color = colorPick

                when (effectStyle) {
                    Style.SPARKS -> {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val speed = (Random.nextFloat() * 7f + 3f) * density
                        p.vx = cos(angle) * speed
                        p.vy = sin(angle) * speed - (2f * density)
                        p.size = (Random.nextFloat() * 3.5f + 2f) * density
                        p.decay = Random.nextFloat() * 0.035f + 0.03f
                    }
                    Style.HEARTS -> {
                        val angle = (Random.nextFloat() * 0.8f + 1.1f) * Math.PI.toFloat()
                        val speed = (Random.nextFloat() * 3f + 1.5f) * density
                        p.vx = cos(angle) * speed * 0.6f
                        p.vy = -speed
                        p.size = (Random.nextFloat() * 7f + 9f) * density
                        p.decay = Random.nextFloat() * 0.025f + 0.018f
                    }
                    Style.BUBBLES -> {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val speed = (Random.nextFloat() * 3f + 1f) * density
                        p.vx = cos(angle) * speed
                        p.vy = sin(angle) * speed - (1.5f * density)
                        p.size = (Random.nextFloat() * 8f + 6f) * density
                        p.decay = Random.nextFloat() * 0.028f + 0.02f
                    }
                    Style.STARS -> {
                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                        val speed = (Random.nextFloat() * 5f + 2f) * density
                        p.vx = cos(angle) * speed
                        p.vy = sin(angle) * speed - (1.5f * density)
                        p.size = (Random.nextFloat() * 6f + 7f) * density
                        p.decay = Random.nextFloat() * 0.03f + 0.025f
                    }
                    else -> {}
                }

                spawned++
                activeCount++
                if (spawned >= spawnCount) break
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (effectStyle == Style.OFF) return

        val density = resources.displayMetrics.density
        var hasActive = false
        val now = SystemClock.uptimeMillis()

        // 1. Draw and update particles
        for (p in particlePool) {
            if (!p.active) continue

            p.x += p.vx
            p.y += p.vy
            p.rotation += p.vRot

            when (effectStyle) {
                Style.SPARKS -> {
                    p.vy += 0.25f * density // subtle gravity
                    p.vx *= 0.94f
                    p.vy *= 0.94f
                }
                Style.HEARTS -> {
                    p.x += sin(p.y / (16f * density)) * (0.8f * density)
                    p.vy *= 0.98f
                }
                Style.BUBBLES -> {
                    p.vy -= 0.08f * density // buoyancy
                    p.vx *= 0.96f
                    p.size += 0.15f * density // expanding bubble
                }
                Style.STARS -> {
                    p.vx *= 0.95f
                    p.vy *= 0.95f
                }
                else -> {}
            }

            p.alpha -= p.decay
            if (p.alpha <= 0f) {
                p.active = false
                activeCount = (activeCount - 1).coerceAtLeast(0)
                continue
            }

            hasActive = true
            val alphaInt = (p.alpha * 255).toInt().coerceIn(0, 255)

            when (effectStyle) {
                Style.SPARKS -> {
                    paint.color = ColorUtils.setAlphaComponent(p.color, alphaInt)
                    canvas.drawCircle(p.x, p.y, p.size * p.alpha, paint)
                }
                Style.HEARTS -> {
                    paint.color = ColorUtils.setAlphaComponent(p.color, alphaInt)
                    drawHeart(canvas, p.x, p.y, p.size * p.alpha, paint)
                }
                Style.BUBBLES -> {
                    // Translucent bubble body
                    paint.color = ColorUtils.setAlphaComponent(p.color, (alphaInt * 0.35f).toInt())
                    canvas.drawCircle(p.x, p.y, p.size, paint)
                    // Iridescent ring
                    strokePaint.color = ColorUtils.setAlphaComponent(p.color, alphaInt)
                    strokePaint.strokeWidth = 1.4f * density
                    canvas.drawCircle(p.x, p.y, p.size, strokePaint)
                }
                Style.STARS -> {
                    paint.color = ColorUtils.setAlphaComponent(p.color, alphaInt)
                    drawStar(canvas, p.x, p.y, p.size * p.alpha, p.rotation, paint)
                }
                else -> {}
            }
        }

        // 2. Draw and update neon trail
        if (effectStyle == Style.NEON_TRAIL && trailPoints.isNotEmpty()) {
            val maxAge = 350L
            val iterator = trailPoints.iterator()
            var prevX = 0f
            var prevY = 0f
            var hasPrev = false

            while (iterator.hasNext()) {
                val pt = iterator.next()
                val age = now - pt.time
                if (age > maxAge) {
                    iterator.remove()
                    continue
                }
                hasActive = true
                val progress = 1f - (age.toFloat() / maxAge)
                val alpha = (progress * 220).toInt().coerceIn(0, 255)

                if (hasPrev) {
                    strokePaint.color = ColorUtils.setAlphaComponent(pt.color, alpha)
                    strokePaint.strokeWidth = (7f * progress + 2f) * density
                    canvas.drawLine(prevX, prevY, pt.x, pt.y, strokePaint)
                }
                prevX = pt.x
                prevY = pt.y
                hasPrev = true
            }
        }

        if (hasActive) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, p: Paint) {
        val s = size / 2f
        heartPath.reset()
        heartPath.moveTo(cx, cy + s * 0.8f)
        heartPath.cubicTo(cx - s * 1.2f, cy, cx - s * 1.2f, cy - s, cx, cy - s * 0.4f)
        heartPath.cubicTo(cx + s * 1.2f, cy - s, cx + s * 1.2f, cy, cx, cy + s * 0.8f)
        heartPath.close()
        canvas.drawPath(heartPath, p)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, size: Float, rotDeg: Float, p: Paint) {
        starPath.reset()
        val radRot = Math.toRadians(rotDeg.toDouble())
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) size else size * 0.4f
            val angle = radRot + i * (Math.PI / 4.0)
            val px = cx + (r * cos(angle)).toFloat()
            val py = cy + (r * sin(angle)).toFloat()
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, p)
    }
}
