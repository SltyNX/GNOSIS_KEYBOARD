package gnosis.keyboard.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.text.TextUtils
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import gnosis.keyboard.MainActivity
import gnosis.keyboard.ModernToggleView
import gnosis.keyboard.R
import gnosis.keyboard.Skins
import gnosis.keyboard.ToolbarConfig

object ConceptComponents {

    fun dp(context: Context, v: Float): Int {
        return (v * context.resources.displayMetrics.density).toInt()
    }

    fun applyTactileFeedback(
        view: View,
        cornerRadiusDp: Float = 16f,
        pressScale: Float = 0.975f,
        tokens: ConceptThemeTokens? = null
    ) {
        val density = view.resources.displayMetrics.density
        view.isClickable = true
        view.isFocusable = false

        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(tokens?.accent ?: Color.WHITE, if (tokens?.isLight == true) 28 else 45)
        )
        val mask = GradientDrawable().apply {
            cornerRadius = cornerRadiusDp * density
            setColor(Color.WHITE)
        }
        view.foreground = RippleDrawable(rippleColor, null, mask)

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(pressScale).scaleY(pressScale).setDuration(80).setInterpolator(DecelerateInterpolator()).start()
                    try {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Exception) {}
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).setInterpolator(OvershootInterpolator(1.3f)).start()
                }
            }
            false
        }
    }

    fun createCard(context: Context, tokens: ConceptThemeTokens, density: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val radius = 14f * density
            val strokeW = (tokens.strokeWidthDp * density).toInt().coerceAtLeast(1)
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(tokens.surface)
                setStroke(strokeW, tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * density).toInt()
            }
        }
    }

    fun createSectionHeader(context: Context, title: String, tokens: ConceptThemeTokens, density: Float): TextView {
        return TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }
    }

    fun createDivider(context: Context, tokens: ConceptThemeTokens, density: Float): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (0.8f * density).toInt().coerceAtLeast(1)
            ).apply {
                marginStart = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
            }
            setBackgroundColor(tokens.subtleDivider)
        }
    }

    fun createSwitchRow(
        context: Context,
        title: String,
        subtitle: String?,
        prefKey: String,
        defaultValue: Boolean,
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int = 0,
        onToggleChanged: ((Boolean) -> Unit)? = null
    ): View {
        val p = Skins.prefs(context)
        val initialChecked = p.getBoolean(prefKey, defaultValue)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            isClickable = true
            isFocusable = false
        }

        if (iconRes != 0) {
            val iconContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply {
                    marginEnd = (12 * density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = (12 * density)
                    setColor(tokens.iconContainerBg)
                    setStroke((1 * density).toInt(), tokens.iconContainerStroke)
                }
            }
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt(), Gravity.CENTER)
            }
            iconContainer.addView(iv)
            row.addView(iconContainer)
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        textCol.addView(titleTv)

        if (!subtitle.isNullOrEmpty()) {
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            textCol.addView(subTv)
        }
        row.addView(textCol)

        val toggle = ModernToggleView(context, initialChecked, density) { isChecked ->
            p.edit().putBoolean(prefKey, isChecked).apply()
            onToggleChanged?.invoke(isChecked)
        }.apply {
            uiStyle = "md3"
            isLight = tokens.isLight
            accentColor = tokens.accent
            trackOffColor = tokens.chipBg
            strokeOffColor = tokens.cardBorder
            strokeWidthDp = tokens.strokeWidthDp
        }
        row.addView(toggle)

        row.setOnClickListener {
            toggle.performClick()
        }

        return row
    }

    fun createSwitchRow(
        context: Context,
        title: String,
        subtitle: String?,
        initialChecked: Boolean,
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int = 0,
        onToggleChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            isClickable = true
            isFocusable = false
        }

        if (iconRes != 0) {
            val iconContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply {
                    marginEnd = (14 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens.chipBg)
                }
            }
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = (9 * density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            iconContainer.addView(iv)
            row.addView(iconContainer)
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        textCol.addView(titleTv)

        if (!subtitle.isNullOrEmpty()) {
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            textCol.addView(subTv)
        }
        row.addView(textCol)

        val toggle = ModernToggleView(context, initialChecked, density) { isChecked ->
            onToggleChanged(isChecked)
        }.apply {
            uiStyle = "md3"
            isLight = tokens.isLight
            accentColor = tokens.accent
            trackOffColor = tokens.chipBg
            strokeOffColor = tokens.cardBorder
            strokeWidthDp = tokens.strokeWidthDp
        }
        row.addView(toggle)

        row.setOnClickListener {
            toggle.performClick()
        }

        return row
    }

    fun createNavRow(
        context: Context,
        title: String,
        subtitle: String?,
        rightValue: String? = null,
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int = 0,
        showChevron: Boolean = true,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        if (iconRes != 0) {
            val iconContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply {
                    marginEnd = (12 * density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = (12 * density)
                    setColor(tokens.iconContainerBg)
                    setStroke((1 * density).toInt(), tokens.iconContainerStroke)
                }
            }
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt(), Gravity.CENTER)
            }
            iconContainer.addView(iv)
            row.addView(iconContainer)
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        textCol.addView(titleTv)

        if (!subtitle.isNullOrEmpty()) {
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            textCol.addView(subTv)
        }
        row.addView(textCol)

        if (!rightValue.isNullOrEmpty()) {
            val rightTv = TextView(context).apply {
                text = rightValue
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(tokens.textSecondary)
                setPadding(0, 0, (4 * density).toInt(), 0)
            }
            row.addView(rightTv)
        }

        if (showChevron) {
            val chevron = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)?.mutate()
                d?.setTint(tokens.textSecondary)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt()).apply {
                    marginStart = (6 * density).toInt()
                }
            }
            row.addView(chevron)
        }

        applyTactileFeedback(row, cornerRadiusDp = 18f, pressScale = 0.985f, tokens = tokens)

        return row
    }

    fun createSegmentSelector(
        context: Context,
        options: List<String>,
        selectedIndex: Int,
        tokens: ConceptThemeTokens,
        density: Float,
        onSelect: (Int) -> Unit
    ): LinearLayout {
        var currentIndex = selectedIndex
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(tokens.chipBg)
                setStroke((1 * density).toInt(), tokens.chipBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (42 * density).toInt()
            ).apply {
                marginStart = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
                bottomMargin = (12 * density).toInt()
            }
        }

        val buttonViews = mutableListOf<TextView>()

        for (i in options.indices) {
            val btn = TextView(context).apply {
                text = options[i]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setTypeface(null, if (i == currentIndex) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = false
            }

            fun updateBtnStyle(target: TextView, isSel: Boolean) {
                target.setTypeface(null, Typeface.BOLD)
                if (isSel) {
                    target.setTextColor(if (tokens.isLight) Color.parseColor("#0F172A") else Color.WHITE)
                    target.background = GradientDrawable().apply {
                        cornerRadius = (8 * density)
                        setColor(tokens.surface)
                        setStroke((1 * density).toInt(), tokens.cardBorder)
                    }
                } else {
                    target.setTextColor(tokens.textSecondary)
                    target.background = GradientDrawable().apply {
                        cornerRadius = (8 * density)
                        setColor(Color.TRANSPARENT)
                        setStroke((1 * density).toInt(), Color.TRANSPARENT)
                    }
                }
            }

            updateBtnStyle(btn, i == currentIndex)

            btn.setOnClickListener {
                if (currentIndex != i) {
                    currentIndex = i
                    for (j in buttonViews.indices) {
                        updateBtnStyle(buttonViews[j], j == currentIndex)
                    }
                    onSelect(i)
                }
            }

            buttonViews.add(btn)
            container.addView(btn)
        }

        return container
    }

    fun createCallout(context: Context, text: String, iconRes: Int, tokens: ConceptThemeTokens, density: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (11 * density).toInt(), (14 * density).toInt(), (11 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(tokens.chipBg)
                setStroke((1 * density).toInt(), tokens.chipBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
                topMargin = (6 * density).toInt()
                bottomMargin = (14 * density).toInt()
            }

            if (iconRes != 0) {
                val iv = ImageView(context).apply {
                    val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                    d?.setTint(tokens.textSecondary)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt()).apply {
                        marginEnd = (10 * density).toInt()
                    }
                }
                addView(iv)
            }

            val tv = TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(tv)
        }
    }

    fun createPillButton(
        context: Context,
        text: String,
        tokens: ConceptThemeTokens,
        density: Float,
        isAccent: Boolean = true,
        filled: Boolean = isAccent,
        onClick: () -> Unit
    ): TextView {
        return createPillButton(context, text, isAccent && filled, tokens, density, onClick)
    }

    fun createPillButton(
        context: Context,
        text: String,
        isAccent: Boolean,
        tokens: ConceptThemeTokens,
        density: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val hPad = (20 * density).toInt()
            val vPad = (13 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            isClickable = true
            isFocusable = true

            if (isAccent) {
                setTextColor(tokens.onAccentText)
                background = GradientDrawable().apply {
                    cornerRadius = (12 * density)
                    setColor(tokens.accent)
                }
            } else {
                setTextColor(tokens.textPrimary)
                background = GradientDrawable().apply {
                    cornerRadius = (12 * density)
                    setColor(tokens.chipBg)
                    setStroke((1 * density).toInt(), tokens.cardBorder)
                }
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * density).toInt()
                bottomMargin = (14 * density).toInt()
            }

            applyTactileFeedback(this, cornerRadiusDp = 24f, pressScale = 0.965f, tokens = tokens)

            setOnClickListener { onClick() }
        }
    }

    class HeroCardAnimatedBackgroundView(
        context: Context,
        private val tokens: ConceptThemeTokens,
        private val density: Float
    ) : View(context) {

        private val clipPath = Path()
        private val cornerRadius = 24f * density
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        private val topHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }

        private var animator: ValueAnimator? = null
        private var animTime = 0f

        init {
            setWillNotDraw(false)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            clipPath.reset()
            clipPath.addRoundRect(
                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )

            val baseColors = if (tokens.isLight) {
                intArrayOf(
                    Color.parseColor("#F4F8FF"),
                    Color.parseColor("#E5EEFC"),
                    Color.parseColor("#D8E6FA")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#1C1C20"),
                    Color.parseColor("#131316"),
                    Color.parseColor("#09090B")
                )
            }
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                baseColors,
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )

            borderPaint.color = tokens.cardBorder
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.save()
            canvas.clipPath(clipPath)

            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val t = animTime * 2f * Math.PI.toFloat()

            val cx1 = w * 0.26f + (sin(t.toDouble()) * (w * 0.16f)).toFloat()
            val cy1 = h * 0.36f + (cos(t.toDouble()) * (h * 0.22f)).toFloat()
            val r1 = (w * 0.44f).coerceAtLeast(1f)
            val orb1Alpha = if (tokens.isLight) 38 else 52
            orbPaint.shader = RadialGradient(
                cx1, cy1, r1,
                intArrayOf(
                    ColorUtils.setAlphaComponent(tokens.accent, orb1Alpha),
                    ColorUtils.setAlphaComponent(tokens.accent, orb1Alpha / 3),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx1, cy1, r1, orbPaint)

            val compColor = if (tokens.isLight) Color.parseColor("#6366F1") else Color.parseColor("#818CF8")
            val cx2 = w * 0.74f - (cos(t.toDouble() * 0.85) * (w * 0.18f)).toFloat()
            val cy2 = h * 0.62f - (sin(t.toDouble() * 0.85) * (h * 0.24f)).toFloat()
            val r2 = (w * 0.46f).coerceAtLeast(1f)
            val orb2Alpha = if (tokens.isLight) 28 else 42
            orbPaint.shader = RadialGradient(
                cx2, cy2, r2,
                intArrayOf(
                    ColorUtils.setAlphaComponent(compColor, orb2Alpha),
                    ColorUtils.setAlphaComponent(compColor, orb2Alpha / 3),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx2, cy2, r2, orbPaint)

            val cx3 = w * 0.50f + (sin(t.toDouble() * 1.3) * (w * 0.12f)).toFloat()
            val cy3 = h * 0.50f + (cos(t.toDouble() * 1.3) * (h * 0.14f)).toFloat()
            val r3 = (w * 0.34f).coerceAtLeast(1f)
            val orb3Alpha = if (tokens.isLight) 22 else 32
            orbPaint.shader = RadialGradient(
                cx3, cy3, r3,
                intArrayOf(
                    ColorUtils.setAlphaComponent(tokens.accent, orb3Alpha),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx3, cy3, r3, orbPaint)

            val sheenCycle = animTime % 1f
            if (sheenCycle < 0.35f) {
                val sheenFrac = sheenCycle / 0.35f
                val sheenX = -w * 0.35f + sheenFrac * (w * 1.7f)
                val sheenWidth = w * 0.45f
                val sheenAlpha = if (tokens.isLight) 65 else 40

                sheenPaint.shader = LinearGradient(
                    sheenX - sheenWidth * 0.5f, 0f,
                    sheenX + sheenWidth * 0.5f, h,
                    intArrayOf(
                        Color.TRANSPARENT,
                        ColorUtils.setAlphaComponent(Color.WHITE, sheenAlpha),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, sheenPaint)
            }

            val topEdgeAlpha = if (tokens.isLight) 75 else 45
            topHighlightPaint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    ColorUtils.setAlphaComponent(Color.WHITE, topEdgeAlpha),
                    ColorUtils.setAlphaComponent(tokens.accent, topEdgeAlpha / 2),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            val strokeInset = borderPaint.strokeWidth * 0.5f
            val topRect = RectF(strokeInset, strokeInset, w - strokeInset, h - strokeInset)
            canvas.drawRoundRect(topRect, cornerRadius, cornerRadius, topHighlightPaint)

            canvas.drawRoundRect(topRect, cornerRadius, cornerRadius, borderPaint)

            canvas.restore()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startAnimation()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == VISIBLE) {
                startAnimation()
            } else {
                stopAnimation()
            }
        }

        private fun startAnimation() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 6200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    animTime = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopAnimation() {
            animator?.cancel()
            animator = null
        }
    }

    fun createHeroBrandCard(
        context: Context,
        title: String,
        subtitle: String = "",
        tokens: ConceptThemeTokens,
        density: Float,
        description: String = subtitle,
        iconRes: Int = 0
    ): View {
        val root = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (154 * density).toInt()
            ).apply {
                bottomMargin = (18 * density).toInt()
            }
        }

        val animatedBg = HeroCardAnimatedBackgroundView(context, tokens, density).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(animatedBg)

        val targetAlpha = if (tokens.isLight) 0.18f else 0.10f
        val mountainIv = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_mountain_landscape)?.mutate()
            d?.setTint(if (tokens.isLight) Color.parseColor("#1D4ED8") else Color.parseColor("#60A5FA"))
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_END
            alpha = 0f
            translationY = 16f * density
            scaleX = 1.04f
            scaleY = 1.04f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
            )
        }
        root.addView(mountainIv)

        val centerCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ).apply {
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            }
        }

        val iconBadge = FrameLayout(context).apply {
            val s = (46 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                bottomMargin = (8 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = (14 * density)
                setColor(ColorUtils.setAlphaComponent(tokens.cardBorder, if (tokens.isLight) 25 else 45))
                setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 80 else 100))
            }
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
        }

        val logoIv = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, if (iconRes != 0) iconRes else R.drawable.ic_gnosis_logo)?.mutate()
            if (iconRes != 0) d?.setTint(tokens.accent)
            setImageDrawable(d)
            val iconSize = (24 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        iconBadge.addView(logoIv)
        centerCol.addView(iconBadge)

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = 8f * density
        }
        centerCol.addView(titleTv)

        val descText = if (description.isNotEmpty()) description else subtitle
        var subTvView: TextView? = null
        if (descText.isNotEmpty()) {
            val subTv = TextView(context).apply {
                text = descText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, (2 * density).toInt(), 0, 0)
                maxLines = 3
                alpha = 0f
                translationY = 6f * density
            }
            centerCol.addView(subTv)
            subTvView = subTv
        }

        root.addView(centerCol)

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                root.alpha = 0f
                root.translationY = 16f * density
                root.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                iconBadge.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(480)
                    .setStartDelay(90)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .withEndAction {
                        try {
                            val floatAnim = ObjectAnimator.ofFloat(iconBadge, View.TRANSLATION_Y, 0f, -3.5f * density, 0f).apply {
                                duration = 3200L
                                repeatCount = ValueAnimator.INFINITE
                                repeatMode = ValueAnimator.REVERSE
                                interpolator = AccelerateDecelerateInterpolator()
                            }
                            floatAnim.start()
                        } catch (_: Throwable) {}
                    }
                    .start()

                titleTv.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(380)
                    .setStartDelay(130)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                subTvView?.animate()
                    ?.alpha(1f)
                    ?.translationY(0f)
                    ?.setDuration(380)
                    ?.setStartDelay(180)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.start()

                mountainIv.alpha = 0f
                mountainIv.translationY = 14f * density
                mountainIv.animate()
                    .alpha(targetAlpha)
                    .translationY(0f)
                    .setDuration(550)
                    .setStartDelay(50)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        try {
                            val floatAnim = ObjectAnimator.ofFloat(mountainIv, View.TRANSLATION_Y, 0f, -4f * density, 0f).apply {
                                duration = 4500L
                                repeatCount = ValueAnimator.INFINITE
                                repeatMode = ValueAnimator.REVERSE
                                interpolator = AccelerateDecelerateInterpolator()
                            }
                            floatAnim.start()
                        } catch (_: Throwable) {}
                    }
                    .start()
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })

        return root
    }

    fun createInteractiveKeyboardPreviewIsland(
        context: Context,
        tokens: ConceptThemeTokens,
        density: Float,
        isLightPreview: Boolean,
        onToggleLight: (Boolean) -> Unit
    ): View {
        var currentIsLight = isLightPreview
        val island = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
        }

        val typingDisplay = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (12 * density).toInt()
            val padV = (7 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        val displayText = TextView(context).apply {
            text = "Tap keys below to test..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.START
        }
        typingDisplay.addView(displayText)

        val clearDisplayBtn = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_rotate_ccw)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            val s = (15 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            visibility = View.GONE
            setOnClickListener {
                displayText.text = "Tap keys below to test..."
                displayText.setTextColor(tokens.textSecondary)
                visibility = View.GONE
            }
        }
        typingDisplay.addView(clearDisplayBtn)

        fun updateDisplayBg() {
            typingDisplay.background = GradientDrawable().apply {
                cornerRadius = (12 * density)
                setColor(if (currentIsLight) Color.parseColor("#EEF2F6") else Color.parseColor("#151A24"))
                setStroke((1 * density).toInt(), if (currentIsLight) Color.parseColor("#CBD5E1") else Color.parseColor("#222B3B"))
            }
        }

        fun updateIslandBg() {
            island.background = GradientDrawable().apply {
                cornerRadius = (20 * density)
                setColor(if (currentIsLight) Color.parseColor("#F8FAFC") else Color.parseColor("#0B0E14"))
                setStroke((1 * density).toInt(), if (currentIsLight) Color.parseColor("#CBD5E1") else Color.parseColor("#222B3B"))
            }
            updateDisplayBg()
        }
        updateIslandBg()

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        val badge = TextView(context).apply {
            text = "Default"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.accent)
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(ColorUtils.setAlphaComponent(tokens.accent, 25))
            }
        }
        topRow.addView(badge)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        topRow.addView(spacer)

        val modeToggle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = (12 * density)
                setColor(if (currentIsLight) Color.parseColor("#E2E8F0") else Color.parseColor("#1E2533"))
            }
        }

        val lightTab = TextView(context).apply {
            text = "Light"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            isClickable = true
        }
        val darkTab = TextView(context).apply {
            text = "Dark"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            isClickable = true
        }

        fun updateTabs() {
            lightTab.setTextColor(if (currentIsLight) (if (tokens.isLight) Color.parseColor("#0F172A") else Color.WHITE) else tokens.textSecondary)
            lightTab.setTypeface(null, if (currentIsLight) Typeface.BOLD else Typeface.NORMAL)
            lightTab.background = if (currentIsLight) GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(tokens.surface)
            } else null

            darkTab.setTextColor(if (!currentIsLight) (if (tokens.isLight) Color.parseColor("#0F172A") else Color.WHITE) else tokens.textSecondary)
            darkTab.setTypeface(null, if (!currentIsLight) Typeface.BOLD else Typeface.NORMAL)
            darkTab.background = if (!currentIsLight) GradientDrawable().apply {
                cornerRadius = (10 * density)
                setColor(tokens.surface)
            } else null
        }
        updateTabs()

        lightTab.setOnClickListener {
            if (!currentIsLight) {
                currentIsLight = true
                updateTabs()
                updateIslandBg()
                onToggleLight(true)
            }
        }
        darkTab.setOnClickListener {
            if (currentIsLight) {
                currentIsLight = false
                updateTabs()
                updateIslandBg()
                onToggleLight(false)
            }
        }

        modeToggle.addView(lightTab)
        modeToggle.addView(darkTab)
        topRow.addView(modeToggle)
        island.addView(topRow)
        island.addView(typingDisplay)

        val keyboardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val rows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("?123", "🌐", "English", "↵")
        )

        for (rowKeys in rows) {
            val keyRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (34 * density).toInt()).apply {
                    bottomMargin = (4 * density).toInt()
                }
            }

            for (k in rowKeys) {
                val isAction = k == "↵"
                val isSpace = k == "English"
                val isSpecial = k == "⇧" || k == "⌫" || k == "?123" || k == "🌐"

                val weight = when {
                    isSpace -> 4.5f
                    isAction -> 1.8f
                    isSpecial -> 1.4f
                    else -> 1.0f
                }

                val keyView = TextView(context).apply {
                    text = k
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isSpace) 11f else 13f)
                    gravity = Gravity.CENTER
                    setTypeface(null, if (isAction || isSpace) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                        marginStart = (2 * density).toInt()
                        marginEnd = (2 * density).toInt()
                    }

                    val keyBgColor = when {
                        isAction -> tokens.accent
                        isSpecial -> if (currentIsLight) Color.parseColor("#E2E8F0") else Color.parseColor("#1B2230")
                        else -> if (currentIsLight) Color.WHITE else Color.parseColor("#151A24")
                    }

                    val keyTextColor = when {
                        isAction -> tokens.onAccentText
                        else -> if (currentIsLight) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
                    }

                    setTextColor(keyTextColor)
                    background = GradientDrawable().apply {
                        cornerRadius = (7 * density)
                        setColor(keyBgColor)
                        if (!isAction) {
                            setStroke((1 * density).toInt(), if (currentIsLight) Color.parseColor("#E2E8F0") else Color.parseColor("#222B3B"))
                        }
                    }
                    isClickable = true
                    setOnClickListener { v ->
                        try {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60).withEndAction {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                        }.start()
                        if (context is MainActivity) {
                            context.playSoundPreview()
                        }
                        val currentStr = if (displayText.text == "Tap keys below to test...") "" else displayText.text.toString()
                        when (k) {
                            "⌫" -> {
                                val newStr = if (currentStr.isNotEmpty()) currentStr.dropLast(1) else ""
                                if (newStr.isEmpty()) {
                                    displayText.text = "Tap keys below to test..."
                                    displayText.setTextColor(tokens.textSecondary)
                                    clearDisplayBtn.visibility = View.GONE
                                } else {
                                    displayText.text = newStr
                                    displayText.setTextColor(tokens.textPrimary)
                                    clearDisplayBtn.visibility = View.VISIBLE
                                }
                            }
                            "↵" -> {
                                typingDisplay.animate().scaleX(1.03f).scaleY(1.03f).setDuration(70).withEndAction {
                                    typingDisplay.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
                                }.start()
                            }
                            "English" -> {
                                displayText.text = "$currentStr "
                                displayText.setTextColor(tokens.textPrimary)
                                clearDisplayBtn.visibility = View.VISIBLE
                            }
                            "⇧", "?123", "🌐" -> {}
                            else -> {
                                val nextStr = (currentStr + k).takeLast(32)
                                displayText.text = nextStr
                                displayText.setTextColor(tokens.textPrimary)
                                clearDisplayBtn.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                keyRow.addView(keyView)
            }
            keyboardContainer.addView(keyRow)
        }

        island.addView(keyboardContainer)
        return island
    }

    fun createAccentPaletteRow(
        context: Context,
        tokens: ConceptThemeTokens,
        density: Float,
        onAccentSelected: (Int) -> Unit
    ): View {
        val hScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        var activeAccent = tokens.accent
        val swatchViews = mutableListOf<Pair<Int, FrameLayout>>()

        for (c in ConceptDesignTokens.ACCENT_PALETTE) {
            val isSelected = (c == activeAccent)
            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply {
                    marginStart = (6 * density).toInt()
                    marginEnd = (6 * density).toInt()
                }
            }

            fun updateSwatchState(fl: FrameLayout, sel: Boolean) {
                fl.removeAllViews()
                if (sel) {
                    val outer = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt(), Gravity.CENTER)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.TRANSPARENT)
                            setStroke((2 * density).toInt(), c)
                        }
                    }
                    fl.addView(outer)
                }

                val innerSize = if (sel) (28 * density).toInt() else (32 * density).toInt()
                val inner = View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                    }
                }
                fl.addView(inner)

                if (sel) {
                    val check = ImageView(context).apply {
                        val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                        d?.setTint(Color.WHITE)
                        setImageDrawable(d)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        layoutParams = FrameLayout.LayoutParams((14 * density).toInt(), (14 * density).toInt(), Gravity.CENTER)
                    }
                    fl.addView(check)
                }
            }

            updateSwatchState(container, isSelected)

            container.setOnClickListener {
                if (activeAccent != c) {
                    activeAccent = c
                    for ((color, fl) in swatchViews) {
                        updateSwatchState(fl, color == activeAccent)
                    }
                    onAccentSelected(c)
                }
            }

            swatchViews.add(c to container)
            row.addView(container)
        }

        hScroll.addView(row)
        return hScroll
    }

    class FloatingBottomNavView(
        context: Context,
        val tokens: ConceptThemeTokens,
        val density: Float,
        val onTabSelect: (Int) -> Unit
    ) : FrameLayout(context) {

        private val pillWidth = (178 * density).toInt()
        private val pillHeight = (48 * density).toInt()
        private val tabPad = (4 * density).toInt()
        private val indicatorView: View
        private val tabViews = mutableListOf<LinearLayout>()
        private val iconViews = mutableListOf<ImageView>()
        private val labelViews = mutableListOf<TextView>()
        private var currentSelectedTab = 0

        init {
            layoutParams = LayoutParams(
                pillWidth,
                pillHeight,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = (18 * density).toInt()
            }

            background = GradientDrawable().apply {
                cornerRadius = (26 * density)
                setColor(tokens.surface)
                setStroke((1 * density).toInt(), tokens.cardBorder)
            }
            elevation = 12 * density

            val innerW = pillWidth - tabPad * 2
            val tabW = innerW / 2
            val indH = pillHeight - tabPad * 2

            indicatorView = View(context).apply {
                layoutParams = LayoutParams(tabW, indH).apply {
                    topMargin = tabPad
                    marginStart = tabPad
                }
                background = GradientDrawable().apply {
                    cornerRadius = (22 * density)
                    setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                    setStroke((0.8f * density).toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(tokens.accent, 90))
                }
            }
            addView(indicatorView)

            val tabsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    setPadding(tabPad, tabPad, tabPad, tabPad)
                }
            }

            data class TabItem(val title: String, val iconRes: Int)
            val tabs = listOf(
                TabItem("Home", R.drawable.ic_home),
                TabItem("Stats", R.drawable.ic_bar_chart)
            )

            for (i in tabs.indices) {
                val tabView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    isClickable = true
                    isFocusable = true
                }

                val iconIv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams((17 * density).toInt(), (17 * density).toInt()).apply {
                        marginEnd = (5 * density).toInt()
                    }
                }
                tabView.addView(iconIv)

                val labelTv = TextView(context).apply {
                    text = tabs[i].title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    isSingleLine = true
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                tabView.addView(labelTv)

                tabView.setOnClickListener {
                    setSelectedTab(i, animateBounce = true)
                    onTabSelect(i)
                }

                tabViews.add(tabView)
                iconViews.add(iconIv)
                labelViews.add(labelTv)
                tabsContainer.addView(tabView)
            }

            addView(tabsContainer)
            updateTabAppearance(0)
        }

        fun setProgress(progress: Float) {
            val clamped = progress.coerceIn(0f, 1f)
            val innerW = pillWidth - tabPad * 2
            val tabW = innerW / 2
            indicatorView.translationX = clamped * tabW

            for (i in tabViews.indices) {
                val weight = if (i == 0) (1f - clamped) else clamped
                val tint = ColorUtils.blendARGB(tokens.textSecondary, tokens.accent, weight)
                val iconRes = if (i == 0) R.drawable.ic_home else R.drawable.ic_bar_chart
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tint)
                iconViews[i].setImageDrawable(d)
                labelViews[i].setTextColor(tint)
                labelViews[i].setTypeface(null, if (weight > 0.5f) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        fun setSelectedTab(tab: Int, animateBounce: Boolean = true) {
            currentSelectedTab = tab
            val innerW = pillWidth - tabPad * 2
            val tabW = innerW / 2
            val targetX = if (tab == 0) 0f else tabW.toFloat()

            indicatorView.animate()
                .translationX(targetX)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()

            updateTabAppearance(tab)

            if (animateBounce && tab in iconViews.indices) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {}
                val iv = iconViews[tab]
                iv.animate().scaleX(0.80f).scaleY(0.80f).setDuration(60).withEndAction {
                    iv.animate().scaleX(1.18f).scaleY(1.18f).setDuration(110).withEndAction {
                        iv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                    }.start()
                }.start()
            }
        }

        private fun updateTabAppearance(activeTab: Int) {
            for (i in tabViews.indices) {
                val isSel = (i == activeTab)
                val tint = if (isSel) tokens.accent else tokens.textSecondary
                val iconRes = if (i == 0) R.drawable.ic_home else R.drawable.ic_bar_chart
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tint)
                iconViews[i].setImageDrawable(d)
                labelViews[i].setTextColor(tint)
                labelViews[i].setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    fun createFloatingBottomNav(
        context: Context,
        currentTab: Int,
        tokens: ConceptThemeTokens,
        density: Float,
        onTabSelect: (Int) -> Unit
    ): FloatingBottomNavView {
        val nav = FloatingBottomNavView(context, tokens, density, onTabSelect)
        nav.setSelectedTab(currentTab, animateBounce = false)
        return nav
    }

    fun createSliderRow(
        context: Context,
        title: String,
        subtitle: String? = null,
        min: Int,
        max: Int,
        current: Int,
        suffix: String = "",
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int? = null,
        step: Int = 1,
        defaultValue: Int = current,
        valueFormatter: ((Int) -> String)? = null,
        onReset: (() -> Unit)? = null,
        onLiveChange: (Int) -> Unit
    ): LinearLayout {
        fun dp(v: Float) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

        if (iconRes != null) {
            val iconBox = FrameLayout(context).apply {
                val s = dp(32f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(10f) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens.chipBg)
                    setStroke(dp(1f), tokens.cardBorder)
                }
                val iv = ImageView(context).apply {
                    val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(7f)
                    setPadding(pad, pad, pad, pad)
                }
                addView(iv)
            }
            topRow.addView(iconBox)
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        titleCol.addView(titleTv)

        if (subtitle != null) {
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(1f), 0, 0)
            }
            titleCol.addView(subTv)
        }
        topRow.addView(titleCol)

        val valueBadge = TextView(context).apply {
            text = valueFormatter?.invoke(current) ?: "$current$suffix"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6f)
            }
        }
        topRow.addView(valueBadge)

        val chevron = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
        }
        topRow.addView(chevron)
        container.addView(topRow)

        val drawer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                if (tokens.isLight) {
                    setColor(Color.argb(180, 241, 245, 249))
                    setStroke(dp(1f), Color.argb(120, 203, 213, 225))
                } else {
                    setColor(Color.argb(200, 18, 22, 28))
                    setStroke(dp(1f), Color.argb(120, 37, 46, 62))
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6f)
            }
        }

        var isExpanded = false
        topRow.setOnClickListener {
            isExpanded = !isExpanded
            drawer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            val chevronRes = if (isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            val d = ContextCompat.getDrawable(context, chevronRes)?.mutate()
            d?.setTint(tokens.textSecondary)
            chevron.setImageDrawable(d)
        }

        val liveChangeHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingSliderVal: Int? = null
        val liveChangeRunnable = Runnable {
            pendingSliderVal?.let { onLiveChange(it) }
            pendingSliderVal = null
        }

        val slider = HyperGlideSliderView(context).apply {
            this.min = min
            this.max = max
            this.step = step
            this.value = current
            this.accentColor = tokens.accent
            this.isLightMode = tokens.isLight
            this.onValueChange = { v ->
                valueBadge.text = valueFormatter?.invoke(v) ?: "$v$suffix"
                pendingSliderVal = v
                liveChangeHandler.removeCallbacks(liveChangeRunnable)
                liveChangeHandler.postDelayed(liveChangeRunnable, 60L)
            }
            this.onValueChangeFinished = { v ->
                liveChangeHandler.removeCallbacks(liveChangeRunnable)
                pendingSliderVal = null
                valueBadge.text = valueFormatter?.invoke(v) ?: "$v$suffix"
                onLiveChange(v)
            }
        }
        drawer.addView(slider)

        val resetBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(28f)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6f)
                bottomMargin = dp(2f)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            isClickable = true
            isFocusable = true
        }

        val resetIcon = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_rotate_ccw)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(13f), dp(13f)).apply { marginEnd = dp(6f) }
        }
        resetBtn.addView(resetIcon)

        val resetTv = TextView(context).apply {
            text = "Reset to Default"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
        }
        resetBtn.addView(resetTv)

        resetBtn.setOnClickListener {
            liveChangeHandler.removeCallbacks(liveChangeRunnable)
            pendingSliderVal = null
            if (onReset != null) {
                onReset()
            } else {
                slider.value = defaultValue
                valueBadge.text = valueFormatter?.invoke(defaultValue) ?: "$defaultValue$suffix"
                onLiveChange(defaultValue)
            }
            resetIcon.animate().rotationBy(360f).setDuration(300).start()
        }
        drawer.addView(resetBtn)

        container.addView(drawer)
        return container
    }

    fun createFloatSliderRow(
        context: Context,
        title: String,
        subtitle: String? = null,
        min: Float,
        max: Float,
        current: Float,
        suffix: String = "",
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int? = null,
        step: Float = 0.05f,
        defaultValue: Float = current,
        onLiveChange: (Float) -> Unit
    ): LinearLayout {
        val steps = ((max - min) / step).toInt().coerceAtLeast(1)
        val curStep = (((current - min) / step).toInt()).coerceIn(0, steps)
        val defStep = (((defaultValue - min) / step).toInt()).coerceIn(0, steps)

        return createSliderRow(
            context = context,
            title = title,
            subtitle = subtitle,
            min = 0,
            max = steps,
            current = curStep,
            suffix = suffix,
            tokens = tokens,
            density = density,
            iconRes = iconRes,
            step = 1,
            defaultValue = defStep,
            valueFormatter = { s ->
                val f = min + s * step
                if (suffix == "%") {
                    "${(f * 100).toInt()}%"
                } else if (step >= 1f) {
                    "${f.toInt()}$suffix"
                } else {
                    String.format(java.util.Locale.US, "%.2f$suffix", f)
                }
            },
            onLiveChange = { s ->
                val f = min + s * step
                onLiveChange(f)
            }
        )
    }

    fun createChipsRow(
        context: Context,
        title: String,
        options: List<Pair<String, String>>,
        selectedId: String,
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int? = null,
        onSelect: (String) -> Unit
    ): LinearLayout {
        fun dp(v: Float) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8f))
        }

        if (iconRes != null) {
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tokens.textPrimary)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) }
            }
            topRow.addView(iv)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        topRow.addView(titleTv)
        container.addView(topRow)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipViews = mutableListOf<TextView>()
        var activeId = selectedId
        val hPad = dp(12f)
        val vPad = dp(7f)
        val rad = dp(8f).toFloat()
        val strokeW = dp(1f)

        fun applyChipStyle(target: TextView, isSel: Boolean) {
            target.setTextColor(if (isSel) tokens.onAccentText else tokens.textSecondary)
            target.setTypeface(null, Typeface.BOLD)
            target.background = GradientDrawable().apply {
                cornerRadius = rad
                if (isSel) {
                    setColor(tokens.accent)
                    setStroke(strokeW, tokens.accent)
                } else {
                    setColor(tokens.chipBg)
                    setStroke(strokeW, tokens.cardBorder)
                }
            }
            target.setPadding(hPad, vPad, hPad, vPad)
        }

        for (opt in options) {
            val chip = TextView(context).apply {
                text = opt.second
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6f)
                }
            }

            applyChipStyle(chip, opt.first == activeId)

            chip.setOnClickListener {
                activeId = opt.first
                options.forEachIndexed { idx, o ->
                    if (idx in chipViews.indices) {
                        applyChipStyle(chipViews[idx], o.first == activeId)
                    }
                }
                onSelect(opt.first)
            }

            chipViews.add(chip)
            chipsRow.addView(chip)
        }

        scroll.addView(chipsRow)
        container.addView(scroll)
        return container
    }

    fun createLivePreviewField(
        context: Context,
        tokens: ConceptThemeTokens,
        density: Float,
        hintText: String = "Test typing here to see live changes\u2026"
    ): LinearLayout {
        fun dp(v: Float) = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6f))
        }

        val iv = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_eye)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(6f) }
        }
        row.addView(iv)

        val label = TextView(context).apply {
            text = "LIVE KEYBOARD TEST"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.accent)
            letterSpacing = 0.08f
        }
        row.addView(label)
        root.addView(row)

        val input = EditText(context).apply {
            hint = hintText
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 1
            privateImeOptions = ToolbarConfig.IME_PREVIEW
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        root.addView(input)

        return root
    }

    fun createCardHeader(
        context: Context,
        title: String,
        tokens: ConceptThemeTokens,
        density: Float,
        iconRes: Int? = null
    ): View {
        fun dp(v: Float) = (v * density).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(8f))
        }

        if (iconRes != null && iconRes != 0) {
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
                    marginEnd = dp(8f)
                }
            }
            row.addView(iv)
        }

        val tv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
            letterSpacing = 0.06f
        }
        row.addView(tv)

        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                row.alpha = 0f
                row.translationX = -8f * density
                row.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(350)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        return row
    }

    fun createMountainBanner(context: Context, tokens: ConceptThemeTokens, density: Float): View {
        fun dp(v: Float) = (v * density).toInt()
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80f))
            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_mountain_landscape)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = if (tokens.isLight) 0.25f else 0.15f
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            addView(iv)
        }
    }

    fun createActionRow(
        context: Context,
        title: String,
        subtitle: String?,
        iconRes: Int,
        isDestructive: Boolean,
        tokens: ConceptThemeTokens,
        density: Float,
        onClick: () -> Unit
    ): View {
        fun dp(v: Float) = (v * density).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { onClick() }
        }

        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f)).apply {
                marginEnd = dp(14f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val iconBg = if (isDestructive) {
                    ColorUtils.setAlphaComponent(Color.parseColor("#F43F5E"), if (tokens.isLight) 30 else 50)
                } else {
                    tokens.chipBg
                }
                setColor(iconBg)
            }
        }

        val iv = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
            val iconTint = if (isDestructive) Color.parseColor("#F43F5E") else tokens.accent
            d?.setTint(iconTint)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(9f)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        iconContainer.addView(iv)
        row.addView(iconContainer)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isDestructive) Color.parseColor("#F43F5E") else tokens.textPrimary)
        }
        textCol.addView(titleTv)

        if (!subtitle.isNullOrEmpty()) {
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(2f), 0, 0)
            }
            textCol.addView(subTv)
        }
        row.addView(textCol)

        val arrow = ImageView(context).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f))
        }
        row.addView(arrow)

        applyTactileFeedback(row, cornerRadiusDp = 18f, pressScale = 0.985f, tokens = tokens)

        return row
    }
}
