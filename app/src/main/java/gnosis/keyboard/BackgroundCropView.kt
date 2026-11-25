package gnosis.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView

class AspectImageView(ctx: Context, private val ratio: Float) : ImageView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w / ratio).toInt())
    }
}

class BackgroundCropView(
    ctx: Context,
    private val bitmap: Bitmap,
    private val ratio: Float,
    private val drawable: CenterCropDrawable,
    var zoom: Float,
    var cx: Float,
    var cy: Float,
    private val onZoomChanged: (Float) -> Unit
) : View(ctx) {

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            setZoomInternal(zoom * d.scaleFactor)
            onZoomChanged(zoom)
            return true
        }
    })

    init {
        setBackgroundColor(Color.BLACK)
        apply()
    }

    private fun clampCenter() {
        val (fw, fh) = CustomBackground.visibleFraction(bitmap.width, bitmap.height, ratio, zoom)
        cx = cx.coerceIn(fw / 2, 1f - fw / 2)
        cy = cy.coerceIn(fh / 2, 1f - fh / 2)
    }

    private fun apply() {
        clampCenter()
        drawable.setCrop(zoom, cx, cy)
        invalidate()
    }

    private fun setZoomInternal(z: Float) {
        zoom = z.coerceIn(1f, CustomBackground.MAX_ZOOM)
        apply()
    }

    fun setZoomFromSlider(z: Float) = setZoomInternal(z)

    fun reset() {
        cx = 0.5f; cy = 0.5f
        setZoomInternal(1f)
        onZoomChanged(zoom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w / ratio).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        drawable.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        drawable.draw(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; dragging = true }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) (if (e.actionIndex == 0) 1 else 0) else 0
                if (i < e.pointerCount) { lastX = e.getX(i); lastY = e.getY(i) }
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaler.isInProgress && e.pointerCount == 1 && width > 0) {
                val (fw, fh) = CustomBackground.visibleFraction(bitmap.width, bitmap.height, ratio, zoom)
                cx -= (e.x - lastX) / width * fw
                cy -= (e.y - lastY) / height * fh
                lastX = e.x; lastY = e.y
                apply()
            } else if (e.pointerCount == 1) { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
