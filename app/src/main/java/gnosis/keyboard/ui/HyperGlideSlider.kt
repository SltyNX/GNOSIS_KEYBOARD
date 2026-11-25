package gnosis.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

class HyperGlideSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var min: Int = 0
        set(v) {
            field = v
            value = value.coerceIn(min, max)
            invalidate()
        }

    var max: Int = 100
        set(v) {
            field = v.coerceAtLeast(min + 1)
            value = value.coerceIn(min, max)
            invalidate()
        }

    var step: Int = 1
        set(v) {
            field = v.coerceAtLeast(1)
            invalidate()
        }

    var value: Int = 50
        set(v) {
            val clamped = v.coerceIn(min, max)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var accentColor: Int = Color.parseColor("#2563EB")
        set(v) {
            field = v
            invalidate()
        }

    var isLightMode: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    var onValueChange: ((Int) -> Unit)? = null
    var onValueChangeFinished: ((Int) -> Unit)? = null

    private var isDragging = false
    private var thumbScale = 1.0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val trackRect = RectF()
    private val activeTrackRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val minHeight = (48f * d).toInt()
        val height = resolveSize(minHeight, heightMeasureSpec)
        val defaultWidth = (240f * d).toInt()
        val width = resolveSize(defaultWidth, widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val baseThumbRadius = 15f * d
        val currentThumbRadius = baseThumbRadius * thumbScale
        val trackHeight = 13f * d
        val trackCornerRadius = trackHeight / 2f

        val paddingLeft = baseThumbRadius + (4f * d)
        val paddingRight = w - baseThumbRadius - (4f * d)
        val trackWidth = (paddingRight - paddingLeft).coerceAtLeast(1f)

        val centerY = h / 2f
        val range = (max - min).coerceAtLeast(1)
        var fraction = ((value - min).toFloat() / range.toFloat()).coerceIn(0f, 1f)
        if (isRtl) fraction = 1f - fraction

        val thumbX = paddingLeft + (fraction * trackWidth)

        trackRect.set(paddingLeft - trackCornerRadius, centerY - (trackHeight / 2f), paddingRight + trackCornerRadius, centerY + (trackHeight / 2f))
        trackPaint.color = if (isLightMode) Color.argb(165, 226, 232, 240) else Color.argb(225, 15, 18, 25)
        canvas.drawRoundRect(trackRect, trackCornerRadius, trackCornerRadius, trackPaint)

        trackBorderPaint.strokeWidth = 1f * d
        trackBorderPaint.color = if (isLightMode) Color.argb(70, 0, 0, 0) else Color.argb(55, 255, 255, 255)
        canvas.drawRoundRect(trackRect, trackCornerRadius, trackCornerRadius, trackBorderPaint)

        val activeLeft = if (isRtl) thumbX else (paddingLeft - trackCornerRadius)
        val activeRight = if (isRtl) (paddingRight + trackCornerRadius) else thumbX

        if (activeRight > activeLeft) {
            activeTrackRect.set(activeLeft, centerY - (trackHeight / 2f), activeRight, centerY + (trackHeight / 2f))

            val gradStart = ColorUtils.blendARGB(accentColor, Color.parseColor("#7C3AED"), 0.45f)
            val gradEnd = ColorUtils.blendARGB(accentColor, Color.parseColor("#38BDF8"), 0.25f)

            activeTrackPaint.shader = LinearGradient(
                activeTrackRect.left, centerY, activeTrackRect.right.coerceAtLeast(activeTrackRect.left + 1f), centerY,
                if (isRtl) gradEnd else gradStart,
                if (isRtl) gradStart else gradEnd,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(activeTrackRect, trackCornerRadius, trackCornerRadius, activeTrackPaint)

            glowPaint.color = ColorUtils.setAlphaComponent(accentColor, 40)
            canvas.drawRoundRect(activeTrackRect, trackCornerRadius, trackCornerRadius, glowPaint)
        }

        glowPaint.color = ColorUtils.setAlphaComponent(accentColor, if (isDragging) 75 else 45)
        canvas.drawCircle(thumbX, centerY, currentThumbRadius + (3.5f * d), glowPaint)

        val thumbTop = ColorUtils.blendARGB(accentColor, Color.WHITE, 0.24f)
        val thumbBottom = ColorUtils.blendARGB(accentColor, Color.BLACK, 0.15f)
        thumbPaint.shader = LinearGradient(
            thumbX - currentThumbRadius, centerY - currentThumbRadius,
            thumbX + currentThumbRadius, centerY + currentThumbRadius,
            thumbTop, thumbBottom, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(thumbX, centerY, currentThumbRadius, thumbPaint)

        thumbRimPaint.strokeWidth = 2.4f * d
        thumbRimPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, centerY, currentThumbRadius, thumbRimPaint)

        thumbDotPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, centerY, 3.5f * d, thumbDotPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val d = resources.displayMetrics.density
        val baseThumbRadius = 15f * d
        val paddingLeft = baseThumbRadius + (4f * d)
        val paddingRight = width.toFloat() - baseThumbRadius - (4f * d)
        val trackWidth = (paddingRight - paddingLeft).coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                thumbScale = 1.12f
                updateProgressFromTouch(event.x, paddingLeft, trackWidth)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgressFromTouch(event.x, paddingLeft, trackWidth)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                thumbScale = 1.0f
                updateProgressFromTouch(event.x, paddingLeft, trackWidth)
                onValueChangeFinished?.invoke(value)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(touchX: Float, paddingLeft: Float, trackWidth: Float) {
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var fraction = ((touchX - paddingLeft) / trackWidth).coerceIn(0f, 1f)
        if (isRtl) fraction = 1f - fraction

        val range = (max - min)
        val rawValue = min + (fraction * range)
        val stepped = (min + ((rawValue - min) / step).roundToInt() * step).coerceIn(min, max)

        if (stepped != value) {
            value = stepped
            onValueChange?.invoke(stepped)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        val rangeType = AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT
        info.rangeInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityNodeInfo.RangeInfo(rangeType, min.toFloat(), max.toFloat(), value.toFloat())
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.RangeInfo.obtain(rangeType, min.toFloat(), max.toFloat(), value.toFloat())
        }
        if (isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (super.performAccessibilityAction(action, arguments)) return true
        if (!isEnabled) return false

        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                val next = (value + step).coerceAtMost(max)
                if (next != value) {
                    value = next
                    onValueChange?.invoke(value)
                    onValueChangeFinished?.invoke(value)
                    return true
                }
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                val prev = (value - step).coerceAtLeast(min)
                if (prev != value) {
                    value = prev
                    onValueChange?.invoke(value)
                    onValueChangeFinished?.invoke(value)
                    return true
                }
            }
        }
        return false
    }
}
