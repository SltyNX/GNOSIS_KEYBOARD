package gnosis.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

class LucidSliderView(
    context: Context,
    var min: Float = 0f,
    var max: Float = 100f,
    initialValue: Float = 50f,
    var step: Float = 1f,
    private val density: Float = context.resources.displayMetrics.density,
    var onValueChanged: ((Float) -> Unit)? = null
) : View(context) {

    var uiStyle: String = "md3"
        set(value) {
            field = value
            invalidate()
        }

    var value: Float = initialValue.coerceIn(min, max)
        set(v) {
            val clamped = v.coerceIn(min, max)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var accentColor: Int = Color.parseColor("#7C9CFF")
        set(c) {
            field = c
            invalidate()
        }

    var isLight: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var trackColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var trackBorderColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1B2336")
    }

    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#2C3A56")
    }

    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000")
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val neuInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val neuBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val neuTactileRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val neuTrackPath = Path()
    private val neuActivePath = Path()

    private val trackRect = RectF()
    private val activeRect = RectF()

    private val trackHeight = dp(6f)
    private val thumbRadius = dp(10f)
    private val thumbInnerRadius = dp(6f)
    private var isDragging = false
    private var lastHapticValue = -9999f

    init {
        isFocusable = true
        isClickable = true
    }

    private fun dp(v: Float): Float = v * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (thumbRadius * 2 + dp(14f)).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (uiStyle == "neumorphic") {
            val neuTrackHeight = dp(7.5f)
            val neuThumbRadius = dp(11f)
            val neuPadLeft = paddingLeft.toFloat() + neuThumbRadius
            val neuPadRight = width.toFloat() - paddingRight.toFloat() - neuThumbRadius
            val neuTrackWidth = (neuPadRight - neuPadLeft).coerceAtLeast(1f)
            val centerY = height / 2f
            val fraction = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
            val thumbX = neuPadLeft + fraction * neuTrackWidth

            val defaultNeuTrack = if (isLight) Color.parseColor("#E0E5EC") else Color.parseColor("#1E2127")
            val baseTrackColor = trackColor ?: defaultNeuTrack
            trackRect.set(neuPadLeft, centerY - neuTrackHeight / 2f, neuPadRight, centerY + neuTrackHeight / 2f)
            val trackR = neuTrackHeight / 2f

            trackPaint.color = baseTrackColor
            canvas.drawRoundRect(trackRect, trackR, trackR, trackPaint)

            canvas.save()
            neuTrackPath.reset()
            neuTrackPath.addRoundRect(trackRect, trackR, trackR, Path.Direction.CW)
            canvas.clipPath(neuTrackPath)
            val innerDark = if (isLight) Color.argb(120, 163, 177, 198) else Color.argb(140, 0, 0, 0)
            val innerLight = if (isLight) Color.argb(180, 255, 255, 255) else Color.argb(35, 255, 255, 255)
            neuInnerPaint.shader = LinearGradient(
                trackRect.left, trackRect.top, trackRect.left, trackRect.top + dp(4f),
                innerDark, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(trackRect, trackR, trackR, neuInnerPaint)
            neuInnerPaint.shader = LinearGradient(
                trackRect.left, trackRect.bottom - dp(3f), trackRect.left, trackRect.bottom,
                Color.TRANSPARENT, innerLight, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(trackRect, trackR, trackR, neuInnerPaint)
            canvas.restore()

            if (thumbX > neuPadLeft) {
                canvas.save()
                canvas.clipPath(neuTrackPath)
                activeTrackPaint.color = accentColor
                canvas.drawRect(neuPadLeft, centerY - neuTrackHeight / 2f, thumbX, centerY + neuTrackHeight / 2f, activeTrackPaint)
                neuInnerPaint.shader = LinearGradient(
                    neuPadLeft, centerY - neuTrackHeight / 2f, neuPadLeft, centerY - neuTrackHeight / 2f + dp(3.5f),
                    Color.argb(80, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawRect(neuPadLeft, centerY - neuTrackHeight / 2f, thumbX, centerY + neuTrackHeight / 2f, neuInnerPaint)
                canvas.restore()
            }

            if (isDragging) {
                haloPaint.color = ColorUtils.setAlphaComponent(accentColor, 35)
                canvas.drawCircle(thumbX, centerY, neuThumbRadius + dp(5f), haloPaint)
            }

            val shadowDist = dp(1.8f)
            val shadowLight = if (isLight) Color.argb(220, 255, 255, 255) else Color.argb(35, 255, 255, 255)
            val shadowDark = if (isLight) Color.argb(85, 163, 177, 198) else Color.argb(120, 0, 0, 0)

            neuShadowPaint.color = shadowLight
            canvas.drawCircle(thumbX - shadowDist, centerY - shadowDist, neuThumbRadius, neuShadowPaint)

            neuShadowPaint.color = shadowDark
            canvas.drawCircle(thumbX + shadowDist * 1.15f, centerY + shadowDist * 1.25f, neuThumbRadius, neuShadowPaint)

            thumbOuterPaint.color = accentColor
            canvas.drawCircle(thumbX, centerY, neuThumbRadius, thumbOuterPaint)

            neuTactileRingPaint.strokeWidth = dp(1.2f)
            neuTactileRingPaint.color = if (isLight) Color.argb(90, 255, 255, 255) else Color.argb(100, 16, 19, 26)
            canvas.drawCircle(thumbX, centerY, dp(4.5f), neuTactileRingPaint)

            neuBevelPaint.strokeWidth = dp(0.8f)
            neuBevelPaint.shader = LinearGradient(
                thumbX - neuThumbRadius, centerY - neuThumbRadius, thumbX + neuThumbRadius, centerY + neuThumbRadius,
                if (isLight) Color.argb(160, 255, 255, 255) else Color.argb(45, 255, 255, 255),
                if (isLight) Color.argb(60, 163, 177, 198) else Color.argb(60, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(thumbX, centerY, neuThumbRadius - dp(0.4f), neuBevelPaint)
            return
        }

        if (uiStyle == "md3") {
            val padLeft = paddingLeft.toFloat() + thumbRadius
            val padRight = width.toFloat() - paddingRight.toFloat() - thumbRadius
            val trackWidth = (padRight - padLeft).coerceAtLeast(1f)
            val centerY = height / 2f
            val fraction = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
            val thumbX = padLeft + fraction * trackWidth

            val offTrack = if (isLight) Color.parseColor("#E0E2EC") else Color.parseColor("#35383D")
            val onThumb = if (isLight) Color.WHITE else Color.parseColor("#10131A")

            trackPaint.color = trackColor ?: offTrack
            trackRect.set(padLeft, centerY - trackHeight / 2f, padRight, centerY + trackHeight / 2f)
            canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

            if (thumbX > padLeft) {
                canvas.save()
                val md3Path = Path().apply {
                    addRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, Path.Direction.CW)
                }
                canvas.clipPath(md3Path)
                activeTrackPaint.color = accentColor
                canvas.drawRect(padLeft, centerY - trackHeight / 2f, thumbX, centerY + trackHeight / 2f, activeTrackPaint)
                canvas.restore()
            }

            if (isDragging) {
                haloPaint.color = ColorUtils.setAlphaComponent(accentColor, 40)
                canvas.drawCircle(thumbX, centerY, thumbRadius + dp(6f), haloPaint)
            }

            val activeThumbR = if (isDragging) thumbRadius + dp(1.5f) else thumbRadius
            canvas.drawCircle(thumbX, centerY, activeThumbR, thumbOuterPaint.apply { color = accentColor })
            canvas.drawCircle(thumbX, centerY, thumbInnerRadius, thumbInnerPaint.apply { color = onThumb })
            return
        }

        val padLeft = paddingLeft.toFloat() + thumbRadius
        val padRight = width.toFloat() - paddingRight.toFloat() - thumbRadius
        val trackWidth = (padRight - padLeft).coerceAtLeast(1f)
        val centerY = height / 2f

        val fraction = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
        val thumbX = padLeft + fraction * trackWidth

        val defaultTrack = if (isLight) Color.parseColor("#E2E8F0") else Color.parseColor("#1B2336")
        val defaultBorder = if (isLight) Color.parseColor("#CBD5E1") else Color.parseColor("#2C3A56")
        trackPaint.color = trackColor ?: defaultTrack
        trackBorderPaint.color = trackBorderColor ?: defaultBorder

        trackRect.set(padLeft, centerY - trackHeight / 2f, padRight, centerY + trackHeight / 2f)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint)
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackBorderPaint)

        if (thumbX > padLeft) {
            canvas.save()
            val defPath = Path().apply {
                addRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, Path.Direction.CW)
            }
            canvas.clipPath(defPath)
            activeTrackPaint.color = accentColor
            canvas.drawRect(padLeft, centerY - trackHeight / 2f, thumbX, centerY + trackHeight / 2f, activeTrackPaint)
            canvas.restore()
        }

        if (isDragging) {
            haloPaint.color = ColorUtils.setAlphaComponent(accentColor, 40)
            canvas.drawCircle(thumbX, centerY, thumbRadius + dp(6f), haloPaint)
        }

        val activeThumbR = if (isDragging) thumbRadius + dp(1.5f) else thumbRadius
        canvas.drawCircle(thumbX, centerY + dp(1.5f), activeThumbR + dp(1f), thumbShadowPaint)
        canvas.drawCircle(thumbX, centerY, activeThumbR, thumbOuterPaint)
        thumbInnerPaint.color = if (Skins.safeContrast(Color.WHITE, accentColor) < 2.0) {
            Color.parseColor("#1B2336")
        } else {
            accentColor
        }
        canvas.drawCircle(thumbX, centerY, thumbInnerRadius, thumbInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateValueFromTouch(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateValueFromTouch(event.x)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateValueFromTouch(touchX: Float) {
        val effThumbRadius = if (uiStyle == "neumorphic") dp(11f) else thumbRadius
        val padLeft = paddingLeft.toFloat() + effThumbRadius
        val padRight = width.toFloat() - paddingRight.toFloat() - effThumbRadius
        val trackWidth = (padRight - padLeft).coerceAtLeast(1f)
        val fraction = ((touchX - padLeft) / trackWidth).coerceIn(0f, 1f)

        var rawVal = min + fraction * (max - min)
        if (step > 0f) {
            rawVal = min + Math.round((rawVal - min) / step) * step
        }
        val finalVal = rawVal.coerceIn(min, max)

        if (finalVal != value) {
            value = finalVal
            if (Math.abs(finalVal - lastHapticValue) >= (if (step > 0f) step else 1f)) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {}
                lastHapticValue = finalVal
            }
            onValueChanged?.invoke(finalVal)
            invalidate()
        }
    }
}
