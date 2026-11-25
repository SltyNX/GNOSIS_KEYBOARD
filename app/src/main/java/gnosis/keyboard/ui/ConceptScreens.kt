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
import android.view.WindowManager
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
import gnosis.keyboard.KeySounds
import gnosis.keyboard.KeyboardService
import gnosis.keyboard.LayoutExtensionManager
import gnosis.keyboard.MainActivity
import gnosis.keyboard.ModernToggleView
import gnosis.keyboard.R
import gnosis.keyboard.RemoteFontInfo
import gnosis.keyboard.Skins
import gnosis.keyboard.ToolbarConfig
import gnosis.keyboard.WordDictionary
import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.GnosisEngine
import gnosis.keyboard.engine.ai.GnosisAiConfig
import gnosis.keyboard.engine.ai.GnosisAiDetector
import gnosis.keyboard.engine.language.LanguageDefinition
import gnosis.keyboard.stats.StatsPeriod
import gnosis.keyboard.stats.TypingStatisticsRepository
import java.io.File
import java.util.Locale

class ConceptScreens(
    private val activity: MainActivity,
    var tokens: ConceptThemeTokens,
    private val density: Float,
    private val statsRepo: TypingStatisticsRepository,
    private val navigateTo: (View, String) -> Unit,
    private val navigateBack: () -> Boolean,
    private val navigateToRoot: () -> Unit,
    private val onThemeChanged: () -> Unit
) {

    private fun dp(v: Float) = ConceptComponents.dp(activity, v)

    private fun getPrimaryUserName(): String? {
        // Query primary Google Account display name
        try {
            val am = android.accounts.AccountManager.get(activity)
            val accounts = am.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                val acc = accounts[0]
                val possibleKeys = listOf("name", "display_name", "full_name", "given_name", "first_name")
                for (k in possibleKeys) {
                    val dName = try { am.getUserData(acc, k) } catch (_: Throwable) { null }
                    if (!dName.isNullOrBlank()) return dName.trim()
                }
                val email = acc.name
                if (email.contains("@")) {
                    val namePart = email.substringBefore("@")
                    val nameSegments = namePart.split(".", "_", "-")
                        .filter { it.isNotBlank() && !it.all { ch -> ch.isDigit() } }
                    if (nameSegments.isNotEmpty()) {
                        val cleanName = nameSegments.joinToString(" ") { part ->
                            part.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
                        }
                        if (cleanName.isNotBlank()) return cleanName
                    }
                }
            }
        } catch (_: Throwable) {}

        // Query synchronized Google Account profile name via ContactsContract.Profile
        try {
            val cursor = activity.contentResolver.query(
                android.provider.ContactsContract.Profile.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.Profile.DISPLAY_NAME_PRIMARY,
                    android.provider.ContactsContract.Profile.DISPLAY_NAME
                ),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val dName = it.getString(0) ?: it.getString(1)
                    if (!dName.isNullOrBlank()) return dName.trim()
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    fun buildHomeScreen(
        onOpenAiIntelligence: () -> Unit,
        onOpenTyping: () -> Unit,
        onOpenAppearance: () -> Unit,
        onOpenLanguage: () -> Unit = {},
        onOpenPersonalization: () -> Unit = {},
        onOpenAdvanced: () -> Unit = {},
        onOpenQuickSettings: () -> Unit = {},
        onOpenLayoutToolbar: () -> Unit = {},
        onOpenFontStore: () -> Unit = {},
        onOpenSoundHaptics: () -> Unit = {},
        onOpenEffectsWallpaper: () -> Unit = {},
        onOpenClipboardSnippets: () -> Unit = {},
        onOpenVoice: () -> Unit = {},
        onOpenMaintenance: () -> Unit = {}
    ): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(90f))
        }

        val welcomeTitle = "Welcome to Gnosis Keyboard"

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = welcomeTitle,
                description = "Modern keyboard engine with neural text intelligence, custom themes, and adaptive styling.",
                iconRes = R.drawable.ic_sparkles,
                tokens = tokens,
                density = density
            )
        )

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
            CategorySpec("AI Intelligence", "On-device neural prediction, contextual completions & smart spell check", R.drawable.ic_sparkles, onOpenAiIntelligence),
            CategorySpec("Typing & Autocorrect", "Auto-correction, contractions, free online predictions & gestures", R.drawable.ic_keyboard, onOpenTyping),
            CategorySpec("Themes & Styling", "Visual themes gallery, modern custom accent studio & surface styling", R.drawable.ic_palette, onOpenAppearance),
            CategorySpec("Layout & Toolbar", "Revamped & Classic layout architecture, toolbar sizing hyper slider & quick keys", R.drawable.ic_layout, onOpenLayoutToolbar),
            CategorySpec("Typography & Font Store", "Live typeface previews, font scale & cool decorative font styles", R.drawable.ic_type, onOpenFontStore),
            CategorySpec("Sound & Haptics", "Acoustic & piano tone schemes, audio volume & haptic vibration strength", R.drawable.ic_volume_2, onOpenSoundHaptics),
            CategorySpec("Effects & Wallpaper", "Keypress particle effects, custom photo background & dim levels", R.drawable.ic_wand_sparkles, onOpenEffectsWallpaper),
            CategorySpec("Clipboard & Snippets", "Clipboard history, screenshot pasting, message armour & text shortcuts", R.drawable.ic_clipboard, onOpenClipboardSnippets),
            CategorySpec("Voice Typing", "Speech recognition engine selection & microphone toolbar integration", R.drawable.ic_mic, onOpenVoice),
            CategorySpec("System & Maintenance", "Restart keyboard, reset settings, theme backup & live diagnostics", R.drawable.ic_settings, onOpenMaintenance)
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
                title = "Typing & Intelligence",
                description = "Configure predictive text, auto-correction, writing assistant, and typing gestures.",
                iconRes = R.drawable.ic_keyboard,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "TEXT PREDICTION & CORRECTION", tokens, density, R.drawable.ic_sparkles))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Predictive Text", "Predict next words and completions in real time as you type",
            p.getBoolean(Skins.KEY_PREDICTIONS, true), tokens, density, R.drawable.ic_sparkles
        ) { v ->
            p.edit().putBoolean(Skins.KEY_PREDICTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Free Online Predictions", "Fetch real-time cloud neural suggestions and completions for free",
            p.getBoolean(Skins.KEY_ONLINE_PREDICTIONS, Skins.DEFAULT_ONLINE_PREDICTIONS), tokens, density, R.drawable.ic_sparkles
        ) { v ->
            p.edit().putBoolean(Skins.KEY_ONLINE_PREDICTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Auto-Correction", "Automatically correct typos, missed contractions and apostrophes when pressing Space or Punctuation",
            p.getBoolean(Skins.KEY_AUTOCORRECT, Skins.DEFAULT_AUTOCORRECT), tokens, density, R.drawable.ic_check
        ) { v ->
            p.edit().putBoolean(Skins.KEY_AUTOCORRECT, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Writing Assistant & Proofreading", "Real-time grammar suggestions, homophone checks, and apostrophe fixes",
            p.getBoolean(Skins.KEY_GRAMMAR, true), tokens, density, R.drawable.ic_wand_sparkles
        ) { v ->
            p.edit().putBoolean(Skins.KEY_GRAMMAR, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Suggestion Strip", "Display candidate pills and word completions above the keyboard",
            p.getBoolean(Skins.KEY_SUGGESTIONS, true), tokens, density, R.drawable.ic_keyboard
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SUGGESTIONS, v).apply()
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Contextual Emoji Suggestions", "Recommend relevant emojis corresponding to typed words",
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
            current = Skins.prefInt(p, Skins.KEY_EMOJI_SIZE, Skins.DEFAULT_EMOJI_SIZE),
            suffix = "%", tokens = tokens, density = density,
            iconRes = R.drawable.ic_smile,
            step = 1,
            defaultValue = Skins.DEFAULT_EMOJI_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_EMOJI_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "TYPING MECHANICS & GESTURES", tokens, density, R.drawable.ic_sliders))

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

        val savedMs = Skins.prefInt(p, Skins.KEY_LONG_PRESS_MS, 250).coerceIn(100, 400)
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

    fun buildAiIntelligenceScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "AI Text Intelligence",
                description = "On-device neural language modeling, contextual predictions, intelligent autocomplete, and private typo recovery.",
                iconRes = R.drawable.ic_sparkles,
                tokens = tokens,
                density = density
            )
        )

        // Card 1: Master Controls & Core Capabilities
        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "AI CORE & TYPING ASSIST", tokens, density, R.drawable.ic_sparkles))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Enable AI Intelligence", "Master switch for neural predictions, completions and contextual spell check",
            GnosisAiConfig.isAiEnabled(activity), tokens, density, R.drawable.ic_sparkles
        ) { v ->
            GnosisAiConfig.setAiEnabled(activity, v)
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Contextual Predictions", "Predict words and phrases based on conversation and linguistic context",
            GnosisAiConfig.isContextualPredictionsEnabled(activity), tokens, density, R.drawable.ic_type
        ) { v ->
            GnosisAiConfig.setContextualPredictionsEnabled(activity, v)
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Smart Autocomplete", "Complete words, phrases, and abbreviations dynamically as you type",
            GnosisAiConfig.isSmartAutocompleteEnabled(activity), tokens, density, R.drawable.ic_keyboard
        ) { v ->
            GnosisAiConfig.setSmartAutocompleteEnabled(activity, v)
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "AI Spell Correction", "Intelligent error recovery with spatial key awareness and context ranking",
            GnosisAiConfig.isAiSpellCorrectionEnabled(activity), tokens, density, R.drawable.ic_check
        ) { v ->
            GnosisAiConfig.setAiSpellCorrectionEnabled(activity, v)
            activity.notifySettingsChanged(restart = false)
        })
        root.addView(card1)

        // Card 2: Execution & Privacy
        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "PROCESSING MODE & PERFORMANCE", tokens, density, R.drawable.ic_sliders))

        val callout = ConceptComponents.createCallout(
            context = activity,
            text = "On-Device Neural Engine: Keystrokes and text suggestions are processed locally on your device.",
            iconRes = R.drawable.ic_shield,
            tokens = tokens,
            density = density
        )
        card2.addView(callout)

        card2.addView(ConceptComponents.createSectionHeader(activity, "Processing Architecture", tokens, density))
        card2.addView(ConceptComponents.createNavRow(
            activity, "Execution Mode", "On-Device Neural Engine (Active)",
            null, tokens, density, R.drawable.ic_sliders, false
        ) {})

        card2.addView(ConceptComponents.createSectionHeader(activity, "Performance Profile", tokens, density))
        val currentProfile = GnosisAiConfig.getPerformanceProfile(activity)
        val profileOptions = listOf("Balanced (Standard)", "Lightweight (Battery Saver)")
        val profileIdx = if (currentProfile == GnosisAiConfig.PROFILE_BALANCED) 0 else 1
        val profileSelector = ConceptComponents.createSegmentSelector(
            context = activity,
            options = profileOptions,
            selectedIndex = profileIdx,
            tokens = tokens,
            density = density
        ) { idx ->
            val p = if (idx == 0) GnosisAiConfig.PROFILE_BALANCED else GnosisAiConfig.PROFILE_LIGHTWEIGHT
            GnosisAiConfig.setPerformanceProfile(activity, p)
            Toast.makeText(activity, "Performance profile set to ${profileOptions[idx]}", Toast.LENGTH_SHORT).show()
        }
        card2.addView(profileSelector)

        card2.addView(ConceptComponents.createSectionHeader(activity, "Language Intelligence", tokens, density))
        val currentLang = GnosisAiConfig.getLanguageSupport(activity)
        val langOptions = listOf("Automatic", "English", "Hinglish", "Multilingual")
        val langIdx = when (currentLang) {
            GnosisAiConfig.LANG_EN -> 1
            GnosisAiConfig.LANG_HI -> 2
            GnosisAiConfig.LANG_MULTILINGUAL -> 3
            else -> 0
        }
        val langSelector = ConceptComponents.createSegmentSelector(
            context = activity,
            options = langOptions,
            selectedIndex = langIdx,
            tokens = tokens,
            density = density
        ) { idx ->
            val code = when (idx) {
                1 -> GnosisAiConfig.LANG_EN
                2 -> GnosisAiConfig.LANG_HI
                3 -> GnosisAiConfig.LANG_MULTILINGUAL
                else -> GnosisAiConfig.LANG_AUTO
            }
            GnosisAiConfig.setLanguageSupport(activity, code)
            Toast.makeText(activity, "Language mode: ${langOptions[idx]}", Toast.LENGTH_SHORT).show()
        }
        card2.addView(langSelector)
        root.addView(card2)

        // Card 3: System AI Core & Hardware Diagnostics
        val report = GnosisAiDetector.inspectCapabilities(activity)
        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "AI ENGINE STATUS & HARDWARE", tokens, density, R.drawable.ic_info))

        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(4f))
        }
        val statusDot = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10f), dp(10f)).apply { marginEnd = dp(10f) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (report.isUniversalEngineActive) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            }
        }
        statusRow.addView(statusDot)
        val statusTv = TextView(activity).apply {
            text = report.statusSummary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        statusRow.addView(statusTv)
        card3.addView(statusRow)

        val backendRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(4f), dp(16f), dp(12f))
        }
        fun addDiagItem(title: String, desc: String) {
            val tv = TextView(activity).apply {
                text = "$title: $desc"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(2f), 0, dp(2f))
            }
            backendRow.addView(tv)
        }
        addDiagItem("Engine Backend", report.accelerationBackend)
        addDiagItem("Hardware Tier", report.memoryTier)
        for (d in report.details) {
            val tv = TextView(activity).apply {
                text = "• $d"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(1f), 0, dp(1f))
            }
            backendRow.addView(tv)
        }
        card3.addView(backendRow)
        root.addView(card3)

        // Card 4: Clear Learned AI Data
        val card4 = ConceptComponents.createCard(activity, tokens, density)
        card4.addView(ConceptComponents.createCardHeader(activity, "LEARNED DATA & ADAPTATION", tokens, density, R.drawable.ic_sparkles))

        val clearRow = ConceptComponents.createActionRow(
            context = activity,
            title = "Clear Learned AI Data",
            subtitle = "Wipe personalized transition probabilities and n-gram memory without affecting other keyboard settings",
            iconRes = R.drawable.ic_trash_2,
            tokens = tokens,
            density = density,
            isDestructive = true
        ) {
            AlertDialog.Builder(activity)
                .setTitle("Clear Learned AI Data?")
                .setMessage("This will reset learned phrase predictions and vocabulary weights. General keyboard settings will not be affected.")
                .setPositiveButton("Clear") { _, _ ->
                    GnosisAiConfig.clearLearnedData(activity)
                    Toast.makeText(activity, "Personalized AI predictions reset successfully", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        card4.addView(clearRow)
        root.addView(card4)

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
        val curTheme = p.getString(Skins.KEY_THEME, Skins.THEME_MD3_DYNAMIC) ?: Skins.THEME_MD3_DYNAMIC
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

        // Card 1: App Settings Appearance Mode
        val appModeCard = ConceptComponents.createCard(activity, tokens, density)
        appModeCard.addView(ConceptComponents.createCardHeader(activity, "APP SETTINGS APPEARANCE", tokens, density, R.drawable.ic_smartphone))

        val curAppMode = p.getString(Skins.KEY_APP_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        val appModeOptions = listOf("Auto (System)", "Dark Mode", "Light Mode")
        val appModeIdx = when (curAppMode) {
            Skins.MODE_DARK -> 1
            Skins.MODE_LIGHT -> 2
            else -> 0
        }
        appModeCard.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = appModeOptions,
                selectedIndex = appModeIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val targetMode = when (idx) {
                    1 -> Skins.MODE_DARK
                    2 -> Skins.MODE_LIGHT
                    else -> Skins.MODE_AUTO
                }
                p.edit().putString(Skins.KEY_APP_THEME_MODE, targetMode).apply()
                p.edit().putString("settings_theme_mode", targetMode).apply()
                activity.reloadTheme()
                onThemeChanged()
                activity.refreshCurrentScreen()
            }
        )
        root.addView(appModeCard)

        // Card 2: Keyboard Theme Appearance Mode
        val kbModeCard = ConceptComponents.createCard(activity, tokens, density)
        kbModeCard.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD THEME APPEARANCE", tokens, density, R.drawable.ic_keyboard))

        val curKbMode = if (p.contains(Skins.KEY_KEYBOARD_THEME_MODE)) {
            p.getString(Skins.KEY_KEYBOARD_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        } else {
            p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        }
        val kbModeOptions = listOf("Auto (System)", "Dark Mode", "Light Mode")
        val kbModeIdx = when (curKbMode) {
            Skins.MODE_DARK -> 1
            Skins.MODE_LIGHT -> 2
            else -> 0
        }
        kbModeCard.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = kbModeOptions,
                selectedIndex = kbModeIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val targetMode = when (idx) {
                    1 -> Skins.MODE_DARK
                    2 -> Skins.MODE_LIGHT
                    else -> Skins.MODE_AUTO
                }
                p.edit().putString(Skins.KEY_KEYBOARD_THEME_MODE, targetMode).apply()
                p.edit().putString(Skins.KEY_THEME_MODE, targetMode).apply()
                activity.notifySettingsChanged(restart = true)
                activity.refreshCurrentScreen()
            }
        )
        root.addView(kbModeCard)

        // Card 3: Dedicated Accent Color Palette Card
        val accentCard = ConceptComponents.createCard(activity, tokens, density)
        accentCard.addView(ConceptComponents.createCardHeader(activity, "ACCENT COLOR PALETTE", tokens, density, R.drawable.ic_palette))

        val lockNotice = LinearLayout(activity).apply {
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
            visibility = if (isLucidVoid) View.VISIBLE else View.GONE
        }
        val lockIv = ImageView(activity).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_lock)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) }
        }
        lockNotice.addView(lockIv)
        val lockTv = TextView(activity).apply {
            text = "Accent color is locked for Lucid Void (Frosted glass uses adaptive spectral tones)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(tokens.textSecondary)
        }
        lockNotice.addView(lockTv)
        accentCard.addView(lockNotice)

        val scrollPalette = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            if (isLucidVoid) alpha = 0.45f
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val paletteRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        var currentAccent = Skins.prefInt(p, Skins.KEY_ACCENT, Skins.DEFAULT_ACCENT)

        data class SwatchEntry(val color: Int, val box: FrameLayout, val checkIv: ImageView)
        val swatchEntries = mutableListOf<SwatchEntry>()

        fun updateAccentSelection(newColor: Int, swatchName: String? = null) {
            currentAccent = newColor
            for (entry in swatchEntries) {
                val isSel = entry.color == newColor
                entry.box.background = GradientDrawable().apply {
                    cornerRadius = dp(9f).toFloat()
                    setColor(entry.color)
                    if (isSel) {
                        setStroke(dp(2.5f), Color.WHITE)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
                entry.checkIv.visibility = if (isSel) View.VISIBLE else View.GONE
            }
            activity.updateAccentColor(newColor)
            if (swatchName != null) {
                activity.showToast("Accent: $swatchName")
            }
        }

        // Custom Color Picker Swatch Button
        val customColorBtn = FrameLayout(activity).apply {
            val s = dp(36f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
            background = GradientDrawable().apply {
                cornerRadius = dp(9f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1.2f), tokens.cardBorder)
            }
            val iv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_palette)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = dp(7f)
                setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            addView(iv)
            isClickable = true
            setOnClickListener {
                showCustomAccentColorPickerDialog(currentAccent) { chosenColor ->
                    updateAccentSelection(chosenColor, String.format("#%06X", 0xFFFFFF and chosenColor))
                }
            }
        }
        paletteRow.addView(customColorBtn)

        for ((hex, name) in Skins.ACCENT_PALETTE) {
            val colorVal = Color.parseColor(hex)
            val isSelected = (colorVal == currentAccent)

            val box = FrameLayout(activity).apply {
                val s = dp(36f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(9f).toFloat()
                    setColor(colorVal)
                    if (isSelected) {
                        setStroke(dp(2.5f), Color.WHITE)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
                isClickable = true
            }

            val checkIv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()
                d?.setTint(Color.WHITE)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = dp(7f)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            box.addView(checkIv)

            box.setOnClickListener {
                updateAccentSelection(colorVal, name)
            }

            swatchEntries.add(SwatchEntry(colorVal, box, checkIv))
            paletteRow.addView(box)
        }
        scrollPalette.addView(paletteRow)
        accentCard.addView(scrollPalette)
        root.addView(accentCard)

        // Card 4: Keyboard Theme & Active Preview
        val presetCard = ConceptComponents.createCard(activity, tokens, density)
        presetCard.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD THEME", tokens, density, R.drawable.ic_palette))

        val pairedActiveThemeId = if (tokens.isLight) Skins.getPairedThemeId(curTheme, true) else Skins.getPairedThemeId(curTheme, false)
        val activePreset = Skins.PRESETS[curTheme] ?: Skins.PRESETS[pairedActiveThemeId] ?: Skins.PRESETS["onyx"]!!
        val activeThemeName = activePreset.name

        // Active Theme Card Box matching Theme Gallery design
        val activeThemeCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(2f), tokens.accent)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(14f), dp(4f), dp(14f), dp(10f))
            }
            isClickable = true
            isFocusable = true
            ConceptComponents.applyTactileFeedback(this, cornerRadiusDp = 14f, pressScale = 0.98f, tokens = tokens)
            setOnClickListener { onOpenThemeGallery() }
        }

        val activeChassis = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                try {
                    setColor(Color.parseColor(activePreset.bg))
                } catch (_: Exception) {
                    setColor(Color.parseColor("#121212"))
                }
                try {
                    setStroke(dp(1f), Color.parseColor(activePreset.keyBorder))
                } catch (_: Exception) {}
            }
        }

        val miniStrip = LinearLayout(activity).apply {
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
                    setTextColor(Color.parseColor(activePreset.muted))
                } catch (_: Exception) {
                    setTextColor(Color.LTGRAY)
                }
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dp(2f)
                    marginEnd = dp(2f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(4f).toFloat()
                    try {
                        setColor(Color.parseColor(activePreset.chip))
                        setStroke(dp(0.5f), Color.parseColor(activePreset.chipBorder))
                    } catch (_: Exception) {}
                }
            }
            miniStrip.addView(candTv)
        }
        activeChassis.addView(miniStrip)

        fun makeActiveMiniRow(keys: List<String>): LinearLayout {
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
                    includeFontPadding = false
                    try {
                        setTextColor(Color.parseColor(activePreset.text))
                    } catch (_: Exception) {
                        setTextColor(Color.WHITE)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, keyWeight).apply {
                        marginStart = dp(1f)
                        marginEnd = dp(1f)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(3.5f).toFloat()
                        try {
                            if (isSpace || isAction) {
                                setColor(Color.parseColor(activePreset.defaultAccent))
                            } else {
                                setColor(Color.parseColor(activePreset.key))
                                setStroke(dp(0.7f), Color.parseColor(activePreset.keyBorder))
                            }
                        } catch (_: Exception) {}
                    }
                }
                row.addView(keyView)
            }
            return row
        }

        activeChassis.addView(makeActiveMiniRow(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")))
        activeChassis.addView(makeActiveMiniRow(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")))
        activeChassis.addView(makeActiveMiniRow(listOf("Shift", "Z", "X", "C", "SPACE", "B", "N", "M", "Enter")))
        activeThemeCard.addView(activeChassis)

        val activeBottomRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, 0)
        }

        val activeTextCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val activeTitleTv = TextView(activity).apply {
            text = activeThemeName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        }
        activeTextCol.addView(activeTitleTv)

        val activeDescTv = TextView(activity).apply {
            text = "Active keyboard preset • Tap to browse gallery"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(2f), 0, 0)
        }
        activeTextCol.addView(activeDescTv)
        activeBottomRow.addView(activeTextCol)

        val activeBadge = TextView(activity).apply {
            text = "ACTIVE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.onAccentText)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(tokens.accent)
            }
        }
        activeBottomRow.addView(activeBadge)
        activeThemeCard.addView(activeBottomRow)
        presetCard.addView(activeThemeCard)

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
            visibility = if (isLucidVoid) View.VISIBLE else View.GONE
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
            val curOpLight = Skins.prefInt(p, Skins.KEY_LUCID_VOID_OPACITY_LIGHT, Skins.DEFAULT_LUCID_VOID_OPACITY_LIGHT).coerceIn(5, 95)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Frosted Canvas Opacity", subtitle = "Light mode glass translucency through key surfaces",
                min = 5, max = 95, current = curOpLight,
                suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_OPACITY_LIGHT
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_OPACITY_LIGHT, v).putInt(Skins.KEY_LUCID_VOID_OPACITY, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })

            val curBlurLight = Skins.prefInt(p, Skins.KEY_LUCID_VOID_BLUR_LIGHT, Skins.DEFAULT_LUCID_VOID_BLUR_LIGHT).coerceIn(0, 80)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Background Blur Radius", subtitle = "Gaussian glass blur applied behind frosted layers",
                min = 0, max = 80, current = curBlurLight,
                suffix = "px", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_BLUR_LIGHT
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_BLUR_LIGHT, v).putInt(Skins.KEY_LUCID_VOID_BLUR, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })

            val curTintLight = Skins.prefInt(p, Skins.KEY_LUCID_VOID_TINT_LIGHT, Skins.DEFAULT_LUCID_VOID_TINT_LIGHT).coerceIn(0, 100)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Milky Background Tint", subtitle = "Subtle milky frosted tint contrast overlay for light glass",
                min = 0, max = 100, current = curTintLight,
                suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_TINT_LIGHT
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_TINT_LIGHT, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })
        } else {
            val curOpDark = Skins.prefInt(p, Skins.KEY_LUCID_VOID_OPACITY_DARK, Skins.DEFAULT_LUCID_VOID_OPACITY_DARK).coerceIn(5, 95)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Frosted Canvas Opacity", subtitle = "Dark mode glass translucency through key surfaces",
                min = 5, max = 95, current = curOpDark,
                suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_OPACITY_DARK
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_OPACITY_DARK, v).putInt(Skins.KEY_LUCID_VOID_OPACITY, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })

            val curBlurDark = Skins.prefInt(p, Skins.KEY_LUCID_VOID_BLUR_DARK, Skins.DEFAULT_LUCID_VOID_BLUR_DARK).coerceIn(0, 80)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Background Blur Radius", subtitle = "Gaussian glass blur applied behind frosted layers",
                min = 0, max = 80, current = curBlurDark,
                suffix = "px", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_BLUR_DARK
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_BLUR_DARK, v).putInt(Skins.KEY_LUCID_VOID_BLUR, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })

            val curTintDark = Skins.prefInt(p, Skins.KEY_LUCID_VOID_TINT_DARK, Skins.DEFAULT_LUCID_VOID_TINT_DARK).coerceIn(0, 100)
            lucidGlassBox.addView(ConceptComponents.createSliderRow(
                context = activity, title = "Milky Background Tint", subtitle = "Smoky obsidian milky tint contrast overlay for dark frosted glass",
                min = 0, max = 100, current = curTintDark,
                suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
                defaultValue = Skins.DEFAULT_LUCID_VOID_TINT_DARK
            ) { v ->
                p.edit().putInt(Skins.KEY_LUCID_VOID_TINT_DARK, v).apply()
                KeyboardService.activeInstance?.restartKeyboard()
                activity.notifySettingsChanged(restart = false)
            })
        }
        presetCard.addView(lucidGlassBox)

        presetCard.addView(
            ConceptComponents.createNavRow(
                activity, "Theme Selection Gallery", "Browse visual presets & color studios",
                null, tokens, density, R.drawable.ic_palette, true
            ) { onOpenThemeGallery() }
        )
        root.addView(presetCard)

        // Card 5: Key Surface Style
        val styleCard = ConceptComponents.createCard(activity, tokens, density)
        styleCard.addView(ConceptComponents.createCardHeader(activity, "KEY SURFACE STYLE", tokens, density, R.drawable.ic_sliders))

        val styleOptions = listOf(
            Skins.STYLE_DEFAULT to "Default Flat",
            Skins.STYLE_STROKE to "Outlined Stroke",
            Skins.STYLE_MATERIAL to "Material Tonal",
            Skins.STYLE_NEUMORPHIC to "Neumorphic Bevel"
        )
        val curStyle = p.getString(Skins.KEY_KEY_STYLE, Skins.STYLE_DEFAULT) ?: Skins.STYLE_DEFAULT

        val styleScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val styleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        data class StyleViewEntry(val styleId: String, val box: FrameLayout, val checkIv: ImageView, val nameTv: TextView)
        val styleEntries = mutableListOf<StyleViewEntry>()

        fun updateStyleSelection(newStyleId: String, styleName: String) {
            for (entry in styleEntries) {
                val isSel = entry.styleId == newStyleId
                entry.box.background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    if (isSel) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
                entry.checkIv.visibility = if (isSel) View.VISIBLE else View.GONE
                entry.nameTv.setTextColor(if (isSel) tokens.accent else tokens.textSecondary)
                entry.nameTv.setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
            }
            p.edit().putString(Skins.KEY_KEY_STYLE, newStyleId).apply()
            p.edit().putString("settings_ui_style", if (newStyleId == Skins.STYLE_STROKE) "stroke" else if (newStyleId == Skins.STYLE_NEUMORPHIC) "neumorphic" else "md3").apply()
            activity.notifySettingsChanged(restart = true)
            activity.showToast("Applied key style: $styleName")
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
                visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            box.addView(checkIv)
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
                updateStyleSelection(styleId, styleName)
            }

            styleEntries.add(StyleViewEntry(styleId, box, checkIv, nameTv))
            styleRow.addView(itemCol)
        }
        styleScroll.addView(styleRow)
        styleCard.addView(styleScroll)
        root.addView(styleCard)

        // Card 6: Key Geometry & Shapes
        val shapeCard = ConceptComponents.createCard(activity, tokens, density)
        shapeCard.addView(ConceptComponents.createCardHeader(activity, "KEY GEOMETRY & SHAPES", tokens, density, R.drawable.ic_sliders))

        val shapeOptions = listOf(
            Skins.SHAPE_ROUNDED to "Rounded",
            Skins.SHAPE_SQUIRCLE to "Squircle",
            Skins.SHAPE_PILL to "Pill",
            Skins.SHAPE_SHARP to "Sharp Box",
            Skins.SHAPE_CUT_CORNER to "Cut Corner",
            Skins.SHAPE_GEM to "Gem Facet"
        )
        val curShape = p.getString(Skins.KEY_STUDIO_SHAPE, Skins.SHAPE_ROUNDED) ?: Skins.SHAPE_ROUNDED

        val shapeScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
        }
        val shapeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        data class ShapeViewEntry(val shapeId: String, val box: FrameLayout, val checkIv: ImageView, val nameTv: TextView)
        val shapeEntries = mutableListOf<ShapeViewEntry>()

        fun updateShapeSelection(newShapeId: String, shapeName: String) {
            for (entry in shapeEntries) {
                val isSel = entry.shapeId == newShapeId
                entry.box.background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(tokens.chipBg)
                    if (isSel) {
                        setStroke(dp(2f), tokens.accent)
                    } else {
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                }
                entry.checkIv.visibility = if (isSel) View.VISIBLE else View.GONE
                entry.nameTv.setTextColor(if (isSel) tokens.accent else tokens.textSecondary)
                entry.nameTv.setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
            }
            p.edit().putString(Skins.KEY_STUDIO_SHAPE, newShapeId).apply()
            activity.notifySettingsChanged(restart = true)
            activity.showToast("Applied key shape: $shapeName")
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
                visibility = if (isSelected) View.VISIBLE else View.GONE
            }
            box.addView(checkIv)
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
                updateShapeSelection(shapeId, shapeName)
            }

            shapeEntries.add(ShapeViewEntry(shapeId, box, checkIv, nameTv))
            shapeRow.addView(itemCol)
        }
        shapeScroll.addView(shapeRow)
        shapeCard.addView(shapeScroll)

        shapeCard.addView(ConceptComponents.createSwitchRow(
            activity, "Key Outlines", "Render border outline around individual keys",
            p.getBoolean(Skins.KEY_KEY_BORDERS, true), tokens, density, R.drawable.ic_pen_line
        ) { v ->
            p.edit().putBoolean(Skins.KEY_KEY_BORDERS, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        shapeCard.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Corner Radius", subtitle = "Roundness of individual keyboard keys",
            min = 0, max = 28, current = Skins.prefInt(p, Skins.KEY_STUDIO_RADIUS, 12),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 12
        ) { v ->
            p.edit().putFloat(Skins.KEY_STUDIO_RADIUS, v.toFloat()).putInt(Skins.KEY_STUDIO_RADIUS, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        shapeCard.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Border Width", subtitle = "Thickness of outer key outlines",
            min = 0, max = 4, current = Skins.prefInt(p, Skins.KEY_STUDIO_BORDER_WIDTH, 1),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 1
        ) { v ->
            p.edit().putFloat(Skins.KEY_STUDIO_BORDER_WIDTH, v.toFloat()).putInt(Skins.KEY_STUDIO_BORDER_WIDTH, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        shapeCard.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Shadow & Elevation", subtitle = "Depth shadow underneath key surfaces",
            min = 0, max = 12, current = Skins.prefInt(p, Skins.KEY_STUDIO_SHADOW, 2),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 2
        ) { v ->
            p.edit().putFloat(Skins.KEY_STUDIO_SHADOW, v.toFloat()).putInt(Skins.KEY_STUDIO_SHADOW, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(shapeCard)

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
                description = "Manage keyboard layout styles, system language settings, and toolbar quick keys.",
                iconRes = R.drawable.ic_globe,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "SYSTEM LOCALE & INPUT", tokens, density, R.drawable.ic_globe))
        card1.addView(
            ConceptComponents.createNavRow(
                activity, "System Language Settings", "Adapting automatically to device locale (${java.util.Locale.getDefault().displayName})",
                null, tokens, density, R.drawable.ic_sliders, true
            ) {
                activity.startActivity(Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS))
            }
        )
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD LAYOUT & STYLE", tokens, density, R.drawable.ic_keyboard))

        val styleOptions = listOf("Revamped (Modern)", "Classics (Standard)")
        var curStyle = p.getString(Skins.KEY_LAYOUT_STYLE, Skins.STYLE_REVAMPED) ?: Skins.STYLE_REVAMPED
        val styleIdx = if (curStyle == Skins.STYLE_CLASSICS) 1 else 0

        val toolbarSizeContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun refreshToolbarSizeOption(activeStyle: String) {
            toolbarSizeContainer.removeAllViews()
            if (activeStyle == Skins.STYLE_REVAMPED) {
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Revamped Toolbar Icon Size",
                        subtitle = "Icon dimensions for modern pill toolbar (Lucid layout)",
                        min = 22,
                        max = 44,
                        current = Skins.prefInt(p, Skins.KEY_REVAMPED_TOOLBAR_ICON_SIZE, Skins.DEFAULT_REVAMPED_TOOLBAR_ICON_SIZE),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_REVAMPED_TOOLBAR_ICON_SIZE
                    ) { v ->
                        p.edit().putInt(Skins.KEY_REVAMPED_TOOLBAR_ICON_SIZE, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
            } else {
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Classic Toolbar Icon Size",
                        subtitle = "Icon dimensions for classic circular toolbar buttons",
                        min = 24,
                        max = 50,
                        current = Skins.prefInt(p, Skins.KEY_CLASSIC_TOOLBAR_ICON_SIZE, Skins.DEFAULT_CLASSIC_TOOLBAR_ICON_SIZE),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_CLASSIC_TOOLBAR_ICON_SIZE
                    ) { v ->
                        p.edit().putInt(Skins.KEY_CLASSIC_TOOLBAR_ICON_SIZE, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
            }
        }

        card2.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = styleOptions,
                selectedIndex = styleIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val target = if (idx == 1) Skins.STYLE_CLASSICS else Skins.STYLE_REVAMPED
                curStyle = target
                p.edit().putString(Skins.KEY_LAYOUT_STYLE, target).apply()
                p.edit().putString("settings_layout_style", target).apply()
                activity.notifySettingsChanged(restart = true)
                refreshToolbarSizeOption(target)
                activity.showToast("Keyboard style: " + (if (idx == 1) "Classics" else "Revamped"))
            }
        )

        refreshToolbarSizeOption(curStyle)
        card2.addView(toolbarSizeContainer)

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

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD LAYOUT GALLERY", tokens, density, R.drawable.ic_keyboard))
        card3.addView(
            ConceptComponents.createNavRow(
                activity, "Layout Gallery & Downloader", "Browse layouts with visual key arrangements & previews",
                null, tokens, density, R.drawable.ic_keyboard, true
            ) {
                navigateTo(buildLayoutGalleryScreen(), "Layout Gallery")
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
            min = 70, max = 130, current = Skins.prefInt(p, Skins.KEY_QUICK_KEYS_SIZE, Skins.DEFAULT_QUICK_KEYS_SIZE),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = Skins.DEFAULT_QUICK_KEYS_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_QUICK_KEYS_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card4)
        return root
    }

    fun buildLayoutToolbarScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Layout & Toolbar",
                description = "Switch between Revamped and Classic layouts, adjust toolbar heights and icon sizes with hyper sliders, and configure toolbar quick keys.",
                iconRes = R.drawable.ic_layout,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "KEYBOARD LAYOUT ARCHITECTURE", tokens, density, R.drawable.ic_keyboard))

        val styleOptions = listOf("Revamped (Modern)", "Classics (Standard)")
        var curStyle = p.getString(Skins.KEY_LAYOUT_STYLE, Skins.STYLE_REVAMPED) ?: Skins.STYLE_REVAMPED
        val styleIdx = if (curStyle == Skins.STYLE_CLASSICS) 1 else 0

        val toolbarSizeContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun refreshToolbarSizeOption(activeStyle: String) {
            toolbarSizeContainer.removeAllViews()
            if (activeStyle == Skins.STYLE_REVAMPED) {
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Revamped Toolbar Height",
                        subtitle = "Overall height of modern pill toolbar strip",
                        min = 34,
                        max = 56,
                        current = Skins.prefInt(p, Skins.KEY_TOOLBAR_HEIGHT_REVAMPED, Skins.DEFAULT_TOOLBAR_HEIGHT_REVAMPED),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_TOOLBAR_HEIGHT_REVAMPED
                    ) { v ->
                        p.edit().putInt(Skins.KEY_TOOLBAR_HEIGHT_REVAMPED, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Revamped Toolbar Icon Size",
                        subtitle = "Icon dimensions for modern pill toolbar (Lucid layout)",
                        min = 20,
                        max = 42,
                        current = Skins.prefInt(p, Skins.KEY_REVAMPED_TOOLBAR_ICON_SIZE, Skins.DEFAULT_REVAMPED_TOOLBAR_ICON_SIZE),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_REVAMPED_TOOLBAR_ICON_SIZE
                    ) { v ->
                        p.edit().putInt(Skins.KEY_REVAMPED_TOOLBAR_ICON_SIZE, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
            } else {
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Classic Keyboard Key Height",
                        subtitle = "Individual keycap height in classic layout",
                        min = 36,
                        max = 64,
                        current = Skins.prefInt(p, Skins.KEY_KEYBOARD_HEIGHT_CLASSIC, Skins.DEFAULT_KEYBOARD_HEIGHT_CLASSIC),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_keyboard,
                        step = 1,
                        defaultValue = Skins.DEFAULT_KEYBOARD_HEIGHT_CLASSIC
                    ) { v ->
                        p.edit().putInt(Skins.KEY_KEYBOARD_HEIGHT_CLASSIC, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Classic Toolbar Height",
                        subtitle = "Overall height of classic suggestions strip",
                        min = 36,
                        max = 58,
                        current = Skins.prefInt(p, Skins.KEY_TOOLBAR_HEIGHT_CLASSIC, Skins.DEFAULT_TOOLBAR_HEIGHT_CLASSIC),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_TOOLBAR_HEIGHT_CLASSIC
                    ) { v ->
                        p.edit().putInt(Skins.KEY_TOOLBAR_HEIGHT_CLASSIC, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
                toolbarSizeContainer.addView(
                    ConceptComponents.createSliderRow(
                        context = activity,
                        title = "Classic Toolbar Icon Size",
                        subtitle = "Icon dimensions for classic circular toolbar buttons",
                        min = 22,
                        max = 48,
                        current = Skins.prefInt(p, Skins.KEY_CLASSIC_TOOLBAR_ICON_SIZE, Skins.DEFAULT_CLASSIC_TOOLBAR_ICON_SIZE),
                        suffix = "dp",
                        tokens = tokens,
                        density = density,
                        iconRes = R.drawable.ic_scaling,
                        step = 1,
                        defaultValue = Skins.DEFAULT_CLASSIC_TOOLBAR_ICON_SIZE
                    ) { v ->
                        p.edit().putInt(Skins.KEY_CLASSIC_TOOLBAR_ICON_SIZE, v).apply()
                        activity.notifySettingsChanged(restart = true)
                        KeyboardService.activeInstance?.restartKeyboard()
                    }
                )
            }
        }

        card1.addView(
            ConceptComponents.createSegmentSelector(
                context = activity,
                options = styleOptions,
                selectedIndex = styleIdx,
                tokens = tokens,
                density = density
            ) { idx ->
                val target = if (idx == 1) Skins.STYLE_CLASSICS else Skins.STYLE_REVAMPED
                curStyle = target
                p.edit().putString(Skins.KEY_LAYOUT_STYLE, target).apply()
                p.edit().putString("settings_layout_style", target).apply()
                activity.notifySettingsChanged(restart = true)
                refreshToolbarSizeOption(target)
                activity.showToast("Keyboard style: " + (if (idx == 1) "Classics" else "Revamped"))
            }
        )

        refreshToolbarSizeOption(curStyle)
        card1.addView(toolbarSizeContainer)

        card1.addView(
            ConceptComponents.createSwitchRow(
                activity, "Hold Spacebar to Switch Layout", "Long-press the spacebar to switch between Revamped and Classic layouts",
                p.getBoolean(Skins.KEY_SPACE_HOLD_SWITCH_LAYOUT, Skins.DEFAULT_SPACE_HOLD_SWITCH_LAYOUT), tokens, density, R.drawable.ic_keyboard
            ) { v ->
                p.edit().putBoolean(Skins.KEY_SPACE_HOLD_SWITCH_LAYOUT, v).apply()
                activity.notifySettingsChanged(restart = false)
            }
        )

        val curLayout = p.getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
        val activeLayoutName = LayoutExtensionManager.getLayout(activity, curLayout).name
        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Active Physical Layout", "Selected character and key arrangement",
                activeLayoutName, tokens, density, R.drawable.ic_keyboard, true
            ) {
                activity.showActiveLayoutSelectorDialog {
                    activity.refreshCurrentScreen()
                }
            }
        )

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Layout Gallery & Downloader", "Browse layouts with live visual key arrangements & previews",
                null, tokens, density, R.drawable.ic_keyboard, true
            ) {
                navigateTo(buildLayoutGalleryScreen(), "Layout Gallery")
            }
        )
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "TOOLBAR QUICK KEYS", tokens, density, R.drawable.ic_sliders))

        val quickHint = TextView(activity).apply {
            text = "Tap a slot above, then tap any symbol below to place it:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
        }
        card2.addView(quickHint)

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
        card2.addView(slotBoxesRow)

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
        card2.addView(quickScroll)

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Quick Keys Size", subtitle = "Proportion of quick keys on top toolbar",
            min = 70, max = 130, current = Skins.prefInt(p, Skins.KEY_QUICK_KEYS_SIZE, Skins.DEFAULT_QUICK_KEYS_SIZE),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = Skins.DEFAULT_QUICK_KEYS_SIZE
        ) { v ->
            p.edit().putInt(Skins.KEY_QUICK_KEYS_SIZE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card2)

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "LANDSCAPE PROFILE", tokens, density, R.drawable.ic_smartphone))

        card3.addView(ConceptComponents.createSwitchRow(
            activity, "Custom Landscape Profile", "Use independent dimensions when device is rotated horizontally",
            p.getBoolean(Skins.KEY_LANDSCAPE_CUSTOM, false), tokens, density, R.drawable.ic_smartphone
        ) { v ->
            p.edit().putBoolean(Skins.KEY_LANDSCAPE_CUSTOM, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSwitchRow(
            activity, "Landscape Split Keyboard", "Split left and right halves for easy two-thumb typing",
            p.getBoolean(Skins.KEY_LANDSCAPE_SPLIT, false), tokens, density, R.drawable.ic_sliders
        ) { v ->
            p.edit().putBoolean(Skins.KEY_LANDSCAPE_SPLIT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Landscape Height", subtitle = "Keyboard vertical footprint in landscape",
            min = 50, max = 120, current = Skins.prefInt(p, Skins.KEY_LANDSCAPE_HEIGHT, 80),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 80
        ) { v ->
            p.edit().putInt(Skins.KEY_LANDSCAPE_HEIGHT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        card3.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Landscape Horizontal Gap", subtitle = "Space between keys in landscape",
            min = 0, max = 16, current = Skins.prefInt(p, Skins.KEY_LANDSCAPE_H_GAP, 4),
            suffix = "dp", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 4
        ) { v ->
            p.edit().putFloat(Skins.KEY_LANDSCAPE_H_GAP, v.toFloat()).putInt(Skins.KEY_LANDSCAPE_H_GAP, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(card3)

        return root
    }

    fun buildSoundHapticsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Sound & Haptics",
                description = "Configure audible keypress sound tone schemes, volume levels, and tactile vibration intensity.",
                iconRes = R.drawable.ic_volume_2,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "KEYPRESS AUDIO & TONES", tokens, density, R.drawable.ic_volume_2))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Keypress Sound", "Play audible click sound on key tap",
            p.getBoolean(Skins.KEY_SOUND, false), tokens, density, R.drawable.ic_volume_2
        ) { v ->
            p.edit().putBoolean(Skins.KEY_SOUND, v).apply()
            activity.notifySettingsChanged(restart = false)
            if (v) activity.playSoundPreview()
        })

        card1.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Audio Click Volume", subtitle = "Audible keypress volume level",
            min = 1, max = 100, current = Skins.prefInt(p, Skins.KEY_SOUND_VOLUME, 80),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_volume_2, step = 1,
            defaultValue = 80
        ) { v ->
            p.edit().putInt(Skins.KEY_SOUND_VOLUME, v).apply()
            activity.notifySettingsChanged(restart = false)
            activity.playSoundPreview(volume = v / 100f)
        })

        val soundTones = KeySounds.TONES
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
                val label = soundTones.firstOrNull { it.first == t }?.second ?: "Default"
                activity.showToast("Sound: $label")
            }
        )
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "TACTILE HAPTIC FEEDBACK", tokens, density, R.drawable.ic_smartphone))

        card2.addView(ConceptComponents.createSwitchRow(
            activity, "Haptic Feedback", "Vibrate device gently on every keypress",
            p.getBoolean(Skins.KEY_HAPTIC, false), tokens, density, R.drawable.ic_smartphone
        ) { v ->
            p.edit().putBoolean(Skins.KEY_HAPTIC, v).apply()
            activity.notifySettingsChanged(restart = false)
            if (v) {
                try {
                    gnosis.keyboard.Haptics.buzz(activity, Skins.prefInt(p, Skins.KEY_HAPTIC_PRESSURE, 50))
                } catch (_: Exception) {}
            }
        })

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Vibration Strength", subtitle = "Tactile haptic motor vibration intensity",
            min = 10, max = 100, current = Skins.prefInt(p, Skins.KEY_HAPTIC_PRESSURE, 50),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_smartphone, step = 1,
            defaultValue = 50
        ) { v ->
            p.edit().putInt(Skins.KEY_HAPTIC_PRESSURE, v).apply()
            activity.notifySettingsChanged(restart = false)
            try {
                gnosis.keyboard.Haptics.buzz(activity, v)
            } catch (_: Exception) {}
        })
        root.addView(card2)

        return root
    }

    fun buildEffectsWallpaperScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Effects & Wallpaper",
                description = "Configure animated keypress particle effects, custom photo backgrounds, and dimming levels.",
                iconRes = R.drawable.ic_wand_sparkles,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "KEYPRESS PARTICLE EFFECTS", tokens, density, R.drawable.ic_sparkles))

        val particleOptions = listOf(
            "off" to "Off",
            "sparks" to "Neon Sparks",
            "hearts" to "Floating Hearts",
            "bubbles" to "Bubble Burst",
            "stars" to "Glitter Stars",
            "trail" to "Neon Glow Trail"
        )
        val curParticle = p.getString(Skins.KEY_PARTICLE_EFFECTS, Skins.DEFAULT_PARTICLE_EFFECTS) ?: Skins.DEFAULT_PARTICLE_EFFECTS
        card1.addView(
            ConceptComponents.createChipsRow(
                context = activity,
                title = "Interactive Keypress Effects",
                options = particleOptions,
                selectedId = curParticle,
                tokens = tokens,
                density = density,
                iconRes = R.drawable.ic_sparkles
            ) { styleId ->
                p.edit().putString(Skins.KEY_PARTICLE_EFFECTS, styleId).apply()
                activity.notifySettingsChanged(restart = false)
                val label = particleOptions.firstOrNull { it.first == styleId }?.second ?: "Off"
                activity.showToast("Effects: $label")
            }
        )
        root.addView(card1)

        val card2 = ConceptComponents.createCard(activity, tokens, density)
        card2.addView(ConceptComponents.createCardHeader(activity, "CUSTOM PHOTO BACKGROUND", tokens, density, R.drawable.ic_image))

        val bgBtnsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        }
        val btnH = dp(42f)
        val padH = dp(4f)
        val padV = dp(10f)

        val pickBtn = ConceptComponents.createPillButton(activity, "Choose Photo", tokens, density, true) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            activity.pickBackground.launch(intent)
        }.apply {
            setPadding(padH, padV, padH, padV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, btnH, 1f).apply { marginEnd = dp(6f) }
        }
        bgBtnsRow.addView(pickBtn)

        val cropBtn = ConceptComponents.createPillButton(activity, "Crop Photo", tokens, density, false) {
            activity.showCropDialog()
        }.apply {
            setPadding(padH, padV, padH, padV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, btnH, 1f).apply { marginEnd = dp(6f) }
        }
        bgBtnsRow.addView(cropBtn)

        val removeBtn = ConceptComponents.createPillButton(activity, "Remove Photo", tokens, density, false) {
            CustomBackground.remove(activity)
            activity.notifySettingsChanged(restart = true)
            activity.showToast("Custom background removed")
            activity.refreshCurrentScreen()
        }.apply {
            setPadding(padH, padV, padH, padV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, btnH, 1f)
        }
        bgBtnsRow.addView(removeBtn)
        card2.addView(bgBtnsRow)

        card2.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Background Dim Level", subtitle = "Darken photo background for maximum key legibility",
            min = 0, max = 85, current = Skins.prefInt(p, Skins.KEY_DIM, 20),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 1,
            defaultValue = 20
        ) { v ->
            p.edit().putInt(Skins.KEY_DIM, v).apply()
            activity.notifySettingsChanged(lightUpdate = true)
        })
        root.addView(card2)

        return root
    }

    fun buildClipboardSnippetsScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Clipboard & Snippets",
                description = "Manage copied text history, screenshot clipboard pasting, privacy scans, and quick shortcuts.",
                iconRes = R.drawable.ic_clipboard,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "CLIPBOARD HISTORY", tokens, density, R.drawable.ic_clipboard))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Clipboard History", "Save copied items for quick access from the keyboard toolbar",
            ClipboardHistory.isEnabled(activity, ClipboardHistory.CAT_TEXT), tokens, density, R.drawable.ic_clipboard
        ) { v ->
            ClipboardHistory.setEnabled(activity, ClipboardHistory.CAT_TEXT, v)
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Screenshot & Image Pasting", "Show recently captured screenshots directly in the suggestions strip",
            ClipboardHistory.isEnabled(activity, ClipboardHistory.CAT_SCREENSHOT), tokens, density, R.drawable.ic_image
        ) { v ->
            ClipboardHistory.setEnabled(activity, ClipboardHistory.CAT_SCREENSHOT, v)
            activity.notifySettingsChanged(restart = false)
        })

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Message Armour Privacy Scan", "Automatically detect and mask sensitive passwords or OTPs in clipboard items",
            p.getBoolean("message_armour_enabled", true), tokens, density, R.drawable.ic_shield
        ) { v ->
            p.edit().putBoolean("message_armour_enabled", v).apply()
            activity.notifySettingsChanged(restart = false)
        })
        root.addView(card1)

        val cardSnippets = ConceptComponents.createCard(activity, tokens, density)
        cardSnippets.addView(ConceptComponents.createCardHeader(activity, "CANNED SNIPPETS & PINNED PHRASES", tokens, density, R.drawable.ic_clipboard))

        val snippetInputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        }
        val snippetEt = EditText(activity).apply {
            hint = "Add canned snippet / response..."
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8f)
            }
        }
        snippetInputRow.addView(snippetEt)

        val addBtn = ConceptComponents.createPillButton(activity, "Add", tokens, density, true) {
            val text = snippetEt.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                ClipboardHistory.addSnippet(activity, text, pinned = true)
                snippetEt.setText("")
                activity.notifySettingsChanged(restart = false)
                activity.showToast("Snippet added successfully")
                activity.refreshCurrentScreen()
            }
        }.apply {
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38f))
        }
        snippetInputRow.addView(addBtn)
        cardSnippets.addView(snippetInputRow)

        val snippetsList = ClipboardHistory.getItems(activity, null).filter { it.isPinned && !it.isImage }
        if (snippetsList.isEmpty()) {
            val emptyTv = TextView(activity).apply {
                text = "No pinned snippets yet. Add frequently used phrases, email signatures, or addresses above."
                setTextColor(tokens.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
            }
            cardSnippets.addView(emptyTv)
        } else {
            snippetsList.forEach { item ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                    val padH = dp(12f)
                    val padV = dp(8f)
                    setPadding(padH, padV, padH, padV)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(14f)
                        marginEnd = dp(14f)
                        bottomMargin = dp(6f)
                    }
                }
                val tv = TextView(activity).apply {
                    text = item.text
                    setTextColor(tokens.textPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                row.addView(tv)

                val delBtn = ImageView(activity).apply {
                    val d = ContextCompat.getDrawable(activity, R.drawable.ic_trash_2)?.mutate()
                    d?.setTint(tokens.textSecondary)
                    setImageDrawable(d)
                    setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
                    layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
                    setOnClickListener {
                        ClipboardHistory.delete(activity, item.id)
                        activity.notifySettingsChanged(restart = false)
                        activity.refreshCurrentScreen()
                    }
                }
                row.addView(delBtn)
                cardSnippets.addView(row)
            }
        }
        root.addView(cardSnippets)

        return root
    }

    fun buildVoiceScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Voice Typing Studio",
                description = "Configure speech-to-text dictation engines, microphone toolbar buttons, and audio permissions.",
                iconRes = R.drawable.ic_mic,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "VOICE INPUT SETTINGS", tokens, density, R.drawable.ic_mic))

        card1.addView(ConceptComponents.createSwitchRow(
            activity, "Voice Input Mic Button", "Show dedicated microphone key on toolbar for speech-to-text",
            p.getBoolean("voice_input_enabled", true), tokens, density, R.drawable.ic_mic
        ) { v ->
            p.edit().putBoolean("voice_input_enabled", v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        val speechEngines = listOf(
            "google" to "Google Speech Recognition",
            "system" to "Android Default Recognizer"
        )
        val curEngine = p.getString("setting_speech_engine", "google") ?: "google"
        card1.addView(
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

        card1.addView(
            ConceptComponents.createNavRow(
                activity, "Test Microphone Permission", "Grant audio record permission for voice typing",
                null, tokens, density, R.drawable.ic_check, true
            ) {
                activity.voiceAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )
        root.addView(card1)

        return root
    }

    fun buildMaintenanceScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }
        val p = Skins.prefs(activity)

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "System & Maintenance",
                description = "Restart the keyboard engine, reset settings, backup and share themes, or monitor live diagnostics.",
                iconRes = R.drawable.ic_settings,
                tokens = tokens,
                density = density
            )
        )

        val card1 = ConceptComponents.createCard(activity, tokens, density)
        card1.addView(ConceptComponents.createCardHeader(activity, "SYSTEM OPERATIONS", tokens, density, R.drawable.ic_refresh_cw))

        val restartBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(6f))
        }
        val restartBtn = ConceptComponents.createPillButton(activity, "Restart Keyboard Engine", tokens, density, true) {
            activity.restartKeyboardEngine(showToastMessage = true)
        }
        restartBox.addView(restartBtn)
        card1.addView(restartBox)

        val resetBox = LinearLayout(activity).apply {
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        }
        val resetBtn = ConceptComponents.createPillButton(activity, "Reset All Settings to Defaults", tokens, density, false) {
            activity.resetToDefault()
        }
        resetBox.addView(resetBtn)
        card1.addView(resetBox)
        root.addView(card1)

        val card3 = ConceptComponents.createCard(activity, tokens, density)
        card3.addView(ConceptComponents.createCardHeader(activity, "EXPORT & BACKUP THEME", tokens, density, R.drawable.ic_share_2))

        val shareRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14f), dp(6f), dp(14f), dp(12f))
        }
        val exportBtn = ConceptComponents.createPillButton(activity, "Export Theme", tokens, density, true) {
            activity.exportTheme()
        }
        exportBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
        shareRow.addView(exportBtn)

        val importBtn = ConceptComponents.createPillButton(activity, "Import Theme", tokens, density, false) {
            activity.importThemeLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        importBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        shareRow.addView(importBtn)
        card3.addView(shareRow)
        root.addView(card3)

        return root
    }

    fun showCustomAccentColorPickerDialog(initialColor: Int, onColorApplied: (Int) -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dm = activity.resources.displayMetrics
        val dialogW = (dm.widthPixels * 0.90f).toInt().coerceIn(dp(300f), dp(360f))

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(20f)
            val padV = dp(20f)
            setPadding(padH, padV, padH, padV)
            minimumWidth = dialogW
            background = GradientDrawable().apply {
                cornerRadius = dp(24f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1.2f), tokens.cardBorder)
            }
            layoutParams = FrameLayout.LayoutParams(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }

        // Title Row
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14f))
        }
        val iconBox = FrameLayout(activity).apply {
            val s = dp(32f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(10f) }
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            val iv = ImageView(activity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_palette)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = dp(7f)
                setPadding(p, p, p, p)
            }
            addView(iv)
        }
        titleRow.addView(iconBox)

        titleRow.addView(TextView(activity).apply {
            text = "Custom Accent Color"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(titleRow)

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var currentColor = initialColor

        // Live Preview Box: Shows current color, sample badge, and hex text
        val previewCard = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f)).apply {
                bottomMargin = dp(14f)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(currentColor)
            }
        }

        val previewContent = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), 0, dp(16f), 0)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val previewHexTv = TextView(activity).apply {
            text = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and currentColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (ColorUtils.calculateLuminance(currentColor) > 0.45) Color.parseColor("#0F172A") else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        previewContent.addView(previewHexTv)

        val sampleBadge = TextView(activity).apply {
            text = "Live Keycap"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            val onCol = if (ColorUtils.calculateLuminance(currentColor) > 0.45) Color.parseColor("#0F172A") else Color.WHITE
            setTextColor(onCol)
            background = GradientDrawable().apply {
                cornerRadius = dp(8f).toFloat()
                setColor(ColorUtils.setAlphaComponent(onCol, 40))
                setStroke(dp(1f), ColorUtils.setAlphaComponent(onCol, 70))
            }
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }
        previewContent.addView(sampleBadge)
        previewCard.addView(previewContent)
        root.addView(previewCard)

        // Hex direct input row
        val hexCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(dp(14f), 0, dp(14f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
                bottomMargin = dp(12f)
            }
        }
        val hexPrefix = TextView(activity).apply {
            text = "HEX  #"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            setPadding(0, 0, dp(6f), 0)
        }
        hexCard.addView(hexPrefix)

        var isUpdatingText = false
        val hexEt = EditText(activity).apply {
            val rawHex = String.format(Locale.ROOT, "%06X", 0xFFFFFF and currentColor)
            setText(rawHex)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        hexCard.addView(hexEt)
        root.addView(hexCard)

        // Smooth Hyper Glide Sliders
        lateinit var updatePreview: () -> Unit
        lateinit var hueBar: HyperGlideSliderView
        lateinit var satBar: HyperGlideSliderView
        lateinit var valBar: HyperGlideSliderView

        fun createSlider(label: String, maxVal: Int, curVal: Int, onProgress: (Int) -> Unit): Pair<LinearLayout, HyperGlideSliderView> {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6f)
                }
            }
            val hdr = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val labelTv = TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valTv = TextView(activity).apply {
                text = "$curVal"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            }
            hdr.addView(labelTv)
            hdr.addView(valTv)
            box.addView(hdr)

            val slider = HyperGlideSliderView(activity).apply {
                min = 0
                max = maxVal
                value = curVal
                accentColor = tokens.accent
                isLightMode = tokens.isLight
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f))
                onValueChange = { p ->
                    valTv.text = "$p"
                    onProgress(p)
                    updatePreview()
                }
            }
            box.addView(slider)
            return Pair(box, slider)
        }

        val (hueBox, hBar) = createSlider("Hue", 360, hsv[0].toInt().coerceIn(0, 360)) { p -> hsv[0] = p.toFloat() }
        hueBar = hBar
        root.addView(hueBox)

        val (satBox, sBar) = createSlider("Saturation", 100, (hsv[1] * 100).toInt().coerceIn(0, 100)) { p -> hsv[1] = p / 100f }
        satBar = sBar
        root.addView(satBox)

        val (valBox, vBar) = createSlider("Brightness", 100, (hsv[2] * 100).toInt().coerceIn(0, 100)) { p -> hsv[2] = p / 100f }
        valBar = vBar
        root.addView(valBox)

        var applyBtn: TextView? = null

        updatePreview = {
            currentColor = Color.HSVToColor(hsv)
            (previewCard.background as? GradientDrawable)?.setColor(currentColor)
            val onCol = if (ColorUtils.calculateLuminance(currentColor) > 0.45) Color.parseColor("#0F172A") else Color.WHITE
            previewHexTv.text = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and currentColor)
            previewHexTv.setTextColor(onCol)
            sampleBadge.setTextColor(onCol)
            (sampleBadge.background as? GradientDrawable)?.apply {
                setColor(ColorUtils.setAlphaComponent(onCol, 40))
                setStroke(dp(1f), ColorUtils.setAlphaComponent(onCol, 70))
            }
            applyBtn?.let { btn ->
                (btn.background as? GradientDrawable)?.setColor(currentColor)
                btn.setTextColor(onCol)
            }
            if (!isUpdatingText) {
                isUpdatingText = true
                val raw = String.format(Locale.ROOT, "%06X", 0xFFFFFF and currentColor)
                if (hexEt.text.toString().uppercase(Locale.ROOT) != raw) {
                    hexEt.setText(raw)
                    hexEt.setSelection(raw.length)
                }
                isUpdatingText = false
            }
        }

        hexEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText) return
                val text = s?.toString()?.trim()?.removePrefix("#") ?: return
                if (text.length == 6) {
                    try {
                        val parsed = Color.parseColor("#$text")
                        Color.colorToHSV(parsed, hsv)
                        isUpdatingText = true
                        hueBar.value = hsv[0].toInt().coerceIn(0, 360)
                        satBar.value = (hsv[1] * 100).toInt().coerceIn(0, 100)
                        valBar.value = (hsv[2] * 100).toInt().coerceIn(0, 100)
                        isUpdatingText = false
                        updatePreview()
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        // Aesthetic Quick Presets Row
        val quickPresets = listOf(
            "#EF4444", "#F97316", "#F59E0B", "#10B981",
            "#06B6D4", "#3B82F6", "#6366F1", "#8B5CF6",
            "#EC4899", "#64748B"
        )
        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
                bottomMargin = dp(16f)
            }
        }
        val presetScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        for (hex in quickPresets) {
            val c = Color.parseColor(hex)
            val chip = FrameLayout(activity).apply {
                val s = dp(28f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(7f).toFloat()
                    setColor(c)
                    setStroke(dp(1f), ColorUtils.setAlphaComponent(Color.WHITE, 40))
                }
                setOnClickListener {
                    Color.colorToHSV(c, hsv)
                    hueBar.value = hsv[0].toInt().coerceIn(0, 360)
                    satBar.value = (hsv[1] * 100).toInt().coerceIn(0, 100)
                    valBar.value = (hsv[2] * 100).toInt().coerceIn(0, 100)
                    updatePreview()
                }
            }
            presetRow.addView(chip)
        }
        presetScroll.addView(presetRow)
        root.addView(presetScroll)

        // Action Buttons Row
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f))
        }

        val cancelBtn = TextView(activity).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44f), 1f).apply { marginEnd = dp(10f) }
            setOnClickListener {
                dialog.dismiss()
            }
        }
        btnRow.addView(cancelBtn)

        applyBtn = TextView(activity).apply {
            text = "Apply Accent"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            val onCol = if (ColorUtils.calculateLuminance(currentColor) > 0.45) Color.parseColor("#0F172A") else Color.WHITE
            setTextColor(onCol)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(currentColor)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44f), 1.25f)
            setOnClickListener {
                dialog.dismiss()
                onColorApplied(currentColor)
                activity.updateAccentColor(currentColor)
            }
        }
        btnRow.addView(applyBtn)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(dialogW, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
            val lp = w.attributes
            lp.width = dialogW
            w.attributes = lp
        }
        dialog.show()
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
                description = "Sound, haptic feedback pressure, voice typing, clipboard history, and reset.",
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
            min = 1, max = 100, current = Skins.prefInt(p, Skins.KEY_SOUND_VOLUME, 80),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_volume_2, step = 1,
            defaultValue = 80
        ) { v ->
            p.edit().putInt(Skins.KEY_SOUND_VOLUME, v).apply()
            activity.notifySettingsChanged(restart = false)
            activity.playSoundPreview(volume = v / 100f)
        })

        val soundTones = KeySounds.TONES
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
                val label = soundTones.firstOrNull { it.first == t }?.second ?: "Default"
                activity.showToast("Sound: $label")
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
                    gnosis.keyboard.Haptics.buzz(activity, Skins.prefInt(p, Skins.KEY_HAPTIC_PRESSURE, 50))
                } catch (_: Exception) {}
            }
        })

        card1.addView(ConceptComponents.createSliderRow(
            context = activity, title = "Vibration Strength", subtitle = "Tactile haptic motor vibration intensity",
            min = 10, max = 100, current = Skins.prefInt(p, Skins.KEY_HAPTIC_PRESSURE, 50),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_smartphone, step = 1,
            defaultValue = 50
        ) { v ->
            p.edit().putInt(Skins.KEY_HAPTIC_PRESSURE, v).apply()
            activity.notifySettingsChanged(restart = false)
            try {
                gnosis.keyboard.Haptics.buzz(activity, v)
            } catch (_: Exception) {}
        })

        val particleOptions = listOf(
            "off" to "Off",
            "sparks" to "Neon Sparks",
            "hearts" to "Floating Hearts",
            "bubbles" to "Bubble Burst",
            "stars" to "Glitter Stars",
            "trail" to "Neon Glow Trail"
        )
        val curParticle = p.getString(Skins.KEY_PARTICLE_EFFECTS, Skins.DEFAULT_PARTICLE_EFFECTS) ?: Skins.DEFAULT_PARTICLE_EFFECTS
        card1.addView(
            ConceptComponents.createChipsRow(
                context = activity,
                title = "Interactive Keypress Effects",
                options = particleOptions,
                selectedId = curParticle,
                tokens = tokens,
                density = density,
                iconRes = R.drawable.ic_sparkles
            ) { styleId ->
                p.edit().putString(Skins.KEY_PARTICLE_EFFECTS, styleId).apply()
                activity.notifySettingsChanged(restart = false)
                val label = particleOptions.firstOrNull { it.first == styleId }?.second ?: "Off"
                activity.showToast("Effects: $label")
            }
        )
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

        val card4 = ConceptComponents.createCard(activity, tokens, density)
        card4.addView(ConceptComponents.createCardHeader(activity, "APP ICON & DRAWER", tokens, density, R.drawable.ic_eye))
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
            text = "On-Device Engine & Security"
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
        val curAppMode = p.getString(Skins.KEY_APP_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        val curKbMode = if (p.contains(Skins.KEY_KEYBOARD_THEME_MODE)) {
            p.getString(Skins.KEY_KEYBOARD_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        } else {
            p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        }
        val curThemeMode = curKbMode
        val curTheme = p.getString(Skins.KEY_THEME, Skins.THEME_MD3_DYNAMIC) ?: Skins.THEME_MD3_DYNAMIC

        data class GalleryThemeItem(val id: String, val name: String, val desc: String)
        val themes = when (curKbMode) {
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
        val currentMode = if (p.contains(Skins.KEY_KEYBOARD_THEME_MODE)) {
            p.getString(Skins.KEY_KEYBOARD_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        } else {
            p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        }
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
                p.edit().putString(Skins.KEY_KEYBOARD_THEME_MODE, target).apply()
                p.edit().putString(Skins.KEY_THEME_MODE, target).apply()
                activity.notifySettingsChanged(restart = true)
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
                    "Better suggestions, with every word. Typing insights are computed on-device.",
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
            isBaselineAligned = false
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
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
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
            SettingItem("Spacebar Cursor Control", "Typing & Mechanics", "space trackpad cursor slide move navigation", { navigateTo(buildTypingScreen(), "Typing & Mechanics") }),
            SettingItem("Secondary Hints & Symbols", "Typing & Mechanics", "secondary hints symbols numbers on keys", { navigateTo(buildTypingScreen(), "Typing & Mechanics") }),
            SettingItem("Long Press Delay", "Typing & Mechanics", "long press duration delay milliseconds", { navigateTo(buildTypingScreen(), "Typing & Mechanics") }),
            SettingItem("Key Magnifying Popup", "Typing & Mechanics", "key popup bubble magnifier touch preview", { navigateTo(buildTypingScreen(), "Typing & Mechanics") }),
            SettingItem("Auto-Capitalization", "Typing & Mechanics", "caps uppercase capital sentences words", { navigateTo(buildTypingScreen(), "Typing & Mechanics") }),
            SettingItem("Preset Theme Gallery", "Appearance", "theme amoled dark light color palette", { navigateTo(buildThemeGridScreen(), "Theme Gallery") }),
            SettingItem("Design Studio & Geometry", "Appearance", "radius corners padding gap borders height scale", { navigateTo(buildAppearanceScreen { navigateTo(buildThemeGridScreen(), "Theme Gallery") }, "Appearance") }),
            SettingItem("Custom Fonts & Typography Store", "Appearance", "fonts store typeface roboto inter jetbrains mono custom", { navigateTo(buildFontStoreScreen(), "Typography & Fonts Store") }),
            SettingItem("Lucid Void Glassmorphism", "Appearance", "glass frosted blur transparent background effect", { navigateTo(buildAppearanceScreen { navigateTo(buildThemeGridScreen(), "Theme Gallery") }, "Appearance") }),
            SettingItem("Custom Photo Background", "Appearance", "wallpaper picture gallery photo crop background", { activity.showCropDialog() }),
            SettingItem("Layout Gallery & Downloader", "Language & Dictionary", "qwerty dvorak colemak azerty layout download", { navigateTo(buildLayoutGalleryScreen(), "Layout Gallery") }),
            SettingItem("Quick Access Toolbar Keys", "Language & Dictionary", "toolbar top bar 3 keys shortcuts customizer", { navigateTo(buildLanguageScreen { navigateTo(buildLearnedDataScreen(), "Learned Data") }, "Language & Dictionary") }),
            SettingItem("Text Expansion Shortcuts", "Personalization", "snippets shortcuts text expansion omw brb", { navigateTo(buildShortcutsScreen(), "Text Shortcuts") }),
            SettingItem("Favorite Vocabulary Words", "Personalization", "words custom personal vocabulary custom words", { navigateTo(buildFavoriteWordsScreen(), "Favorite Words") }),
            SettingItem("Manage Learned Dictionary", "Personalization", "learned data export import backup wipe trie", { navigateTo(buildLearnedDataScreen(), "Learned Data") }),
            SettingItem("Haptic Feedback Pressure", "Advanced Settings", "vibrate vibration haptics pressure millisecond", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Keypress Audio & Click Tone", "Advanced Settings", "audio sound volume click mechanical typewriter", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Clipboard History & Screenshots", "Advanced Settings", "clipboard copy paste pin screenshots capture", { navigateTo(buildAdvancedScreen(), "Advanced Settings") }),
            SettingItem("Device Privacy & Security", "About & Privacy", "offline security private local storage", { navigateTo(buildAboutPrivacyScreen(), "About & Privacy") }),
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
                "Local Processing & Security: All learned typing patterns and custom dictionaries are processed directly on your device.",
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

    fun buildLayoutGalleryScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Keyboard Layout Gallery",
                description = "Browse ergonomic and regional physical keyboard arrangements. Download, preview, and apply layouts in real time.",
                iconRes = R.drawable.ic_keyboard,
                tokens = tokens,
                density = density
            )
        )

        val searchCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(dp(14f), 0, dp(14f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)).apply {
                bottomMargin = dp(16f)
            }
        }
        val searchIcon = ImageView(activity).apply {
            setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_search)?.mutate()?.apply {
                setTint(tokens.textSecondary)
            })
            val s = dp(18f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(10f) }
        }
        searchCard.addView(searchIcon)

        val searchInput = EditText(activity).apply {
            hint = "Search layout (e.g. Dvorak, Colemak, AZERTY)..."
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        searchCard.addView(searchInput)
        root.addView(searchCard)

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(listContainer)

        var filterQuery = ""

        fun render() {
            listContainer.removeAllViews()
            val p = Skins.prefs(activity)
            val curLayoutId = p.getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
            val installedLayouts = LayoutExtensionManager.getInstalledLayouts(activity)
            val installedIds = installedLayouts.map { it.id.lowercase(Locale.ROOT) }.toSet()

            // Section 1: Installed Layouts
            fun renderMiniKeyboardPreview(rows: List<String>, isCurrent: Boolean): View {
                val previewBox = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8f)
                    }
                }
                for (rowStr in rows.take(3)) {
                    val rowLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(3f)
                        }
                    }
                    val chars: List<String> = if (rowStr.contains(" ")) rowStr.split(" ") else rowStr.map { it.toString() }
                    for (ch in chars.take(10)) {
                        val keycap = TextView(activity).apply {
                            text = ch.uppercase(Locale.ROOT)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (isCurrent) tokens.accent else tokens.textPrimary)
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                cornerRadius = dp(4f).toFloat()
                                setColor(tokens.surface)
                                setStroke(dp(0.8f), if (isCurrent) ColorUtils.setAlphaComponent(tokens.accent, 90) else tokens.cardBorder)
                            }
                            layoutParams = LinearLayout.LayoutParams(0, dp(22f), 1f).apply {
                                marginStart = dp(1f)
                                marginEnd = dp(1f)
                            }
                        }
                        rowLayout.addView(keycap)
                    }
                    previewBox.addView(rowLayout)
                }
                return previewBox
            }

            fun getCatalogLayoutRows(id: String): List<String> = when (id.lowercase(Locale.ROOT)) {
                "qwertz" -> listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
                "azerty" -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
                "spanish" -> listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")
                "deva" -> listOf("कखगघङचछजझञ", "टठडढणतथदधन", "पफबभमयरलवश")
                "russian" -> listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
                "arabic" -> listOf("ضصثقفغعهخح", "شسیبلاتنمك", "ئءؤرلاىةوز")
                "bengali" -> listOf("কখগঘঙচছজঝঞ", "টঠডঢণতথদধন", "পফবভমযরলবশ")
                "tamil" -> listOf("கஙசஞடணதநபம", "யரலவழளறனஸஷ", "ஹஜஅஆஇஈஉஊஎஏ")
                "telugu" -> listOf("కఖగఘఙచఛజఝఞ", "టఠడఢణతథదధన", "పఫబభమయరలవశ")
                "portuguese" -> listOf("qwertyuiop", "asdfghjklç", "zxcvbnm")
                "turkish" -> listOf("qwertyuıop", "asdfghjklş", "zxcvbnmöç")
                "dvorak" -> listOf("pyfgcrl", "aoeuidhtns", "qjkxbmwvz")
                "colemak" -> listOf("qwfpgjluy", "arstdhneio", "zxcvbkm")
                else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            }

            val headerInstalled = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4f), dp(4f), dp(4f), dp(8f))
            }
            headerInstalled.addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply { setTint(tokens.accent) })
                val s = dp(16f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(6f) }
            })
            headerInstalled.addView(TextView(activity).apply {
                text = "INSTALLED LAYOUTS (${installedLayouts.size})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            })
            listContainer.addView(headerInstalled)

            for (model in installedLayouts) {
                if (filterQuery.isNotEmpty() && !model.name.contains(filterQuery, ignoreCase = true) && !model.id.contains(filterQuery, ignoreCase = true)) {
                    continue
                }
                val isCurrent = model.id.equals(curLayoutId, ignoreCase = true)
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16f).toFloat()
                        setColor(tokens.surface)
                        setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10f)
                    }
                }

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val infoCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) }
                }

                val titleRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(activity).apply {
                    text = model.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                })

                if (isCurrent) {
                    titleRow.addView(TextView(activity).apply {
                        text = " ACTIVE / BEING USED "
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#10B981"))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(6f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.parseColor("#10B981"), 35))
                        }
                        setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(8f)
                        }
                    })
                }
                infoCol.addView(titleRow)

                val previewKeys = LayoutExtensionManager.getPreviewKeys(model.id).joinToString(" ")
                infoCol.addView(TextView(activity).apply {
                    text = "Top Row: $previewKeys • ${model.language}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(3f), 0, 0)
                })
                row.addView(infoCol)

                if (!isCurrent) {
                    val activateBtn = TextView(activity).apply {
                        text = "Activate"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.onAccentText)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12f).toFloat()
                            setColor(tokens.accent)
                        }
                        setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                        setOnClickListener {
                            p.edit().putString(Skins.KEY_LAYOUT, model.id).apply()
                            activity.notifySettingsChanged(restart = true)
                            activity.showToast("Applied ${model.name} layout")
                            render()
                        }
                    }
                    row.addView(activateBtn)
                }

                if (model.id != "qwerty") {
                    val delBtn = FrameLayout(activity).apply {
                        val s = dp(34f)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dp(8f) }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.RED, if (tokens.isLight) 25 else 40))
                        }
                        val iv = ImageView(activity).apply {
                            val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                                ?: ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
                            d?.setTint(Color.parseColor("#FF5252"))
                            setImageDrawable(d)
                            val pad = dp(8f)
                            setPadding(pad, pad, pad, pad)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        addView(iv)
                        setOnClickListener {
                            LayoutExtensionManager.deleteLayout(activity, model.id)
                            activity.showToast("Deleted ${model.name} layout")
                            activity.notifySettingsChanged(restart = true)
                            render()
                        }
                    }
                    row.addView(delBtn)
                }
                card.addView(row)
                card.addView(renderMiniKeyboardPreview(model.rows, isCurrent))
                listContainer.addView(card)
            }

            // Section 2: Available Online Layouts
            val available = LayoutExtensionManager.STATIC_CATALOG.filter { !installedIds.contains(it.id.lowercase(Locale.ROOT)) }
            val headerAvailable = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4f), dp(14f), dp(4f), dp(8f))
            }
            headerAvailable.addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download)?.mutate()?.apply { setTint(tokens.accent) })
                val s = dp(16f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(6f) }
            })
            headerAvailable.addView(TextView(activity).apply {
                text = "AVAILABLE ONLINE LAYOUTS (${available.size})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            })
            listContainer.addView(headerAvailable)

            for (item in available) {
                if (filterQuery.isNotEmpty() && !item.name.contains(filterQuery, ignoreCase = true) && !item.id.contains(filterQuery, ignoreCase = true) && !item.description.contains(filterQuery, ignoreCase = true)) {
                    continue
                }
                val itemCard = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16f).toFloat()
                        setColor(tokens.surface)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10f)
                    }
                }

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val col = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) }
                }
                col.addView(TextView(activity).apply {
                    text = item.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                })
                col.addView(TextView(activity).apply {
                    text = item.description
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(3f), 0, 0)
                })
                row.addView(col)

                val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = false
                    max = 100
                    progress = 0
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4f)).apply {
                        topMargin = dp(8f)
                    }
                }

                val dlBtn = TextView(activity).apply {
                    text = "Download"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.accent)
                    }
                    setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                    setOnClickListener {
                        isEnabled = false
                        progressBar.isIndeterminate = true
                        progressBar.visibility = View.VISIBLE
                        LayoutExtensionManager.downloadAndInstall(activity, item) { success, _ ->
                            activity.runOnUiThread {
                                if (success) {
                                    activity.showToast("Installed ${item.name} layout")
                                    activity.notifySettingsChanged(restart = true)
                                    render()
                                } else {
                                    isEnabled = true
                                    progressBar.visibility = View.GONE
                                    activity.showToast("Download failed")
                                }
                            }
                        }
                    }
                }
                row.addView(dlBtn)
                itemCard.addView(row)
                itemCard.addView(renderMiniKeyboardPreview(getCatalogLayoutRows(item.id), false))
                itemCard.addView(progressBar)
                listContainer.addView(itemCard)
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                filterQuery = s?.toString()?.trim() ?: ""
                render()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        render()
        return root
    }

    fun buildFontStoreScreen(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16f), dp(12f), dp(16f), dp(80f))
        }

        root.addView(
            ConceptComponents.createHeroBrandCard(
                context = activity,
                title = "Typography & Font Store",
                description = "Download and apply custom typefaces for key labels. Switch system font or customize font scale & weight in real time.",
                iconRes = R.drawable.ic_type,
                tokens = tokens,
                density = density
            )
        )

        val p = Skins.prefs(activity)

        val typographyCard = ConceptComponents.createCard(activity, tokens, density)
        typographyCard.addView(ConceptComponents.createCardHeader(activity, "TYPOGRAPHY CONTROLS", tokens, density, R.drawable.ic_type))

        typographyCard.addView(ConceptComponents.createSwitchRow(
            activity, "Use System Font", "Follow default Android device typography",
            p.getBoolean(Skins.KEY_USE_SYSTEM_FONT, Skins.DEFAULT_USE_SYSTEM_FONT), tokens, density, R.drawable.ic_type
        ) { v ->
            p.edit().putBoolean(Skins.KEY_USE_SYSTEM_FONT, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        typographyCard.addView(ConceptComponents.createSwitchRow(
            activity, "Bold Key Labels", "Render key letters with heavy bold typeface weight",
            p.getBoolean(Skins.KEY_STUDIO_FONT_BOLD, false), tokens, density, R.drawable.ic_type
        ) { v ->
            p.edit().putBoolean(Skins.KEY_STUDIO_FONT_BOLD, v).apply()
            activity.notifySettingsChanged(restart = true)
        })

        typographyCard.addView(ConceptComponents.createFloatSliderRow(
            context = activity, title = "Key Font Scale", subtitle = "Proportional letter character size",
            min = 0.80f, max = 1.30f, current = Skins.prefFloat(p, Skins.KEY_STUDIO_FONT_SCALE, 0.80f),
            suffix = "%", tokens = tokens, density = density, iconRes = R.drawable.ic_sliders, step = 0.01f,
            defaultValue = 0.80f
        ) { v ->
            p.edit().putFloat(Skins.KEY_STUDIO_FONT_SCALE, v).apply()
            activity.notifySettingsChanged(restart = true)
        })
        root.addView(typographyCard)

        // Refined Search Box
        val searchCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(dp(14f), 0, dp(14f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
                topMargin = dp(6f)
                bottomMargin = dp(16f)
            }
        }
        val searchIcon = ImageView(activity).apply {
            setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_search)?.mutate()?.apply {
                setTint(tokens.textSecondary)
            })
            val s = dp(18f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(10f) }
        }
        searchCard.addView(searchIcon)

        val searchInput = EditText(activity).apply {
            hint = "Search typefaces (e.g. Roboto, Inter, Fira)..."
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        searchCard.addView(searchInput)
        root.addView(searchCard)

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(listContainer)

        var filterQuery = ""
        var remoteFonts: List<RemoteFontInfo> = emptyList()

        fun render() {
            listContainer.removeAllViews()
            val curFont = p.getString(Skins.KEY_STUDIO_FONT, Skins.FONT_DEFAULT) ?: Skins.FONT_DEFAULT
            val installedFonts = FontExtensionManager.getInstalledFonts(activity)
            val installedFilenames = installedFonts.map { it.filename.lowercase(Locale.ROOT) }.toSet()

            fun renderFontPreviewBox(tf: Typeface?, isCurrent: Boolean): View {
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), if (isCurrent) ColorUtils.setAlphaComponent(tokens.accent, 110) else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8f)
                    }
                }
                val sampleTv = TextView(activity).apply {
                    text = "The quick brown fox jumps over the lazy dog • 12345"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setTextColor(tokens.textPrimary)
                    includeFontPadding = false
                    if (tf != null) typeface = tf
                }
                box.addView(sampleTv)

                val keyRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8f)
                    }
                }
                val sampleLetters = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                for (letter in sampleLetters) {
                    val k = TextView(activity).apply {
                        text = letter
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                        setTypeface(tf, Typeface.BOLD)
                        setTextColor(if (isCurrent) tokens.accent else tokens.textPrimary)
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setPadding(0, 0, 0, 0)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                            setColor(tokens.surface)
                            setStroke(dp(0.8f), if (isCurrent) ColorUtils.setAlphaComponent(tokens.accent, 90) else tokens.cardBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dp(26f), 1f).apply {
                            marginStart = dp(1.5f)
                            marginEnd = dp(1.5f)
                        }
                    }
                    keyRow.addView(k)
                }
                box.addView(keyRow)
                return box
            }

            // Header for installed
            val headerInst = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4f), dp(4f), dp(4f), dp(8f))
            }
            headerInst.addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply { setTint(tokens.accent) })
                val s = dp(16f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(6f) }
            })
            headerInst.addView(TextView(activity).apply {
                text = "INSTALLED FONTS (${installedFonts.size + 1})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            })
            listContainer.addView(headerInst)

            // Default Font Row
            val isDefActive = curFont == Skins.FONT_DEFAULT
            val defCard = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16f).toFloat()
                    setColor(tokens.surface)
                    setStroke(dp(1f), if (isDefActive) tokens.accent else tokens.cardBorder)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10f)
                }
            }
            val defRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val defCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) }
            }
            val defTitleRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            defTitleRow.addView(TextView(activity).apply {
                text = "Inter / Default System"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
            })
            if (isDefActive) {
                defTitleRow.addView(TextView(activity).apply {
                    text = " ACTIVE / BEING USED "
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#10B981"))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(6f).toFloat()
                        setColor(ColorUtils.setAlphaComponent(Color.parseColor("#10B981"), 35))
                    }
                    setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(8f)
                    }
                })
            }
            defCol.addView(defTitleRow)
            defCol.addView(TextView(activity).apply {
                text = "Default high-legibility sans-serif font"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(2f), 0, 0)
            })
            defRow.addView(defCol)
            if (!isDefActive) {
                val actBtn = TextView(activity).apply {
                    text = "Apply"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.accent)
                    }
                    setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                    setOnClickListener {
                        p.edit().putString(Skins.KEY_STUDIO_FONT, Skins.FONT_DEFAULT).putBoolean(Skins.KEY_USE_SYSTEM_FONT, false).apply()
                        activity.notifySettingsChanged(restart = true)
                        activity.showToast("Applied Default Font")
                        render()
                    }
                }
                defRow.addView(actBtn)
            }
            defCard.addView(defRow)
            defCard.addView(renderFontPreviewBox(null, isDefActive))
            listContainer.addView(defCard)

            // Installed downloaded fonts
            for (f in installedFonts) {
                if (filterQuery.isNotEmpty() && !f.name.contains(filterQuery, ignoreCase = true) && !f.filename.contains(filterQuery, ignoreCase = true)) {
                    continue
                }
                val isCurrent = curFont.equals(f.filename, ignoreCase = true) || curFont.equals(f.filename.removeSuffix(".ttf"), ignoreCase = true)
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16f).toFloat()
                        setColor(tokens.surface)
                        setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10f)
                    }
                }

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val col = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) }
                }

                val titleRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(activity).apply {
                    text = f.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                })

                if (isCurrent) {
                    titleRow.addView(TextView(activity).apply {
                        text = " ACTIVE / BEING USED "
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#10B981"))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(6f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.parseColor("#10B981"), 35))
                        }
                        setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(8f)
                        }
                    })
                }
                col.addView(titleRow)

                val previewTf = FontExtensionManager.getTypeface(activity, f.filename)
                col.addView(TextView(activity).apply {
                    text = "${f.filename} • Custom typeface"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(tokens.textSecondary)
                    setPadding(0, dp(2f), 0, 0)
                })
                row.addView(col)

                if (!isCurrent) {
                    val actBtn = TextView(activity).apply {
                        text = "Apply"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.onAccentText)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12f).toFloat()
                            setColor(tokens.accent)
                        }
                        setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                        setOnClickListener {
                            p.edit().putString(Skins.KEY_STUDIO_FONT, f.filename).apply()
                            p.edit().putBoolean(Skins.KEY_USE_SYSTEM_FONT, false).apply()
                            activity.notifySettingsChanged(restart = true)
                            activity.showToast("Applied ${f.name} font to keycaps")
                            render()
                        }
                    }
                    row.addView(actBtn)
                }

                val delBtn = FrameLayout(activity).apply {
                    val s = dp(34f)
                    layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dp(8f) }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(ColorUtils.setAlphaComponent(Color.RED, if (tokens.isLight) 25 else 40))
                    }
                    val iv = ImageView(activity).apply {
                        val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                            ?: ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
                        d?.setTint(Color.parseColor("#FF5252"))
                        setImageDrawable(d)
                        val pad = dp(8f)
                        setPadding(pad, pad, pad, pad)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    addView(iv)
                    setOnClickListener {
                        FontExtensionManager.deleteFont(activity, f.filename)
                        if (isCurrent) {
                            p.edit().putString(Skins.KEY_STUDIO_FONT, Skins.FONT_DEFAULT).apply()
                        }
                        activity.showToast("Deleted ${f.name}")
                        activity.notifySettingsChanged(restart = true)
                        render()
                    }
                }
                row.addView(delBtn)
                card.addView(row)
                card.addView(renderFontPreviewBox(previewTf, isCurrent))
                listContainer.addView(card)
            }

            // Section 2: Online fonts
            val available = remoteFonts.filter { !installedFilenames.contains(it.file.lowercase(Locale.ROOT)) }
            if (available.isNotEmpty()) {
                val headerAvail = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4f), dp(14f), dp(4f), dp(8f))
                }
                headerAvail.addView(ImageView(activity).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_download)?.mutate()?.apply { setTint(tokens.accent) })
                    val s = dp(16f)
                    layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(6f) }
                })
                headerAvail.addView(TextView(activity).apply {
                    text = "AVAILABLE ONLINE FONTS (${available.size})"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.accent)
                })
                listContainer.addView(headerAvail)

                for (item in available) {
                    if (filterQuery.isNotEmpty() && !item.name.contains(filterQuery, ignoreCase = true) && !item.file.contains(filterQuery, ignoreCase = true)) {
                        continue
                    }
                    val itemCard = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(16f).toFloat()
                            setColor(tokens.surface)
                            setStroke(dp(1f), tokens.cardBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(10f)
                        }
                    }

                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val col = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) }
                    }
                    col.addView(TextView(activity).apply {
                        text = item.name
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                    })
                    val kb = if (item.size > 0) "${item.size / 1024} KB" else "TrueType Font"
                    col.addView(TextView(activity).apply {
                        text = "$kb • Clean custom keycap typeface"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        setTextColor(tokens.textSecondary)
                        setPadding(0, dp(3f), 0, 0)
                    })
                    row.addView(col)

                    val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        isIndeterminate = false
                        max = 100
                        progress = 0
                        visibility = View.GONE
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4f)).apply {
                            topMargin = dp(8f)
                        }
                    }

                    val dlBtn = TextView(activity).apply {
                        text = "Download"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.onAccentText)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12f).toFloat()
                            setColor(tokens.accent)
                        }
                        setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                        setOnClickListener {
                            isEnabled = false
                            progressBar.visibility = View.VISIBLE
                            FontExtensionManager.downloadFont(activity, item, { pct ->
                                activity.runOnUiThread { progressBar.progress = pct }
                            }) { file ->
                                val success = file != null && file.exists()
                                activity.runOnUiThread {
                                    if (success) {
                                        activity.showToast("Downloaded ${item.name} font")
                                        activity.notifySettingsChanged(restart = true)
                                        render()
                                    } else {
                                        isEnabled = true
                                        progressBar.visibility = View.GONE
                                        activity.showToast("Download failed")
                                    }
                                }
                            }
                        }
                    }
                    row.addView(dlBtn)
                    itemCard.addView(row)
                    val previewHolder = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    previewHolder.addView(renderFontPreviewBox(null, false))
                    itemCard.addView(previewHolder)
                    FontExtensionManager.loadPreviewTypeface(activity, item) { loadedTf ->
                        if (loadedTf != null) {
                            activity.runOnUiThread {
                                previewHolder.removeAllViews()
                                previewHolder.addView(renderFontPreviewBox(loadedTf, false))
                            }
                        }
                    }
                    itemCard.addView(progressBar)
                    listContainer.addView(itemCard)
                }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                filterQuery = s?.toString()?.trim() ?: ""
                render()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Fetch catalog online
        FontExtensionManager.fetchRemoteCatalog { catalog ->
            if (catalog != null) {
                remoteFonts = catalog
                render()
            }
        }

        render()
        return root
    }

    private fun showAppInfoDialog() {
        AlertDialog.Builder(activity)
            .setTitle("App Information")
            .setMessage("Gnosis Keyboard v2.1.83\nBuild Code: 183\nTarget SDK: 35 (Android 15)\nMin SDK: 26 (Android 8.0)\nArchitecture: Fully Offline Native Kotlin\nDeveloper: Gnosis Core Team")
            .setPositiveButton("OK", null)
            .show()
    }
}
