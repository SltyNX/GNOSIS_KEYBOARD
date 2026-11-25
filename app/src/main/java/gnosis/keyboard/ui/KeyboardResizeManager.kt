package gnosis.keyboard.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import gnosis.keyboard.R

class KeyboardResizeManager(private val context: Context) {

    companion object {
        const val PREF_NAME = "keyboard_resize_prefs"
        const val KEY_HEIGHT_PCT = "height_percent"
        const val KEY_BOTTOM_PAD_DP = "bottom_pad_dp"
        const val KEY_ONE_HANDED = "one_handed_mode"
        const val KEY_FLOAT_ENABLED = "floating_enabled"
        const val KEY_FLOAT_X = "float_x"
        const val KEY_FLOAT_Y = "float_y"
        const val KEY_FLOAT_WIDTH_PCT = "float_w_pct"
        const val KEY_FLOAT_HEIGHT_PCT = "float_h_pct"

        const val BASE_KEY_HEIGHT_DP = 50f
        const val BASE_BAR_HEIGHT_DP = 42f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val density: Float = context.resources.displayMetrics.density

    fun dp(v: Float): Int = (v * density).toInt()
    fun screenWidth(): Int = context.resources.displayMetrics.widthPixels
    fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    var heightPercent: Int = prefs.getInt(KEY_HEIGHT_PCT, 100).coerceIn(70, 130)
    var bottomPadDp: Int = prefs.getInt(KEY_BOTTOM_PAD_DP, 0).coerceIn(0, 60)
    var oneHandedMode: String = prefs.getString(KEY_ONE_HANDED, "off") ?: "off"
    var isFloating: Boolean = prefs.getBoolean(KEY_FLOAT_ENABLED, false)
    var floatX: Int = prefs.getInt(KEY_FLOAT_X, dp(20f))
    var floatY: Int = prefs.getInt(KEY_FLOAT_Y, dp(40f))
    var floatWidthPct: Int = prefs.getInt(KEY_FLOAT_WIDTH_PCT, 86)
    var floatHeightPct: Int = prefs.getInt(KEY_FLOAT_HEIGHT_PCT, 100)

    fun heightScale(): Float {
        return if (isFloating) {
            (floatHeightPct / 100f).coerceIn(0.65f, 1.15f)
        } else {
            (heightPercent / 100f).coerceIn(0.70f, 1.30f)
        }
    }

    fun getKeyHeightPx(): Int = (dp(BASE_KEY_HEIGHT_DP) * heightScale()).toInt()
    fun getToolbarHeightPx(): Int = (dp(BASE_BAR_HEIGHT_DP) * heightScale()).toInt()

    fun save() {
        prefs.edit()
            .putInt(KEY_HEIGHT_PCT, heightPercent)
            .putInt(KEY_BOTTOM_PAD_DP, bottomPadDp)
            .putString(KEY_ONE_HANDED, oneHandedMode)
            .putBoolean(KEY_FLOAT_ENABLED, isFloating)
            .putInt(KEY_FLOAT_X, floatX)
            .putInt(KEY_FLOAT_Y, floatY)
            .putInt(KEY_FLOAT_WIDTH_PCT, floatWidthPct)
            .putInt(KEY_FLOAT_HEIGHT_PCT, floatHeightPct)
            .apply()
    }

    fun applyContainerLayout(container: View) {
        val sW = screenWidth()

        when {
            isFloating -> {
                val cardW = ((sW * floatWidthPct) / 100).coerceIn((sW * 0.50f).toInt(), (sW * 0.95f).toInt())
                val lp = FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = floatX.coerceIn(0, sW - cardW)
                    bottomMargin = floatY.coerceIn(dp(10f), screenHeight() - dp(200f))
                }
                container.layoutParams = lp
            }
            oneHandedMode != "off" -> {
                val width = (sW * 0.85f).toInt()
                val lp = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = if (oneHandedMode == "left") Gravity.START or Gravity.BOTTOM else Gravity.END or Gravity.BOTTOM
                }
                container.layoutParams = lp
            }
            else -> {
                val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                }
                container.layoutParams = lp
            }
        }
        container.requestLayout()
    }

    fun createCornerResizeOverlay(targetCard: View, onResizeUpdate: () -> Unit): View {
        return object : View(context) {
            private val cornerRadius = dp(14f).toFloat()
            private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = dp(3.2f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(Color.parseColor("#38BDF8"), 55)
                style = Paint.Style.STROKE
                strokeWidth = dp(6.5f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.FILL
            }
            private val beadCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            private val borderGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.setAlphaComponent(Color.parseColor("#38BDF8"), 60)
                style = Paint.Style.STROKE
                strokeWidth = dp(1f).toFloat()
                pathEffect = DashPathEffect(floatArrayOf(dp(4f).toFloat(), dp(4f).toFloat()), 0f)
            }

            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var initialW = 0
            private var initialH = 0
            private var activeCorner: Int = -1
            private val guideRect = RectF()
            private val pathTL = Path()
            private val pathTR = Path()
            private val pathBL = Path()
            private val pathBR = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val pad = dp(4f).toFloat()
                val arm = dp(20f).toFloat()
                val r = cornerRadius

                if (w <= pad * 2 || h <= pad * 2) return

                guideRect.set(pad, pad, w - pad, h - pad)
                canvas.drawRoundRect(guideRect, r, r, borderGuidePaint)

                pathTL.reset()
                pathTL.moveTo(pad, pad + r + arm)
                pathTL.lineTo(pad, pad + r)
                pathTL.arcTo(pad, pad, pad + 2 * r, pad + 2 * r, 180f, 90f, false)
                pathTL.lineTo(pad + r + arm, pad)

                pathTR.reset()
                pathTR.moveTo(w - pad - r - arm, pad)
                pathTR.lineTo(w - pad - r, pad)
                pathTR.arcTo(w - pad - 2 * r, pad, w - pad, pad + 2 * r, 270f, 90f, false)
                pathTR.lineTo(w - pad, pad + r + arm)

                pathBL.reset()
                pathBL.moveTo(pad + r + arm, h - pad)
                pathBL.lineTo(pad + r, h - pad)
                pathBL.arcTo(pad, h - pad - 2 * r, pad + 2 * r, h - pad, 90f, 90f, false)
                pathBL.lineTo(pad, h - pad - r - arm)

                pathBR.reset()
                pathBR.moveTo(w - pad, h - pad - r - arm)
                pathBR.lineTo(w - pad, h - pad - r)
                pathBR.arcTo(w - pad - 2 * r, h - pad - 2 * r, w - pad, h - pad, 0f, 90f, false)
                pathBR.lineTo(w - pad - r - arm, h - pad)

                canvas.drawPath(pathTL, glowPaint); canvas.drawPath(pathTL, bracketPaint)
                canvas.drawPath(pathTR, glowPaint); canvas.drawPath(pathTR, bracketPaint)
                canvas.drawPath(pathBL, glowPaint); canvas.drawPath(pathBL, bracketPaint)
                canvas.drawPath(pathBR, glowPaint); canvas.drawPath(pathBR, bracketPaint)

                val beadRadius = dp(4.2f).toFloat()
                val coreRadius = dp(1.8f).toFloat()
                canvas.drawCircle(pad, pad, beadRadius, beadPaint)
                canvas.drawCircle(pad, pad, coreRadius, beadCorePaint)
                canvas.drawCircle(w - pad, pad, beadRadius, beadPaint)
                canvas.drawCircle(w - pad, pad, coreRadius, beadCorePaint)
                canvas.drawCircle(w - pad, h - pad, beadRadius, beadPaint)
                canvas.drawCircle(w - pad, h - pad, coreRadius, beadCorePaint)
                canvas.drawCircle(pad, h - pad, beadRadius, beadPaint)
                canvas.drawCircle(pad, h - pad, coreRadius, beadCorePaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x
                val y = event.y
                val w = width
                val h = height
                val touchGrip = dp(36f)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialW = targetCard.width
                        initialH = floatHeightPct

                        activeCorner = when {
                            x < touchGrip && y < touchGrip -> 0
                            x > w - touchGrip && y < touchGrip -> 1
                            x > w - touchGrip && y > h - touchGrip -> 2
                            x < touchGrip && y > h - touchGrip -> 3
                            else -> -1
                        }
                        return activeCorner != -1
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activeCorner == -1) return false
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        val sW = screenWidth()

                        when (activeCorner) {
                            1 -> {
                                val newW = (initialW + dx.toInt()).coerceIn((sW * 0.50f).toInt(), (sW * 0.95f).toInt())
                                floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100).toInt()
                                val newH = (initialH - (dy / dp(4f))).toInt().coerceIn(65, 115)
                                floatHeightPct = newH
                            }
                            0 -> {
                                val newW = (initialW - dx.toInt()).coerceIn((sW * 0.50f).toInt(), (sW * 0.95f).toInt())
                                floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100).toInt()
                                floatX = (floatX + dx.toInt()).coerceAtLeast(0)
                                val newH = (initialH - (dy / dp(4f))).toInt().coerceIn(65, 115)
                                floatHeightPct = newH
                            }
                            2 -> {
                                val newW = (initialW + dx.toInt()).coerceIn((sW * 0.50f).toInt(), (sW * 0.95f).toInt())
                                floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100).toInt()
                                val newH = (initialH + (dy / dp(4f))).toInt().coerceIn(65, 115)
                                floatHeightPct = newH
                            }
                            3 -> {
                                val newW = (initialW - dx.toInt()).coerceIn((sW * 0.50f).toInt(), (sW * 0.95f).toInt())
                                floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100).toInt()
                                floatX = (floatX + dx.toInt()).coerceAtLeast(0)
                                val newH = (initialH + (dy / dp(4f))).toInt().coerceIn(65, 115)
                                floatHeightPct = newH
                            }
                        }
                        applyContainerLayout(targetCard)
                        onResizeUpdate()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeCorner = -1
                        save()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    fun createGnosisResizeOverlay(
        targetView: View,
        accentColor: Int,
        textColor: Int,
        surfaceColor: Int,
        onHeightChanged: (Int) -> Unit,
        onDismiss: (saved: Boolean) -> Unit
    ): View {
        val overlay = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val targetH = if (targetView.height > 0) targetView.height else targetView.measuredHeight
                val targetW = if (targetView.width > 0) targetView.width else targetView.measuredWidth
                val finalW = if (targetW > 0) targetW else MeasureSpec.getSize(widthMeasureSpec)
                val finalH = if (targetH > 0) targetH else dp(260f)
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(finalW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(finalH, MeasureSpec.EXACTLY)
                )
                setMeasuredDimension(finalW, finalH)
            }
        }.apply {
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val effectiveAccent = if (accentColor != 0) accentColor else Color.parseColor("#4285F4")
        val effectiveSurface = if (surfaceColor != 0) surfaceColor else Color.parseColor("#1B1F2A")
        val effectiveText = if (textColor != 0) textColor else Color.WHITE
        val isLight = ColorUtils.calculateLuminance(effectiveSurface) > 0.45

        val viewfinderFrame = object : View(context) {
            private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(1.2f).toFloat()
                color = ColorUtils.setAlphaComponent(effectiveAccent, 75)
            }
            private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(3.2f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = effectiveAccent
            }
            private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = ColorUtils.setAlphaComponent(effectiveSurface, 45)
            }
            private val boundsRect = RectF()
            private val pathTL = Path()
            private val pathTR = Path()
            private val pathBL = Path()
            private val pathBR = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return

                val m = dp(4f).toFloat()
                val r = dp(16f).toFloat()
                val bracketArm = dp(24f).toFloat()

                boundsRect.set(m, m, w - m, h - m)
                canvas.drawRoundRect(boundsRect, r, r, scrimPaint)

                canvas.drawRoundRect(boundsRect, r, r, framePaint)

                pathTL.reset()
                pathTL.moveTo(m, m + r + bracketArm)
                pathTL.lineTo(m, m + r)
                pathTL.arcTo(m, m, m + 2 * r, m + 2 * r, 180f, 90f, false)
                pathTL.lineTo(m + r + bracketArm, m)
                canvas.drawPath(pathTL, bracketPaint)

                pathTR.reset()
                pathTR.moveTo(w - m - r - bracketArm, m)
                pathTR.lineTo(w - m - r, m)
                pathTR.arcTo(w - m - 2 * r, m, w - m, m + 2 * r, 270f, 90f, false)
                pathTR.lineTo(w - m, m + r + bracketArm)
                canvas.drawPath(pathTR, bracketPaint)

                pathBL.reset()
                pathBL.moveTo(m + r + bracketArm, h - m)
                pathBL.lineTo(m + r, h - m)
                pathBL.arcTo(m, h - m - 2 * r, m + 2 * r, h - m, 90f, 90f, false)
                pathBL.lineTo(m, h - m - r - bracketArm)
                canvas.drawPath(pathBL, bracketPaint)

                pathBR.reset()
                pathBR.moveTo(w - m, h - m - r - bracketArm)
                pathBR.lineTo(w - m, h - m - r)
                pathBR.arcTo(w - m - 2 * r, h - m - 2 * r, w - m, h - m, 0f, 90f, false)
                pathBR.lineTo(w - m - r - bracketArm, h - m)
                canvas.drawPath(pathBR, bracketPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
        }
        overlay.addView(viewfinderFrame)

        val topHandle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(6f).toFloat()
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(effectiveSurface, 246))
                setStroke(dp(1.2f), ColorUtils.setAlphaComponent(effectiveAccent, 175))
                cornerRadius = dp(22f).toFloat()
            }
            val padH = dp(14f)
            val padV = dp(7f)
            setPadding(padH, padV, padH, padV)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(8f)
            }
        }

        val topGripIcon = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_grip_horizontal)?.mutate()
                ?: ContextCompat.getDrawable(context, R.drawable.ic_scaling)?.mutate()
            d?.setTint(effectiveAccent)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
                marginEnd = dp(8f)
            }
        }
        topHandle.addView(topGripIcon)

        val resizeLabel = TextView(context).apply {
            text = "HEIGHT"
            setTextColor(ColorUtils.setAlphaComponent(effectiveText, 170))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8f)
            }
        }
        topHandle.addView(resizeLabel)

        val badgeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(effectiveAccent, 40))
                cornerRadius = dp(10f).toFloat()
            }
            val bH = dp(7f)
            val bV = dp(2f)
            setPadding(bH, bV, bH, bV)
        }

        val heightLabel = TextView(context).apply {
            text = "${heightPercent}%"
            setTextColor(effectiveAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        badgeContainer.addView(heightLabel)
        topHandle.addView(badgeContainer)

        val presetValues = listOf(80, 100, 120)
        val presetViews = mutableListOf<TextView>()

        fun updatePresetHighlight(pct: Int) {
            presetViews.forEachIndexed { index, tv ->
                val pVal = presetValues[index]
                val isSelected = pct == pVal
                tv.background = GradientDrawable().apply {
                    cornerRadius = dp(12f).toFloat()
                    if (isSelected) {
                        setColor(effectiveAccent)
                    } else {
                        setColor(ColorUtils.setAlphaComponent(Color.WHITE, 18))
                    }
                }
                tv.setTextColor(if (isSelected) Color.WHITE else ColorUtils.setAlphaComponent(effectiveText, 180))
            }
        }

        var topTouchStartY = 0f
        var topStartHeight = heightPercent
        topHandle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    topTouchStartY = event.rawY
                    topStartHeight = heightPercent
                    (topHandle.background as? GradientDrawable)?.setStroke(dp(1.8f), effectiveAccent)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = topTouchStartY - event.rawY
                    val delta = (dy / dp(2.2f)).toInt()
                    val newPct = (topStartHeight + delta).coerceIn(70, 130)
                    if (newPct != heightPercent) {
                        heightPercent = newPct
                        heightLabel.text = "${newPct}%"
                        updatePresetHighlight(newPct)
                        onHeightChanged(newPct)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (topHandle.background as? GradientDrawable)?.setStroke(
                        dp(1.2f),
                        ColorUtils.setAlphaComponent(effectiveAccent, 175)
                    )
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
        overlay.addView(topHandle)

        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(8f).toFloat()
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(effectiveSurface, 248))
                setStroke(dp(1.2f), ColorUtils.setAlphaComponent(effectiveAccent, 85))
                cornerRadius = dp(28f).toFloat()
            }
            val padH = dp(10f)
            val padV = dp(5f)
            setPadding(padH, padV, padH, padV)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14f)
            }
        }

        fun createPillIconButton(iconRes: Int, desc: String, tintCol: Int, bgCol: Int, onClick: () -> Unit): View {
            return FrameLayout(context).apply {
                contentDescription = desc
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(bgCol)
                }
                val iv = ImageView(context).apply {
                    val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                    d?.setTint(tintCol)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val sz = dp(34f)
                val iconSz = dp(18f)
                addView(iv, FrameLayout.LayoutParams(iconSz, iconSz, Gravity.CENTER))
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                    val m = dp(3f)
                    setMargins(m, 0, m, 0)
                }
                setOnClickListener { onClick() }
            }
        }

        fun createDivider(): View {
            return View(context).apply {
                val divColor = if (isLight) ColorUtils.setAlphaComponent(Color.BLACK, 30) else ColorUtils.setAlphaComponent(Color.WHITE, 30)
                setBackgroundColor(divColor)
                layoutParams = LinearLayout.LayoutParams(dp(1f), dp(18f)).apply {
                    val m = dp(6f)
                    setMargins(m, 0, m, 0)
                }
            }
        }

        val resetBtn = createPillIconButton(
            iconRes = R.drawable.ic_rotate_ccw,
            desc = "Reset to Default",
            tintCol = ColorUtils.setAlphaComponent(effectiveText, 210),
            bgCol = if (isLight) ColorUtils.setAlphaComponent(Color.BLACK, 16) else ColorUtils.setAlphaComponent(Color.WHITE, 16)
        ) {
            heightPercent = 100
            heightLabel.text = "100%"
            updatePresetHighlight(100)
            onHeightChanged(100)
        }
        actionBar.addView(resetBtn)

        actionBar.addView(createDivider())

        for (pVal in presetValues) {
            val isSelected = heightPercent == pVal
            val chip = TextView(context).apply {
                text = "${pVal}%"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.DEFAULT_BOLD)
                gravity = Gravity.CENTER
                val padH = dp(8f)
                val padV = dp(4f)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f).toFloat()
                    if (isSelected) {
                        setColor(effectiveAccent)
                    } else {
                        setColor(if (isLight) ColorUtils.setAlphaComponent(Color.BLACK, 18) else ColorUtils.setAlphaComponent(Color.WHITE, 18))
                    }
                }
                setTextColor(if (isSelected) Color.WHITE else ColorUtils.setAlphaComponent(effectiveText, 180))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = dp(2f)
                    setMargins(m, 0, m, 0)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    heightPercent = pVal
                    heightLabel.text = "${pVal}%"
                    updatePresetHighlight(pVal)
                    onHeightChanged(pVal)
                }
            }
            presetViews.add(chip)
            actionBar.addView(chip)
        }

        actionBar.addView(createDivider())

        val cancelBtn = createPillIconButton(
            iconRes = R.drawable.ic_x,
            desc = "Cancel",
            tintCol = Color.parseColor("#EF4444"),
            bgCol = ColorUtils.setAlphaComponent(Color.parseColor("#EF4444"), 28)
        ) {
            onDismiss(false)
        }
        actionBar.addView(cancelBtn)

        val doneBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(17f).toFloat()
                setColor(effectiveAccent)
            }
            elevation = dp(2f).toFloat()
            val padH = dp(12f)
            val padV = dp(6f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34f)
            ).apply {
                marginStart = dp(4f)
            }
            isClickable = true
            isFocusable = true

            val checkIcon = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                d?.setTint(Color.WHITE)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(dp(15f), dp(15f)).apply {
                    marginEnd = dp(4f)
                }
            }
            addView(checkIcon)

            val doneText = TextView(context).apply {
                text = "Done"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(Typeface.DEFAULT_BOLD)
            }
            addView(doneText)

            setOnClickListener {
                save()
                onDismiss(true)
            }
        }
        actionBar.addView(doneBtn)

        overlay.addView(actionBar)
        return overlay
    }
}
