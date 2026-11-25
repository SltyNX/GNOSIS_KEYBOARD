package gnosis.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

class ModernToggleView(
    context: Context,
    private var isChecked: Boolean,
    private val density: Float,
    private val onToggle: (Boolean) -> Unit
) : View(context) {

    private var animProgress: Float = if (isChecked) 1f else 0f
    private var toggleAnimator: ValueAnimator? = null

    var uiStyle: String = "md3"
        set(value) {
            field = value
            invalidate()
        }

    var accentColor: Int = Color.parseColor("#7C9CFF")
        set(value) {
            field = value
            invalidate()
        }

    var isLight: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var trackOffColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var strokeOffColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var strokeWidthDp: Float = 1.2f
        set(value) {
            field = value
            strokePaint.strokeWidth = value * density
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.parseColor("#374151")
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val neuShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val trackPath = Path()
    private val trackRect = RectF()

    init {
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isChecked = !isChecked
            animateToState(if (isChecked) 1f else 0f)
            onToggle(isChecked)
        }
    }

    private fun animateToState(target: Float) {
        toggleAnimator?.cancel()
        toggleAnimator = ValueAnimator.ofFloat(animProgress, target).apply {
            duration = 190L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                animProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setChecked(checked: Boolean, animated: Boolean = true) {
        if (isChecked == checked) return
        isChecked = checked
        if (animated && isAttachedToWindow) {
            animateToState(if (checked) 1f else 0f)
        } else {
            toggleAnimator?.cancel()
            animProgress = if (checked) 1f else 0f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        toggleAnimator?.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((48 * density).toInt(), (28 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (uiStyle == "neumorphic") {
            val pad = 1.8f * density
            trackRect.set(pad, pad, w - pad, h - pad)
            val trackR = trackRect.height() / 2f
            trackPath.reset()
            trackPath.addRoundRect(trackRect, trackR, trackR, Path.Direction.CW)

            val neuSurface = if (isLight) Color.parseColor("#E0E5EC") else Color.parseColor("#1E2127")
            val baseTrackColor = trackOffColor ?: neuSurface

            trackPaint.color = baseTrackColor
            canvas.drawRoundRect(trackRect, trackR, trackR, trackPaint)

            canvas.save()
            canvas.clipPath(trackPath)
            val innerDark = if (isLight) Color.argb(90, 163, 177, 198) else Color.argb(140, 0, 0, 0)
            val innerLight = if (isLight) Color.argb(220, 255, 255, 255) else Color.argb(35, 255, 255, 255)
            neuInnerPaint.shader = LinearGradient(
                trackRect.left, trackRect.top, trackRect.left, trackRect.top + (4.5f * density),
                innerDark, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(trackRect, trackR, trackR, neuInnerPaint)

            neuInnerPaint.shader = LinearGradient(
                trackRect.left, trackRect.bottom - (3.5f * density), trackRect.left, trackRect.bottom,
                Color.TRANSPARENT, innerLight, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(trackRect, trackR, trackR, neuInnerPaint)
            canvas.restore()

            val thumbRadius = trackR - (2.2f * density)
            val offX = trackRect.left + trackR
            val onX = trackRect.right - trackR
            val thumbX = offX + animProgress * (onX - offX)
            val thumbY = trackRect.centerY()
            val thumbShadowOffset = 1.2f * density

            neuShadowPaint.color = if (isLight) Color.argb(220, 255, 255, 255) else Color.argb(35, 255, 255, 255)
            canvas.drawCircle(thumbX - thumbShadowOffset, thumbY - thumbShadowOffset, thumbRadius, neuShadowPaint)

            neuShadowPaint.color = if (isLight) Color.argb(85, 163, 177, 198) else Color.argb(120, 0, 0, 0)
            canvas.drawCircle(thumbX + thumbShadowOffset, thumbY + thumbShadowOffset, thumbRadius, neuShadowPaint)

            val neuSubtext = if (isLight) Color.parseColor("#718096") else Color.parseColor("#888E97")
            val thumbSurface = ColorUtils.blendARGB(neuSubtext, accentColor, animProgress)
            thumbPaint.color = thumbSurface
            canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)

            neuBevelPaint.strokeWidth = 0.8f * density
            val bevelLight = if (isLight) Color.argb(160, 255, 255, 255) else Color.argb(45, 255, 255, 255)
            val bevelDark = if (isLight) Color.argb(60, 163, 177, 198) else Color.argb(60, 0, 0, 0)
            neuBevelPaint.shader = LinearGradient(
                thumbX - thumbRadius, thumbY - thumbRadius, thumbX + thumbRadius, thumbY + thumbRadius,
                bevelLight, bevelDark, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(thumbX, thumbY, thumbRadius - (0.4f * density), neuBevelPaint)
            return
        }

        if (uiStyle == "md3") {
            val pad = 1.5f * density
            trackRect.set(pad, pad, w - pad, h - pad)
            val trackR = trackRect.height() / 2f

            val offTrack = if (isLight) Color.parseColor("#E0E2EC") else Color.parseColor("#35383D")
            val offThumb = if (isLight) Color.parseColor("#74777F") else Color.parseColor("#CFD3D8")
            val onTrack = accentColor
            val onThumb = if (isLight) Color.WHITE else Color.parseColor("#10131A")

            val currentTrack = ColorUtils.blendARGB(trackOffColor ?: offTrack, onTrack, animProgress)
            trackPaint.color = currentTrack
            canvas.drawRoundRect(trackRect, trackR, trackR, trackPaint)

            val baseThumbRadius = trackR - (3.4f * density)
            val activeThumbRadius = trackR - (2.2f * density)
            val thumbRadius = baseThumbRadius + animProgress * (activeThumbRadius - baseThumbRadius)

            val offX = trackRect.left + trackR
            val onX = trackRect.right - trackR
            val thumbX = offX + animProgress * (onX - offX)
            val thumbY = trackRect.centerY()

            val currentThumb = ColorUtils.blendARGB(offThumb, onThumb, animProgress)
            thumbPaint.color = currentThumb
            canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
            return
        }

        val strokeW = strokePaint.strokeWidth
        val halfStroke = strokeW / 2f
        trackRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        val trackR = trackRect.height() / 2f

        val defaultOffTrack = if (isLight) Color.parseColor("#CBD5E1") else Color.parseColor("#1F2432")
        val defaultOffStroke = if (isLight) Color.parseColor("#94A3B8") else Color.parseColor("#374151")

        val effectiveAccent = Skins.ensureContrast(accentColor, listOf(defaultOffTrack, thumbPaint.color))

        val currentTrack = ColorUtils.blendARGB(trackOffColor ?: defaultOffTrack, effectiveAccent, animProgress)
        trackPaint.color = currentTrack
        strokePaint.color = strokeOffColor ?: defaultOffStroke
        val strokeAlpha = ((1f - animProgress) * 255f).toInt().coerceIn(0, 255)
        strokePaint.alpha = strokeAlpha

        canvas.drawRoundRect(trackRect, trackR, trackR, trackPaint)
        if (strokeAlpha > 0) {
            canvas.drawRoundRect(trackRect, trackR, trackR, strokePaint)
        }

        val thumbRadius = trackR - (2.5f * density)
        val offX = trackRect.left + trackR
        val onX = trackRect.right - trackR
        val thumbX = offX + animProgress * (onX - offX)
        val thumbY = trackRect.centerY()

        val onThumb = if (Skins.safeContrast(Color.WHITE, effectiveAccent) < 2.0) {
            Color.parseColor("#151822")
        } else {
            Color.WHITE
        }
        val offThumb = Color.WHITE
        thumbPaint.color = ColorUtils.blendARGB(offThumb, onThumb, animProgress)

        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }
}
