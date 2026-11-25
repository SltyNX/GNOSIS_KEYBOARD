package gnosis.keyboard.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import gnosis.keyboard.ClipboardHistory
import gnosis.keyboard.CustomBackground
import gnosis.keyboard.FontExtensionManager
import gnosis.keyboard.DictionaryExtensionManager
import gnosis.keyboard.SuggestionExtensionManager
import gnosis.keyboard.HinglishDictionaryManager
import gnosis.keyboard.LayoutExtensionManager
import gnosis.keyboard.MainActivity
import gnosis.keyboard.ModernToggleView
import gnosis.keyboard.R
import gnosis.keyboard.Skins
import gnosis.keyboard.ToolbarConfig
import gnosis.keyboard.WordDictionary
import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.EngineFactory
import gnosis.keyboard.stats.StatsPeriod
import gnosis.keyboard.stats.TypingStatisticsRepository
import java.io.File
import java.util.Locale

class ConceptScreens(
    private val activity: MainActivity,
    private val tokens: ConceptThemeTokens,
    private val density: Float,
    private val statsRepo: TypingStatisticsRepository,
    private val navigateTo: (View, String) -> Unit,
    private val navigateBack: () -> Boolean,
    private val navigateToRoot: () -> Unit,
    private val onThemeChanged: () -> Unit
) {

    private fun dp(v: Float) = ConceptComponents.dp(activity, v)

    fun buildHomeScreen(
        onOpenTyping: () -> Unit,
        onOpenAppearance: () -> Unit,
        onOpenLanguage: () -> Unit,
        onOpenPersonalization: () -> Unit,
        onOpenAdvanced: () -> Unit,
        onOpenQuickSettings: () -> Unit
    ): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(90f))
        }

        val profileRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16f)
            }
        }

        val avatarBox = FrameLayout(activity).apply {
            val s = dp(42f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = dp(12f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.accent)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Change profile photo"
        }

        val avatarFile = File(activity.filesDir, "user_avatar.jpg")
        var hasCustomAvatar = false
        if (avatarFile.exists() && avatarFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(avatarFile.absolutePath)
                if (bmp != null) {
                    val avatarIv = ImageView(activity).apply {
                        val rounded = RoundedBitmapDrawableFactory.create(activity.resources, bmp).apply {
                            isCircular = true
                        }
                        setImageDrawable(rounded)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                    avatarBox.addView(avatarIv)
                    hasCustomAvatar = true
                }
            } catch (_: Exception) {}
        }

        if (!hasCustomAvatar) {
            val defaultTv = TextView(activity).apply {
                text = "G"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.onAccentText)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            avatarBox.addView(defaultTv)
        }

        val editBadge = FrameLayout(activity).apply {
            val bs = dp(16f)
            layoutParams = FrameLayout.LayoutParams(bs, bs, Gravity.BOTTOM or Gravity.END)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            val editIv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_pen_line)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = dp(3f)
                setPadding(p, p, p, p)
            }
            addView(editIv)
        }
        avatarBox.addView(editBadge)
        ConceptComponents.applyTactileFeedback(avatarBox, cornerRadiusDp = 21f, pressScale = 0.93f, tokens = tokens)
        avatarBox.setOnClickListener {
            activity.pickProfilePhoto.launch("image/*")
        }
        profileRow.addView(avatarBox)

        val greetingCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val greetingTv = TextView(activity).apply {
            text = "Welcome to\nGnosis Keyboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setLineSpacing(dp(2f).toFloat(), 1.0f)
        }
        greetingCol.addView(greetingTv)
        profileRow.addView(greetingCol)

        root.addView(profileRow)

        val isEnabled = activity.isKeyboardEnabled()
        val isSelected = activity.isKeyboardSelected()
        if (!isEnabled || !isSelected) {
            val setupCard = ConceptComponents.createCard(activity, tokens, density).apply {
                val pad = dp(16f)
                setPadding(pad, pad, pad, pad)
            }
            val heroLandscape = ConceptComponents.createMountainBanner(activity, tokens, density)
            setupCard.addView(heroLandscape)

            val statusRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12f), 0, dp(6f))
            }
            val statusDot = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { marginEnd = dp(8f) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F59E0B"))
                }
            }
            statusRow.addView(statusDot)
            val statusTv = TextView(activity).apply {
                text = if (!isEnabled) "Setup Required \u2022 Keyboard Disabled" else "Setup Required \u2022 Not Selected"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
            }
            statusRow.addView(statusTv)
            setupCard.addView(statusRow)

            val setupBtn = ConceptComponents.createPillButton(
                context = activity,
                text = if (!isEnabled) "Enable in System Settings" else "Switch to Gnosis Keyboard",
                tokens = tokens,
                density = density,
                filled = true
            ) {
                if (!isEnabled) {
                    activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } else {
                    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            }
            setupCard.addView(setupBtn)
            root.addView(setupCard)
        }

        data class CategorySpec(val title: String, val subtitle: String, val iconRes: Int, val action: () -> Unit)
        val categories = listOf(
            CategorySpec("Typing & Suggestions", "Smart typing, multi-engine, mechanics & gestures", R.drawable.ic_keyboard, onOpenTyping),
            CategorySpec("Appearance", "Design studio, themes, key shapes, fonts & effects", R.drawable.ic_palette, onOpenAppearance),
            CategorySpec("Language & Dictionary", "Languages, layouts, Hinglish & quick keys", R.drawable.ic_globe, onOpenLanguage),
            CategorySpec("Personalization", "Shortcuts, learned data & custom words", R.drawable.ic_sparkles, onOpenPersonalization),
            CategorySpec("Advanced Settings", "Sound, haptics, voice, clipboard & system modes", R.drawable.ic_settings, onOpenAdvanced)
        )

        for (spec in categories) {
            val card = ConceptComponents.createCard(activity, tokens, density)
            ConceptComponents.applyTactileFeedback(card, cornerRadiusDp = 20f, pressScale = 0.982f, tokens = tokens)
            val row = ConceptComponents.createNavRow(
                context = activity,
                title = spec.title,
                subtitle = spec.subtitle,
                rightValue = null,
                tokens = tokens,
                density = density,
                iconRes = spec.iconRes,
                showChevron = true,
                onClick = spec.action
            )
            card.addView(row)
            root.addView(card)
        }

        val currentStats = statsRepo.getStats(StatsPeriod.ALL_TIME)
        val statsMini = ConceptComponents.createCard(activity, tokens, density)
        ConceptComponents.applyTactileFeedback(statsMini, cornerRadiusDp = 20f, pressScale = 0.982f, tokens = tokens)
        statsMini.addView(ConceptComponents.createCardHeader(activity, "TYPING INSIGHTS", tokens, density, R.drawable.ic_bar_chart))
        val statsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        fun addMiniCol(label: String, value: String) {
            val col = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(activity).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
            })
            col.addView(TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(tokens.textSecondary)
            })
            statsRow.addView(col)
        }
        addMiniCol("Words Typed", "${currentStats.totalKeystrokes / 5}")
        addMiniCol("Time Saved", currentStats.timeSavedFormatted)
        addMiniCol("Suggestions", "${currentStats.suggestionsAccepted}")
        statsMini.addView(statsRow)
        root.addView(statsMini)

        return root
    }

    fun buildTypingScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Typing & Suggestions",
                description = "Configure smart offline predictions, auto-correction sensitivity, punctuation behaviors, and gesture typing.",
                iconRes = R.drawable.ic_keyboard,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)

        card1.addView(ConceptComponents.createCardHeader(activity, "NEURAL PREDICTION & GRAMMAR", tokens, density, R.drawable.ic_sparkles))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Next-Word Predictions", "On-device 96-dimensional neural vector language predictions",
            p.getBoolean(Skins.KEY_PREDICTIONS, true), tokens, density, R.drawable.ic_sparkles
        ) { v ->
            p.edit().putBoolean(Skins.KEY_PREDICTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Smart Auto-Correction", "Continuous 2D spatial Gaussian and semantic vector typo correction",
            p.getBoolean(Skins.KEY_AUTOCORRECT, true), tokens, density, R.drawable.ic_check
        ) { v ->
            p.edit().putBoolean(Skins.KEY_AUTOCORRECT, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Grammar & Proofread Assistant", "Dedicated toolbar proofread action for subject-verb agreement and homophones",
            p.getBoolean(Skins.KEY_GRAMMAR, true), tokens, density, R.drawable.ic_wand_sparkles
        ) { v ->
            p.edit().putBoolean(Skins.KEY_GRAMMAR, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Next-Word Suggestions", "Predict what you might type next based on context",
            p.getBoolean(Skins.KEY_SUGGESTIONS, true), tokens, density, R.drawable.ic_keyboard
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SUGGESTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Emoji Suggestions", "Suggest contextual emojis as you type words",
            p.getBoolean(Skins.KEY_EMOJI_SUGGESTIONS, true), tokens, density, R.drawable.ic_smile
        ) { v ->
            p.edit().putBoolean(Skins.KEY_EMOJI_SUGGESTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSliderRow(
            context = activity,
            title = "Emoji Size Scale",
            subtitle = "Adjust size of emojis in candidate bar and picker",
            min = 70, max = 140,
            current = p.getInt(Skins.KEY_EMOJI_SIZE, Skins.DEFAULT_EMOJI_SIZE),
            suffix = "%", tokens = tokens, density = density,
            iconRes = R.drawable.ic_smile,
            step = 1,
            defaultValue = Skins.DEFAULT_EMOJI_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_EMOJI_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Grammar Check", "Flag grammatical errors and suggest corrections",
            p.getBoolean(Skins.KEY_GRAMMAR, true), tokens, density, R.drawable.ic_shield
        ) { v ->
            p.edit().putBoolean(Skins.KEY_GRAMMAR, v).apply()
            activity.notifySettingsChanged(restart = false)
        })
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "MECHANICS & GESTURES", tokens, density, R.drawable.ic_sliders))

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Double-Space Period", "Double-tap spacebar to insert a period and a space",
            p.getBoolean(Skins.KEY_DOUBLE_SPACE_PERIOD, true), tokens, density, R.drawable.ic_keyboard
        ) { v ->
            p.edit().putBoolean(Skins.KEY_DOUBLE_SPACE_PERIOD, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Spacebar Cursor Control", "Slide finger across spacebar to move cursor precisely",
            p.getBoolean(Skins.KEY_CURSOR_CONTROL, true), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean(Skins.KEY_CURSOR_CONTROL, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Gesture Delete", "Swipe left from backspace key to quickly delete words",
            p.getBoolean(Skins.KEY_GESTURE_DELETE, true), tokens, density, R.drawable.ic_chevron_left
        ) { v ->
            p.edit().putBoolean(Skins.KEY_GESTURE_DELETE, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Glide Typing", "Slide your finger continuously across keys to form words",
            p.getBoolean(Skins.KEY_GLIDE, true), tokens, density, R.drawable.ic_trending_up
        ) { v ->
            p.edit().putBoolean(Skins.KEY_GLIDE, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Secondary Hints", "Show secondary symbols and numbers on keys",
            p.getBoolean(Skins.KEY_SECONDARY_HINTS, Skins.DEFAULT_SECONDARY_HINTS), tokens, density, R.drawable.ic_eye
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SECONDARY_HINTS, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        val savedMs = p.getInt(Skins.KEY_LONG_PRESS_MS, 250).coerceIn(100, 400)
        card2.addView(ConceptComponents.createSliderRow(
            context = activity,
            title = "Long Press Delay",
            subtitle = "Duration to trigger secondary symbol or accented character",
            min = 100, max = 400,
            current = savedMs,
            suffix = " ms", tokens = tokens, density = density,
            iconRes = R.drawable.ic_sliders,
            step = 1,
            defaultValue = 250,
            valueFormatter = { v -> "$v ms" }
        ) { v ->
            p.edit().putInt(Skins.KEY_LONG_PRESS_MS, v).apply()
            activity.notifySettingsChanged(lightUpdate = true)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Number Row", "Show dedicated number row at the top of keyboard",
            p.getBoolean(Skins.KEY_NUMBER_ROW, false), tokens, density, R.drawable.ic_keyboard
        ) { v ->
            p.edit().putBoolean(Skins.KEY_NUMBER_ROW, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Key Popup on Press", "Show magnified character bubble above pressed key",
            p.getBoolean(Skins.KEY_KEY_POPUP, false), tokens, density, R.drawable.ic_eye
        ) { v ->
            p.edit().putBoolean(Skins.KEY_KEY_POPUP, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "High Contrast Keys", "Increase key contrast for enhanced legibility",
            p.getBoolean(Skins.KEY_HIGH_CONTRAST, false), tokens, density, R.drawable.ic_eye
        ) { v ->
            p.edit().putBoolean(Skins.KEY_HIGH_CONTRAST, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card2)

        return root
    }

    private class KeyShapePreviewCapView(
        context: Context,
        private val shapeId: String,
        private val isSelected: Boolean,
        private val tokens: ConceptThemeTokens,
        private val density: Float
    ) : View(context) {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val facetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val path = Path()
        private val innerFacetPath = Path()
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val strokeWidth = 1.6f * density
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = if (isSelected) tokens.accent else tokens.textSecondary

            val halfStroke = strokeWidth / 2f
            rect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

            path.reset()
            innerFacetPath.reset()

            when (shapeId) {
                Skins.SHAPE_ROUNDED -> {
                    val r = 8f * density
                    path.addRoundRect(rect, r, r, Path.Direction.CW)
                }
                Skins.SHAPE_SQUIRCLE -> {
                    val r = 5.5f * density
                    path.addRoundRect(rect, r, r, Path.Direction.CW)
                }
                Skins.SHAPE_PILL -> {
                    val r = rect.height() / 2f
                    path.addRoundRect(rect, r, r, Path.Direction.CW)
                }
                Skins.SHAPE_SHARP -> {
                    val r = 2f * density
                    path.addRoundRect(rect, r, r, Path.Direction.CW)
                }
                Skins.SHAPE_CUT_CORNER -> {
                    val cut = 6f * density
                    path.moveTo(rect.left + cut, rect.top)
                    path.lineTo(rect.right - cut, rect.top)
                    path.lineTo(rect.right, rect.top + cut)
                    path.lineTo(rect.right, rect.bottom - cut)
                    path.lineTo(rect.right - cut, rect.bottom)
                    path.lineTo(rect.left + cut, rect.bottom)
                    path.lineTo(rect.left, rect.bottom - cut)
                    path.lineTo(rect.left, rect.top + cut)
                    path.close()
                }
                Skins.SHAPE_GEM -> {
                    val cut = 6.5f * density
                    path.moveTo(rect.left + cut, rect.top)
                    path.lineTo(rect.right - cut, rect.top)
                    path.lineTo(rect.right, rect.top + cut)
                    path.lineTo(rect.right, rect.bottom - cut)
                    path.lineTo(rect.right - cut, rect.bottom)
                    path.lineTo(rect.left + cut, rect.bottom)
                    path.lineTo(rect.left, rect.bottom - cut)
                    path.lineTo(rect.left, rect.top + cut)
                    path.close()

                    val inset = 4.5f * density
                    val innerCut = 3.5f * density
                    val iL = rect.left + inset
                    val iT = rect.top + inset
                    val iR = rect.right - inset
                    val iB = rect.bottom - inset
                    innerFacetPath.moveTo(iL + innerCut, iT)
                    innerFacetPath.lineTo(iR - innerCut, iT)
                    innerFacetPath.lineTo(iR, iT + innerCut)
                    innerFacetPath.lineTo(iR, iB - innerCut)
                    innerFacetPath.lineTo(iR - innerCut, iB)
                    innerFacetPath.lineTo(iL + innerCut, iB)
                    innerFacetPath.lineTo(iL, iB - innerCut)
                    innerFacetPath.lineTo(iL, iT + innerCut)
                    innerFacetPath.close()
                }
                else -> {
                    val r = 7f * density
                    path.addRoundRect(rect, r, r, Path.Direction.CW)
                }
            }

            fillPaint.color = tokens.surface
            canvas.drawPath(path, fillPaint)

            canvas.drawPath(path, strokePaint)

            if (shapeId == Skins.SHAPE_GEM) {
                facetPaint.strokeWidth = 1f * density
                facetPaint.color = if (tokens.isLight) Color.argb(120, 30, 41, 59) else Color.argb(140, 255, 255, 255)
                canvas.drawPath(innerFacetPath, facetPaint)

                val cut = 6.5f * density
                val inset = 4.5f * density
                val innerCut = 3.5f * density
                canvas.drawLine(rect.left + cut, rect.top, rect.left + inset + innerCut, rect.top + inset, facetPaint)
                canvas.drawLine(rect.right - cut, rect.top, rect.right - inset - innerCut, rect.top + inset, facetPaint)
                canvas.drawLine(rect.right, rect.bottom - cut, rect.right - inset, rect.bottom - inset - innerCut, facetPaint)
                canvas.drawLine(rect.left, rect.bottom - cut, rect.left + inset, rect.bottom - inset - innerCut, facetPaint)
            }

            textPaint.textSize = 12.5f * density
            textPaint.color = if (isSelected) tokens.accent else tokens.textPrimary
            val fontMetrics = textPaint.fontMetrics
            val textY = (h / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("A", w / 2f, textY, textPaint)
        }
    }

    private class KeySurfacePreviewCapView(
        context: Context,
        private val styleId: String,
        private val isSelected: Boolean,
        private val tokens: ConceptThemeTokens,
        private val density: Float
    ) : View(context) {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val r = 7f * density
            val strokeWidth = if (styleId == Skins.STYLE_STROKE) 2.2f * density else 1.2f * density
            val halfStroke = strokeWidth / 2f
            rect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

            when (styleId) {
                Skins.STYLE_DEFAULT -> {
                    fillPaint.shader = null
                    fillPaint.color = tokens.surface
                    canvas.drawRoundRect(rect, r, r, fillPaint)

                    strokePaint.strokeWidth = 1f * density
                    strokePaint.color = if (isSelected) tokens.accent else tokens.cardBorder
                    canvas.drawRoundRect(rect, r, r, strokePaint)
                }
                Skins.STYLE_STROKE -> {
                    fillPaint.shader = null
                    fillPaint.color = if (tokens.isLight) Color.argb(20, 0, 0, 0) else Color.argb(30, 255, 255, 255)
                    canvas.drawRoundRect(rect, r, r, fillPaint)

                    strokePaint.strokeWidth = 2.2f * density
                    strokePaint.color = if (isSelected) tokens.accent else tokens.textSecondary
                    canvas.drawRoundRect(rect, r, r, strokePaint)
                }
                Skins.STYLE_MATERIAL -> {
                    val tonalTop = ColorUtils.blendARGB(tokens.surface, tokens.accent, 0.22f)
                    val tonalBottom = ColorUtils.blendARGB(tokens.surface, tokens.accent, 0.08f)
                    fillPaint.shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, h,
                        tonalTop, tonalBottom,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(rect, r, r, fillPaint)

                    strokePaint.strokeWidth = 1f * density
                    strokePaint.color = if (isSelected) tokens.accent else ColorUtils.blendARGB(tokens.cardBorder, tokens.accent, 0.35f)
                    canvas.drawRoundRect(rect, r, r, strokePaint)
                }
                Skins.STYLE_NEUMORPHIC -> {
                    val lightHighlight = if (tokens.isLight) Color.WHITE else Color.argb(50, 255, 255, 255)
                    val darkShadow = if (tokens.isLight) Color.argb(40, 0, 0, 0) else Color.BLACK
                    fillPaint.shader = android.graphics.LinearGradient(
                        0f, 0f, w, h,
                        ColorUtils.blendARGB(tokens.surface, lightHighlight, 0.25f),
                        ColorUtils.blendARGB(tokens.surface, darkShadow, 0.35f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(rect, r, r, fillPaint)

                    strokePaint.strokeWidth = 1f * density
                    strokePaint.color = if (isSelected) tokens.accent else tokens.cardBorder
                    canvas.drawRoundRect(rect, r, r, strokePaint)
                }
            }

            textPaint.textSize = 12.5f * density
            textPaint.color = if (isSelected) tokens.accent else tokens.textPrimary
            val fontMetrics = textPaint.fontMetrics
            val textY = (h / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText("A", w / 2f, textY, textPaint)
        }
    }

    fun buildAppearanceScreen(onOpenThemeGallery: () -> Unit): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)
        val curTheme = p.getString(Skins.KEY_THEME, "lucid-void") ?: "lucid-void"
        val isLucidVoid = curTheme.startsWith("lucid-void")

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Appearance & Studio",
                description = "Customize themes, key shapes, fonts, spacing, glass effects, and photo backgrounds in real time.",
                iconRes = R.drawable.ic_palette,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "THEME & COLOR PALETTE", tokens, density, R.drawable.ic_palette))

        val modeOptions = listOf("Auto (System)", "Dark Mode", "Light Mode")
        val currentMode = p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        val modeIdx = when (currentMode) {
            Skins.MODE_DARK -> 1
            Skins.MODE_LIGHT -> 2
            else -> 0
        }
        card1.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = modeOptions,
                selectedIndex = modeIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val target = when (idx) {
                    1 -> Skins.MODE_DARK
                    2 -> Skins.MODE_LIGHT
                    else -> Skins.MODE_AUTO
                }
                p.edit().putString(Skins.KEY_THEME_MODE, target).apply()
                p.edit().putString("settings_theme_mode", target).apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
            }
        )

        if (isLucidVoid) {
            val notice = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    setStroke(dp(1f), tokens.cardBorder)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(14f), dp(6f), dp(14f), dp(6f))
                }
            }
            val lockIv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_lock)?.mutate()
                d?.setTint(tokens.textSecondary)
                setImageDrawable(d)
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) }
            }
            notice.addView(lockIv)
            val lockTv = TextView(activity).apply {
                text = "Accent color is locked for Lucid Void (Frosted glass uses adaptive spectral tones)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
            }
            notice.addView(lockTv)
            card1.addView(notice)
        }

        val paletteRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(6f), dp(14f), dp(12f))
        }
        val currentAccent = p.getInt(Skins.KEY_ACCENT, Skins.DEFAULT_ACCENT)
        for ((hex, name) in Skins.ACCENT_PALETTE) {
            val colorVal = Color.parseColor(hex)
            val isSelected = colorVal == currentAccent
            val circle = FrameLayout(activity).apply {
                val s = dp(32f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorVal)
                    if (isSelected && !isLucidVoid) {
                        setStroke(dp(3f), tokens.textPrimary)
                    }
                }
                isClickable = true
                setOnClickListener {
                    if (isLucidVoid) {
                        activity.showToast("Accent color is locked for Lucid Void theme")
                    } else {
                        p.edit().putInt(Skins.KEY_ACCENT, colorVal).apply()
                        p.edit().putInt("settings_accent_color", colorVal).apply()
                        activity.notifySettingsChanged(restart = true)
                        activity.reloadTheme()
                        onThemeChanged()
                        activity.showToast("Accent: $name")
                    }
                }
            }
            paletteRow.addView(circle)
        }
        val scrollPalette = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            if (isLucidVoid) alpha = 0.45f
        }
        scrollPalette.addView(paletteRow)
        card1.addView(scrollPalette)

        val themeFamilies = listOf(
            "lucid-void" to "Lucid Void",
            "md3_dynamic" to "Material You",
            "onyx" to "Onyx Black",
            "graphite" to "Graphite",
            "midnight" to "Midnight Navy",
            "ocean" to "Deep Ocean",
            "ember" to "Sunset Ember",
            "cyberpunk" to "Neon Cyberpunk",
            "amoled" to "AMOLED Pure",
            "forest" to "Emerald Forest",
            "coral" to "Warm Coral",
            "rose" to "Rose",
            "contrast" to "High Contrast"
        )

        val presetHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(4f))
        }
        val presetIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_palette)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        }
        presetHeader.addView(presetIv)
        val presetTv = TextView(activity).apply {
            text = "PRESET THEMES"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        presetHeader.addView(presetTv)
        card1.addView(presetHeader)

        val presetScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for ((id, name) in themeFamilies) {
            val targetId = if (tokens.isLight) Skins.getPairedThemeId(id, true) else Skins.getPairedThemeId(id, false)
            val preset = Skins.PRESETS[targetId] ?: Skins.PRESETS[id] ?: Skins.PRESETS["onyx"]!!
            val isSelected = curTheme == id || (id == "lucid-void" && curTheme.startsWith("lucid-void")) ||
                (id == "onyx" && (curTheme == "onyx" || curTheme == "light" || curTheme == "onyx_light")) ||
                (curTheme == targetId)

            val itemCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(76f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(4f)
                    marginEnd = dp(4f)
                }
                isClickable = true
                isFocusable = true
            }

            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(68f), dp(44f)).apply {
                    bottomMargin = dp(4f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    try {
                        setColor(Color.parseColor(preset.bg))
                    } catch (_: Exception) {
                        setColor(Color.parseColor("#121212"))
                    }
                    if (isSelected) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
            }

            val keycapsRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }

            for (k in 0..2) {
                val isAccentKey = (k == 1)
                val miniCap = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(14f), dp(18f)).apply {
                        marginStart = dp(2f)
                        marginEnd = dp(2f)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(3.5f).toFloat()
                        try {
                            if (isAccentKey) {
                                setColor(Color.parseColor(preset.defaultAccent))
                            } else {
                                setColor(Color.parseColor(preset.key))
                                setStroke(dp(0.7f), Color.parseColor(preset.keyBorder))
                            }
                        } catch (_: Exception) {
                            setColor(Color.DKGRAY)
                        }
                    }
                }
                keycapsRow.addView(miniCap)
            }
            box.addView(keycapsRow)

            if (isSelected) {
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(Color.WHITE)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(2f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(3f), dp(3f), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.accent)
                    }
                }
                box.addView(checkIv)
            }

            itemCol.addView(box)

            val nameTv = TextView(activity).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(if (isSelected) tokens.accent else tokens.textSecondary)
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            itemCol.addView(nameTv)

            itemCol.setOnClickListener {
                p.edit().putString(Skins.KEY_THEME, id).apply()
                activity.notifySettingsChanged(restart = true)
                activity.showToast("Applied theme: $name")
                activity.reloadTheme()
                onThemeChanged()
            }

            presetRow.addView(itemCol)
        }
        presetScroll.addView(presetRow)
        card1.addView(presetScroll)

        if (isLucidVoid) {
            val lucidGlassBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14f).toFloat()
                    setColor(tokens.chipBg)
                    setStroke(dp(1f), tokens.cardBorder)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(14f), dp(4f), dp(14f), dp(10f))
                }
            }

            val glassHeader = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(8f), dp(14f), dp(4f))
            }
            val glassIv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_sparkles)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) }
            }
            glassHeader.addView(glassIv)
            val glassTv = TextView(activity).apply {
                text = if (tokens.isLight) "LUCID VOID GLASS (LIGHT MODE)" else "LUCID VOID GLASS (DARK MODE)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            }
            glassHeader.addView(glassTv)
            lucidGlassBox.addView(glassHeader)

            if (tokens.isLight) {
                val curOpLight = p.getInt(Skins.KEY_LUCID_VOID_OPACITY_LIGHT, Skins.DEFAULT_LUCID_VOID_OPACITY_LIGHT).coerceIn(5, 95)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Frosted Canvas Opacity", subtitle = "Light mode glass translucency through key surfaces",
                    min = 5, max = 95, current = curOpLight,
                    suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_OPACITY_LIGHT
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_OPACITY_LIGHT, v).putInt(Skins.KEY_LUCID_VOID_OPACITY, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })

                val curBlurLight = p.getInt(Skins.KEY_LUCID_VOID_BLUR_LIGHT, Skins.DEFAULT_LUCID_VOID_BLUR_LIGHT).coerceIn(0, 80)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Background Blur Radius", subtitle = "Gaussian glass blur applied behind frosted layers",
                    min = 0, max = 80, current = curBlurLight,
                    suffix = "px", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_BLUR_LIGHT
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_BLUR_LIGHT, v).putInt(Skins.KEY_LUCID_VOID_BLUR, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })

                val curTintLight = p.getInt(Skins.KEY_LUCID_VOID_TINT_LIGHT, Skins.DEFAULT_LUCID_VOID_TINT_LIGHT).coerceIn(0, 100)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Milky Background Tint", subtitle = "Subtle milky frosted tint contrast overlay for light glass",
                    min = 0, max = 100, current = curTintLight,
                    suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_TINT_LIGHT
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_TINT_LIGHT, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })
            } else {
                val curOpDark = p.getInt(Skins.KEY_LUCID_VOID_OPACITY_DARK, Skins.DEFAULT_LUCID_VOID_OPACITY_DARK).coerceIn(5, 95)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Frosted Canvas Opacity", subtitle = "Dark mode glass translucency through key surfaces",
                    min = 5, max = 95, current = curOpDark,
                    suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_OPACITY_DARK
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_OPACITY_DARK, v).putInt(Skins.KEY_LUCID_VOID_OPACITY, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })

                val curBlurDark = p.getInt(Skins.KEY_LUCID_VOID_BLUR_DARK, Skins.DEFAULT_LUCID_VOID_BLUR_DARK).coerceIn(0, 80)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Background Blur Radius", subtitle = "Gaussian glass blur applied behind frosted layers",
                    min = 0, max = 80, current = curBlurDark,
                    suffix = "px", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_BLUR_DARK
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_BLUR_DARK, v).putInt(Skins.KEY_LUCID_VOID_BLUR, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })

                val curTintDark = p.getInt(Skins.KEY_LUCID_VOID_TINT_DARK, Skins.DEFAULT_LUCID_VOID_TINT_DARK).coerceIn(0, 100)
                lucidGlassBox.addView(ConceptComponents.createSliderRow(
                    context = activity, title = "Milky Background Tint", subtitle = "Smoky obsidian milky tint contrast overlay for dark frosted glass",
                    min = 0, max = 100, current = curTintDark,
                    suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                    defaultValue = Skins.DEFAULT_LUCID_VOID_TINT_DARK
                ) { v ->
                    p.edit().putInt(Skins.KEY_LUCID_VOID_TINT_DARK, v).apply()
                    activity.notifySettingsChanged(restart = false)
                })
            }
            card1.addView(lucidGlassBox)
        }

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Theme Selection Gallery", "Browse visual presets & color studios",
                null, tokens, density, R.drawable.ic_palette, true
            ) { onOpenThemeGallery() }
        )

        val styleOptions = listOf(
            Skins.STYLE_DEFAULT to "Default Flat",
            Skins.STYLE_STROKE to "Outlined Stroke",
            Skins.STYLE_MATERIAL to "Material Tonal",
            Skins.STYLE_NEUMORPHIC to "Neumorphic Bevel"
        )
        val curStyle = p.getString(Skins.KEY_KEY_STYLE, Skins.STYLE_DEFAULT) ?: Skins.STYLE_DEFAULT

        val styleHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(4f))
        }
        val styleIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_sliders)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        }
        styleHeader.addView(styleIv)
        val styleTv = TextView(activity).apply {
            text = "KEY SURFACE STYLE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        styleHeader.addView(styleTv)
        card1.addView(styleHeader)

        val styleScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val styleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for ((styleId, styleName) in styleOptions) {
            val isSelected = curStyle == styleId

            val itemCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(82f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(4f)
                    marginEnd = dp(4f)
                }
                isClickable = true
                isFocusable = true
            }

            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(74f), dp(44f)).apply {
                    bottomMargin = dp(4f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    if (isSelected) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
            }

            val previewCap = KeySurfacePreviewCapView(activity, styleId, isSelected, tokens, density).apply {
                layoutParams = FrameLayout.LayoutParams(dp(38f), dp(28f), Gravity.CENTER)
            }
            box.addView(previewCap)

            if (isSelected) {
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(Color.WHITE)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(2f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(3f), dp(3f), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.accent)
                    }
                }
                box.addView(checkIv)
            }
            itemCol.addView(box)

            val nameTv = TextView(activity).apply {
                text = styleName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(if (isSelected) tokens.accent else tokens.textSecondary)
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            itemCol.addView(nameTv)

            itemCol.setOnClickListener {
                p.edit().putString(Skins.KEY_KEY_STYLE, styleId).apply()
                p.edit().putString("settings_ui_style", if (styleId == Skins.STYLE_STROKE) "stroke" else if (styleId == Skins.STYLE_NEUMORPHIC) "neumorphic" else "md3").apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
            }

            styleRow.addView(itemCol)
        }
        styleScroll.addView(styleRow)
        card1.addView(styleScroll)

        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "KEY GEOMETRY & SHAPES", tokens, density, R.drawable.ic_sliders))

        val shapeOptions = listOf(
            Skins.SHAPE_ROUNDED to "Rounded",
            Skins.SHAPE_SQUIRCLE to "Squircle",
            Skins.SHAPE_PILL to "Pill",
            Skins.SHAPE_SHARP to "Sharp Box",
            Skins.SHAPE_CUT_CORNER to "Cut Corner",
            Skins.SHAPE_GEM to "Gem Facet"
        )
        val curShape = p.getString(Skins.KEY_STUDIO_SHAPE, Skins.SHAPE_ROUNDED) ?: Skins.SHAPE_ROUNDED

        val shapeHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(4f))
        }
        val shapeIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_sparkles)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        }
        shapeHeader.addView(shapeIv)
        val shapeTv = TextView(activity).apply {
            text = "KEY SHAPE GEOMETRY"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        shapeHeader.addView(shapeTv)
        card2.addView(shapeHeader)

        val shapeScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val shapeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for ((shapeId, shapeName) in shapeOptions) {
            val isSelected = curShape == shapeId

            val itemCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(76f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(4f)
                    marginEnd = dp(4f)
                }
                isClickable = true
                isFocusable = true
            }

            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(68f), dp(44f)).apply {
                    bottomMargin = dp(4f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    if (isSelected) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
            }

            val keyCap = KeyShapePreviewCapView(activity, shapeId, isSelected, tokens, density).apply {
                layoutParams = FrameLayout.LayoutParams(dp(38f), dp(28f), Gravity.CENTER)
            }
            box.addView(keyCap)

            if (isSelected) {
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(Color.WHITE)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(2f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(3f), dp(3f), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.accent)
                    }
                }
                box.addView(checkIv)
            }

            itemCol.addView(box)

            val nameTv = TextView(activity).apply {
                text = shapeName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(if (isSelected) tokens.accent else tokens.textSecondary)
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            itemCol.addView(nameTv)

            itemCol.setOnClickListener {
                p.edit().putString(Skins.KEY_STUDIO_SHAPE, shapeId).apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
            }

            shapeRow.addView(itemCol)
        }
        shapeScroll.addView(shapeRow)
        card2.addView(shapeScroll)

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Key Outlines", "Render border outline around individual keys",
            p.getBoolean(Skins.KEY_KEY_BORDERS, false), tokens, density, R.drawable.ic_pen_line
        ) { v ->
            p.edit().putBoolean(Skins.KEY_KEY_BORDERS, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Corner Radius", subtitle = "Roundness of individual keyboard keys",
            min = 0, max = 28, current = p.getInt(Skins.KEY_STUDIO_RADIUS, 12),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 12
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_RADIUS, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Border Width", subtitle = "Thickness of outer key outlines",
            min = 0, max = 4, current = p.getInt(Skins.KEY_STUDIO_BORDER_WIDTH, 1),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 1
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_BORDER_WIDTH, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Shadow & Elevation", subtitle = "Depth shadow underneath key surfaces",
            min = 0, max = 12, current = p.getInt(Skins.KEY_STUDIO_SHADOW, 2),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 2
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_SHADOW, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card2)

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "DIMENSIONS & SPACING", tokens, density, R.drawable.ic_sliders))

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Keyboard Height", subtitle = "Overall height of the keyboard frame",
            min = 60, max = 150, current = p.getInt(Skins.KEY_KEYBOARD_HEIGHT, 100),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 100
        ) { v ->
            p.edit().putInt(Skins.KEY_KEYBOARD_HEIGHT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Key Width Scale", subtitle = "Horizontal stretch and margin proportion",
            min = 70, max = 130, current = p.getInt(Skins.KEY_KEY_TILE_WIDTH, Skins.DEFAULT_KEY_TILE_WIDTH),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = Skins.DEFAULT_KEY_TILE_WIDTH
        ) { v ->
            p.edit().putInt(Skins.KEY_KEY_TILE_WIDTH, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Key Height Scale", subtitle = "Vertical key footprint scale",
            min = 70, max = 130, current = p.getInt(Skins.KEY_KEY_TILE_SIZE, Skins.DEFAULT_KEY_TILE_SIZE),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = Skins.DEFAULT_KEY_TILE_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_KEY_TILE_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Horizontal Gap", subtitle = "Spacing between adjacent keys in rows",
            min = 0, max = 12, current = p.getInt(Skins.KEY_STUDIO_H_GAP, 3),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 3
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_H_GAP, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Vertical Gap", subtitle = "Spacing between rows of keys",
            min = 0, max = 16, current = p.getInt(Skins.KEY_STUDIO_V_GAP, 4),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 4
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_V_GAP, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Side Margin Padding", subtitle = "Left and right padding from screen edges",
            min = 0, max = 24, current = p.getInt(Skins.KEY_STUDIO_SIDE_PADDING, 4),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 4
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_SIDE_PADDING, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Bottom Navigation Gap", subtitle = "Distance above Android navigation bar",
            min = 0, max = 40, current = p.getInt(Skins.KEY_STUDIO_BOTTOM_PADDING, 6),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 6
        ) { v ->
            p.edit().putInt(Skins.KEY_STUDIO_BOTTOM_PADDING, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card3)

        val card4 = ConceptComponents.createCard(activity, tokens, density)
        card4.addView(ConceptComponents.createCardHeader(activity, "TYPOGRAPHY & FONTS", tokens, density, R.drawable.ic_type))

        card4.addView(ConceptComponents.createSwitchRow(
            activity, "Use System Font", "Follow default Android device typography",
            p.getBoolean(Skins.KEY_USE_SYSTEM_FONT, Skins.DEFAULT_USE_SYSTEM_FONT), tokens, density, R.drawable.ic_type
        ) { v ->
            p.edit().putBoolean(Skins.KEY_USE_SYSTEM_FONT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        val fontOptions = listOf(
            Skins.FONT_DEFAULT to "Inter Default",
            Skins.FONT_ROUNDED to "Rounded Soft",
            Skins.FONT_SERIF to "Editorial Serif",
            Skins.FONT_MONO to "Code Monospace",
            Skins.FONT_CONDENSED to "Condensed Compact",
            Skins.FONT_LIGHT to "Clean Light"
        )
        val curFont = p.getString(Skins.KEY_STUDIO_FONT, Skins.FONT_DEFAULT) ?: Skins.FONT_DEFAULT

        val fontHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(4f))
        }
        val fontIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_type)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        }
        fontHeader.addView(fontIv)
        val fontTv = TextView(activity).apply {
            text = "FONT STYLE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        fontHeader.addView(fontTv)
        card4.addView(fontHeader)

        val fontScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val fontRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for ((fontId, fontName) in fontOptions) {
            val isSelected = curFont == fontId

            val itemCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(82f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(4f)
                    marginEnd = dp(4f)
                }
                isClickable = true
                isFocusable = true
            }

            val box = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(74f), dp(44f)).apply {
                    bottomMargin = dp(4f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    if (isSelected) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
            }

            val sampleTv = TextView(activity).apply {
                text = "Ag"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                try {
                    typeface = Skins.resolveTypeface(activity, fontStyle = fontId, bold = true)
                } catch (_: Exception) {
                    typeface = Typeface.DEFAULT_BOLD
                }
                setTextColor(if (isSelected) tokens.accent else tokens.textPrimary)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            box.addView(sampleTv)

            if (isSelected) {
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(Color.WHITE)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(2f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(3f), dp(3f), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.accent)
                    }
                }
                box.addView(checkIv)
            }
            itemCol.addView(box)

            val nameTv = TextView(activity).apply {
                text = fontName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(if (isSelected) tokens.accent else tokens.textSecondary)
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            itemCol.addView(nameTv)

            itemCol.setOnClickListener {
                p.edit().putString(Skins.KEY_STUDIO_FONT, fontId).apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
            }

            fontRow.addView(itemCol)
        }
        fontScroll.addView(fontRow)
        card4.addView(fontScroll)

        card4.addView(
            ConceptComponents.createNavRow(
                activity, "Custom Fonts Store", "Download & apply custom typefaces from remote store",
                null, tokens, density, R.drawable.ic_download, true
            ) { activity.showFontStoreDialog() }
        )

        card4.addView(ConceptComponents.createSwitchRow(
            activity, "Bold Key Labels", "Render key letters with heavy bold typeface weight",
            p.getBoolean(Skins.KEY_STUDIO_FONT_BOLD, true), tokens, density, R.drawable.ic_type
        ) { v ->
            p.edit().putBoolean(Skins.KEY_STUDIO_FONT_BOLD, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card4.addView(ConceptComponents.createFloatSliderRow(
            context = activity, title = "Key Font Scale", subtitle = "Proportional letter character size",
            min = 0.80f, max = 1.30f, current = Skins.prefFloat(p, Skins.KEY_STUDIO_FONT_SCALE, 1.0f),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 0.01f,
            defaultValue = 1.0f
        ) { v ->
            p.edit().putFloat(Skins.KEY_STUDIO_FONT_SCALE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card4)

        val card5 = ConceptComponents.createCard(activity, tokens, density)
        card5.addView(ConceptComponents.createCardHeader(activity, "LANDSCAPE PROFILE", tokens, density, R.drawable.ic_smartphone))

        card5.addView(ConceptComponents.createSwitchRow(
            activity, "Custom Landscape Profile", "Use independent dimensions when device is rotated horizontally",
            p.getBoolean(Skins.KEY_LANDSCAPE_CUSTOM, false), tokens, density, R.drawable.ic_smartphone
        ) { v ->
            p.edit().putBoolean(Skins.KEY_LANDSCAPE_CUSTOM, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card5.addView(ConceptComponents.createSwitchRow(
            activity, "Landscape Split Keyboard", "Split left and right halves for easy two-thumb typing",
            p.getBoolean(Skins.KEY_LANDSCAPE_SPLIT, false), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean(Skins.KEY_LANDSCAPE_SPLIT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card5.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Landscape Height", subtitle = "Keyboard vertical footprint in landscape",
            min = 50, max = 120, current = p.getInt(Skins.KEY_LANDSCAPE_HEIGHT, 80),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 80
        ) { v ->
            p.edit().putInt(Skins.KEY_LANDSCAPE_HEIGHT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card5.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Landscape Horizontal Gap", subtitle = "Space between keys in landscape",
            min = 0, max = 16, current = p.getInt(Skins.KEY_LANDSCAPE_H_GAP, 4),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 4
        ) { v ->
            p.edit().putInt(Skins.KEY_LANDSCAPE_H_GAP, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card5)

        val card6 = ConceptComponents.createCard(activity, tokens, density)
        card6.addView(ConceptComponents.createCardHeader(activity, "CUSTOM PHOTO BACKGROUND", tokens, density, R.drawable.ic_eye))

        val photoButtonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(4f), dp(14f), dp(8f))
        }
        val chooseBtn = ConceptComponents.createPillButton(activity, "Choose Photo", tokens, density, true) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            activity.pickBackground.launch(intent)
        }
        chooseBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
        photoButtonsRow.addView(chooseBtn)

        val cropBtn = ConceptComponents.createPillButton(activity, "Crop Photo", tokens, density, false) {
            activity.showCropDialog()
        }
        cropBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        photoButtonsRow.addView(cropBtn)
        card6.addView(photoButtonsRow)

        card6.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Background Dim Level", subtitle = "Darken photo overlay for maximum key contrast",
            min = 0, max = 100, current = p.getInt(Skins.KEY_DIM, 35),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 35
        ) { v ->
            p.edit().putInt(Skins.KEY_DIM, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        val removeBtn = ConceptComponents.createPillButton(activity, "Remove Photo Background", tokens, density, false) {
            CustomBackground.remove(activity)
            activity.notifySettingsChanged(restart = true)
            activity.showToast("Custom photo removed")
        }
        val removeBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
            addView(removeBtn)
        }
        card6.addView(removeBox)
        root.addView(card6)

        val card7 = ConceptComponents.createCard(activity, tokens, density)
        card7.addView(ConceptComponents.createCardHeader(activity, "THEME BACKUP & SHARE", tokens, density, R.drawable.ic_refresh_cw))

        val shareRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        val exportBtn = ConceptComponents.createPillButton(activity, "Export Theme", tokens, density, false) {
            val json = Skins.exportThemeJson(activity)
            activity.exportThemeLauncher.launch("gnosis_theme_${System.currentTimeMillis()}.json")
        }
        exportBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
        shareRow.addView(exportBtn)

        val importBtn = ConceptComponents.createPillButton(activity, "Import Theme", tokens, density, false) {
            activity.importThemeLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        importBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        shareRow.addView(importBtn)
        card7.addView(shareRow)
        root.addView(card7)

        return root
    }
    fun buildLanguageScreen(onOpenDictionary: () -> Unit): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Language & Dictionary",
                description = "Manage input languages, layout styles, offline Hinglish dictionary packs, and toolbar quick keys.",
                iconRes = R.drawable.ic_globe,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        val isSystemMode = gnosis.keyboard.engine.language.GnosisLanguageManager.isSystemLanguageMode(activity)
        val activeLang = gnosis.keyboard.engine.language.GnosisLanguageManager.resolveEffectiveLanguage(activity)

        card1.addView(ConceptComponents.createCardHeader(activity, "NEURAL AI LANGUAGE & REGION", tokens, density, R.drawable.ic_globe))

        card1.addView(
            ConceptComponents.createSwitchRow(
                activity, "Follow Phone Region / Language",
                "Automatically adapt neural predictions, autocorrect, and dictionaries to device locale (" + java.util.Locale.getDefault().displayName + ")",
                isSystemMode, tokens, density, R.drawable.ic_globe
            ) { enabled ->
                val mode = if (enabled) Skins.MODE_LANG_SYSTEM else Skins.MODE_LANG_MANUAL
                gnosis.keyboard.engine.language.GnosisLanguageManager.setLanguageMode(activity, mode)
                EngineFactory.invalidateCache()
                activity.notifySettingsChanged(restart = true)
                activity.refreshCurrentScreen()
            }
        )

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Active Keyboard Language",
                if (isSystemMode) "Auto: " + activeLang.name + " (" + activeLang.nativeName + ")" else activeLang.name + " (" + activeLang.nativeName + ")",
                activeLang.shortLabel, tokens, density, R.drawable.ic_check, true
            ) {
                activity.showLanguageSelectorDialog {
                    activity.refreshCurrentScreen()
                }
            }
        )

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "System Language Settings", "Add more keyboard languages from Android settings",
                null, tokens, density, R.drawable.ic_sliders, true
            ) {
                activity.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
            }
        )
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD LAYOUT & STYLE", tokens, density, R.drawable.ic_keyboard))

        val styleOptions = listOf("Revamped (Modern)", "Classics (Standard)")
        val curStyle = p.getString(Skins.KEY_LAYOUT_STYLE, Skins.STYLE_REVAMPED) ?: Skins.STYLE_REVAMPED
        val styleIdx = if (curStyle == Skins.STYLE_CLASSICS) 1 else 0
        card2.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = styleOptions,
                selectedIndex = styleIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val target = if (idx == 1) Skins.STYLE_CLASSICS else Skins.STYLE_REVAMPED
                p.edit().putString(Skins.KEY_LAYOUT_STYLE, target).apply()
                p.edit().putString("settings_layout_style", target).apply()
                activity.notifySettingsChanged(restart = true)
                activity.showToast("Keyboard style: " + (if (idx == 1) "Classics" else "Revamped"))
            }
        )

        val curLayout = p.getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
        val activeLayoutName = LayoutExtensionManager.getLayout(activity, curLayout).name

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Active Physical Layout", "Selected character and key arrangement",
                activeLayoutName, tokens, density, R.drawable.ic_keyboard, true
            ) {
                activity.showActiveLayoutSelectorDialog {
                    activity.refreshCurrentScreen()
                }
            }
        )

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Layout Gallery & Downloader", "Browse layouts with visual key arrangements & previews",
                null, tokens, density, R.drawable.ic_download, true
            ) {
                activity.showLayoutDownloaderDialog {
                    activity.refreshCurrentScreen()
                }
            }
        )
        root.addView(card2)

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "ADDITIONAL DICTIONARY PACKS", tokens, density, R.drawable.ic_globe))

        card3.addView(
            ConceptComponents.createNavRow(
                activity, "Browse Dictionary Store", "Download multi-language autocorrect wordlists from GitHub",
                null, tokens, density, R.drawable.ic_download, true
            ) { activity.showDictionaryStoreDialog() }
        )



        val isHinglishInstalled = DictionaryExtensionManager.isInstalled(activity, "hinglish")
        val hinglishCount = DictionaryExtensionManager.getWordCount(activity, "hinglish")
        val isHinglishEnabled = DictionaryExtensionManager.isEnabled(activity, "hinglish")

        card3.addView(
            ConceptComponents.createSwitchRow(
                activity,
                "Hinglish Auto-Correction Pack",
                if (isHinglishInstalled) "Installed \u2022 $hinglishCount words" else "Tap Browse Dictionary Store to download",
                isHinglishEnabled, tokens, density,
                if (isHinglishInstalled) R.drawable.ic_check else R.drawable.ic_globe
            ) { en ->
                if (isHinglishInstalled) {
                    DictionaryExtensionManager.setEnabled(activity, "hinglish", en)
                    activity.notifySettingsChanged(restart = true)
                } else {
                    activity.showDictionaryStoreDialog()
                }
            }
        )

        val isEnglishInstalled = DictionaryExtensionManager.isInstalled(activity, "english")
        val englishCount = DictionaryExtensionManager.getWordCount(activity, "english")
        val isEnglishEnabled = DictionaryExtensionManager.isEnabled(activity, "english")

        card3.addView(
            ConceptComponents.createSwitchRow(
                activity,
                "English Enhanced Dictionary",
                if (isEnglishInstalled) "Installed \u2022 $englishCount words" else "Additional English vocabulary pack",
                isEnglishEnabled, tokens, density,
                if (isEnglishInstalled) R.drawable.ic_check else R.drawable.ic_globe
            ) { en ->
                if (isEnglishInstalled) {
                    DictionaryExtensionManager.setEnabled(activity, "english", en)
                    activity.notifySettingsChanged(restart = true)
                } else {
                    activity.showDictionaryStoreDialog()
                }
            }
        )
        root.addView(card3)

        val card4 = ConceptComponents.createCard(activity, tokens, density)
        card4.addView(ConceptComponents.createCardHeader(activity, "TOOLBAR QUICK KEYS", tokens, density, R.drawable.ic_sliders))

        val quickHint = TextView(activity).apply {
            text = "Tap a slot above, then tap any symbol below to place it:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
        }
        card4.addView(quickHint)

        val quickKeysList = ToolbarConfig.loadQuick(activity).toMutableList()
        while (quickKeysList.size < ToolbarConfig.MAX_QUICK) quickKeysList.add(".")
        var selectedSlot = 0

        val slotBoxesRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }

        val slotViews = mutableListOf<TextView>()
        val slotContainers = mutableListOf<FrameLayout>()

        for (i in 0 until ToolbarConfig.MAX_QUICK) {
            val fl = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(58f), 1f).apply {
                    if (i > 0) marginStart = dp(8f)
                }
                isClickable = true
                isFocusable = true
            }

            val tv = TextView(activity).apply {
                text = quickKeysList.getOrNull(i) ?: ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }

            fun updateSlotBox(isSel: Boolean) {
                if (isSel) {
                    tv.setTextColor(tokens.accent)
                    fl.background = GradientDrawable().apply {
                        cornerRadius = dp(14f).toFloat()
                        setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                        setStroke(dp(2f), tokens.accent)
                    }
                } else {
                    tv.setTextColor(tokens.textPrimary)
                    fl.background = GradientDrawable().apply {
                        cornerRadius = dp(14f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
            }

            updateSlotBox(i == selectedSlot)

            fl.setOnClickListener {
                selectedSlot = i
                for (j in slotContainers.indices) {
                    val sTv = slotViews[j]
                    if (j == selectedSlot) {
                        sTv.setTextColor(tokens.accent)
                        slotContainers[j].background = GradientDrawable().apply {
                            cornerRadius = dp(14f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                            setStroke(dp(2f), tokens.accent)
                        }
                    } else {
                        sTv.setTextColor(tokens.textPrimary)
                        slotContainers[j].background = GradientDrawable().apply {
                            cornerRadius = dp(14f).toFloat()
                            setColor(tokens.chipBg)
                            setStroke(dp(1f), tokens.cardBorder)
                        }
                    }
                }
            }

            fl.addView(tv)
            slotViews.add(tv)
            slotContainers.add(fl)
            slotBoxesRow.addView(fl)
        }
        card4.addView(slotBoxesRow)

        val quickScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val quickRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(2f), dp(14f), dp(8f))
        }

        val availableSymbols = listOf(".", ",", "?", "!", "'", "\"", ":", ";", "-", "@", "#", "$", "%", "&", "*", "/", "(", ")", "+", "=")
        for (sym in availableSymbols) {
            val tv = TextView(activity).apply {
                text = sym
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f).toFloat()
                    setColor(tokens.chipBg)
                    setStroke(dp(1f), tokens.cardBorder)
                }
                setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6f)
                }
                setOnClickListener {
                    quickKeysList[selectedSlot] = sym
                    slotViews[selectedSlot].text = sym
                    ToolbarConfig.saveQuick(activity, quickKeysList)
                    activity.notifySettingsChanged(restart = true)
                    activity.showToast("Slot ${selectedSlot + 1} set to '$sym'")
                }
            }
            quickRow.addView(tv)
        }
        quickScroll.addView(quickRow)
        card4.addView(quickScroll)

        card4.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Quick Keys Size", subtitle = "Proportion of quick keys on top toolbar",
            min = 70, max = 130, current = p.getInt(Skins.KEY_QUICK_KEYS_SIZE, Skins.DEFAULT_QUICK_KEYS_SIZE),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = Skins.DEFAULT_QUICK_KEYS_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_QUICK_KEYS_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card4)
        return root
    }

    fun buildPersonalizationScreen(
        onOpenShortcuts: () -> Unit,
        onOpenLearnedData: () -> Unit,
        onOpenFavoriteWords: () -> Unit
    ): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Personalization",
                description = "Custom text expansion shortcuts, favorite vocabulary, and on-device typing learning.",
                iconRes = R.drawable.ic_sparkles,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "ADAPTIVE INTELLIGENCE", tokens, density, R.drawable.ic_sparkles))

        val p = Skins.prefs(activity)
        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Adaptive Word Learning", "Learn frequently used terms and phrase combinations automatically",
            p.getBoolean("setting_adaptive_learning", true), tokens, density, R.drawable.ic_trending_up
        ) { v ->
            p.edit().putBoolean("setting_adaptive_learning", v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Personalized Suggestions", "Order suggestion strip based on your personal writing style",
            p.getBoolean("setting_personalized_suggestions", true), tokens, density, R.drawable.ic_user
        ) { v ->
            p.edit().putBoolean("setting_personalized_suggestions", v).apply()
            activity.notifySettingsChanged(restart = false)
        })
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "CUSTOM VOCABULARY & SHORTCUTS", tokens, density, R.drawable.ic_type))

        val repo = UserDictionaryRepository(activity.filesDir)
        val shortcutsCount = repo.getAllShortcuts().size
        val wordsCount = repo.getAllWords().size

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Text Expansion Shortcuts", "Auto-expand abbreviations into full phrases (e.g. omw)",
                "$shortcutsCount active", tokens, density, R.drawable.ic_type, true
            ) { onOpenShortcuts() }
        )

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Favorite Words", "Personal dictionary words prioritized in prediction strip",
                "$wordsCount words", tokens, density, R.drawable.ic_sparkles, true
            ) { onOpenFavoriteWords() }
        )

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Manage Learned Data", "Inspect learned vocabulary, clear typing history, and export",
                null, tokens, density, R.drawable.ic_shield, true
            ) { onOpenLearnedData() }
        )
        root.addView(card2)

        return root
    }

    fun buildAdvancedScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Advanced Settings",
                description = "Sound, haptic feedback pressure, voice typing, clipboard history, one-handed mode, and reset.",
                iconRes = R.drawable.ic_settings,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "SOUND & HAPTICS", tokens, density, R.drawable.ic_volume_2))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Keypress Sound", "Play audible click sound on key tap",
            p.getBoolean(Skins.KEY_SOUND, false), tokens, density, R.drawable.ic_volume_2
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SOUND, v).apply()
            activity.notifySettingsChanged(restart = false)
            if (v) {
                activity.playSoundPreview()
            }
        })

        card1.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Audio Click Volume", subtitle = "Audible keypress volume level",
            min = 1, max = 100, current = p.getInt(Skins.KEY_SOUND_VOLUME, 80),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_volume_2, step = 1,
            defaultValue = 80
        ) { v ->
            p.edit().putInt(Skins.KEY_SOUND_VOLUME, v).apply()
            activity.notifySettingsChanged(restart = false)
            activity.playSoundPreview(volume = v / 100f)
        })

        val soundTones = listOf(
            "default" to "Default Click",
            "multi" to "Piano Chords",
            "drum" to "Acoustic Drums"
        )
        val curTone = p.getString(Skins.KEY_SOUND_TONE, "default") ?: "default"
        card1.addView(
            ConceptComponents.createChipsRow(
                context = activity,
                title = "Sound Tone Scheme",
                options = soundTones,
                selectedId = curTone,
                tokens = tokens,
                density = density,
                iconRes = R.drawable.ic_volume_2
            ) { t ->
                p.edit().putString(Skins.KEY_SOUND_TONE, t).apply()
                activity.notifySettingsChanged(restart = false)
                activity.playSoundPreview(tone = t)
                activity.showToast("Sound: ${if (t == "multi") "Piano" else if (t == "drum") "Drum" else "Default"}")
            }
        )

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Haptic Feedback", "Vibrate device gently on every keypress",
            p.getBoolean(Skins.KEY_HAPTIC, false), tokens, density, R.drawable.ic_smartphone
        ) { v ->
            p.edit().putBoolean(Skins.KEY_HAPTIC, v).apply()
            activity.notifySettingsChanged(restart = false)
            if (v) {
                try {
                    gnosis.keyboard.Haptics.buzz(activity, p.getInt(Skins.KEY_HAPTIC_PRESSURE, 50))
                } catch (_: Exception) {}
            }
        })

        card1.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Vibration Strength", subtitle = "Tactile haptic motor vibration intensity",
            min = 10, max = 100, current = p.getInt(Skins.KEY_HAPTIC_PRESSURE, 50),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_smartphone, step = 1,
            defaultValue = 50
        ) { v ->
            p.edit().putInt(Skins.KEY_HAPTIC_PRESSURE, v).apply()
            activity.notifySettingsChanged(restart = false)
            try {
                gnosis.keyboard.Haptics.buzz(activity, v)
            } catch (_: Exception) {}
        })
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "VOICE TYPING & DICTATION", tokens, density, R.drawable.ic_sliders))

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Voice Input Mic Button", "Show dedicated microphone key on toolbar for speech-to-text",
            p.getBoolean("voice_input_enabled", true), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean("voice_input_enabled", v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        val speechEngines = listOf(
            "google" to "Google Speech Recognition",
            "system" to "Android Default Recognizer"
        )
        val curEngine = p.getString("setting_speech_engine", "google") ?: "google"
        card2.addView(
            ConceptComponents.createChipsRow(
                context = activity,
                title = "Speech-to-Text Engine",
                options = speechEngines,
                selectedId = curEngine,
                tokens = tokens,
                density = density,
                iconRes = R.drawable.ic_sliders
            ) { eng ->
                p.edit().putString("setting_speech_engine", eng).apply()
                activity.showToast("Voice engine: $eng")
            }
        )

        card2.addView(
            ConceptComponents.createNavRow(
                activity, "Test Microphone Permission", "Grant audio record permission for voice typing",
                null, tokens, density, R.drawable.ic_check, true
            ) {
                activity.voiceAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )
        root.addView(card2)

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "CLIPBOARD HISTORY", tokens, density, R.drawable.ic_sliders))

        card3.addView(ConceptComponents.createSwitchRow(
            activity, "Clipboard History Capture", "Save copied snippets and URLs in on-device history",
            p.getBoolean("clipboard_history_enabled", true), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean("clipboard_history_enabled", v).apply()
            ClipboardHistory.setEnabled(activity, ClipboardHistory.CAT_TEXT, v)
        })

        card3.addView(ConceptComponents.createSwitchRow(
            activity, "Auto-Capture Screenshots", "Automatically show recent screenshots in clipboard strip",
            p.getBoolean("clipboard_screenshots_enabled", false), tokens, density, R.drawable.ic_eye
        ) { v ->
            p.edit().putBoolean("clipboard_screenshots_enabled", v).apply()
            if (v) {
                activity.screenshotPermission.launch(
                    if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
                    else android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            } else {
                ClipboardHistory.setEnabled(activity, ClipboardHistory.CAT_SCREENSHOT, false)
            }
        })

        val clearClipBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        val clearClipBtn = ConceptComponents.createPillButton(activity, "Clear Clipboard History", tokens, density, false) {
            activity.showConfirmDialog(
                title = "Clear Clipboard History?",
                msg = "This will delete all copied snippets, clips, and screenshot previews.",
                confirmLabel = "Clear Clips",
                destructive = true
            ) {
                ClipboardHistory.clear(activity)
                activity.showToast("Clipboard history cleared")
            }
        }
        clearClipBox.addView(clearClipBtn)
        card3.addView(clearClipBox)
        root.addView(card3)

        val card4 = ConceptComponents.createCard(activity, tokens, density)
        card4.addView(ConceptComponents.createCardHeader(activity, "WINDOW & LAYOUT MODES", tokens, density, R.drawable.ic_sliders))

        val oneHandedModes = listOf(
            "off" to "Off (Centered)",
            "left" to "Left-Handed",
            "right" to "Right-Handed"
        )
        val curOneHanded = p.getString(Skins.KEY_ONE_HANDED, "off") ?: "off"
        card4.addView(
            ConceptComponents.createChipsRow(
                context = activity,
                title = "One-Handed Keyboard Mode",
                options = oneHandedModes,
                selectedId = curOneHanded,
                tokens = tokens,
                density = density,
                iconRes = R.drawable.ic_smartphone
            ) { m ->
                p.edit().putString(Skins.KEY_ONE_HANDED, m).apply()
                activity.notifySettingsChanged(restart = true)
                activity.showToast("One-handed mode: $m")
            }
        )

        card4.addView(ConceptComponents.createSwitchRow(
            activity, "Floating Keyboard", "Detached moveable mini-keyboard window",
            p.getBoolean(Skins.KEY_FLOATING, false), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean(Skins.KEY_FLOATING, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card4.addView(ConceptComponents.createSwitchRow(
            activity, "Hide App Launcher Icon", "Hide Gnosis Keyboard icon from app drawer (open from settings)",
            p.getBoolean(Skins.KEY_HIDE_LAUNCHER_ICON, false), tokens, density, R.drawable.ic_eye
        ) { v ->
            p.edit().putBoolean(Skins.KEY_HIDE_LAUNCHER_ICON, v).apply()
            activity.setLauncherIconHidden(v)
            activity.showToast(if (v) "Launcher icon hidden" else "Launcher icon restored")
        })
        root.addView(card4)

        val card5 = ConceptComponents.createCard(activity, tokens, density)
        card5.addView(ConceptComponents.createCardHeader(activity, "SYSTEM MAINTENANCE", tokens, density, R.drawable.ic_refresh_cw))

        val restartBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(6f))
        }
        val restartBtn = ConceptComponents.createPillButton(activity, "Restart Keyboard Engine", tokens, density, true) {
            activity.restartKeyboardEngine(showToastMessage = true)
        }
        restartBox.addView(restartBtn)
        card5.addView(restartBox)

        val resetBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        val resetBtn = ConceptComponents.createPillButton(activity, "Reset All Settings to Defaults", tokens, density, false) {
            activity.resetToDefault()
        }
        resetBox.addView(resetBtn)
        card5.addView(resetBox)

        val privacyCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14f), dp(4f), dp(14f), dp(14f))
            }
        }

        val shieldIcon = FrameLayout(activity).apply {
            val s = dp(36f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(12f) }
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(Color.argb(35, 16, 185, 129))
                setStroke(dp(1f), Color.argb(90, 16, 185, 129))
            }
            val iv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_shield)?.mutate()
                d?.setTint(Color.parseColor("#10B981"))
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = dp(7f)
                setPadding(pad, pad, pad, pad)
            }
            addView(iv)
        }
        privacyCard.addView(shieldIcon)

        val pTextCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8f)
            }
        }
        val pTitle = TextView(activity).apply {
            text = "100% On-Device Privacy Shield"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        pTextCol.addView(pTitle)

        val pDesc = TextView(activity).apply {
            text = "Keystrokes are processed locally without internet transmission"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(1f), 0, 0)
        }
        pTextCol.addView(pDesc)
        privacyCard.addView(pTextCol)

        val verifiedBadge = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(Color.argb(25, 16, 185, 129))
                setStroke(dp(1f), Color.parseColor("#10B981"))
            }
        }
        val checkIcon = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
            d?.setTint(Color.parseColor("#10B981"))
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { marginEnd = dp(4f) }
        }
        verifiedBadge.addView(checkIcon)
        val verifiedText = TextView(activity).apply {
            text = "Verified"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        }
        verifiedBadge.addView(verifiedText)
        privacyCard.addView(verifiedBadge)

        card5.addView(privacyCard)
        root.addView(card5)

        return root
    }

    fun buildThemeGridScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Theme Gallery",
                description = "Choose from carefully handcrafted themes or design your own personalized palette.",
                iconRes = R.drawable.ic_palette,
                tokens = tokens,
                density = density
            )
        )

        val p = Skins.prefs(activity)
        val curThemeMode = p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        val curTheme = p.getString(Skins.KEY_THEME, "lucid-void") ?: "lucid-void"

        val modeCard = ConceptComponents.createCard(activity, tokens, density)
        modeCard.addView(ConceptComponents.createCardHeader(activity, "THEME MODE FILTER", tokens, density, R.drawable.ic_sun))

        val modeOptions = listOf("Auto (System)", "Dark Mode", "Light Mode")
        val modeIdx = when (curThemeMode) {
            Skins.MODE_DARK -> 1
            Skins.MODE_LIGHT -> 2
            else -> 0
        }
        modeCard.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = modeOptions,
                selectedIndex = modeIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val targetMode = when (idx) {
                    1 -> Skins.MODE_DARK
                    2 -> Skins.MODE_LIGHT
                    else -> Skins.MODE_AUTO
                }
                p.edit().putString(Skins.KEY_THEME_MODE, targetMode).apply()
                p.edit().putString("settings_theme_mode", targetMode).apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
                activity.refreshCurrentScreen()
            }
        )
        root.addView(modeCard)

        data class GalleryThemeItem(val id: String, val name: String, val desc: String)
        val themes = when (curThemeMode) {
            Skins.MODE_LIGHT -> listOf(
                GalleryThemeItem("lucid-void_light", "Lucid Void Light", "Flagship frosted daylight acrylic & soft glow"),
                GalleryThemeItem("md3_dynamic_light", "Material You Light", "Material You dynamic daylight surface elevation"),
                GalleryThemeItem("onyx_light", "Daylight", "Clean soft daylight surfaces and crisp typography"),
                GalleryThemeItem("graphite_light", "Graphite Light", "Modern clean slate daylight tones"),
                GalleryThemeItem("midnight_light", "Midnight Light", "Calm oceanic daylight blue surfaces"),
                GalleryThemeItem("ocean_light", "Ocean Light", "Vibrant daylight cyan over crisp white"),
                GalleryThemeItem("ember_light", "Ember Light", "Warm sunrise glow with fiery amber accents"),
                GalleryThemeItem("rose_light", "Delicate Rose", "Soft pastel rose petal daylight tones"),
                GalleryThemeItem("cyberpunk_light", "Cyberpunk Light", "High-contrast daylight violet & magenta"),
                GalleryThemeItem("amoled_light", "AMOLED Daylight", "Crisp ultra-white surfaces with cobalt blue"),
                GalleryThemeItem("forest_light", "Emerald Forest Light", "Fresh mint and natural deep moss accents"),
                GalleryThemeItem("coral_light", "Warm Coral Light", "Delicate coral rose daylight tones"),
                GalleryThemeItem("contrast_light", "High Contrast Light", "Maximized contrast for daylight accessibility")
            )
            Skins.MODE_DARK -> listOf(
                GalleryThemeItem("lucid-void", "Lucid Void", "Flagship frosted obsidian glassmorphism & deep glow"),
                GalleryThemeItem("md3_dynamic", "Material You", "Tonal surface elevation and adaptive hues"),
                GalleryThemeItem("onyx", "Onyx Black", "Pure AMOLED black for OLED battery efficiency"),
                GalleryThemeItem("graphite", "Graphite Slate", "Modern minimalist slate charcoal tones"),
                GalleryThemeItem("midnight", "Midnight Navy", "Calm oceanic dark blue surfaces"),
                GalleryThemeItem("ocean", "Deep Ocean", "Vibrant cyan glow over abyssal blue"),
                GalleryThemeItem("ember", "Sunset Ember", "Warm sunset glow with fiery orange accents"),
                GalleryThemeItem("cyberpunk", "Neon Cyberpunk", "High-contrast neon pink and midnight violet"),
                GalleryThemeItem("amoled", "AMOLED Pure", "Deep obsidian zero-light pixels"),
                GalleryThemeItem("forest", "Emerald Forest", "Natural deep moss and mint accents"),
                GalleryThemeItem("coral", "Warm Coral", "Delicate coral rose and soft dark surfaces"),
                GalleryThemeItem("rose", "Delicate Rose", "Soft dark pastel rose petal tones"),
                GalleryThemeItem("contrast", "High Contrast", "Maximized contrast for high visibility")
            )
            else -> listOf(
                GalleryThemeItem("lucid-void", "Lucid Void (Auto)", "Adaptive frosted glassmorphism that transitions with system theme"),
                GalleryThemeItem("md3_dynamic", "Material You (Auto)", "Tonal Material You surfaces dynamically matching dark/light mode"),
                GalleryThemeItem("onyx", "Onyx / Daylight (Auto)", "Onyx pure AMOLED in dark mode, Daylight in light mode"),
                GalleryThemeItem("graphite", "Graphite (Auto)", "Graphite slate charcoal in dark mode, clean slate in light mode"),
                GalleryThemeItem("midnight", "Midnight (Auto)", "Oceanic navy in dark mode, calm blue in light mode"),
                GalleryThemeItem("ocean", "Ocean (Auto)", "Abyssal blue in dark mode, bright cyan in light mode"),
                GalleryThemeItem("ember", "Ember (Auto)", "Sunset ember in dark mode, morning amber in light mode"),
                GalleryThemeItem("cyberpunk", "Cyberpunk (Auto)", "Neon violet in dark mode, vivid lilac in light mode"),
                GalleryThemeItem("amoled", "AMOLED / Daylight (Auto)", "Zero-power black in dark mode, crisp daylight in light mode"),
                GalleryThemeItem("forest", "Forest (Auto)", "Deep moss in dark mode, fresh mint in light mode"),
                GalleryThemeItem("coral", "Coral (Auto)", "Warm dark coral in dark mode, soft rose coral in light mode"),
                GalleryThemeItem("rose", "Rose (Auto)", "Midnight rose in dark mode, delicate petal in light mode"),
                GalleryThemeItem("contrast", "High Contrast (Auto)", "Pure high contrast daylight & night accessibility")
            )
        }

        for (item in themes) {
            val previewThemeId = if (curThemeMode == Skins.MODE_AUTO) {
                if (tokens.isLight) Skins.getPairedThemeId(item.id, true) else Skins.getPairedThemeId(item.id, false)
            } else {
                item.id
            }
            val preset = Skins.PRESETS[previewThemeId] ?: Skins.PRESETS[item.id] ?: Skins.PRESETS["onyx"]!!

            val isSelected = when (curThemeMode) {
                Skins.MODE_LIGHT -> curTheme == item.id || Skins.getPairedThemeId(curTheme, true) == item.id
                Skins.MODE_DARK -> curTheme == item.id || Skins.getPairedThemeId(curTheme, false) == item.id
                else -> curTheme == item.id || previewThemeId == curTheme ||
                    Skins.getPairedThemeId(curTheme, false) == Skins.getPairedThemeId(item.id, false)
            }

            val card = ConceptComponents.createCard(activity, tokens, density).apply {
                if (isSelected) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(tokens.cardRadiusDp).toFloat()
                        setColor(tokens.surface)
                        setStroke(dp(2.5f), tokens.accent)
                    }
                }
            }

            val contentCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(12f), dp(14f), dp(14f))
            }

            val keyboardChassis = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f).toFloat()
                    try {
                        setColor(Color.parseColor(preset.bg))
                    } catch (_: Exception) {
                        setColor(Color.parseColor("#121212"))
                    }
                    try {
                        setStroke(dp(1f), Color.parseColor(preset.keyBorder))
                    } catch (_: Exception) {}
                }
            }

            val stripRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14f)).apply {
                    bottomMargin = dp(6f)
                }
            }
            for (cand in listOf("Gnosis", "Smart", "Flow")) {
                val candTv = TextView(activity).apply {
                    text = cand
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    setTypeface(null, Typeface.BOLD)
                    try {
                        setTextColor(Color.parseColor(preset.muted))
                    } catch (_: Exception) {
                        setTextColor(Color.LTGRAY)
                    }
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        marginStart = dp(2f)
                        marginEnd = dp(2f)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(4f).toFloat()
                        try {
                            setColor(Color.parseColor(preset.chip))
                            setStroke(dp(0.5f), Color.parseColor(preset.chipBorder))
                        } catch (_: Exception) {}
                    }
                }
                stripRow.addView(candTv)
            }
            keyboardChassis.addView(stripRow)

            fun makeMiniRow(keys: List<String>): LinearLayout {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(15f)).apply {
                        bottomMargin = dp(3f)
                    }
                }
                for (k in keys) {
                    val isSpace = k == "SPACE"
                    val isAction = k == "Enter"
                    val isShift = k == "Shift"
                    val keyWeight = when {
                        isSpace -> 3.2f
                        isAction -> 1.5f
                        isShift -> 1.3f
                        else -> 1.0f
                    }
                    val keyView = TextView(activity).apply {
                        text = if (isSpace) "" else k
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isAction || isShift) 6.5f else 7.5f)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        try {
                            setTextColor(Color.parseColor(preset.text))
                        } catch (_: Exception) {
                            setTextColor(Color.WHITE)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, keyWeight
                        ).apply {
                            marginStart = dp(1f)
                            marginEnd = dp(1f)
                        }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(3.5f).toFloat()
                            try {
                                if (isSpace || isAction) {
                                    setColor(Color.parseColor(preset.defaultAccent))
                                } else {
                                    setColor(Color.parseColor(preset.key))
                                    setStroke(dp(0.7f), Color.parseColor(preset.keyBorder))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    row.addView(keyView)
                }
                return row
            }

            keyboardChassis.addView(makeMiniRow(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")))
            keyboardChassis.addView(makeMiniRow(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")))
            keyboardChassis.addView(makeMiniRow(listOf("Shift", "Z", "X", "C", "SPACE", "B", "N", "M", "Enter")))

            contentCol.addView(keyboardChassis)

            val bottomRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10f), 0, 0)
            }

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleTv = TextView(activity).apply {
                text = item.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
            }
            textCol.addView(titleTv)

            val descTv = TextView(activity).apply {
                text = item.desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(1f), 0, 0)
            }
            textCol.addView(descTv)
            bottomRow.addView(textCol)

            val applyThemeAction = {
                p.edit().putString(Skins.KEY_THEME, item.id).apply()
                if (!preset.isLight) p.edit().putString(Skins.KEY_LAST_DARK, item.id).apply()
                activity.notifySettingsChanged(restart = true)
                activity.showToast("Applied theme: ${item.name}")
                activity.reloadTheme()
                onThemeChanged()
                activity.refreshCurrentScreen()
            }

            if (isSelected) {
                val activeBadge = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14f).toFloat()
                        setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                        setStroke(dp(1f), tokens.accent)
                    }
                    setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                }
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    layoutParams = LinearLayout.LayoutParams(dp(13f), dp(13f)).apply { marginEnd = dp(4f) }
                }
                activeBadge.addView(checkIv)
                val activeTv = TextView(activity).apply {
                    text = "Active"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.accent)
                }
                activeBadge.addView(activeTv)
                bottomRow.addView(activeBadge)
            } else {
                val applyBtn = TextView(activity).apply {
                    text = "Apply"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                    setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { applyThemeAction() }
                }
                bottomRow.addView(applyBtn)
            }

            contentCol.addView(bottomRow)
            card.addView(contentCol)

            card.isClickable = true
            card.setOnClickListener {
                if (!isSelected) applyThemeAction()
            }
            root.addView(card)
        }
        return root
    }

    fun buildLearnedDataScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Manage Learned Data",
                description = "Inspect vocabulary learned from your typing habits, clear learned history, and backup.",
                iconRes = R.drawable.ic_shield,
                tokens = tokens,
                density = density
            )
        )

        val repo = UserDictionaryRepository(activity.filesDir)
        val pWords = repo.getAllWords().size

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "LEARNED VOCABULARY OVERVIEW", tokens, density, R.drawable.ic_sparkles))

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Learned Words Count", "Words dynamically added from typing habits",
                "$pWords words", tokens, density, R.drawable.ic_type, false
            ) {}
        )

        val clearBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        val clearBtn = ConceptComponents.createPillButton(activity, "Clear All Learned Words", tokens, density, false) {
            activity.showConfirmDialog(
                title = "Clear All Learned Words?",
                msg = "This will permanently erase all custom vocabulary learned from your typing history.",
                confirmLabel = "Clear All",
                destructive = true
            ) {
                WordDictionary.clearLearnedWords(activity)
                repo.clearAll()
                activity.notifySettingsChanged(restart = true)
                activity.showToast("Learned dictionary cleared")
                activity.openHomeScreen()
            }
        }
        clearBox.addView(clearBtn)
        card1.addView(clearBox)
        root.addView(card1)

        return root
    }

    fun buildQuickSettingsScreen(
        onOpenFullSettings: () -> Unit,
        onOpenSearch: () -> Unit
    ): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Quick Settings",
                description = "Fast toggle controls for theme appearance, accent colors, sounds, and vibration.",
                iconRes = R.drawable.ic_sliders,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "FAST THEME SELECTOR", tokens, density, R.drawable.ic_palette))

        val modeOptions = listOf("Auto", "Dark", "Light")
        val currentMode = p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        val modeIdx = when (currentMode) {
            Skins.MODE_DARK -> 1
            Skins.MODE_LIGHT -> 2
            else -> 0
        }
        card1.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = modeOptions,
                selectedIndex = modeIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val target = when (idx) {
                    1 -> Skins.MODE_DARK
                    2 -> Skins.MODE_LIGHT
                    else -> Skins.MODE_AUTO
                }
                p.edit().putString(Skins.KEY_THEME_MODE, target).apply()
                p.edit().putString("settings_theme_mode", target).apply()
                activity.notifySettingsChanged(restart = true)
                activity.reloadTheme()
                onThemeChanged()
            }
        )

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Haptic Vibration", "Vibrate device gently on key taps",
            p.getBoolean(Skins.KEY_HAPTIC, false), tokens, density, R.drawable.ic_smartphone
        ) { v ->
            p.edit().putBoolean(Skins.KEY_HAPTIC, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Keypress Sound", "Audio click on key taps",
            p.getBoolean(Skins.KEY_SOUND, false), tokens, density, R.drawable.ic_volume_2
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SOUND, v).apply()
            activity.notifySettingsChanged(restart = false)
            if (v) {
                activity.playSoundPreview()
            }
        })
        root.addView(card1)

        val actionBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(12f))
        }
        val fullBtn = ConceptComponents.createPillButton(activity, "Open Full Settings", tokens, density, true) {
            onOpenFullSettings()
        }
        actionBox.addView(fullBtn)
        root.addView(actionBox)

        return root
    }

    fun buildTypingStatisticsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        var activePeriod = StatsPeriod.DAYS_7

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun updateStatsView() {
            container.removeAllViews()
            val stats = statsRepo.getStats(activePeriod)

            if (stats.isEmpty) {
                val emptyCard = ConceptComponents.createCard(activity, tokens, density).apply {
                    gravity = Gravity.CENTER
                    val pad = dp(32f)
                    setPadding(pad, pad, pad, pad)
                }
                val chartIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_bar_chart)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                        bottomMargin = dp(12f)
                    }
                }
                emptyCard.addView(chartIv)

                val emptyTitle = TextView(activity).apply {
                    text = "No Typing Data Yet"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                    gravity = Gravity.CENTER
                }
                emptyCard.addView(emptyTitle)

                val emptySub = TextView(activity).apply {
                    text = "Start typing anywhere using Gnosis Keyboard to see your live keystrokes, accuracy, and estimated time saved."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(dp(12f), dp(4f), dp(12f), 0)
                }
                emptyCard.addView(emptySub)
                container.addView(emptyCard)
                return
            }

            val kpiRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14f)
                }
            }

            fun makeKpi(count: String, label: String, trend: Int, isTrendGood: Boolean): View {
                return LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val pad = dp(12f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(3f)
                        marginEnd = dp(3f)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16f).toFloat()
                        setColor(tokens.surface)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                    ConceptComponents.applyTactileFeedback(this, cornerRadiusDp = 16f, pressScale = 0.96f, tokens = tokens)

                    val lTv = TextView(activity).apply {
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(tokens.textSecondary)
                    }
                    addView(lTv)

                    val cTv = TextView(activity).apply {
                        text = count
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                        setPadding(0, dp(2f), 0, dp(2f))
                    }
                    addView(cTv)

                    val numVal = count.replace(",", "").toLongOrNull()
                    if (numVal != null && numVal > 0) {
                        val anim = ValueAnimator.ofInt(0, numVal.toInt().coerceAtMost(1000000)).apply {
                            duration = 450L
                            interpolator = DecelerateInterpolator(1.8f)
                            addUpdateListener { va ->
                                val v = va.animatedValue as Int
                                cTv.text = "%,d".format(v)
                            }
                        }
                        anim.start()
                    }

                    val tTv = TextView(activity).apply {
                        text = if (trend >= 0) "\u2191 +${trend}%" else "\u2193 -${kotlin.math.abs(trend)}%"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (isTrendGood) Color.parseColor("#10B981") else Color.parseColor("#F43F5E"))
                    }
                    addView(tTv)
                }
            }

            kpiRow.addView(makeKpi("${stats.totalKeystrokes}", "Keystrokes", stats.keystrokesTrendPct, true))
            kpiRow.addView(makeKpi("${stats.totalCorrections}", "Corrections", stats.correctionsTrendPct, false))
            kpiRow.addView(makeKpi(stats.timeSavedFormatted, "Time Saved", stats.timeSavedTrendPct, true))
            container.addView(kpiRow)

            if (stats.dailyActivity.isNotEmpty()) {
                container.addView(ConceptComponents.createSectionHeader(activity, "Daily Activity", tokens, density))
                val chartCard = ConceptComponents.createCard(activity, tokens, density).apply {
                    val pad = dp(16f)
                    setPadding(pad, pad, pad, pad)
                }

                val infoHint = TextView(activity).apply {
                    text = "Tap any bar to inspect daily keystrokes"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(tokens.textSecondary)
                    setPadding(0, 0, 0, dp(6f))
                }
                chartCard.addView(infoHint)

                val maxKeys = stats.dailyActivity.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                val chartRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110f))
                }

                for (day in stats.dailyActivity) {
                    val col = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                            marginStart = dp(2f)
                            marginEnd = dp(2f)
                        }
                    }

                    val barHeight = ((day.second.toFloat() / maxKeys.toFloat()) * dp(75f)).toInt().coerceAtLeast(dp(6f))
                    val bar = View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(18f), 0)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(6f).toFloat()
                            val topCol = tokens.accent
                            val btmCol = ColorUtils.blendARGB(tokens.accent, tokens.surface, 0.35f)
                            colors = intArrayOf(topCol, btmCol)
                            orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        }
                    }
                    col.addView(bar)

                    val barAnim = ValueAnimator.ofInt(0, barHeight).apply {
                        duration = 380L
                        startDelay = (chartRow.childCount * 28L)
                        interpolator = DecelerateInterpolator(1.6f)
                        addUpdateListener { va ->
                            bar.layoutParams.height = va.animatedValue as Int
                            bar.requestLayout()
                        }
                    }
                    barAnim.start()

                    col.setOnClickListener {
                        try {
                            col.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}
                        bar.animate().scaleY(1.15f).setDuration(70).withEndAction {
                            bar.animate().scaleY(1.0f).setDuration(100).start()
                        }.start()
                        infoHint.text = "\uD83D\uDCC5 ${day.first}: %,d keystrokes".format(day.second)
                        infoHint.setTextColor(tokens.accent)
                        infoHint.setTypeface(null, Typeface.BOLD)
                    }

                    val label = TextView(activity).apply {
                        text = day.first.takeLast(2)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(4f), 0, 0)
                    }
                    col.addView(label)
                    chartRow.addView(col)
                }
                chartCard.addView(chartRow)
                container.addView(chartCard)
            }

            if (stats.topSuggestedWords.isNotEmpty()) {
                container.addView(ConceptComponents.createSectionHeader(activity, "Top Suggested Words", tokens, density))
                val wordsCard = ConceptComponents.createCard(activity, tokens, density).apply {
                    val pad = dp(14f)
                    setPadding(pad, pad, pad, pad)
                }

                val maxCount = stats.topSuggestedWords.maxOfOrNull { it.second } ?: 1
                for (i in stats.topSuggestedWords.indices) {
                    val item = stats.topSuggestedWords[i]
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(8f)
                        }
                    }

                    val rankTv = TextView(activity).apply {
                        text = "${i + 1}."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(tokens.textSecondary)
                        layoutParams = LinearLayout.LayoutParams(dp(22f), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    row.addView(rankTv)

                    val wTv = TextView(activity).apply {
                        text = item.first
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                        layoutParams = LinearLayout.LayoutParams(dp(85f), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    row.addView(wTv)

                    val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = maxCount
                        progress = 0
                        progressDrawable = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                            setColor(tokens.accent)
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dp(8f), 1f).apply {
                            marginStart = dp(8f)
                            marginEnd = dp(12f)
                        }
                    }
                    row.addView(bar)

                    ObjectAnimator.ofInt(bar, "progress", 0, item.second).apply {
                        duration = 420L
                        startDelay = (i * 35L)
                        interpolator = DecelerateInterpolator(1.8f)
                        start()
                    }

                    val cTv = TextView(activity).apply {
                        text = "${item.second}"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(tokens.textSecondary)
                    }
                    row.addView(cTv)

                    wordsCard.addView(row)
                }
                container.addView(wordsCard)
            }

            container.addView(
                ConceptComponents.createActionRow(
                    activity,
                    "Reset Statistics Data",
                    "Clear all typing metrics and counters",
                    R.drawable.ic_trash_2,
                    isDestructive = true,
                    tokens = tokens,
                    density = density
                ) {
                    AlertDialog.Builder(activity)
                        .setTitle("Reset Statistics")
                        .setMessage("Are you sure you want to clear all typing activity, metrics, and word frequencies?")
                        .setPositiveButton("Reset") { _, _ ->
                            statsRepo.clearAllStats()
                            updateStatsView()
                            activity.showToast("Statistics reset successfully")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            )

            container.addView(
                ConceptComponents.createCallout(
                    activity,
                    "Better suggestions, with every word. Keystrokes are measured 100% offline.",
                    R.drawable.ic_shield,
                    tokens,
                    density
                )
            )
        }

        val selector = ConceptComponents.createSegmentSelector(
            activity,
            listOf("7 Days", "30 Days", "All Time"),
            0,
            tokens,
            density
        ) { idx ->
            activePeriod = when (idx) {
                0 -> StatsPeriod.DAYS_7
                1 -> StatsPeriod.DAYS_30
                else -> StatsPeriod.ALL_TIME
            }
            updateStatsView()
        }
        root.addView(selector)

        updateStatsView()
        root.addView(container)

        statsRepo.addListener {
            activity.runOnUiThread { updateStatsView() }
        }

        return root
    }

    fun buildSearchSettingsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val searchBox = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.chipBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14f)
            }
        }

        val searchIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_search)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        }
        searchBox.addView(searchIv)

        val searchEt = EditText(activity).apply {
            hint = "Search all 62+ settings..."
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = null
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        searchBox.addView(searchEt)

        val clearBtn = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f))
            visibility = View.GONE
            setOnClickListener { searchEt.setText("") }
        }
        searchBox.addView(clearBtn)
        root.addView(searchBox)

        data class SettingItem(val name: String, val category: String, val keywords: String, val action: () -> Unit)
        val catalog = listOf(
            SettingItem("Auto-correction", "Typing & Suggestions", "autocorrect typo spell fix punctuation", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Next-Word Prediction", "Typing & Suggestions", "predict next word suggestion ai n-gram", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Continuous Glide Typing", "Typing & Suggestions", "gesture swipe glide trace sliding", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Gesture Delete", "Typing & Suggestions", "swipe backspace delete word swipe left", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Spacebar Cursor Control", "Typing & Suggestions", "space trackpad cursor slide move navigation", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Emoji Suggestion Strip", "Typing & Suggestions", "emoji emoticons reactions symbols stickers", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Double Space to Period", "Typing & Suggestions", "period dot punctuation shortcut tap", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Auto-Capitalization", "Typing & Suggestions", "caps uppercase capital sentences words", { navigateTo(buildTypingScreen(), "Typing & Suggestions") }),
            SettingItem("Preset Theme Gallery", "Appearance", "theme amoled dark light color palette", { navigateTo(buildThemeGridScreen(), "Theme Gallery") }),
            SettingItem("Design Studio & Geometry", "Appearance", "radius corners padding gap borders height scale", { navigateTo(buildAppearanceScreen { navigateTo(buildThemeGridScreen(), "Theme Gallery") }, "Appearance") }),
            SettingItem("Custom Fonts Typography", "Appearance", "fonts store typeface roboto inter jetbrains mono custom", { activity.showFontStoreDialog() }),
            SettingItem("Lucid Void Glassmorphism", "Appearance", "glass frosted blur transparent background effect", { navigateTo(buildAppearanceScreen { navigateTo(buildThemeGridScreen(), "Theme Gallery") }, "Appearance") }),
            SettingItem("Custom Photo Background", "Appearance", "wallpaper picture gallery photo crop background", { activity.showCropDialog() }),
            SettingItem("Layout Style & Downloader", "Language & Dictionary", "qwerty dvorak colemak azerty layout download", { activity.showLayoutDownloaderDialog() }),
            SettingItem("Additional Dictionaries Store", "Language & Dictionary", "dictionary autocorrect wordlist hinglish english spanish german hindi", { activity.showDictionaryStoreDialog() }),
                        SettingItem("Quick Access Toolbar Keys", "Language & Dictionary", "toolbar top bar 3 keys shortcuts customizer", { navigateTo(buildLanguageScreen { navigateTo(buildLearnedDataScreen(), "Learned Data") }, "Language & Dictionary") }),
            SettingItem("Text Expansion Shortcuts", "Personalization", "snippets shortcuts text expansion omw brb", { navigateTo(buildShortcutsScreen(), "Text Shortcuts") }),
            SettingItem("Favorite Vocabulary Words", "Personalization", "words custom personal vocabulary custom words", { navigateTo(buildFavoriteWordsScreen(), "Favorite Words") }),
            SettingItem("Manage Learned Dictionary", "Personalization", "learned data export import backup wipe trie", { navigateTo(buildLearnedDataScreen(), "Learned Data") }),
            SettingItem("Haptic Feedback Pressure", "Advanced Settings", "vibrate vibration haptics pressure millisecond", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Keypress Audio & Click Tone", "Advanced Settings", "audio sound volume click mechanical typewriter", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Clipboard History & Screenshots", "Advanced Settings", "clipboard copy paste pin screenshots capture", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("One-Handed Keyboard Mode", "Advanced Settings", "one handed left right single hand compact", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Floating Keyboard Mode", "Advanced Settings", "floating movable window freeform keyboard", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Offline Privacy Shield", "About & Privacy", "offline security telemetry private local storage", { navigateTo(buildAboutPrivacyScreen(), "About & Privacy") }),
            SettingItem("Restart Keyboard Engine", "Advanced Settings", "restart reload service reset crash fix", { activity.restartKeyboardEngine() }),
            SettingItem("Reset All Settings to Defaults", "Advanced Settings", "reset factory restore clear preferences", { activity.resetToDefault() })
        )

        val resultsCard = ConceptComponents.createCard(activity, tokens, density)
        val emptyText = TextView(activity).apply {
            text = "No matching settings found\nTry typing 'vibration', 'theme', or 'gesture'"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, dp(30f), 0, dp(30f))
            visibility = View.GONE
        }

        fun updateSearch(q: String) {
            resultsCard.removeAllViews()
            clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
            val matches = if (q.isBlank()) catalog.take(8) else catalog.filter {
                it.name.contains(q, ignoreCase = true) || it.keywords.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
            }

            if (matches.isEmpty()) {
                emptyText.visibility = View.VISIBLE
            } else {
                emptyText.visibility = View.GONE
                for (i in matches.indices) {
                    val m = matches[i]
                    val row = ConceptComponents.createNavRow(
                        activity,
                        m.name,
                        m.category,
                        null,
                        tokens,
                        density,
                        R.drawable.ic_search,
                        true,
                        m.action
                    )
                    resultsCard.addView(row)
                    if (i < matches.size - 1) {
                        resultsCard.addView(ConceptComponents.createDivider(activity, tokens, density))
                    }
                }
            }
        }

        searchEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateSearch(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        updateSearch("")
        root.addView(resultsCard)
        root.addView(emptyText)

        return root
    }

    fun buildAboutPrivacyScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val headerCard = ConceptComponents.createCard(activity, tokens, density).apply {
            gravity = Gravity.CENTER
            val pad = dp(20f)
            setPadding(pad, pad, pad, pad)
        }
        val logoIv = ImageView(activity).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_gnosis_logo))
            layoutParams = LinearLayout.LayoutParams(dp(52f), dp(52f)).apply {
                bottomMargin = dp(8f)
            }
        }
        headerCard.addView(logoIv)

        val titleTv = TextView(activity).apply {
            text = "Gnosis Keyboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        headerCard.addView(titleTv)

        val verTv = TextView(activity).apply {
            text = "v2.1.87 (Build 187) \u2022 Release"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.accent)
            setPadding(0, dp(2f), 0, 0)
        }
        headerCard.addView(verTv)
        root.addView(headerCard)

        root.addView(
            ConceptComponents.createCallout(
                activity,
                "100% Offline & Private: Zero network permission, zero internet access, zero telemetry. All learned typing patterns remain strictly isolated on your device.",
                R.drawable.ic_shield,
                tokens,
                density
            )
        )

        root.addView(ConceptComponents.createSectionHeader(activity, "Information & Diagnostics", tokens, density))
        val listCard = ConceptComponents.createCard(activity, tokens, density)

        listCard.addView(
            ConceptComponents.createNavRow(
                activity,
                "Dictionary & Neural Trie",
                "Local n-gram frequency database and user vocabulary",
                null,
                tokens,
                density,
                R.drawable.ic_type,
                true
            ) {
                navigateTo(buildLearnedDataScreen(), "Learned Data")
            }
        )
        listCard.addView(ConceptComponents.createDivider(activity, tokens, density))

        listCard.addView(
            ConceptComponents.createNavRow(
                activity,
                "Open Source Licenses",
                "View third-party software acknowledgements",
                null,
                tokens,
                density,
                R.drawable.ic_info,
                true
            ) {
                showLicensesDialog()
            }
        )
        listCard.addView(ConceptComponents.createDivider(activity, tokens, density))

        listCard.addView(
            ConceptComponents.createNavRow(
                activity,
                "Technical Specifications",
                "Android 15 SDK 35, offline architecture, and compiler target",
                null,
                tokens,
                density,
                R.drawable.ic_settings,
                true
            ) {
                showAppInfoDialog()
            }
        )
        root.addView(listCard)

        root.addView(ConceptComponents.createSectionHeader(activity, "Engine Controls", tokens, density))
        val recoveryCard = ConceptComponents.createCard(activity, tokens, density)

        recoveryCard.addView(
            ConceptComponents.createActionRow(
                activity,
                "Restart Keyboard Engine",
                "Force-reload IME service, skin shaders, and layout buffers",
                R.drawable.ic_refresh_cw,
                isDestructive = false,
                tokens = tokens,
                density = density
            ) {
                activity.restartKeyboardEngine()
            }
        )
        recoveryCard.addView(ConceptComponents.createDivider(activity, tokens, density))

        recoveryCard.addView(
            ConceptComponents.createActionRow(
                activity,
                "Reset All Settings to Defaults",
                "Restore factory configuration and reset all 62+ preferences",
                R.drawable.ic_trash_2,
                isDestructive = true,
                tokens = tokens,
                density = density
            ) {
                activity.resetToDefault()
            }
        )
        root.addView(recoveryCard)

        return root
    }

    fun buildOnboardingScreen(step: Int, onComplete: () -> Unit): View {
        val root = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                colors = if (tokens.isLight) {
                    intArrayOf(Color.parseColor("#EEF4FF"), Color.parseColor("#E0ECFE"), Color.parseColor("#F6F7FA"))
                } else {
                    intArrayOf(Color.parseColor("#152136"), Color.parseColor("#101726"), Color.parseColor("#0B0E14"))
                }
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
        }

        val mountainIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_mountain_landscape)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_END
            alpha = 0.2f
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM)
        }
        root.addView(mountainIv)

        val centerCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setPadding(dp(28f), dp(24f), dp(28f), dp(24f))
            }
        }

        when (step) {
            1 -> {
                val logoIv = ImageView(activity).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_gnosis_logo))
                    layoutParams = LinearLayout.LayoutParams(dp(64f), dp(64f)).apply { bottomMargin = dp(16f) }
                }
                centerCol.addView(logoIv)

                val title = TextView(activity).apply {
                    text = "Welcome to Gnosis Keyboard"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                    gravity = Gravity.CENTER
                }
                centerCol.addView(title)

                val sub = TextView(activity).apply {
                    text = "Type smarter. Think faster."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4f), 0, dp(24f))
                }
                centerCol.addView(sub)

                fun addBullet(text: String) {
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(8f)
                        }
                    }
                    val chk = ImageView(activity).apply {
                        val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                        d?.setTint(tokens.accent)
                        setImageDrawable(d)
                        layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(10f) }
                    }
                    row.addView(chk)
                    val tv = TextView(activity).apply {
                        this.text = text
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        setTextColor(tokens.textPrimary)
                    }
                    row.addView(tv)
                    centerCol.addView(row)
                }
                addBullet("Smarter context suggestions")
                addBullet("Precise on-device autocorrection")
                addBullet("Fully private & offline architecture")

                val btn = ConceptComponents.createPillButton(activity, "Get Started", true, tokens, density) {
                    navigateTo(buildOnboardingScreen(2, onComplete), "Enable Keyboard")
                }
                centerCol.addView(btn)
            }
            2 -> {
                val shieldIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_shield)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { bottomMargin = dp(16f) }
                }
                centerCol.addView(shieldIv)

                val title = TextView(activity).apply {
                    text = "Enable Keyboard"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                }
                centerCol.addView(title)

                val instructions = TextView(activity).apply {
                    text = "1. Go to Settings > Languages & input\n2. Enable Gnosis Keyboard\n3. Select it as your default keyboard"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    setLineSpacing(dp(6f).toFloat(), 1.0f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14f), 0, dp(24f))
                }
                centerCol.addView(instructions)

                val btn = ConceptComponents.createPillButton(activity, "Open Settings", true, tokens, density) {
                    activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    navigateTo(buildOnboardingScreen(3, onComplete), "Select Keyboard")
                }
                centerCol.addView(btn)
            }
            3 -> {
                val kbIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_keyboard)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { bottomMargin = dp(16f) }
                }
                centerCol.addView(kbIv)

                val title = TextView(activity).apply {
                    text = "Select Keyboard"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                }
                centerCol.addView(title)

                val sub = TextView(activity).apply {
                    text = "Choose Gnosis Keyboard from the list of available input methods."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6f), 0, dp(20f))
                }
                centerCol.addView(sub)

                val btn = ConceptComponents.createPillButton(activity, "Choose Input Method", true, tokens, density) {
                    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                    navigateTo(buildOnboardingScreen(4, onComplete), "Setup Complete")
                }
                centerCol.addView(btn)
            }
            4 -> {
                val checkIv = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                    d?.setTint(tokens.accent)
                    setImageDrawable(d)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.setAlphaComponent(tokens.accent, 35))
                    }
                    val pad = dp(14f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams(dp(64f), dp(64f)).apply { bottomMargin = dp(16f) }
                }
                centerCol.addView(checkIv)

                val title = TextView(activity).apply {
                    text = "Setup Complete!"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                }
                centerCol.addView(title)

                val sub = TextView(activity).apply {
                    text = "Gnosis Keyboard is now ready to use. Start typing and enjoy a smarter experience."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6f), 0, dp(24f))
                }
                centerCol.addView(sub)

                val btn = ConceptComponents.createPillButton(activity, "Start Using", true, tokens, density) {
                    onComplete()
                }
                centerCol.addView(btn)
            }
        }

        root.addView(centerCol)
        return root
    }

    fun buildShortcutsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val repo = UserDictionaryRepository(activity.filesDir)

        val descCard = ConceptComponents.createCard(activity, tokens, density).apply {
            val pad = dp(14f)
            setPadding(pad, pad, pad, pad)
        }
        val dTitle = TextView(activity).apply {
            text = "Case-Adaptive Text Expansion"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        descCard.addView(dTitle)
        val dSub = TextView(activity).apply {
            text = "Type any shortcut and tap spacebar to expand it into your full text. Typing in UPPERCASE or Titlecase automatically adapts the expansion."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(3f), 0, 0)
        }
        descCard.addView(dSub)
        root.addView(descCard)

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun renderShortcuts() {
            listContainer.removeAllViews()
            val shortcuts = repo.getAllShortcuts()
            if (shortcuts.isEmpty()) {
                val emptyTv = TextView(activity).apply {
                    text = "No shortcuts added yet.\nTap below to create your first shortcut."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24f), 0, dp(24f))
                }
                listContainer.addView(emptyTv)
                return
            }

            val card = ConceptComponents.createCard(activity, tokens, density)
            val entries = shortcuts.entries.toList()
            for (i in entries.indices) {
                val (k, v) = entries[i]
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = dp(14f)
                    setPadding(pad, pad, pad, pad)
                }

                val pill = TextView(activity).apply {
                    text = k
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.accent)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(ColorUtils.setAlphaComponent(tokens.accent, 30))
                    }
                    setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
                }
                row.addView(pill)

                val arrowTv = TextView(activity).apply {
                    text = " \u2192 "
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    setPadding(dp(6f), 0, dp(6f), 0)
                }
                row.addView(arrowTv)

                val expTv = TextView(activity).apply {
                    text = v
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(expTv)

                val delBtn = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                    d?.setTint(Color.parseColor("#F43F5E"))
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
                    isClickable = true
                    setOnClickListener {
                        repo.removeShortcut(k)
                        renderShortcuts()
                        activity.showToast("Removed shortcut '$k'")
                    }
                }
                row.addView(delBtn)

                card.addView(row)
                if (i < entries.size - 1) {
                    card.addView(ConceptComponents.createDivider(activity, tokens, density))
                }
            }
            listContainer.addView(card)
        }

        renderShortcuts()
        root.addView(listContainer)

        root.addView(
            ConceptComponents.createPillButton(
                activity,
                "+ Add New Shortcut",
                isAccent = true,
                tokens = tokens,
                density = density
            ) {
                showAddShortcutDialog {
                    renderShortcuts()
                }
            }
        )

        return root
    }

    fun buildFavoriteWordsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        val repo = UserDictionaryRepository(activity.filesDir)

        val card = ConceptComponents.createCard(activity, tokens, density).apply {
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
        }

        val tTv = TextView(activity).apply {
            text = "Personal Vocabulary Words"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        card.addView(tTv)

        val sTv = TextView(activity).apply {
            text = "Words that you type repeatedly or add manually are prioritized in the suggestion strip with top confidence."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(4f), 0, dp(12f))
        }
        card.addView(sTv)
        root.addView(card)

        val wordsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun renderWords() {
            wordsContainer.removeAllViews()
            val words = repo.getAllWords()
            if (words.isEmpty()) {
                val emptyTv = TextView(activity).apply {
                    text = "No custom words yet.\nWords you teach the keyboard will appear here."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24f), 0, dp(24f))
                }
                wordsContainer.addView(emptyTv)
                return
            }

            val listCard = ConceptComponents.createCard(activity, tokens, density)
            for (i in words.indices) {
                val w = words[i]
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = dp(14f)
                    setPadding(pad, pad, pad, pad)
                }

                val wordTv = TextView(activity).apply {
                    text = w
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(wordTv)

                val delBtn = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                    d?.setTint(Color.parseColor("#F43F5E"))
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
                    isClickable = true
                    setOnClickListener {
                        repo.removeWord(w)
                        renderWords()
                        activity.showToast("Removed '$w'")
                    }
                }
                row.addView(delBtn)

                listCard.addView(row)
                if (i < words.size - 1) {
                    listCard.addView(ConceptComponents.createDivider(activity, tokens, density))
                }
            }
            wordsContainer.addView(listCard)
        }

        renderWords()
        root.addView(wordsContainer)

        root.addView(
            ConceptComponents.createPillButton(
                activity,
                "+ Add Word to Dictionary",
                isAccent = true,
                tokens = tokens,
                density = density
            ) {
                showAddWordDialog {
                    renderWords()
                }
            }
        )

        return root
    }

    private fun showAddShortcutDialog(onSaved: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams((320 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(activity).apply {
            text = "New Shortcut"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(12f))
        }
        card.addView(titleTv)

        val shortEt = EditText(activity).apply {
            hint = "Shortcut (e.g. btw)"
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.chipBorder)
            }
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f)
            }
        }
        card.addView(shortEt)

        val expEt = EditText(activity).apply {
            hint = "Expanded Text (e.g. by the way)"
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.chipBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16f)
            }
        }
        card.addView(expEt)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = TextView(activity).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(cancelBtn)

        val saveBtn = TextView(activity).apply {
            text = "Save"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.onAccentText)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.accent)
            }
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            setOnClickListener {
                val s = shortEt.text.toString().trim()
                val e = expEt.text.toString().trim()
                if (s.isNotEmpty() && e.isNotEmpty()) {
                    val repo = UserDictionaryRepository(activity.filesDir)
                    repo.addShortcut(s, e)
                    activity.showToast("Saved shortcut '$s' \u2192 '$e'")
                    dialog.dismiss()
                    onSaved()
                } else {
                    activity.showToast("Please enter both shortcut and expansion")
                }
            }
        }
        btnRow.addView(saveBtn)
        card.addView(btnRow)

        dialog.setContentView(card)
        dialog.show()
    }

    private fun showAddWordDialog(onSaved: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams((320 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(activity).apply {
            text = "Add Word to Dictionary"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(12f))
        }
        card.addView(titleTv)

        val wordEt = EditText(activity).apply {
            hint = "Word (e.g. quantum)"
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.chipBorder)
            }
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16f)
            }
        }
        card.addView(wordEt)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = TextView(activity).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(cancelBtn)

        val saveBtn = TextView(activity).apply {
            text = "Add Word"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.onAccentText)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.accent)
            }
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            setOnClickListener {
                val w = wordEt.text.toString().trim()
                if (w.isNotEmpty()) {
                    val repo = UserDictionaryRepository(activity.filesDir)
                    repo.addWord(w)
                    activity.showToast("Added '$w' to personal dictionary")
                    dialog.dismiss()
                    onSaved()
                } else {
                    activity.showToast("Please enter a valid word")
                }
            }
        }
        btnRow.addView(saveBtn)
        card.addView(btnRow)

        dialog.setContentView(card)
        dialog.show()
    }

    private fun showLicensesDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Open Source Licenses")
            .setMessage("Gnosis Keyboard incorporates open-source software under the Apache 2.0, MIT, and BSD licenses:\n\n- AndroidX Libraries (Apache 2.0)\n- Kotlin Standard Library (Apache 2.0)\n- Lucide Icons (ISC License)\n- CMU Pronouncing Dictionary (BSD-style)")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAppInfoDialog() {
        AlertDialog.Builder(activity)
            .setTitle("App Information")
            .setMessage("Gnosis Keyboard v2.1.83\nBuild Code: 183\nTarget SDK: 35 (Android 15)\nMin SDK: 26 (Android 8.0)\nArchitecture: Fully Offline Native Kotlin\nDeveloper: Gnosis Core Team")
            .setPositiveButton("OK", null)
            .show()
    }
}
