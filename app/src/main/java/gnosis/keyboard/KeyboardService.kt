package gnosis.keyboard

import gnosis.keyboard.stats.TypingStatisticsRepository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import gnosis.keyboard.emoji.EmojiSuggestionEngine
import gnosis.keyboard.engine.spell.ContractionEngine
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import gnosis.keyboard.dictionary.UserDictionaryRepository
import gnosis.keyboard.engine.Candidate
import gnosis.keyboard.engine.CandidateKind
import gnosis.keyboard.engine.EngineFactory
import gnosis.keyboard.engine.TypingEngine
import gnosis.keyboard.engine.voice.AgenticVoiceCommander
import gnosis.keyboard.engine.voice.VoiceCommand
import gnosis.keyboard.emoji.EmojiKitchenEngine
import gnosis.keyboard.engine.security.MessageArmourEngine
import gnosis.keyboard.engine.language.GnosisLanguageManager
import gnosis.keyboard.engine.vector.GnosisNeuralVectorEngine
import gnosis.keyboard.ime.EditorConnectionController
import gnosis.keyboard.text.AutocorrectEngine
import gnosis.keyboard.text.SuggestionEngine
import gnosis.keyboard.ui.KeyboardResizeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

class KeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private enum class Layer { LETTERS, NUMBERS, SYMBOLS, NUMPAD, EMOJI, CLIPBOARD, EDIT }
    private enum class Shift { OFF, ONCE, LOCK }

    companion object {
        const val REPEAT_DELAY = 400L
        const val REPEAT_MS = 60L
        const val DOUBLE_TAP_MS = 320L
        const val EMOJI_COLS = 7
        const val RECENT_MAX = 28
        const val SWIPE_START_DP = 22
        const val GLIDE_MIN_MS = 110L
        const val GLIDE_MIN_PATH_DP = 70
        const val SUGGESTION_DELAY_MS = 50L
        const val SWIPE_STEP_DP = 6
        var KEY_HEIGHT_DP = 48
        const val NUMBER_ROW = "1234567890"
        const val BAR_H_DP = 46f
        const val BAR_BTN_H_DP = 38f
        const val BAR_GAP_DP = 4f
        const val FLOAT_HANDLE_DP = 26f
        const val FLOAT_SHADOW_DP = 16f
        const val FLOAT_GUTTER_DP = 18f

        @Volatile
        var activeInstance: KeyboardService? = null
        var isResizingCorner = false
        var isMovingFloating = false

        fun updateLongPressMs(ms: Long) {
            activeInstance?.let {
                it.longPressMs = ms.coerceIn(50L, 500L)
            }
        }

        val LAYOUTS = mapOf(
            "qwerty" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        )

        val NUMBER_ROWS = listOf("1234567890", "@#\$_&-+()/", "*\"':;!?")
        val SYMBOL_ROWS = listOf("~`|•√π÷×§¶", "£¢€¥^°={}\\", "%©®™✓[]")

        val TOP_ROW_DIGITS = mapOf(
            'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
            'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0"
        )

        val ALTERNATES = mapOf(
            'a' to listOf("@", "á", "à", "â", "ä", "ã", "å", "ā", "æ"),
            'b' to listOf(";"),
            'c' to listOf("'", "‘", "’", "ç", "ć", "č"),
            'd' to listOf("$", "₹", "€", "£", "¥", "ď", "đ"),
            'e' to listOf("3", "³", "¾", "é", "è", "ê", "ë", "ē", "ę"),
            'f' to listOf("%", "‰", "_"),
            'g' to listOf("&", "§", "ğ", "ġ"),
            'h' to listOf("-", "–", "—", "_"),
            'i' to listOf("8", "⁸", "í", "ì", "î", "ï", "ī", "į"),
            'j' to listOf("+", "±", "="),
            'k' to listOf("(", "[", "{"),
            'l' to listOf(")", "]", "}"),
            'm' to listOf("?", "¿"),
            'n' to listOf("!", "¡", "ñ", "ń", "ň"),
            'o' to listOf("9", "⁹", "ó", "ò", "ô", "ö", "õ", "ō", "ø", "œ"),
            'p' to listOf("0", "⁰"),
            'q' to listOf("1", "¹", "½"),
            'r' to listOf("4", "⁴", "ŕ", "ř"),
            's' to listOf("#", "ß", "ś", "š", "ş"),
            't' to listOf("5", "⁵", "ţ", "ť"),
            'u' to listOf("7", "⁷", "ú", "ù", "û", "ü", "ū", "ů"),
            'v' to listOf(":"),
            'w' to listOf("2", "²", "⅔"),
            'x' to listOf("\"", "“", "”", "„"),
            'y' to listOf("6", "⁶", "ý", "ÿ", "ŷ"),
            'z' to listOf("*", "×", "•", "ź", "ž", "ż"),
            '1' to listOf("¹", "½", "⅓", "¼"), '2' to listOf("²", "⅔"), '3' to listOf("³", "¾", "⅜"),
            '4' to listOf("⁴"), '5' to listOf("⁵", "⅝"), '6' to listOf("⁶"), '7' to listOf("⁷", "⅞"),
            '8' to listOf("⁸"), '9' to listOf("⁹"), '0' to listOf("+", "⁰", "°", "∅"),
            '-' to listOf("–", "—", "_", "−"), '/' to listOf("÷", "\\", "⁄"), '$' to listOf("₹", "£", "€", "¥", "¢", "₽", "₩", "₿"),
            '%' to listOf("‰"), '&' to listOf("§"), '"' to listOf("“", "”", "„"),
            '\'' to listOf("‘", "’", "‚"), '.' to listOf("…", "?", "!", ",", "-", ":", ";", "'", "\"", "@", "/", "#"), ',' to listOf("،", ";", ":", "'", "?", "!", "*"),
            '?' to listOf("¿", "‽"), '!' to listOf("¡"), '(' to listOf("[", "{", "<"), ')' to listOf("]", "}", ">"),
            '+' to listOf("±", "∓", "†", "‡"), '=' to listOf("≠", "≈", "≤", "≥", "±", "≡"), '*' to listOf("×", "•", "★", "✱"),
            '<' to listOf("«", "≤", "‹", "⟨"), '>' to listOf("»", "≥", "›", "⟩"),
            '[' to listOf("{", "⟨", "‹", "«"), ']' to listOf("}", "⟩", "›", "»"),
            '{' to listOf("[", "⟨"), '}' to listOf("]", "⟩"),
            '£' to listOf("€", "$", "¥", "¢", "₹", "₽", "₩"),
            '€' to listOf("$", "£", "¥", "¢", "₹"),
            '¥' to listOf("₩", "元", "$", "€", "£"),
            '¢' to listOf("$", "€", "£"),
            '~' to listOf("≈", "∼", "≃"),
            '^' to listOf("↑", "↓", "←", "→", "Δ"),
            '°' to listOf("′", "″", "℃", "℉"),
            '•' to listOf("★", "☆", "♠", "♣", "♥", "♦"),
            '✓' to listOf("✔", "☑", "✗", "✘"),
            '¶' to listOf("§"),
            '§' to listOf("¶"),
            '|' to listOf("¦", "‖", "\\"),
            '\\' to listOf("/"),
            'π' to listOf("Δ", "Ω", "μ", "θ")
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSelStart = -1
    private val letterViews = mutableListOf<View>()
    private val keyBoundingBoxes = mutableMapOf<Char, RectF>()
    private lateinit var rootLayout: KeyboardRootLayout
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var panelContainer: FrameLayout

    private lateinit var skin: Skin
    private var shiftView: View? = null
    private var shiftIconView: ImageView? = null
    private var density = 1f
    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var lastShiftTap = 0L

    private var toolbarEmojiBtn: View? = null
    private var toolbarOneHandedBtn: View? = null
    private var toolbarMicBtn: View? = null
    private var toolbarClipBtn: View? = null
    private var toolbarEditBtn: View? = null
    private var toolbarFloatBtn: View? = null

    private var toolboxMicBtn: View? = null
    private var toolboxClipBtn: View? = null
    private var toolboxEditBtn: View? = null
    private var toolboxFloatBtn: View? = null

    private var clipOptionsPopup: PopupWindow? = null
    private var clipOptionsDismissTime = 0L

    private var layoutStyle = Skins.STYLE_REVAMPED
    var longPressMs: Long = Skins.DEFAULT_LONG_PRESS_MS.toLong()
    var secondaryHintsEnabled: Boolean = Skins.DEFAULT_SECONDARY_HINTS
    private var cachedEmojiPanel: View? = null
    private val prewarmEmojiRunnable = Runnable {
        if (isInputViewShown && cachedEmojiPanel == null) {
            prewarmEmojiPanel()
        }
    }
    private var restartReceiverRegistered = false
    private val restartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lightUpdate = intent?.getBooleanExtra("light_update", false) == true
            isSkinDirty = true
            if (lightUpdate) {
                loadPreferences(forceRebuildSkin = true)
            } else {
                restartKeyboard()
            }
        }
    }

    private var floatingMode = false
    private var floatWidthPct = 86
    private var floatHeightPct = 100
    private var editingQuickKey = false
    private var previewMode = false
    private var floatCard: LinearLayout? = null
    private var floatX = 0
    private var floatY = 0
    private var imeHost: FrameLayout? = null
    private val floatPrefs by lazy { getSharedPreferences("gnosis_float", Context.MODE_PRIVATE) }
    private lateinit var resizeManager: KeyboardResizeManager

    private class EnterKeyRefs(val container: View, val icon: ImageView, val title: TextView, var close: Boolean = false)
    private val enterKeys = mutableListOf<EnterKeyRefs>()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningVoice = false
    private var isVoiceComposing = false
    private var voiceNeedsLeadingSpace = false
    private var currentVoiceSpokenText = ""
    private var hasCommittedVoiceResults = false
    private var voiceRetriedDefaultLang = false

    private lateinit var editorController: EditorConnectionController
    private val autocorrectEngine = AutocorrectEngine()
    private val statsRepository by lazy { TypingStatisticsRepository.getInstance(this) }
    private var pendingRolloverCommit: (() -> Unit)? = null

    private fun flushPendingRollover() {
        val pending = pendingRolloverCommit
        pendingRolloverCommit = null
        pending?.invoke()
    }
    private lateinit var userDictionary: UserDictionaryRepository
    private var suggestionEngine: SuggestionEngine? = null

    private var predictionsEnabled = true
    private var emojiSuggestionsEnabled = true
    private var doubleSpacePeriodEnabled = true
    private var lastSpaceTapTime = 0L
    private var currentEmojiSuggestions = emptyList<String>()
    private var voiceReceiverRegistered = false
    private val voicePermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val granted = intent?.getBooleanExtra(VoicePermissionActivity.EXTRA_GRANTED, false) ?: false
            if (granted) {
                startVoiceInput()
            }
        }
    }

    private var voiceInputReceiverRegistered = false
    private val voiceInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(VoiceInputActivity.EXTRA_TEXT) ?: return
            if (text.isNotEmpty()) {
                val ic = currentInputConnection
                if (ic != null) {
                    val before = ic.getTextBeforeCursor(1, 0)
                    val needsLeadingSpace = !before.isNullOrEmpty() && !before.last().isWhitespace() && before.last() != '\n'
                    val textToCommit = if (needsLeadingSpace) " $text " else "$text "
                    ic.commitText(textToCommit, 1)
                }
            }
            isListeningVoice = false
            updateToolbarState()
            updateSuggestions()
        }
    }

    private var clipClearPending = false
    private var clipPanel: LinearLayout? = null
    private var reopenClipboardTab: String? = null
    private var reopenClipboardAt = 0L
    private var clipReceiverRegistered = false
    private val clipPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cat = intent?.getStringExtra(ClipPermissionActivity.EXTRA_CATEGORY) ?: return
            if (intent.getBooleanExtra(ClipPermissionActivity.EXTRA_GRANTED, false)) {
                reopenClipboardTab = cat
                reopenClipboardAt = System.currentTimeMillis()
                val panel = clipPanel
                if (layer == Layer.CLIPBOARD && panel != null && panel.isAttachedToWindow) {
                    clipClearPending = false
                    renderClipboardItems(panel)
                }
            }
            ScreenshotWatcher.sync(this@KeyboardService)
            resumeAfterPermission()
        }
    }

    private var numberRowEnabled = true
    private var keyPopupEnabled = true
    private var soundEnabled = true
    private var soundVolume = 0.8f
    private var soundTone = "default"
    private var hapticEnabled = false
    private var hapticPressure = 50
    private var oneHandedMode = "off"
    private var glideEnabled = true
    private var gestureDeleteEnabled = true
    private var cursorControlEnabled = true
    private var suggestionsEnabled = true
    private var autoCorrectEnabled = true
    private var grammarCheckEnabled = true
    private var currentAutoCorrectCandidate: Candidate? = null
    private var typingEngine: TypingEngine? = null
    private fun getTypingEngine(): TypingEngine? {
        typingEngine?.let { return it }
        val effectiveLang = GnosisLanguageManager.resolveEffectiveLanguage(this)
        return try {
            EngineFactory.create(this, effectiveLang.code).also { typingEngine = it }
        } catch (e: Throwable) {
            android.util.Log.e("KeyboardService", "Failed to initialize typing engine", e)
            null
        }
    }
    private var currentLayoutName = "qwerty"

    private var sounds: KeySounds? = null
    private val currentWord = StringBuilder()
    private var lastCommittedWord = ""
    private var isNewInputSession = true

    private var activePopup: PopupWindow? = null
    private var activePreviewPopup: PopupWindow? = null
    private var crossWindowBlurListener: java.util.function.Consumer<Boolean>? = null
    private var keyPreviewBubble: TextView? = null
    private val activeLongPresses = HashSet<Runnable>()
    private class AltState(val items: List<String>, val cells: List<TextView>, val left: Int, val pad: Int, val cellW: Int, var selected: Int)
    private var altState: AltState? = null

    private var photoExtended = false
    private var toolboxOpen = false
    private var quickKeys: List<String> = ToolbarConfig.DEFAULT_QUICK

    private var currentEmojiCategoryIndex = 0

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Skins.KEY_LONG_PRESS_MS) {
            longPressMs = Skins.prefs(this).getInt(Skins.KEY_LONG_PRESS_MS, Skins.DEFAULT_LONG_PRESS_MS).toLong().coerceIn(50L, 500L)
            return@OnSharedPreferenceChangeListener
        }
        if (key == Skins.KEY_THEME || key == Skins.KEY_THEME_MODE || key == Skins.KEY_ACCENT || key == Skins.KEY_KEY_STYLE
            || key == Skins.KEY_LUCID_VOID_OPACITY || key == Skins.KEY_LUCID_VOID_BLUR
            || key == Skins.KEY_LUCID_VOID_OPACITY_DARK || key == Skins.KEY_LUCID_VOID_OPACITY_LIGHT
            || key == Skins.KEY_LUCID_VOID_BLUR_DARK || key == Skins.KEY_LUCID_VOID_BLUR_LIGHT
            || key == Skins.KEY_LUCID_VOID_TINT_LIGHT || key == Skins.KEY_LUCID_VOID_TINT_DARK
            || key == Skins.KEY_GLASS_ENABLED || key == Skins.KEY_GLASS_OPACITY) {
            cachedEmojiPanel = null
            isSkinDirty = true
        }
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 40L)
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        ClipboardHistory.syncFromSystem(this@KeyboardService)
        refreshSuggestionsNow()
    }

    private val refreshRunnable = Runnable {
        if (!::rootLayout.isInitialized) return@Runnable
        loadPreferences(forceRebuildSkin = isSkinDirty)
        ScreenshotWatcher.sync(this, scanNow = false)
        rootLayout.accentColor = skin.accent
        renderFullKeyboard()
        updateAutoCaps()
        applyHostMode()
    }

    override fun onCreate() {
        super.onCreate()
        setExtractViewShown(false)
        setCandidatesViewShown(false)
        activeInstance = this
        density = resources.displayMetrics.density
        lastUiNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        resizeManager = KeyboardResizeManager(this)
        floatingMode = Skins.prefs(this).getBoolean(Skins.KEY_FLOATING, false)
        Skins.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        editorController = EditorConnectionController(this)
        userDictionary = UserDictionaryRepository(this)
        WordDictionary.init(this)
        ContractionEngine.init(this)
        EmojiSuggestionEngine.init(this)
        val appCtx = applicationContext
        serviceScope.launch(Dispatchers.IO) {
            try {
                val engine = EngineFactory.create(appCtx)
                withContext(Dispatchers.Main) {
                    if (typingEngine == null) typingEngine = engine
                }
            } catch (e: Throwable) {
                android.util.Log.e("KeyboardService", "Async engine preload failed", e)
            }
        }
        ClipboardHistory.init(this)
        ClipboardHistory.syncFromSystem(this)
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.addPrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
        try {
            sounds = KeySounds(this)
        } catch (_: Exception) {}

        try {
            val filter = IntentFilter(VoicePermissionActivity.ACTION_VOICE_PERMISSION_RESULT)
            ContextCompat.registerReceiver(
                this, voicePermissionReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            voiceReceiverRegistered = true
        } catch (_: Exception) {}

        try {
            val filter = IntentFilter(VoiceInputActivity.ACTION_VOICE_INPUT_RESULT)
            ContextCompat.registerReceiver(
                this, voiceInputReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            voiceInputReceiverRegistered = true
        } catch (_: Exception) {}

        try {
            ContextCompat.registerReceiver(
                this, clipPermissionReceiver, IntentFilter(ClipPermissionActivity.ACTION_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            clipReceiverRegistered = true
        } catch (_: Exception) {}

        ScreenshotWatcher.sync(this)

        try {
            val restartFilter = IntentFilter().apply {
                addAction(Skins.ACTION_RESTART_KEYBOARD)
                addAction("com.gnosis.keyboard.SETTINGS_UPDATED")
            }
            ContextCompat.registerReceiver(
                this, restartReceiver, restartFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            restartReceiverRegistered = true
        } catch (_: Exception) {}
    }

    fun restartKeyboard() {
        handler.removeCallbacks(refreshRunnable)
        cachedEmojiPanel = null
        clipPanel = null
        dismissAllPopups()
        isSkinDirty = true
        EngineFactory.invalidateCache()
        SuggestionExtensionManager.invalidateCache()
        suggestionEngine = null
        loadPreferences(forceRebuildSkin = true)
        ScreenshotWatcher.sync(this, scanNow = false)
        if (::rootLayout.isInitialized) {
            rootLayout.accentColor = skin.accent
            renderFullKeyboard()
            updateAutoCaps()
            applyHostMode()
            refreshSuggestionsNow()
            buzz()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (activeInstance === this) activeInstance = null
        Skins.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        dismissAllPopups()
        handler.removeCallbacksAndMessages(null)
        if (voiceReceiverRegistered) {
            try {
                unregisterReceiver(voicePermissionReceiver)
            } catch (_: Exception) {}
            voiceReceiverRegistered = false
        }
        if (voiceInputReceiverRegistered) {
            try {
                unregisterReceiver(voiceInputReceiver)
            } catch (_: Exception) {}
            voiceInputReceiverRegistered = false
        }
        if (clipReceiverRegistered) {
            try {
                unregisterReceiver(clipPermissionReceiver)
            } catch (_: Exception) {}
            clipReceiverRegistered = false
        }
        if (restartReceiverRegistered) {
            try {
                unregisterReceiver(restartReceiver)
            } catch (_: Exception) {}
            restartReceiverRegistered = false
        }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.removePrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
        ClipboardHistory.changeListener = null
        clipPanel = null
        cachedEmojiPanel = null
        keyPreviewBubble = null
        floatCornerIndicators = null
        floatResetBtn = null
        destroyVoiceRecognition()
        sounds?.release()
        sounds = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val wm = getSystemService(WindowManager::class.java)
                crossWindowBlurListener?.let {
                    wm?.removeCrossWindowBlurEnabledListener(it)
                }
            } catch (_: Exception) {}
            crossWindowBlurListener = null
        }
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(prewarmEmojiRunnable)
        handler.removeCallbacks(refreshRunnable)
        pendingRolloverCommit = null
        activeLongPresses.forEach { handler.removeCallbacks(it) }
        activeLongPresses.clear()
        ClipboardHistory.changeListener = null
        dismissAllPopups()
        stopVoiceRecognition()
        resizeOverlay?.let {
            imeHost?.removeView(it)
            resizeOverlay = null
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        ClipboardHistory.syncFromSystem(this)
        ScreenshotWatcher.sync(this)
        refreshSuggestionsNow()
    }

    override fun onWindowHidden() {
        handler.removeCallbacks(prewarmEmojiRunnable)
        handler.removeCallbacks(refreshRunnable)
        pendingRolloverCommit = null
        activeLongPresses.forEach { handler.removeCallbacks(it) }
        activeLongPresses.clear()
        dismissAllPopups()
        stopVoiceRecognition()
        resizeOverlay?.let {
            imeHost?.removeView(it)
            resizeOverlay = null
        }
        layer = Layer.LETTERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window?.window?.let { w ->
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = w.attributes
                    lp.blurBehindRadius = 0
                    w.attributes = lp
                }
            } catch (_: Throwable) {}
        }
        super.onWindowHidden()
    }

    override fun onEvaluateFullscreenMode(): Boolean =
        if (floatActive()) false else super.onEvaluateFullscreenMode()

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!floatActive()) return
        val decorH = window.window?.decorView?.height ?: return
        if (decorH <= 0) return
        outInsets.contentTopInsets = decorH
        outInsets.visibleTopInsets = decorH
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        if (activeSwitcherOverlay != null || isResizingCorner || isMovingFloating) {
            outInsets.touchableRegion.set(0, 0, screenW(), decorH)
            return
        }
        if (::rootLayout.isInitialized && rootLayout.parent != null && rootLayout.width > 0) {
            val loc = IntArray(2)
            rootLayout.getLocationInWindow(loc)
            val pad = dp(48f)
            outInsets.touchableRegion.set(
                (loc[0] - pad).coerceAtLeast(0),
                (loc[1] - pad).coerceAtLeast(0),
                (loc[0] + rootLayout.width + pad).coerceAtMost(screenW()),
                (loc[1] + rootLayout.height + pad).coerceAtMost(decorH)
            )
        } else {
            outInsets.touchableRegion.setEmpty()
        }
    }

    private var lastUiNight = -1
    private var lastOrientation = -1
    private var landscapeSplit = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        density = resources.displayMetrics.density
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val orient = newConfig.orientation
        val orientChanged = lastOrientation != -1 && orient != lastOrientation
        lastOrientation = orient

        if (night != lastUiNight) {
            val hadPrevious = lastUiNight != -1
            lastUiNight = night
            if (hadPrevious) {
                isSkinDirty = true
                handler.removeCallbacks(refreshRunnable)
                handler.post(refreshRunnable)
            }
        }
        imeHost?.let { ViewCompat.requestApplyInsets(it) }
        if (::rootLayout.isInitialized && (floatActive() || orientChanged)) {
            loadPreferences()
            renderFullKeyboard()
            applyHostMode()
            applyHostBottomPadding()
        }
    }

    private var navBarBottomInset = 0

    private fun onHostInsets(insets: WindowInsetsCompat) {
        val bottom = insets.getInsets(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.tappableElement()
        ).bottom
        if (bottom != navBarBottomInset) {
            navBarBottomInset = bottom
            applyHostBottomPadding()
            applyWindowBlurAndStyles()
        }
    }

    private fun applyHostBottomPadding() {
        val host = imeHost ?: return
        val extraBottom = if (floatActive() || !::skin.isInitialized) 0 else dp(skin.bottomPaddingDp)
        val userResizeBottom = if (floatActive() || !::resizeManager.isInitialized) 0 else dp(resizeManager.bottomPadDp.toFloat())
        val bottom = (if (floatActive()) 0 else navBarBottomInset) + extraBottom + userResizeBottom
        if (host.paddingBottom != bottom) host.setPadding(0, 0, 0, bottom)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        lastSelStart = newSelStart
        val cursorJumped = (oldSelStart != -1 && (newSelStart < oldSelStart - 1 || newSelStart > oldSelStart + 1))
        val hasSelection = newSelStart != newSelEnd
        if (cursorJumped || hasSelection || oldSelStart == -1) {
            currentWord.clear()
            currentAutoCorrectCandidate = null
        }
        updateAutoCaps()
        updateSuggestions()
    }

    private fun applyWindowBlurAndStyles() {
        if (!::skin.isInitialized) return
        val isLucidVoid = skin.id == "lucid-void" || skin.id == "lucid-void_light"
        val isGlass = skin.glassEnabled || isLucidVoid
        val win = window?.window ?: return
        try {
            if (isGlass) {
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    @Suppress("DEPRECATION")
                    win.navigationBarColor = Color.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                }
            }
        } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val lp = win.attributes
                val hasBlurFlag = (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
                if (hasBlurFlag || lp.blurBehindRadius != 0) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    lp.blurBehindRadius = 0
                    win.attributes = lp
                }
            } catch (_: Throwable) {}
        }
    }

    private var isSkinDirty = true
    private var lastSkinOrientation = -1

    private fun loadPreferences(forceRebuildSkin: Boolean = false) {
        val p = Skins.prefs(this)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val useLandscapeProfile = isLandscape && p.getBoolean(Skins.KEY_LANDSCAPE_SEPARATE, false)
        landscapeSplit = isLandscape && p.getBoolean(Skins.KEY_LANDSCAPE_SPLIT, false)

        KEY_HEIGHT_DP = if (useLandscapeProfile) {
            Skins.prefInt(p, Skins.KEY_HEIGHT_LANDSCAPE, 36).coerceIn(28, 54)
        } else if (isLandscape) {
            36
        } else {
            Skins.prefInt(p, Skins.KEY_KEYBOARD_HEIGHT, 48).coerceIn(36, 68)
        }
        numberRowEnabled = p.getBoolean(Skins.KEY_NUMBER_ROW, false)
        keyPopupEnabled = p.getBoolean(Skins.KEY_KEY_POPUP, false)
        longPressMs = p.getInt(Skins.KEY_LONG_PRESS_MS, Skins.DEFAULT_LONG_PRESS_MS).toLong().coerceIn(50L, 500L)
        secondaryHintsEnabled = p.getBoolean(Skins.KEY_SECONDARY_HINTS, Skins.DEFAULT_SECONDARY_HINTS)
        if (!keyPopupEnabled) dismissPreview()
        soundEnabled = p.getBoolean(Skins.KEY_SOUND, false)
        soundVolume = p.getFloat(Skins.KEY_SOUND_VOLUME, 0.8f)
        soundTone = p.getString(Skins.KEY_SOUND_TONE, "default") ?: "default"
        hapticEnabled = p.getBoolean(Skins.KEY_HAPTIC, false)
        hapticPressure = p.getInt(Skins.KEY_HAPTIC_PRESSURE, 50)
        oneHandedMode = p.getString(Skins.KEY_ONE_HANDED, "off") ?: "off"
        glideEnabled = p.getBoolean(Skins.KEY_GLIDE, true)
        gestureDeleteEnabled = p.getBoolean(Skins.KEY_GESTURE_DELETE, true)
        cursorControlEnabled = p.getBoolean(Skins.KEY_CURSOR_CONTROL, true)
        suggestionsEnabled = p.getBoolean(Skins.KEY_SUGGESTIONS, true)
        autoCorrectEnabled = p.getBoolean(Skins.KEY_AUTOCORRECT, true)
        predictionsEnabled = p.getBoolean(Skins.KEY_PREDICTIONS, true)
        emojiSuggestionsEnabled = p.getBoolean(Skins.KEY_EMOJI_SUGGESTIONS, true)
        doubleSpacePeriodEnabled = p.getBoolean(Skins.KEY_DOUBLE_SPACE_PERIOD, true)
        grammarCheckEnabled = p.getBoolean(Skins.KEY_GRAMMAR, true)
        currentLayoutName = if (useLandscapeProfile) {
            p.getString(Skins.KEY_LAYOUT_LANDSCAPE, "qwerty") ?: "qwerty"
        } else {
            p.getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
        }
        floatingMode = p.getBoolean(Skins.KEY_FLOATING, false)
        floatWidthPct = p.getInt(Skins.KEY_FLOAT_WIDTH, 86).coerceIn(60, 100)
        floatHeightPct = p.getInt(Skins.KEY_FLOAT_HEIGHT, 100).coerceIn(60, 100)
        quickKeys = ToolbarConfig.loadQuick(this)
        layoutStyle = if (useLandscapeProfile) {
            p.getString(Skins.KEY_LAYOUT_STYLE_LANDSCAPE, Skins.STYLE_REVAMPED) ?: Skins.STYLE_REVAMPED
        } else {
            p.getString(Skins.KEY_LAYOUT_STYLE, Skins.STYLE_REVAMPED) ?: Skins.STYLE_REVAMPED
        }
        val curOrient = resources.configuration.orientation
        if (!::skin.isInitialized || forceRebuildSkin || isSkinDirty || lastSkinOrientation != curOrient) {
            skin = Skins.buildSkin(this)
            isSkinDirty = false
            lastSkinOrientation = curOrient
        }
        applyHostBottomPadding()
        applyWindowBlurAndStyles()
    }

    override fun onCreateInputView(): View {
        loadPreferences()
        density = resources.displayMetrics.density
        lastOrientation = resources.configuration.orientation
        buildRoot()
        val host = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (floatActive() && ::rootLayout.isInitialized && rootLayout.parent === this) {
                    val pad = dp(48f).toFloat()
                    val rx = ev.x - rootLayout.left
                    val ry = ev.y - rootLayout.top
                    val inPaddedBounds = rx in -pad..(rootLayout.width + pad) && ry in -pad..(rootLayout.height + pad)
                    if (isResizingCorner || isMovingFloating || inPaddedBounds) {
                        val childEv = MotionEvent.obtain(ev)
                        childEv.offsetLocation(-rootLayout.left.toFloat(), -rootLayout.top.toFloat())
                        val handled = rootLayout.dispatchTouchEvent(childEv)
                        childEv.recycle()
                        if (handled) return true
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
            val isLucidVoid = skin.id == "lucid-void" || skin.id == "lucid-void_light"
            val isGlass = skin.glassEnabled || isLucidVoid
            setBackgroundColor(if (floatActive() || isGlass) Color.TRANSPARENT else skin.background)
        }
        imeHost = host
        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            onHostInsets(insets)
            insets
        }
        renderFullKeyboard()
        applyHostMode(false)
        applyHostBottomPadding()
        applyWindowBlurAndStyles()
        return host
    }

    private fun buildRoot() {
        rootLayout = KeyboardRootLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            accentColor = skin.accent
            clipChildren = false
            clipToPadding = false
            if (skin.backgroundDrawable != null) {
                background = skin.backgroundDrawable
            } else {
                setBackgroundColor(skin.background)
            }
            onTouchDownListener = {
                showFloatControlsTemporarily()
            }
            addOnLayoutChangeListener { _, _, top, _, bottom, _, _, oldTop, oldBottom ->
                val newH = bottom - top
                val oldH = oldBottom - oldTop
                if (newH > 0 && newH != oldH && !floatActive()) {
                    applyWindowBlurAndStyles()
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val curNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (lastUiNight != -1 && curNight != lastUiNight) {
            lastUiNight = curNight
            isSkinDirty = true
        }
        imeHost?.let { ViewCompat.requestApplyInsets(it) }
        val stayInEmoji = layer == Layer.EMOJI
        beginInput(info, stayInEmoji)
        applyWindowBlurAndStyles()
        if (cachedEmojiPanel == null) {
            handler.removeCallbacks(prewarmEmojiRunnable)
            handler.postDelayed(prewarmEmojiRunnable, 800L)
        }
    }

    private fun beginInput(info: EditorInfo?, keepCurrentLayer: Boolean = false) {
        autocorrectEngine.clear()
        dismissedGrammarIssues.clear()
        editingQuickKey = info?.privateImeOptions == ToolbarConfig.IME_QUICK_EDIT
        previewMode = info?.privateImeOptions == ToolbarConfig.IME_PREVIEW
        loadPreferences()
        if (!keepCurrentLayer) {
            layer = if (isNumericInput(info)) Layer.NUMPAD else Layer.LETTERS
        }
        val reopen = reopenClipboardTab
        reopenClipboardTab = null
        if (reopen != null && System.currentTimeMillis() - reopenClipboardAt < 30_000L) {
            clipClearPending = false
            toolboxOpen = true
            layer = Layer.CLIPBOARD
        }
        ScreenshotWatcher.sync(this)
        ClipboardHistory.syncFromSystem(this)
        ClipboardHistory.changeListener = {
            val panel = clipPanel
            if (layer == Layer.CLIPBOARD && panel != null && panel.isAttachedToWindow) renderClipboardItems(panel)
            refreshSuggestionsNow()
        }
        shift = Shift.OFF
        isNewInputSession = true
        currentWord.clear()
        lastCommittedWord = ""
        currentAutoCorrectCandidate = null
        dismissAllPopups()
        renderFullKeyboard()
        updateAutoCaps()
        updateSuggestions()
    }

    private fun renderFullKeyboard() {
        dismissAllPopups()
        rootLayout.removeAllViews()
        letterViews.clear()
        enterKeys.clear()
        keyBoundingBoxes.clear()

        val customBg = CustomBackground.drawable(this, skin)
        photoExtended = customBg != null && CustomBackground.extendToBars(this)
        if (photoExtended) {
            rootLayout.background = customBg
        } else if (skin.backgroundDrawable != null) {
            rootLayout.background = skin.backgroundDrawable
        } else {
            rootLayout.setBackgroundColor(skin.background)
        }

        floatCard = null
        rootLayout.minimumHeight = 0
        val targetContainer: LinearLayout
        val isLucidVoid = skin.id == "lucid-void" || skin.id == "lucid-void_light"
        if (floatActive()) {
            rootLayout.background = null
            rootLayout.setPadding(0, 0, 0, 0)
            rootLayout.clipToOutline = false
            targetContainer = buildFloatingCard(customBg)
        } else {
            rootLayout.setPadding(0, 0, 0, 0)
            if (isLucidVoid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val d = resources.displayMetrics.density
                val topR = 20f * d
                rootLayout.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height + topR.toInt(), topR)
                    }
                }
                rootLayout.clipToOutline = true
            } else {
                rootLayout.clipToOutline = false
            }
            if (oneHandedMode != "off") {
                val oneHandWrapper = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val kbWidthWeight = 0.85f
                val sideBarWeight = 0.15f

                val sideBar = buildOneHandedSideBar()
                val mainKbContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, kbWidthWeight)
                }

                if (oneHandedMode == "left") {
                    oneHandWrapper.addView(mainKbContainer)
                    oneHandWrapper.addView(sideBar.apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, sideBarWeight)
                    })
                } else {
                    oneHandWrapper.addView(sideBar.apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, sideBarWeight)
                    })
                    oneHandWrapper.addView(mainKbContainer)
                }

                rootLayout.addView(oneHandWrapper)
                targetContainer = mainKbContainer
            } else {
                targetContainer = rootLayout
            }
        }

        if (layoutStyle == Skins.STYLE_REVAMPED) {
            val toolbar = buildOmnitypeToolbar()
            targetContainer.addView(toolbar)
            suggestionStrip = buildSuggestionStrip()
            targetContainer.addView(suggestionStrip)
        } else {
            suggestionStrip = buildSuggestionStrip()
            targetContainer.addView(suggestionStrip)
        }

        panelContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (customBg != null && !photoExtended) background = customBg
        }
        targetContainer.addView(panelContainer)

        renderActiveLayer()
        refreshSuggestionsNow()
    }

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels

    private fun floatActive(): Boolean = floatingMode

    private fun hintsActive(): Boolean = secondaryHintsEnabled && !floatActive()

    private fun shadowPadPx(): Int = dp(FLOAT_SHADOW_DP)

    private fun floatCardWidth(): Int {
        val cw = floatCard?.layoutParams?.width ?: 0
        if (cw > 0) return cw.coerceIn((screenW() * 0.50f).toInt(), (screenW() * 0.98f).toInt())
        return (screenW() * floatWidthPct / 100f).toInt().coerceAtMost(screenW())
    }

    private fun heightScale(): Float {
        return if (floatActive()) {
            (floatHeightPct / 100f).coerceIn(0.65f, 1.15f)
        } else if (::resizeManager.isInitialized) {
            resizeManager.heightScale()
        } else 1f
    }

    private fun floatWidthScale(): Float = if (floatActive()) {
        (floatCardWidth().toFloat() / screenW().toFloat()).coerceIn(0.50f, 1f)
    } else 1f

    private fun keyScaleFactor(): Float {
        if (!floatActive()) return 1f
        val wScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        return minOf(wScale, hScale).coerceIn(0.52f, 1f)
    }

    private fun floatHostHeight(): Int = (screenH() - dp(48f)).coerceAtLeast(dp(300f))

    private fun clampFloat() {
        val sW = screenW()
        val sH = screenH()
        val cardW = floatCardWidth()
        val cardH = if (floatCard != null && floatCard!!.height > 0) floatCard!!.height else dp(220f)
        val maxCardX = (sW - cardW).coerceAtLeast(0)
        floatX = floatX.coerceIn(0, maxCardX)

        val minCardY = dp(4f)
        val maxCardY = (sH - cardH - dp(48f)).coerceAtLeast(minCardY)
        floatY = floatY.coerceIn(minCardY, maxCardY)
    }

    private fun applyFloatPosition() {
        if (!floatActive() || !::rootLayout.isInitialized) return
        val lp = rootLayout.layoutParams as? FrameLayout.LayoutParams ?: return
        val cardW = floatCardWidth()
        if (lp.leftMargin == floatX && lp.bottomMargin == floatY && lp.width == cardW && lp.gravity == (Gravity.BOTTOM or Gravity.START)) return
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.width = cardW
        lp.leftMargin = floatX
        lp.bottomMargin = floatY
        rootLayout.layoutParams = lp
        rootLayout.requestLayout()
        window.window?.decorView?.requestLayout()
    }

    private fun saveFloatPosition() {
        floatPrefs.edit().putInt("x", floatX).putInt("y", floatY).apply()
        window.window?.decorView?.requestLayout()
    }

    private fun applyHostMode(updateShown: Boolean = true) {
        if (!::rootLayout.isInitialized) return
        val host = imeHost ?: return
        if (rootLayout.parent !== host) {
            (rootLayout.parent as? ViewGroup)?.removeView(rootLayout)
            host.addView(rootLayout)
        }
        if (floatActive()) {
            host.minimumHeight = screenH() - dp(24f)
            host.background = null
            val cardW = floatCardWidth()
            val maxCardX = (screenW() - cardW).coerceAtLeast(0)
            floatX = floatPrefs.getInt("x", maxCardX / 2)
            floatY = floatPrefs.getInt("y", dp(24f))
            clampFloat()
            rootLayout.layoutParams = FrameLayout.LayoutParams(
                cardW,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                leftMargin = floatX
                bottomMargin = floatY
            }
        } else {
            host.minimumHeight = 0
            val isLucidVoid = skin.id == "lucid-void" || skin.id == "lucid-void_light"
            val isGlass = skin.glassEnabled || isLucidVoid
            host.setBackgroundColor(if (isGlass) Color.TRANSPARENT else skin.background)
            rootLayout.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        applyHostBottomPadding()
        if (updateShown) updateInputViewShown()
    }

    private fun hideForOtherScreen() {
        requestHideSelf(0)
    }

    private fun resumeAfterPermission() {
        if (!floatingMode) return
        handler.postDelayed({
            try {
                if (currentInputStarted) showWindow(true)
            } catch (_: Exception) {}
        }, 400L)
    }

    private fun closeFloatingKeyboard() {
        dismissAllPopups()
        stopVoiceRecognition()
        layer = Layer.LETTERS
        toolboxOpen = false
        if (::rootLayout.isInitialized) renderFullKeyboard()
        requestHideSelf(0)
    }

    private var floatCornerIndicators: View? = null
    private var floatResetBtn: View? = null
    private val hideFloatControlsRunnable = Runnable {
        floatCornerIndicators?.animate()?.alpha(0f)?.setDuration(300L)?.start()
        floatResetBtn?.animate()?.alpha(0f)?.setDuration(300L)?.withEndAction {
            floatResetBtn?.isClickable = false
        }?.start()
    }

    private fun showFloatControlsTemporarily() {
        if (!floatActive()) return
        handler.removeCallbacks(hideFloatControlsRunnable)
        floatCornerIndicators?.let {
            it.animate().cancel()
            if (it.alpha < 1f) it.animate().alpha(1f).setDuration(150L).start()
        }
        floatResetBtn?.let {
            it.isClickable = true
            it.animate().cancel()
            if (it.alpha < 1f) it.animate().alpha(1f).setDuration(150L).start()
        }
        handler.postDelayed(hideFloatControlsRunnable, 3000L)
    }

    private enum class FloatCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private class FloatingContainer(context: Context) : FrameLayout(context) {
        var cardView: View? = null
        var overlayView: View? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val card = cardView
            val overlay = overlayView
            if (card != null) {
                measureChildWithMargins(card, widthMeasureSpec, 0, heightMeasureSpec, 0)
                val lp = card.layoutParams as MarginLayoutParams
                val totalW = card.measuredWidth + lp.leftMargin + lp.rightMargin
                val totalH = card.measuredHeight + lp.topMargin + lp.bottomMargin
                setMeasuredDimension(totalW, totalH)
                overlay?.measure(
                    MeasureSpec.makeMeasureSpec(totalW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(totalH, MeasureSpec.EXACTLY)
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            overlayView?.layout(0, 0, width, height)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val overlay = overlayView
            if (overlay != null && overlay.visibility == View.VISIBLE && overlay.alpha > 0.05f) {
                if (KeyboardService.isResizingCorner || ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    val handled = overlay.dispatchTouchEvent(ev)
                    if (handled) return true
                }
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private class FloatingResizeOverlayView(
        context: Context,
        val card: View,
        val density: Float,
        val outerPad: Float,
        val isLight: Boolean,
        val onShowControls: () -> Unit,
        val onDrag: (FloatCorner, Float, Float, Int) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(Color.parseColor("#38BDF8"), 55)
            style = Paint.Style.STROKE
            strokeWidth = 7f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
        }
        private val beadCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.parseColor("#0F172A") else Color.WHITE
            style = Paint.Style.FILL
        }
        private val dashedFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(Color.parseColor("#38BDF8"), 65)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
        }

        private var activeCorner: FloatCorner? = null
        private var downRawX = 0f
        private var downRawY = 0f
        private val frameRect = RectF()
        private val pathTL = Path()
        private val pathTR = Path()
        private val pathBL = Path()
        private val pathBR = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cardW = (card.layoutParams?.width?.takeIf { it > 0 } ?: width).toFloat()
            val cardH = (card.layoutParams?.height?.takeIf { it > 0 } ?: height).toFloat()
            if (cardW <= 0f || cardH <= 0f) return

            val cl = 0f
            val ct = 0f
            val cr = cardW
            val cb = cardH

            val r = 20f * density
            val arm = 30f * density
            val offset = 2f * density

            frameRect.set(cl, ct, cr, cb)
            canvas.drawRoundRect(frameRect, r, r, dashedFramePaint)

            pathTL.reset()
            pathTL.moveTo(cl - offset, ct + r + arm)
            pathTL.lineTo(cl - offset, ct + r)
            pathTL.arcTo(cl - offset, ct - offset, cl - offset + 2 * r, ct - offset + 2 * r, 180f, 90f, false)
            pathTL.lineTo(cl + r + arm, ct - offset)

            pathTR.reset()
            pathTR.moveTo(cr - r - arm, ct - offset)
            pathTR.lineTo(cr + offset - r, ct - offset)
            pathTR.arcTo(cr + offset - 2 * r, ct - offset, cr + offset, ct - offset + 2 * r, 270f, 90f, false)
            pathTR.lineTo(cr + offset, ct + r + arm)

            pathBL.reset()
            pathBL.moveTo(cl + r + arm, cb + offset)
            pathBL.lineTo(cl - offset + r, cb + offset)
            pathBL.arcTo(cl - offset, cb + offset - 2 * r, cl - offset + 2 * r, cb + offset, 90f, 90f, false)
            pathBL.lineTo(cl - offset, cb - r - arm)

            pathBR.reset()
            pathBR.moveTo(cr + offset, cb - r - arm)
            pathBR.lineTo(cr + offset, cb + offset - r)
            pathBR.arcTo(cr + offset - 2 * r, cb + offset - 2 * r, cr + offset, cb + offset, 0f, 90f, false)
            pathBR.lineTo(cr - r - arm, cb + offset)

            canvas.drawPath(pathTL, glowPaint); canvas.drawPath(pathTL, paint)
            canvas.drawPath(pathTR, glowPaint); canvas.drawPath(pathTR, paint)
            canvas.drawPath(pathBL, glowPaint); canvas.drawPath(pathBL, paint)
            canvas.drawPath(pathBR, glowPaint); canvas.drawPath(pathBR, paint)

            val beadRadius = 4.2f * density
            val coreRadius = 1.8f * density
            canvas.drawCircle(cl, ct, beadRadius, beadPaint)
            canvas.drawCircle(cl, ct, coreRadius, beadCorePaint)
            canvas.drawCircle(cr, ct, beadRadius, beadPaint)
            canvas.drawCircle(cr, ct, coreRadius, beadCorePaint)
            canvas.drawCircle(cr, cb, beadRadius, beadPaint)
            canvas.drawCircle(cr, cb, coreRadius, beadCorePaint)
            canvas.drawCircle(cl, cb, beadRadius, beadPaint)
            canvas.drawCircle(cl, cb, coreRadius, beadCorePaint)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (alpha < 0.05f && activeCorner == null) {
                onShowControls()
                return false
            }

            val cl = 0f
            val ct = 0f
            val cr = (card.layoutParams?.width?.takeIf { it > 0 } ?: width).toFloat()
            val cb = (card.layoutParams?.height?.takeIf { it > 0 } ?: height).toFloat()

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = e.x
                    val y = e.y

                    val inDockBtn = (x in cl..(cl + 48f * density)) && (y in (cb - 38f * density)..cb)
                    val inResetBtn = (x in (cr - 90f * density)..cr) && (y in (cb - 38f * density)..cb)
                    if (inDockBtn || inResetBtn) return false

                    val reachOut = 52f * density
                    val reachIn = 52f * density
                    val inLeft = x in (cl - reachOut)..(cl + reachIn)
                    val inRight = x in (cr - reachIn)..(cr + reachOut)
                    val inTop = y in (ct - reachOut)..(ct + reachIn)
                    val inBottom = y in (cb - reachIn)..(cb + reachOut)

                    val corner = when {
                        inTop && inLeft -> FloatCorner.TOP_LEFT
                        inTop && inRight -> FloatCorner.TOP_RIGHT
                        inBottom && inLeft -> FloatCorner.BOTTOM_LEFT
                        inBottom && inRight -> FloatCorner.BOTTOM_RIGHT
                        else -> null
                    } ?: return false

                    activeCorner = corner
                    downRawX = e.rawX
                    downRawY = e.rawY
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onDrag(corner, 0f, 0f, MotionEvent.ACTION_DOWN)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val ac = activeCorner ?: return false
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    onDrag(ac, dx, dy, MotionEvent.ACTION_MOVE)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val ac = activeCorner ?: return false
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    onDrag(ac, dx, dy, e.actionMasked)
                    activeCorner = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(e)
        }
    }

    private fun buildCornerResizeOverlay(container: FloatingContainer, card: LinearLayout, outerPad: Float): View {
        var startW = 0
        var startH = 100
        var startX = 0
        var startY = 0

        lateinit var overlayRef: FloatingResizeOverlayView

        val onDragCorner: (FloatCorner, Float, Float, Int) -> Unit = { corner, dx, dy, action ->
            showFloatControlsTemporarily()
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizingCorner = true
                    startW = if (card.width > 0) card.width else floatCardWidth()
                    startH = floatHeightPct
                    startX = floatX
                    startY = floatY
                    window.window?.decorView?.requestLayout()
                }
                MotionEvent.ACTION_MOVE -> {
                    val sW = screenW()
                    val minW = (sW * 0.50f).toInt()
                    val maxW = (sW * 0.98f).toInt()

                    when (corner) {
                        FloatCorner.TOP_RIGHT -> {
                            val newW = (startW + dx.toInt()).coerceIn(minW, (sW - startX).coerceAtMost(maxW))
                            floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100f).toInt().coerceIn(50, 98)
                            card.layoutParams.width = newW

                            val dPct = (-dy / dp(3.5f)).toInt()
                            val newH = (startH + dPct).coerceIn(65, 115)
                            if (newH != floatHeightPct) {
                                floatHeightPct = newH
                                applyHeightLive()
                            }
                        }
                        FloatCorner.TOP_LEFT -> {
                            val rightEdge = startX + startW
                            val minX = (rightEdge - maxW).coerceAtLeast(0)
                            val maxX = rightEdge - minW
                            val newX = (startX + dx.toInt()).coerceIn(minX, maxX)
                            val newW = rightEdge - newX
                            floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100f).toInt().coerceIn(50, 98)
                            floatX = newX
                            card.layoutParams.width = newW

                            val dPct = (-dy / dp(3.5f)).toInt()
                            val newH = (startH + dPct).coerceIn(65, 115)
                            if (newH != floatHeightPct) {
                                floatHeightPct = newH
                                applyHeightLive()
                            }
                        }
                        FloatCorner.BOTTOM_RIGHT -> {
                            val newW = (startW + dx.toInt()).coerceIn(minW, (sW - startX).coerceAtMost(maxW))
                            floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100f).toInt().coerceIn(50, 98)
                            card.layoutParams.width = newW

                            val dPct = (dy / dp(3.5f)).toInt()
                            val newH = (startH + dPct).coerceIn(65, 115)
                            if (newH != floatHeightPct) {
                                floatHeightPct = newH
                                applyHeightLive()
                            }
                        }
                        FloatCorner.BOTTOM_LEFT -> {
                            val rightEdge = startX + startW
                            val minX = (rightEdge - maxW).coerceAtLeast(0)
                            val maxX = rightEdge - minW
                            val newX = (startX + dx.toInt()).coerceIn(minX, maxX)
                            val newW = rightEdge - newX
                            floatWidthPct = ((newW.toFloat() / sW.toFloat()) * 100f).toInt().coerceIn(50, 98)
                            floatX = newX
                            card.layoutParams.width = newW

                            val dPct = (dy / dp(3.5f)).toInt()
                            val newH = (startH + dPct).coerceIn(65, 115)
                            if (newH != floatHeightPct) {
                                floatHeightPct = newH
                                applyHeightLive()
                            }
                        }
                    }

                    clampFloat()
                    applyFloatPosition()
                    card.requestLayout()
                    container.requestLayout()
                    overlayRef.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizingCorner = false
                    savePct(Skins.KEY_FLOAT_WIDTH, floatWidthPct)
                    savePct(Skins.KEY_FLOAT_HEIGHT, floatHeightPct)
                    saveFloatPosition()
                    clampFloat()
                    applyFloatPosition()
                    cachedEmojiPanel = null
                    if (layer == Layer.EMOJI) {
                        renderActiveLayer()
                    } else {
                        renderActiveLayer()
                        refreshSuggestionsNow()
                    }
                    window.window?.decorView?.requestLayout()
                }
            }
        }

        overlayRef = FloatingResizeOverlayView(
            context = this,
            card = card,
            density = density,
            outerPad = outerPad,
            isLight = skin.isLight,
            onShowControls = { showFloatControlsTemporarily() },
            onDrag = onDragCorner
        ).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        return overlayRef
    }

    private fun buildFloatingCard(customBg: Drawable?): LinearLayout {
        val cardW = floatCardWidth()
        val radius = dp(16f).toFloat()

        val container = FloatingContainer(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = if (photoExtended && customBg != null) customBg else GradientDrawable().apply {
                setColor(skin.background)
                cornerRadius = radius
                setStroke(dp(1.2f), skin.chipBorderColor)
            }
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
            elevation = dp(6f).toFloat()
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        card.addView(inner)
        card.addView(buildFloatBottomBar(card))

        container.cardView = card
        container.addView(card)

        val cornerOverlay = buildCornerResizeOverlay(container, card, 0f)
        container.overlayView = cornerOverlay
        container.addView(cornerOverlay)
        floatCornerIndicators = cornerOverlay

        rootLayout.addView(container)
        floatCard = card

        showFloatControlsTemporarily()

        return inner
    }

    private fun savePct(key: String, value: Int) {
        Skins.prefs(this).edit().putInt(key, value).apply()
    }

    private fun applyHeightLive() {
        val s = heightScale()
        when (layer) {
            Layer.LETTERS, Layer.NUMBERS, Layer.SYMBOLS, Layer.NUMPAD -> {
                if (!::keyboardContainer.isInitialized) return
                val sizeScale = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)
                val vGapFactor = (2f - sizeScale).coerceIn(0.15f, 2.2f)
                val effVGap = (skin.keySpacingV * vGapFactor).coerceAtLeast(0.5f)
                val rowH = dp(((KEY_HEIGHT_DP.toFloat() * sizeScale) + (effVGap * 2.0f)) * s)
                for (i in 0 until keyboardContainer.childCount) {
                    val row = keyboardContainer.getChildAt(i)
                    row.layoutParams.height = rowH
                }
                keyboardContainer.requestLayout()
            }
            else -> {
                val panel = panelContainer.getChildAt(0) ?: return
                val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val baseEmojiH = if (isLand) 180f else 264f
                val effBarH = if (isLand) 36f else BAR_H_DP
                panel.layoutParams.height = if (layer == Layer.EMOJI) {
                    dp(baseEmojiH * s + (effBarH * s.coerceIn(0.7f, 1.1f)))
                } else {
                    dp((if (isLand) 160f else 240f) * s)
                }
                panel.requestLayout()
            }
        }
        if (::suggestionStrip.isInitialized) {
            val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val effBarH = if (isLand) 36f else BAR_H_DP
            val hScale = s.coerceIn(0.65f, 1.15f)
            suggestionStrip.layoutParams.height = dp(effBarH * hScale)
            suggestionStrip.requestLayout()
        }
        if (::rootLayout.isInitialized) {
            rootLayout.requestLayout()
        }
    }

    private fun buildFloatBottomBar(card: LinearLayout): View {
        val barH = dp(40f)
        val bar = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(skin.surface, 250))
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(16f).toFloat(), dp(16f).toFloat(),
                    dp(16f).toFloat(), dp(16f).toFloat()
                )
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH)
        }

        val divider = View(this).apply {
            val divCol = if (skin.isLight) ColorUtils.setAlphaComponent(skin.accent, 60) else ColorUtils.setAlphaComponent(skin.accent, 45)
            setBackgroundColor(divCol)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.8f), Gravity.TOP)
        }
        bar.addView(divider)

        val dockBtn = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val btnBg = if (skin.isLight) ColorUtils.setAlphaComponent(skin.accent, 22) else ColorUtils.setAlphaComponent(Color.WHITE, 20)
                val btnStroke = if (skin.isLight) ColorUtils.setAlphaComponent(skin.accent, 50) else ColorUtils.setAlphaComponent(Color.WHITE, 35)
                setColor(btnBg)
                setStroke(dp(0.8f), btnStroke)
            }
            val icon = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)?.mutate()
                val iconTint = if (skin.isLight) skin.textColor else Color.WHITE
                d?.setTint(iconTint)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val sz = dp(28f)
            addView(icon, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
            layoutParams = FrameLayout.LayoutParams(sz, sz, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                feedback(this)
                toggleFloating()
            }
        }
        val dragContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH)
            isClickable = true
            isFocusable = true
        }

        val centerGrip = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_grip_horizontal)?.mutate()
            val gripTint = if (skin.isLight) ColorUtils.setAlphaComponent(skin.textColor, 180) else ColorUtils.setAlphaComponent(Color.WHITE, 180)
            d?.setTint(gripTint)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        dragContainer.addView(centerGrip, FrameLayout.LayoutParams(dp(26f), dp(18f), Gravity.CENTER))

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        dragContainer.setOnTouchListener { _, e ->
            showFloatControlsTemporarily()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    KeyboardService.isMovingFloating = true
                    downX = e.rawX
                    downY = e.rawY
                    startX = floatX
                    startY = floatY
                    dragContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    window.window?.decorView?.requestLayout()
                }
                MotionEvent.ACTION_MOVE -> {
                    floatX = startX + (e.rawX - downX).toInt()
                    floatY = startY - (e.rawY - downY).toInt()
                    clampFloat()
                    applyFloatPosition()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    KeyboardService.isMovingFloating = false
                    dragContainer.parent?.requestDisallowInterceptTouchEvent(false)
                    saveFloatPosition()
                    clampFloat()
                    applyFloatPosition()
                    window.window?.decorView?.requestLayout()
                }
            }
            true
        }
        bar.addView(dragContainer)

        bar.addView(dockBtn)

        val resetBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                val resetBg = if (skin.isLight) ColorUtils.setAlphaComponent(skin.accent, 25) else ColorUtils.setAlphaComponent(Color.WHITE, 22)
                setColor(resetBg)
                setStroke(dp(1f), ColorUtils.setAlphaComponent(skin.accent, 120))
            }
            setPadding(dp(9f), dp(4f), dp(11f), dp(4f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                feedback(this)
                floatWidthPct = 86
                floatHeightPct = 100
                savePct(Skins.KEY_FLOAT_WIDTH, 86)
                savePct(Skins.KEY_FLOAT_HEIGHT, 100)
                val cardW = floatCardWidth()
                card.layoutParams.width = cardW
                card.requestLayout()
                cachedEmojiPanel = null
                applyHeightLive()
                val maxX = (screenW() - cardW).coerceAtLeast(0)
                floatX = maxX / 2
                floatY = dp(24f)
                clampFloat()
                applyFloatPosition()
                saveFloatPosition()
                showFloatControlsTemporarily()
                renderActiveLayer()
                refreshSuggestionsNow()
                window.window?.decorView?.requestLayout()
            }
        }

        val resetIv = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_rotate_ccw)?.mutate()
            val ivTint = if (skin.isLight) skin.textColor else Color.WHITE
            d?.setTint(ivTint)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(dp(13f), dp(13f)).apply {
                marginEnd = dp(5f)
            }
        }
        resetBtn.addView(resetIv)

        val resetTv = TextView(this).apply {
            text = "Reset"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(null, Typeface.BOLD)
            val tvColor = if (skin.isLight) skin.textColor else Color.WHITE
            setTextColor(tvColor)
        }
        resetBtn.addView(resetTv)

        val resetLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply {
            marginEnd = dp(8f)
        }
        bar.addView(resetBtn, resetLp)

        floatResetBtn = resetBtn

        return bar
    }

    private fun toggleFloating() {
        floatingMode = !floatingMode
        cachedEmojiPanel = null
        Skins.prefs(this).edit().putBoolean(Skins.KEY_FLOATING, floatingMode).apply()
        renderFullKeyboard()
        applyHostMode()
    }

    private var resizeOverlay: View? = null

    private fun startOnKeyboardResize() {
        if (resizeOverlay != null) return
        val host = imeHost ?: return
        if (!::rootLayout.isInitialized) return
        toolboxOpen = false

        val initialHeight = resizeManager.heightPercent

        val overlay = resizeManager.createGnosisResizeOverlay(
            targetView = rootLayout,
            accentColor = skin.accent,
            textColor = skin.textColor,
            surfaceColor = skin.surface,
            onHeightChanged = { _ ->
                applyHeightLive()
                imeHost?.requestLayout()
            },
            onDismiss = { saved ->
                host.removeView(resizeOverlay)
                resizeOverlay = null
                if (saved) {
                    resizeManager.save()
                } else {
                    resizeManager.heightPercent = initialHeight
                    applyHeightLive()
                }
                renderActiveLayer()
                refreshSuggestionsNow()
            }
        )
        resizeOverlay = overlay
        host.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    private fun renderActiveLayer() {
        panelContainer.removeAllViews()
        dismissAllPopups()
        updateToolbarState()
        if (::suggestionStrip.isInitialized) {
            val inTyping = (layer == Layer.LETTERS || layer == Layer.NUMBERS || layer == Layer.SYMBOLS || layer == Layer.NUMPAD)
            suggestionStrip.visibility = if (inTyping) View.VISIBLE else View.GONE
            if (inTyping && suggestionStrip.childCount == 0) {
                refreshSuggestionsNow()
            }
        }

        when (layer) {
            Layer.LETTERS, Layer.NUMBERS, Layer.SYMBOLS, Layer.NUMPAD -> {
                keyboardContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    isMotionEventSplittingEnabled = true
                    clipChildren = false
                    clipToPadding = false
                    setPadding(0, dp(4f), 0, dp(6f))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                buildKeyboardKeys(keyboardContainer)
                panelContainer.addView(keyboardContainer)
            }
            Layer.EMOJI -> {
                val emojiView = cachedEmojiPanel ?: buildEmojiPanel().also { cachedEmojiPanel = it }
                (emojiView.parent as? ViewGroup)?.removeView(emojiView)
                panelContainer.addView(emojiView)
            }
            Layer.CLIPBOARD -> {
                val clipView = buildClipboardPanel()
                panelContainer.addView(clipView)
            }
            Layer.EDIT -> {
                val editView = buildEditPanel()
                panelContainer.addView(editView)
            }
        }
    }

    private fun barColor(base: Int): Int =
        if (photoExtended || skin.glassEnabled) Color.TRANSPARENT else base

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun openLayoutSettings() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.TARGET_LANGUAGES)
                putExtra("open_layout_downloader", true)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun buildOmnitypeToolbar(): LinearLayout {
        toolbarMicBtn = null
        toolbarClipBtn = null
        toolbarEditBtn = null
        toolbarFloatBtn = null
        toolbarEmojiBtn = null
        toolbarOneHandedBtn = null

        val fScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(barColor(skin.surface))
            setPadding(dp(6f * fScale).coerceAtLeast(dp(2f)), dp(3f), dp(6f * fScale).coerceAtLeast(dp(2f)), dp(3f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42f * hScale)
            )

            val scroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val btnW = dp(33f * fScale).coerceAtLeast(dp(24f))
            val btnH = dp(32f * hScale).coerceAtLeast(dp(22f))
            val btnPad = dp(6.5f * fScale).coerceAtLeast(dp(3f))
            val btnMargin = dp(3.5f * fScale).coerceAtLeast(dp(2f))

            fun revampedIconBtn(resId: Int, contentDesc: String, active: Boolean = false, onClick: () -> Unit): ImageView {
                return ImageView(this@KeyboardService).apply {
                    val d = ContextCompat.getDrawable(context, resId)?.mutate()
                    d?.setTint(if (active) skin.actionTextColor else skin.textColor)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(btnPad, btnPad, btnPad, btnPad)
                    background = skin.chipDrawable(density, active)
                    layoutParams = LinearLayout.LayoutParams(btnW, btnH).apply {
                        marginEnd = btnMargin
                    }
                    setOnClickListener {
                        feedback(this)
                        onClick()
                    }
                }
            }


            val micBtn = revampedIconBtn(R.drawable.ic_mic, "Voice", active = isListeningVoice) {
                startVoiceInput()
            }
            toolbarMicBtn = micBtn
            row.addView(micBtn)

            val emojiBtn = revampedIconBtn(R.drawable.ic_smile, "Emoji", active = layer == Layer.EMOJI) {
                switchLayer(if (layer == Layer.EMOJI) Layer.LETTERS else Layer.EMOJI)
            }
            toolbarEmojiBtn = emojiBtn
            row.addView(emojiBtn)

            val clipBtn = revampedIconBtn(R.drawable.ic_clipboard, "Clipboard", active = layer == Layer.CLIPBOARD) {
                ClipboardHistory.syncFromSystem(this@KeyboardService)
                switchLayer(if (layer == Layer.CLIPBOARD) Layer.LETTERS else Layer.CLIPBOARD)
            }
            toolbarClipBtn = clipBtn
            row.addView(clipBtn)

            val editBtn = revampedIconBtn(R.drawable.ic_type, "Text Editing", active = layer == Layer.EDIT) {
                switchLayer(if (layer == Layer.EDIT) Layer.LETTERS else Layer.EDIT)
            }
            toolbarEditBtn = editBtn
            row.addView(editBtn)

            val floatBtn = revampedIconBtn(R.drawable.ic_floating, "Floating", active = floatActive()) {
                handler.post { toggleFloating() }
            }
            toolbarFloatBtn = floatBtn
            row.addView(floatBtn)

            val oneHandedBtn = revampedIconBtn(R.drawable.ic_smartphone, "One-Handed", active = oneHandedMode != "off") {
                feedback(this@KeyboardService)
                oneHandedMode = when (oneHandedMode) {
                    "off" -> "right"
                    "right" -> "left"
                    else -> "off"
                }
                Skins.prefs(this@KeyboardService).edit().putString(Skins.KEY_ONE_HANDED, oneHandedMode).apply()
                renderFullKeyboard()
            }
            toolbarOneHandedBtn = oneHandedBtn
            row.addView(oneHandedBtn)

            val resizeBtn = revampedIconBtn(R.drawable.ic_scaling, "Resize") {
                startOnKeyboardResize()
            }
            row.addView(resizeBtn)

            row.addView(revampedIconBtn(R.drawable.ic_settings, "Settings") {
                openSettings()
            })

            val effectiveLang = GnosisLanguageManager.resolveEffectiveLanguage(this@KeyboardService)
            val langLabel = effectiveLang.shortLabel
            val langPill = TextView(context).apply {
                text = langLabel
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (11f * fScale).coerceIn(9f, 11f))
                setTypeface(null, Typeface.BOLD)
                setTextColor(skin.textColor)
                background = skin.chipDrawable(density, false)
                val pillPadH = dp(8f * fScale).coerceAtLeast(dp(4f))
                setPadding(pillPadH, dp(3f), pillPadH, dp(3f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btnH).apply {
                    marginStart = dp(2f)
                    marginEnd = btnMargin
                }
                setOnClickListener {
                    feedback(this)
                    cycleLanguageAndLayout()
                }
                setOnLongClickListener {
                    feedback(this)
                    openLayoutSettings()
                    true
                }
            }
            row.addView(langPill)

            scroll.addView(row)
            addView(scroll)
        }
    }

    private fun buildToolbox(): View {
        toolboxMicBtn = null
        toolboxClipBtn = null
        toolboxEditBtn = null
        toolboxFloatBtn = null

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        for (itemId in ToolbarConfig.ALL_IDS) {
            when (itemId) {
                "mic" -> {
                    val micBtn = createToolbarIconButton(R.drawable.ic_mic, "Voice", active = isListeningVoice) {
                        startVoiceInput()
                    }
                    toolboxMicBtn = micBtn
                    row.addView(micBtn)
                }
                "clip" -> {
                    val clipBtn = createToolbarIconButton(R.drawable.ic_clipboard, "Clipboard", active = layer == Layer.CLIPBOARD) {
                        ClipboardHistory.syncFromSystem(this@KeyboardService)
                        toolboxOpen = true
                        switchLayer(if (layer == Layer.CLIPBOARD) Layer.LETTERS else Layer.CLIPBOARD)
                    }
                    toolboxClipBtn = clipBtn
                    row.addView(clipBtn)
                }
                "edit" -> {
                    val editBtn = createToolbarIconButton(R.drawable.ic_type, "Text Editing", active = layer == Layer.EDIT) {
                        toolboxOpen = true
                        switchLayer(if (layer == Layer.EDIT) Layer.LETTERS else Layer.EDIT)
                    }
                    toolboxEditBtn = editBtn
                    row.addView(editBtn)
                }
                "float" -> {
                    val floatBtn = createToolbarIconButton(R.drawable.ic_floating, "Floating keyboard", active = floatActive()) {
                        handler.post { toggleFloating() }
                    }
                    toolboxFloatBtn = floatBtn
                    row.addView(floatBtn)
                }
                "resize" -> {
                    val resizeBtn = createToolbarIconButton(R.drawable.ic_scaling, "Resize") {
                        startOnKeyboardResize()
                    }
                    row.addView(resizeBtn)
                }
                "settings" -> {
                    row.addView(createToolbarIconButton(R.drawable.ic_settings, "Settings") {
                        openSettings()
                    })
                }
            }
        }

        scroll.addView(row)
        return scroll
    }

    private fun updateButtonHighlight(btn: View?, iconRes: Int, active: Boolean) {
        if (btn == null) return
        val d = ContextCompat.getDrawable(this, iconRes)?.mutate()
        d?.setTint(if (active) skin.actionTextColor else skin.textColor)
        (btn as? ImageView)?.setImageDrawable(d)
        btn.background = skin.chipDrawable(density, active)
    }

    private fun updateToolbarState() {
        val isEmoji = (layer == Layer.EMOJI)
        val isOneHanded = (oneHandedMode != "off")
        val isVoice = isListeningVoice
        val isClip = (layer == Layer.CLIPBOARD)
        val isEdit = (layer == Layer.EDIT)
        val isFloat = floatActive()

        updateButtonHighlight(toolbarEmojiBtn, R.drawable.ic_smile, isEmoji)
        updateButtonHighlight(toolbarOneHandedBtn, R.drawable.ic_smartphone, isOneHanded)
        updateButtonHighlight(toolbarMicBtn, R.drawable.ic_mic, isVoice)
        updateButtonHighlight(toolbarClipBtn, R.drawable.ic_clipboard, isClip)
        updateButtonHighlight(toolbarEditBtn, R.drawable.ic_type, isEdit)
        updateButtonHighlight(toolbarFloatBtn, R.drawable.ic_floating, isFloat)

        updateButtonHighlight(toolboxMicBtn, R.drawable.ic_mic, isVoice)
        updateButtonHighlight(toolboxClipBtn, R.drawable.ic_clipboard, isClip)
        updateButtonHighlight(toolboxEditBtn, R.drawable.ic_type, isEdit)
        updateButtonHighlight(toolboxFloatBtn, R.drawable.ic_floating, isFloat)
    }

    private fun barWidthPx(): Int = when {
        floatActive() -> floatCardWidth()
        oneHandedMode != "off" -> (screenW() * 0.85f).toInt()
        else -> screenW()
    }

    private fun barBtnW(): Int {
        val count = 1 + ToolbarConfig.ALL_IDS.size + quickKeys.size
        val free = barWidthPx() - dp(12f) - dp(BAR_GAP_DP) * count
        val minW = if (floatActive()) dp(22f) else dp(34f)
        val maxW = if (floatActive()) dp(38f) else dp(46f)
        return (free / count).coerceIn(minW, maxW)
    }

    private fun createToolbarIconButton(resId: Int, contentDesc: String, active: Boolean = false, onClick: () -> Unit): View {
        val fScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        return ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, resId)?.mutate()
            d?.setTint(if (active) skin.actionTextColor else skin.textColor)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8f * fScale).coerceAtLeast(dp(3f))
            setPadding(pad, pad, pad, pad)
            background = skin.chipDrawable(density, active)
            layoutParams = LinearLayout.LayoutParams(barBtnW(), dp(BAR_BTN_H_DP * hScale)).apply {
                marginEnd = dp(BAR_GAP_DP * fScale).coerceAtLeast(dp(2f))
            }
            setOnClickListener {
                feedback(this)
                onClick()
            }
        }
    }

    private fun buildSuggestionStrip(): LinearLayout {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val effBarH = if (isLand) 36f else BAR_H_DP
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(barColor(skin.surface))
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(effBarH * hScale)
            )
        }
    }

    private fun getWordBeforeCursor(ic: InputConnection): String {
        val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        if (textBefore.isEmpty() || textBefore.last().isWhitespace()) return ""
        var i = textBefore.length - 1
        while (i >= 0 && !textBefore[i].isWhitespace() && (textBefore[i].isLetter() || textBefore[i] == '\'')) {
            i--
        }
        return textBefore.substring(i + 1)
    }

    private val suggestionRunnable = Runnable {
        try {
            refreshSuggestions()
        } catch (t: Throwable) {
            android.util.Log.e("KeyboardService", "Error in suggestionRunnable", t)
        }
    }

    private fun updateSuggestions() {
        handler.removeCallbacks(suggestionRunnable)
        handler.postDelayed(suggestionRunnable, SUGGESTION_DELAY_MS)
    }

    private fun refreshSuggestionsNow() {
        handler.removeCallbacks(suggestionRunnable)
        try {
            refreshSuggestions()
        } catch (t: Throwable) {
            android.util.Log.e("KeyboardService", "Error in refreshSuggestionsNow", t)
        }
    }

    private fun refreshSuggestions() {
        if (!::suggestionStrip.isInitialized) return
        refreshEnterKeys()
        if (isListeningVoice) return
        suggestionStrip.removeAllViews()
        toolboxMicBtn = null
        toolboxClipBtn = null
        toolboxEditBtn = null
        toolboxFloatBtn = null

        val isRevamped = (layoutStyle == Skins.STYLE_REVAMPED)
        val inTypingLayer = layer == Layer.LETTERS || layer == Layer.NUMBERS || layer == Layer.SYMBOLS || layer == Layer.NUMPAD
        if (!inTypingLayer) {
            suggestionStrip.visibility = View.GONE
            suggestionStrip.removeAllViews()
            return
        }

        val ic = currentInputConnection
        val activeWord = if (currentWord.isNotEmpty()) {
            currentWord.toString()
        } else if (ic != null) {
            val before = getWordBeforeCursor(ic)
            if (before.isNotEmpty()) before else currentWord.toString()
        } else currentWord.toString()

        if (suggestionStrip.visibility != View.VISIBLE) {
            suggestionStrip.visibility = View.VISIBLE
        }

        val inSpecial = layer == Layer.NUMBERS || layer == Layer.SYMBOLS || layer == Layer.NUMPAD
        val showToolbox = if (isRevamped) false else (activeWord.isEmpty() && (inSpecial || editingQuickKey || toolboxOpen))
        val fScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        val btnW = barBtnW()
        val btnH = dp(BAR_BTN_H_DP * hScale)

        if (!isRevamped && !inSpecial && !editingQuickKey && activeWord.isEmpty()) {
            suggestionStrip.addView(createToolbarIconButton(R.drawable.ic_toolbox, "Toolbox", active = toolboxOpen) {
                toolboxOpen = !toolboxOpen
                refreshSuggestionsNow()
            })
        }

        var suggestions: List<String> = emptyList()
        val isPassword = isPasswordField()
        val isUrl = if (ic != null) isUrlOrEmail(ic) else false

        if (showToolbox || ic == null || !suggestionsEnabled || layer == Layer.NUMPAD || activeWord.isEmpty()) {
            suggestions = emptyList()
            currentAutoCorrectCandidate = null
            currentEmojiSuggestions = emptyList()
        } else {
            val contextWords = extractContextWords(ic, activeWord)
            val effectiveLang = GnosisLanguageManager.resolveEffectiveLanguage(this)
            val (suggs, ac, emojis) = try {
                if (suggestionEngine == null) {
                    suggestionEngine = EngineFactory.getSuggestionEngine(this, userDictionary, effectiveLang.code)
                }
                val suggResult = suggestionEngine!!.generateSuggestions(activeWord, contextWords, isPassword, isUrl, language = effectiveLang.code)
                val wordList = suggResult.words.map { it.word }.filter { it.isNotBlank() }
                val finalWords = if (wordList.isNotEmpty()) wordList else (if (activeWord.isNotEmpty()) listOf(activeWord) else emptyList())
                Triple(
                    finalWords,
                    suggResult.autoCorrectCandidate,
                    if (emojiSuggestionsEnabled) suggResult.emojiSuggestions else emptyList()
                )
            } catch (t: Throwable) {
                android.util.Log.e("KeyboardService", "generateSuggestions error", t)
                Triple(if (activeWord.isNotEmpty()) listOf(activeWord) else emptyList(), null, emptyList())
            }
            suggestions = suggs
            currentAutoCorrectCandidate = ac
            currentEmojiSuggestions = emojis
        }

        val suggContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val textEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_TEXT)
        val screenshotEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_SCREENSHOT)
        val acceptsImages = ClipboardHistory.canAcceptImages(currentInputEditorInfo)
        val recentClip = if (ic != null && activeWord.isEmpty() && !showToolbox) {
            ClipboardHistory.getItems(this, null).firstOrNull { item ->
                !ClipboardHistory.isConsumed(this, item) &&
                ((item.isImage && screenshotEnabled && acceptsImages) || (!item.isImage && textEnabled)) &&
                (System.currentTimeMillis() - item.timestamp) < 180_000L
            }
        } else null

        if (recentClip != null) {
            suggestions = emptyList()
            currentEmojiSuggestions = emptyList()
        }

        if (recentClip != null && !showToolbox) {
            val isImage = recentClip.isImage
            val sensitiveMatches = if (!isImage) MessageArmourEngine.scan(recentClip.text) else emptyList()
            val isSensitive = sensitiveMatches.isNotEmpty()
            val clean = if (!isImage) recentClip.text.replace("\n", " ").trim() else ""
            val chipText = if (isSensitive) {
                sensitiveMatches.first().maskedValue
            } else if (clean.length > 16) clean.take(14) + "..." else clean

            val pastePill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(17f * hScale).toFloat()
                    setColor(skin.surface)
                    setStroke(dp(1.2f), if (isSensitive) Color.parseColor("#F59E0B") else skin.accent)
                }
                val p = dp(10f * fScale).toInt().coerceAtLeast(dp(6f))
                setPadding(p, dp(2f), p, dp(2f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f * hScale)).apply {
                    marginEnd = dp(6f * fScale).coerceAtLeast(dp(3f))
                }
                setOnClickListener {
                    feedback(this)
                    if (isImage) {
                        val ok = ClipboardHistory.commitContent(this@KeyboardService, currentInputConnection, currentInputEditorInfo, recentClip)
                        if (!ok) {
                            Toast.makeText(this@KeyboardService, "App does not support image pasting here", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        typeText(recentClip.text)
                    }
                    ClipboardHistory.markConsumed(this@KeyboardService, recentClip)
                    refreshSuggestionsNow()
                }
            }

            if (isImage) {
                val thumb = ClipboardHistory.loadThumbnail(recentClip.imagePath, dp(24f), dp(24f))
                val clipIcon = ImageView(this).apply {
                    if (thumb != null) {
                        setImageBitmap(thumb)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                        }
                        clipToOutline = true
                    } else {
                        val d = ContextCompat.getDrawable(context, R.drawable.ic_image)?.mutate()
                        d?.setTint(if (skin.accentText != 0) skin.accentText else skin.accent)
                        setImageDrawable(d)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(6f) }
                }
                pastePill.addView(clipIcon)

                val clipTv = TextView(this).apply {
                    text = "Paste: Screenshot"
                    setTextColor(if (skin.textColor != 0) skin.textColor else Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (11.5f * fScale).coerceIn(9.5f, 13f))
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                }
                pastePill.addView(clipTv)
            } else {
                val clipIcon = ImageView(this).apply {
                    val d = ContextCompat.getDrawable(context, if (isSensitive) R.drawable.ic_eye_off else R.drawable.ic_clipboard)?.mutate()
                    d?.setTint(if (isSensitive) Color.parseColor("#F59E0B") else skin.accentText)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginEnd = dp(6f) }
                }
                pastePill.addView(clipIcon)

                val clipTv = TextView(this).apply {
                    text = if (isSensitive) "Private: $chipText" else "Paste: \"$chipText\""
                    setTextColor(if (skin.textColor != 0) skin.textColor else Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (11.5f * fScale).coerceIn(9.5f, 13f))
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                }
                pastePill.addView(clipTv)
            }

            val dismissBtn = ImageView(this).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
                d?.setTint(ColorUtils.setAlphaComponent(if (skin.textColor != 0) skin.textColor else Color.WHITE, 140))
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = dp(2f)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
                    marginStart = dp(6f)
                }
                setOnClickListener {
                    ClipboardHistory.markConsumed(this@KeyboardService, recentClip)
                    refreshSuggestionsNow()
                }
            }
            pastePill.addView(dismissBtn)

            suggContainer.addView(pastePill)
        }

        val grammarIssue = if (grammarCheckEnabled && !isPassword && !isUrl && activeWord.isEmpty() && ic != null) {
            val textBefore = ic.getTextBeforeCursor(100, 0)?.toString()
            val issue = gnosis.keyboard.engine.grammar.GnosisGrammarEngine.checkGrammar(textBefore)
            if (issue != null && !dismissedGrammarIssues.contains(issue.originalText)) issue else null
        } else null

        if (grammarIssue != null && !showToolbox && recentClip == null) {
            val proofreadCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(17f * hScale).toFloat()
                    val pillBg = ColorUtils.setAlphaComponent(skin.accent, 45)
                    setColor(pillBg)
                    setStroke(dp(1.2f), skin.accent)
                }
                val p = dp(12f * fScale).toInt().coerceAtLeast(dp(8f))
                setPadding(p, dp(2f), p, dp(2f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34f * hScale)).apply {
                    marginStart = dp(4f * fScale).coerceAtLeast(dp(2f))
                    marginEnd = dp(4f * fScale).coerceAtLeast(dp(2f))
                }
                setOnClickListener {
                    feedback(this)
                    applyGrammarFix(ic, grammarIssue)
                    refreshSuggestionsNow()
                }

                val icon = ImageView(this@KeyboardService).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_wand_sparkles)?.mutate()
                        ?: ContextCompat.getDrawable(context, R.drawable.ic_sparkles)?.mutate()
                    d?.setTint(if (skin.accentText != 0) skin.accentText else skin.accent)
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { marginEnd = dp(8f) }
                }
                addView(icon)

                val fixTv = TextView(this@KeyboardService).apply {
                    val cleanFix = grammarIssue.correctedText.trim()
                    text = "Tap to fix: \"$cleanFix\""
                    setTextColor(if (skin.accentText != 0) skin.accentText else skin.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, (12.5f * fScale).coerceIn(10.5f, 14.5f))
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                }
                addView(fixTv)

                val dismissBtn = ImageView(this@KeyboardService).apply {
                    val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
                    d?.setTint(ColorUtils.setAlphaComponent(if (skin.textColor != 0) skin.textColor else Color.WHITE, 160))
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val pad = dp(3f)
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                        marginStart = dp(6f)
                    }
                    setOnClickListener {
                        dismissedGrammarIssues.add(grammarIssue.originalText)
                        refreshSuggestionsNow()
                    }
                }
                addView(dismissBtn)
            }
            suggContainer.addView(proofreadCard)
        } else if (layer == Layer.NUMPAD && !showToolbox && recentClip == null) {
            val quickSymbols = listOf("(", ")", "$", "₹", "%", "=", ":")
            for (qs in quickSymbols) {
                val qBtn = TextView(this).apply {
                    text = qs
                    gravity = Gravity.CENTER
                    setTextColor(skin.textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(skin.typeface, Typeface.BOLD)
                    background = skin.chipDrawable(density, false)
                    val p = dp(12f * fScale).toInt().coerceAtLeast(dp(6f))
                    setPadding(p, dp(2f), p, dp(2f))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32f * hScale)).apply {
                        marginEnd = dp(6f * fScale).coerceAtLeast(dp(3f))
                    }
                    setOnClickListener {
                        feedback(this)
                        typeText(qs)
                    }
                }
                suggContainer.addView(qBtn)
            }
        } else if (suggestions.isEmpty() && !showToolbox && recentClip == null) {
            val hint = TextView(this).apply {
                text = "Type or glide for suggestions"
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(skin.subtleTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (12f * fScale).coerceIn(10f, 14f))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8f * fScale).toInt().coerceAtLeast(dp(4f)), 0, dp(4f * fScale).toInt().coerceAtLeast(dp(2f)), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            suggContainer.addView(hint)
        } else if (recentClip == null) {
            for (i in suggestions.indices) {
                val word = suggestions[i]
                val isCenter = (i == 1 || suggestions.size == 1)
                val baseSp = if (isCenter) 14f else 12.8f
                val scaledSp = when {
                    word.length >= 12 -> 10.5f
                    word.length >= 9 -> 11.5f
                    word.length >= 7 -> 12.5f
                    else -> baseSp
                }
                val hPad = when {
                    word.length >= 10 -> dp(4f)
                    word.length >= 7 -> dp(6f)
                    else -> dp(8f)
                }
                val weight = when {
                    word.length >= 9 -> 1.45f
                    word.length <= 4 && suggestions.size > 1 -> 0.8f
                    else -> 1.0f
                }
                val suggBtn = TextView(this).apply {
                    text = word
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    val effSp = (scaledSp * fScale).coerceIn(9f, 15f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, effSp)
                    if (isCenter) setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isCenter) skin.accentText else skin.textColor)
                    val effHPad = (hPad * fScale).toInt().coerceAtLeast(dp(2f))
                    setPadding(effHPad, dp(2f), effHPad, dp(2f))
                    background = skin.chipDrawable(density, false)
                    layoutParams = LinearLayout.LayoutParams(0, dp(34f * hScale), weight).apply {
                        marginEnd = dp(4f * fScale).coerceAtLeast(dp(2f))
                    }
                    setOnClickListener {
                        feedback(this)
                        commitSuggestion(word)
                    }
                }
                suggContainer.addView(suggBtn)
            }

            if (currentEmojiSuggestions.isNotEmpty()) {
                for (em in currentEmojiSuggestions.take(1)) {
                    val emBtn = TextView(this).apply {
                        text = em
                        gravity = Gravity.CENTER
                        val emSp = (18f * fScale).coerceIn(14f, 20f)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, emSp)
                        background = skin.chipDrawable(density, false)
                        val p = dp(6f * fScale).toInt().coerceAtLeast(dp(3f))
                        setPadding(p, dp(2f), p, dp(2f))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f * hScale)).apply {
                            marginStart = dp(2f)
                            marginEnd = dp(2f)
                        }
                        setOnClickListener {
                            feedback(this)
                            typeText(em)
                        }
                    }
                    suggContainer.addView(emBtn)

                    val combos = EmojiKitchenEngine.getCombinations(em)
                    if (combos.isNotEmpty()) {
                        val kitchenCombo = combos.first()
                        val kitchenBtn = TextView(this).apply {
                            text = kitchenCombo.result
                            gravity = Gravity.CENTER
                            val emSp = (17f * fScale).coerceIn(13f, 19f)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, emSp)
                            background = skin.chipDrawable(density, false)
                            val p = dp(6f * fScale).toInt().coerceAtLeast(dp(3f))
                            setPadding(p, dp(2f), p, dp(2f))
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f * hScale)).apply {
                                marginStart = dp(2f)
                                marginEnd = dp(2f)
                            }
                            setOnClickListener {
                                feedback(this)
                                typeText(kitchenCombo.result)
                            }
                        }
                        suggContainer.addView(kitchenBtn)
                    }
                }
            }
        }
        suggestionStrip.addView(if (showToolbox) buildToolbox() else suggContainer)

        val punctRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val quickKeysScale = (Skins.prefs(this).getInt(Skins.KEY_QUICK_KEYS_SIZE, Skins.DEFAULT_QUICK_KEYS_SIZE) / 100f).coerceIn(0.6f, 1.6f)
        val qBtnW = (btnW * quickKeysScale).toInt()
        val qBtnH = (btnH * quickKeysScale).toInt()

        quickKeys.forEachIndexed { index, p ->
            val pBtn = TextView(this).apply {
                text = p
                gravity = Gravity.CENTER
                val basePunctSp = if (p.codePointCount(0, p.length) <= 2) 19f else 14f
                val scaledPunctSp = (basePunctSp * fScale * quickKeysScale).coerceIn(9f, 25f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledPunctSp)
                setTypeface(null, Typeface.BOLD)
                setTextColor(skin.textColor)
                background = skin.chipDrawable(density, false)
                minWidth = qBtnW
                val effPad = (dp(6f * fScale) * quickKeysScale).toInt().coerceAtLeast(dp(2f))
                setPadding(effPad, 0, effPad, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, qBtnH).apply {
                    marginStart = dp(BAR_GAP_DP * fScale).coerceAtLeast(dp(2f))
                }
                setOnClickListener {
                    feedback(this)
                    typeText(p)
                }
                setOnLongClickListener {
                    buzz()
                    editQuickKey(index)
                    true
                }
            }
            punctRow.addView(pBtn)
        }
        suggestionStrip.addView(punctRow)
    }

    private fun editQuickKey(index: Int) {
        startActivity(Intent(this, QuickKeyEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(QuickKeyEditActivity.EXTRA_INDEX, index)
        })
    }

    private fun isVoiceStatusText(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("Listening", ignoreCase = true) ||
               t.startsWith("Starting", ignoreCase = true) ||
               t.startsWith("Processing", ignoreCase = true) ||
               t.startsWith("Transcribing", ignoreCase = true) ||
               t.startsWith("Loading", ignoreCase = true) ||
               t.startsWith("Say something", ignoreCase = true) ||
               t.startsWith("Voice service", ignoreCase = true) ||
               t.startsWith("No offline", ignoreCase = true) ||
               t.startsWith("Failed to load", ignoreCase = true) ||
               t == "..."
    }

    private fun showVoiceListeningUI(
        text: String,
        isError: Boolean = false,
        actionText: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        if (!::suggestionStrip.isInitialized) return
        if (suggestionStrip.visibility != View.VISIBLE) {
            suggestionStrip.visibility = View.VISIBLE
        }
        val isSpoken = !isError && !isVoiceStatusText(text) && text.isNotBlank()

        val existingTv = suggestionStrip.findViewWithTag<TextView>("voice_status_tv")
        if (existingTv != null && !isError && actionText == null) {
            existingTv.text = text
            existingTv.setTextColor(if (isSpoken) skin.textColor else skin.subtleTextColor)
            existingTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isSpoken) 14.5f else 13f)
            existingTv.setTypeface(null, if (isSpoken) Typeface.BOLD else Typeface.ITALIC)
            (existingTv.parent as? HorizontalScrollView)?.let { hsv ->
                hsv.post { hsv.fullScroll(View.FOCUS_RIGHT) }
            }
            return
        }
        suggestionStrip.removeAllViews()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val micIcon = ImageView(this).apply {
            tag = "voice_mic_icon"
            val d = ContextCompat.getDrawable(context, R.drawable.ic_mic)?.mutate()
            d?.setTint(if (isError) Color.parseColor("#EF4444") else skin.accent)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                marginEnd = dp(8f)
            }
        }
        row.addView(micIcon)

        val scrollContainer = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(6f)
            }
        }

        val statusTv = TextView(this).apply {
            tag = "voice_status_tv"
            this.text = text
            setTextColor(if (isError) Color.parseColor("#EF4444") else if (isSpoken) skin.textColor else skin.subtleTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isSpoken) 14.5f else 13f)
            setTypeface(null, if (isSpoken) Typeface.BOLD else Typeface.ITALIC)
            maxLines = 1
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scrollContainer.addView(statusTv)
        row.addView(scrollContainer)

        if (actionText != null && onAction != null) {
            val actionBtn = TextView(this).apply {
                this.text = actionText
                setTextColor(skin.actionTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14f).toFloat()
                    setColor(skin.accent)
                }
                setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                setOnClickListener { onAction() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(6f)
                }
            }
            row.addView(actionBtn)
        }

        val stopBtn = TextView(this).apply {
            this.text = if (isError) "Close" else "Done"
            setTextColor(skin.subtleTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = skin.chipDrawable(density, false)
            setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            setOnClickListener {
                stopVoiceRecognition()
            }
        }
        row.addView(stopBtn)

        suggestionStrip.addView(row)
        scrollContainer.post { scrollContainer.fullScroll(View.FOCUS_RIGHT) }
    }

    private val dismissedGrammarIssues = HashSet<String>()

    private fun applyGrammarFix(ic: InputConnection, issue: gnosis.keyboard.engine.grammar.GnosisGrammarEngine.GrammarIssue) {
        val before = ic.getTextBeforeCursor(120, 0)?.toString() ?: return
        val orig = issue.originalText
        var idx = before.lastIndexOf(orig)
        var matchedLen = orig.length
        if (idx < 0) {
            val trimmedOrig = orig.trim()
            idx = before.lastIndexOf(trimmedOrig)
            matchedLen = trimmedOrig.length
        }
        if (idx >= 0) {
            val charsToDelete = before.length - idx
            val trailing = before.substring(idx + matchedLen)
            ic.deleteSurroundingText(charsToDelete, 0)
            ic.commitText(issue.correctedText + trailing, 1)
        }
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val active = currentWord.toString()
        if (word.contains(" ")) {
            val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
            val context = extractContextWords(ic, active)
            val phraseToDelete = if (context.isNotEmpty()) {
                val prevWord = context.last()
                "$prevWord $active"
            } else active

            if (before.endsWith(phraseToDelete)) {
                ic.deleteSurroundingText(phraseToDelete.length, 0)
            } else if (active.isNotEmpty()) {
                ic.deleteSurroundingText(active.length, 0)
            }
        } else {
            if (active.isNotEmpty()) {
                ic.deleteSurroundingText(active.length, 0)
            }
        }
        if (active.isNotEmpty() && word.equals(active, ignoreCase = true) && currentAutoCorrectCandidate != null && !word.equals(currentAutoCorrectCandidate?.word, ignoreCase = true)) {
            autocorrectEngine.suppressWord(active)
            userDictionary.addWord(active)
        }
        val prefs = Skins.prefs(this)
        if (prefs.getBoolean(TypingStatisticsRepository.PREF_STATS_ENABLED, true) && !isPasswordField()) {
            statsRepository.recordSuggestionAccepted(word)
        }
        ic.commitText("$word ", 1)
        try {
            if (!previewMode) {
                getTypingEngine()?.onWordCommitted(word, extractContextWords(ic, ""))
                WordDictionary.learn(this, word)
                gnosis.keyboard.engine.vector.GnosisNeuralVectorEngine.recordUserSelection(extractContextWords(ic, ""), word)
            }
            if (lastCommittedWord.isNotEmpty() && !previewMode) {
                WordDictionary.learnTransition(this, lastCommittedWord, word)
            }
        } catch (e: Throwable) {
            android.util.Log.e("KeyboardService", "commitSuggestion learning error", e)
        }
        lastCommittedWord = word
        isNewInputSession = false
        currentWord.clear()
        currentAutoCorrectCandidate = null
        updateSuggestions()
        updateAutoCaps()
    }

    private fun buildKeyboardKeys(container: LinearLayout) {
        letterViews.clear()
        enterKeys.clear()

        if (numberRowEnabled && layer == Layer.LETTERS) {
            val numRow = newRow()
            for (ch in NUMBER_ROW) {
                numRow.addView(symbolOrLetterKey(ch, 1f))
            }
            container.addView(numRow)
        }

        when (layer) {
            Layer.LETTERS -> buildLettersLayer(container)
            Layer.NUMBERS -> buildNumbersLayer(container)
            Layer.SYMBOLS -> buildSymbolsLayer(container)
            Layer.NUMPAD -> buildNumpadLayer(container)
            else -> {}
        }
    }

    private fun flankTouchSpacer(targetKeyView: View, weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            isClickable = true
            isFocusable = false
            setOnTouchListener { _, event ->
                targetKeyView.dispatchTouchEvent(event)
            }
        }
    }

    private fun splitSpacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f)
    }

    private fun buildLettersLayer(container: LinearLayout) {
        val layoutModel = LayoutExtensionManager.getLayout(this, currentLayoutName)
        val rows = layoutModel.rows.ifEmpty { LAYOUTS["qwerty"]!! }
        val isNonLatin = layoutModel.isNonLatin || currentLayoutName == "deva"
        val maxRowLen = rows.maxOfOrNull { it.length } ?: 10

        for (rIdx in 0 until rows.size - 1) {
            val r = newRow()
            val rowStr = rows[rIdx]
            val rMid = rowStr.length / 2
            val diff = maxRowLen - rowStr.length
            val flankWeight = if (diff > 0 && !landscapeSplit) diff / 2f else 0f
            if (flankWeight > 0f) {
                val keys = rowStr.map { letterKey(it, 1f) }
                r.addView(flankTouchSpacer(keys.first(), flankWeight))
                for (k in keys) r.addView(k)
                r.addView(flankTouchSpacer(keys.last(), flankWeight))
            } else {
                for ((idx, c) in rowStr.withIndex()) {
                    if (landscapeSplit && idx == rMid) {
                        r.addView(splitSpacer())
                    }
                    r.addView(letterKey(c, 1f))
                }
            }
            container.addView(r)
        }

        val lastRowStr = rows.last()
        val rLast = newRow()
        val lastDiff = maxRowLen - lastRowStr.length
        val actionKeyWeight = if (lastDiff >= 2) (lastDiff / 2f).coerceIn(1.0f, 2.0f) else 1.2f
        val letterWeight = if (lastDiff >= 2) 1.0f else ((maxRowLen - (actionKeyWeight * 2f)) / lastRowStr.length).coerceAtLeast(0.70f)

        shiftView = actionIconKey(R.drawable.ic_arrow_up, actionKeyWeight, keyId = "SHIFT") {
            handleShiftPress()
        }
        updateShiftView()
        rLast.addView(shiftView)

        val lastMid = lastRowStr.length / 2
        for ((idx, c) in lastRowStr.withIndex()) {
            if (landscapeSplit && idx == lastMid) {
                rLast.addView(splitSpacer())
            }
            rLast.addView(letterKey(c, letterWeight))
        }

        val backspace = buildBackspaceKey(actionKeyWeight)
        rLast.addView(backspace)
        container.addView(rLast)

        if (layoutStyle == Skins.STYLE_REVAMPED) {
            val r3 = newRow()
            val scale = (maxRowLen / 10f).coerceIn(0.85f, 1.30f)
            val switchKey = key("?123", 1.8f * scale, special = true, onLongClick = {
                switchLayer(Layer.NUMPAD)
            }) {
                switchLayer(Layer.NUMBERS)
            }
            r3.addView(switchKey)
            val space = spaceKey(6.4f * scale)
            r3.addView(space)
            val enter = buildEnterKey(1.8f * scale)
            r3.addView(enter)
            container.addView(r3)
        } else {
            buildBottomRow(container, "?123", Layer.NUMBERS)
        }
    }

    private fun buildNumbersLayer(container: LinearLayout) {
        val r0 = newRow()
        for (ch in NUMBER_ROWS[0]) {
            r0.addView(symbolOrLetterKey(ch, 1f))
        }
        container.addView(r0)

        val r1 = newRow()
        for (ch in NUMBER_ROWS[1]) {
            r1.addView(symbolOrLetterKey(ch, 1f))
        }
        container.addView(r1)

        val r2 = newRow()
        r2.addView(key("=\\<", 1.5f, special = true) {
            switchLayer(Layer.SYMBOLS)
        })
        for (ch in NUMBER_ROWS[2]) {
            r2.addView(symbolOrLetterKey(ch, 1f))
        }
        r2.addView(buildBackspaceKey(1.5f))
        container.addView(r2)

        val r3 = newRow()
        r3.addView(key("ABC", 1.4f, special = true) {
            switchLayer(Layer.LETTERS)
        })
        r3.addView(key("12\n34", 1.2f, special = true, keyId = "NUMPAD") {
            switchLayer(Layer.NUMPAD)
        })
        r3.addView(symbolOrLetterKey(',', 1.0f))
        r3.addView(spaceKey(3.9f))
        r3.addView(symbolOrLetterKey('.', 1.0f))
        r3.addView(buildEnterKey(1.5f))
        container.addView(r3)
    }

    private fun buildSymbolsLayer(container: LinearLayout) {
        val r0 = newRow()
        for (ch in SYMBOL_ROWS[0]) {
            r0.addView(symbolOrLetterKey(ch, 1f))
        }
        container.addView(r0)

        val r1 = newRow()
        for (ch in SYMBOL_ROWS[1]) {
            r1.addView(symbolOrLetterKey(ch, 1f))
        }
        container.addView(r1)

        val r2 = newRow()
        r2.addView(key("?123", 1.5f, special = true) {
            switchLayer(Layer.NUMBERS)
        })
        for (ch in SYMBOL_ROWS[2]) {
            r2.addView(symbolOrLetterKey(ch, 1f))
        }
        r2.addView(buildBackspaceKey(1.5f))
        container.addView(r2)

        val r3 = newRow()
        r3.addView(key("ABC", 1.4f, special = true) {
            switchLayer(Layer.LETTERS)
        })
        r3.addView(key("12\n34", 1.2f, special = true, keyId = "NUMPAD") {
            switchLayer(Layer.NUMPAD)
        })
        r3.addView(symbolOrLetterKey('<', 1.0f))
        r3.addView(spaceKey(3.9f))
        r3.addView(symbolOrLetterKey('>', 1.0f))
        r3.addView(buildEnterKey(1.5f))
        container.addView(r3)
    }

    private fun buildNumpadLayer(container: LinearLayout) {
        val isPhone = isPhoneField()

        val r0 = newRow()
        r0.addView(symbolOrLetterKey('+', 1.6f))
        r0.addView(numpadDigitKey('1', 2.1f))
        r0.addView(numpadDigitKey('2', 2.1f))
        r0.addView(numpadDigitKey('3', 2.1f))
        r0.addView(buildBackspaceKey(2.1f))
        container.addView(r0)

        val r1 = newRow()
        r1.addView(symbolOrLetterKey('-', 1.6f))
        r1.addView(numpadDigitKey('4', 2.1f))
        r1.addView(numpadDigitKey('5', 2.1f))
        r1.addView(numpadDigitKey('6', 2.1f))
        r1.addView(symbolOrLetterKey('/', 2.1f))
        container.addView(r1)

        val r2 = newRow()
        r2.addView(key("?123", 1.6f, special = true) {
            switchLayer(Layer.NUMBERS)
        })
        r2.addView(numpadDigitKey('7', 2.1f))
        r2.addView(numpadDigitKey('8', 2.1f))
        r2.addView(numpadDigitKey('9', 2.1f))
        r2.addView(spaceKey(2.1f))
        container.addView(r2)

        val r3 = newRow()
        r3.addView(key("ABC", 1.6f, special = true) {
            switchLayer(Layer.LETTERS)
        })
        r3.addView(symbolOrLetterKey(if (isPhone) '*' else ',', 2.1f))
        r3.addView(numpadDigitKey('0', 2.1f))
        r3.addView(symbolOrLetterKey(if (isPhone) '#' else '.', 2.1f))
        r3.addView(buildEnterKey(2.1f))
        container.addView(r3)
    }

    private fun numpadDigitKey(digit: Char, weight: Float): TextView {
        val displayed = digit.toString()
        val tv = TextView(this).apply {
            text = displayed
            tag = digit
            gravity = Gravity.CENTER
            val baseSp = 24f * skin.fontScale
            setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * keyScaleFactor())
            setTypeface(skin.typeface, Typeface.BOLD)
            setTextColor(skin.textColorFor(displayed, false))
            background = keyInset(skin.keyBackgroundFor(displayed, density, false))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                    val wDp = w / density
                    val hDp = h / density
                    val factor = minOf((wDp / 48f).coerceIn(0.55f, 1.35f), (hDp / 50f).coerceIn(0.55f, 1.35f))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * factor * skin.fontScale)
                }
            }
        }
        setupLetterKeyTouch(tv, digit)
        return tv
    }

    private fun symbolOrLetterKey(char: Char, weight: Float): TextView {
        val displayed = char.toString()
        val tv = TextView(this).apply {
            text = displayed
            tag = char
            gravity = Gravity.CENTER
            val baseSp = 20f * skin.fontScale
            setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * keyScaleFactor())
            setTypeface(skin.typeface, if (skin.fontBold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(skin.textColorFor(displayed, false))
            background = keyInset(skin.keyBackgroundFor(displayed, density, false))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                    val wDp = w / density
                    val hDp = h / density
                    val factor = minOf((wDp / 36f).coerceIn(0.48f, 1.25f), (hDp / 50f).coerceIn(0.52f, 1.25f))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * factor * skin.fontScale)
                }
            }
        }
        setupLetterKeyTouch(tv, char)
        return tv
    }

    private fun buildBottomRow(container: LinearLayout, switchLabel: String, switchTo: Layer) {
        val r = newRow()
        r.addView(key(switchLabel, 1.4f, special = true, onLongClick = {
            switchLayer(Layer.NUMPAD)
        }) {
            switchLayer(switchTo)
        })
        r.addView(symbolOrLetterKey(',', 0.9f))

        val effectiveLang = GnosisLanguageManager.resolveEffectiveLanguage(this@KeyboardService)
        val currentLangLabel = effectiveLang.shortLabel

        r.addView(key(
            label = currentLangLabel,
            weight = 0.9f,
            special = true,
            keyId = "LANG",
            onLongClick = {
                buzz()
                openLayoutSettings()
            }
        ) {
            feedback(this)
            cycleLanguageAndLayout()
        })

        r.addView(spaceKey(3.8f))
        r.addView(symbolOrLetterKey('.', 0.9f))
        r.addView(buildEnterKey(1.5f))
        container.addView(r)
    }

    private fun keyInset(d: Drawable): Drawable {
        val kScale = if (floatActive()) floatWidthScale().coerceIn(0.55f, 1f) else 1f
        val vScale = heightScale()
        val sFactor = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)
        val wFactor = (skin.keyTileWidth / 100f).coerceIn(0.70f, 1.30f) * sFactor
        val hGapFactor = (2f - wFactor).coerceIn(0.15f, 2.2f)
        val spH = dp((skin.keySpacingH * hGapFactor * kScale).coerceAtLeast(0.5f))

        val vGapFactor = (2f - sFactor).coerceIn(0.15f, 2.2f)
        val spV = dp((skin.keySpacingV * vGapFactor * vScale).coerceAtLeast(0.5f))
        return InsetDrawable(d, spH, spV, spH, spV)
    }

    private fun newRow(): LinearLayout {
        val kScale = if (floatActive()) floatWidthScale().coerceIn(0.55f, 1f) else 1f
        val sidePad = dp(skin.sidePaddingDp * kScale)
        val sizeScale = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)
        val vGapFactor = (2f - sizeScale).coerceIn(0.15f, 2.2f)
        val effVGap = (skin.keySpacingV * vGapFactor).coerceAtLeast(0.5f)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isMotionEventSplittingEnabled = true
            clipChildren = false
            clipToPadding = false
            setPadding(sidePad, 0, sidePad, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(((KEY_HEIGHT_DP.toFloat() * sizeScale) + (effVGap * 2.0f)) * heightScale())
            )
        }
    }

    private fun letterKey(char: Char, weight: Float): View {
        val layoutModel = LayoutExtensionManager.getLayout(this, currentLayoutName)
        val isDeva = layoutModel.isNonLatin || currentLayoutName == "deva"
        val displayed = if (isDeva) char.toString() else cased(char).toString()
        val secChar = layoutModel.secondaries[char.lowercaseChar()] ?: SecondaryKeyMapping.getSecondaryChar(char, currentLayoutName)
        val keyId = char.lowercaseChar().toString()
        val sizeScale = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)

        val lk = LetterKeyView(
            context = this,
            char = char,
            secondaryChar = secChar,
            density = density,
            skin = skin,
            keyHeightDp = KEY_HEIGHT_DP.toFloat() * sizeScale * heightScale(),
            isDeva = isDeva,
            showSecondary = hintsActive(),
            widthScale = floatWidthScale()
        ).apply {
            updatePrimaryText(displayed)
            background = keyInset(skin.keyBackgroundFor(keyId, density, false))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        }
        setupLetterKeyTouch(lk, char)
        letterViews.add(lk)
        return lk
    }

    private fun setupLetterKeyTouch(v: View, ch: Char) {
        val swipePoints = mutableListOf<PointF>()
        var startX = 0f
        var startY = 0f
        var isSwiping = false
        var isLongPressed = false
        var downTime = 0L
        var myLongPress: Runnable? = null
        var committed = false

        fun cancelMyLongPress() {
            myLongPress?.let {
                handler.removeCallbacks(it)
                activeLongPresses.remove(it)
            }
            myLongPress = null
        }

        val commitKeyAction: () -> Unit = {
            if (!committed) {
                committed = true
                cancelMyLongPress()
                dismissPreview()
                v.isPressed = false
                val isL = ch.isLetter()
                typeText(if (isL) cased(ch).toString() else ch.toString(), isLetter = isL)
            }
        }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    flushPendingRollover()
                    committed = false
                    pendingRolloverCommit = commitKeyAction
                    cancelMyLongPress()
                    v.isPressed = true
                    feedback(v)
                    isLongPressed = false
                    isSwiping = false
                    downTime = e.eventTime
                    startX = e.rawX
                    startY = e.rawY
                    swipePoints.clear()

                    val rootPos = IntArray(2)
                    rootLayout.getLocationOnScreen(rootPos)
                    swipePoints.add(PointF(e.rawX - rootPos[0], e.rawY - rootPos[1]))

                    if (keyPopupEnabled) {
                        showCharacterPreview(v, if (ch.isLetter()) cased(ch).toString() else ch.toString())
                    }

                    val alts = getAltCandidates(ch)
                    if (alts.isNotEmpty()) {
                        val runnable = object : Runnable {
                            override fun run() {
                                if (pendingRolloverCommit === commitKeyAction) {
                                    pendingRolloverCommit = null
                                }
                                activeLongPresses.remove(this)
                                myLongPress = null
                                isLongPressed = true
                                dismissPreview()
                                showAltPopup(v, alts)
                                buzz()
                            }
                        }
                        myLongPress = runnable
                        activeLongPresses.add(runnable)
                        handler.postDelayed(runnable, longPressMs)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isLongPressed) {
                        updateAltSelection(e.rawX)
                        return@setOnTouchListener true
                    }

                    val dist = hypot(e.rawX - startX, e.rawY - startY)
                    val outside = dist > dp(24f)

                    if (!isSwiping && glideEnabled && layer == Layer.LETTERS) {
                        val rootPos = IntArray(2)
                        rootLayout.getLocationOnScreen(rootPos)
                        swipePoints.add(PointF(e.rawX - rootPos[0], e.rawY - rootPos[1]))
                    }

                    if (!isSwiping && outside && glideEnabled && layer == Layer.LETTERS &&
                        e.eventTime - downTime > GLIDE_MIN_MS &&
                        dist > dp(SWIPE_START_DP.toFloat())
                    ) {
                        if (pendingRolloverCommit === commitKeyAction) {
                            pendingRolloverCommit = null
                        }
                        isSwiping = true
                        v.isPressed = false
                        cancelMyLongPress()
                        dismissPreview()
                        rootLayout.clearTrail()
                    }

                    if (isSwiping) {
                        val rootPos = IntArray(2)
                        rootLayout.getLocationOnScreen(rootPos)
                        val px = e.rawX - rootPos[0]
                        val py = e.rawY - rootPos[1]
                        swipePoints.add(PointF(px, py))
                        rootLayout.addTrailPoint(px, py)
                    } else if (outside) {
                        if (pendingRolloverCommit === commitKeyAction) {
                            pendingRolloverCommit = null
                        }
                        cancelMyLongPress()
                        dismissPreview()
                        v.isPressed = false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (pendingRolloverCommit === commitKeyAction) {
                        pendingRolloverCommit = null
                    }
                    cancelMyLongPress()
                    val heldMs = e.eventTime - downTime
                    val realLongPress = isLongPressed && heldMs >= longPressMs
                    val assignedSec = if (secondaryHintsEnabled) {
                        LayoutExtensionManager.getLayout(this@KeyboardService, currentLayoutName).secondaries[ch.lowercaseChar()]
                            ?: SecondaryKeyMapping.getSecondaryChar(ch, currentLayoutName)
                    } else null
                    val pickedAlt = if (realLongPress) (altState?.let { it.items[it.selected] } ?: assignedSec) else null
                    hidePreviewSoon()
                    if (isLongPressed) dismissAltPopup()
                    v.isPressed = false

                    val isL = ch.isLetter()
                    if (pickedAlt != null) {
                        committed = true
                        typeText(pickedAlt, isLetter = if (pickedAlt.length == 1) pickedAlt[0].isLetter() else false)
                    } else if (isSwiping && isL && layer == Layer.LETTERS) {
                        committed = true
                        rootLayout.clearTrail()
                        isSwiping = false
                        val isQuickFlick = e.eventTime - downTime < GLIDE_MIN_MS + 40L ||
                            pathLength(swipePoints) < dp(GLIDE_MIN_PATH_DP.toFloat())
                        if (isQuickFlick || !processSwipeGesture(swipePoints)) {
                            typeText(cased(ch).toString(), isLetter = true)
                        }
                        swipePoints.clear()
                    } else if (!committed) {
                        commitKeyAction()
                    }

                    isLongPressed = false
                    isSwiping = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (pendingRolloverCommit === commitKeyAction) {
                        pendingRolloverCommit = null
                    }
                    committed = true
                    cancelMyLongPress()
                    dismissPreview()
                    if (isLongPressed) dismissAltPopup()
                    rootLayout.clearTrail()
                    v.isPressed = false
                    isLongPressed = false
                    isSwiping = false
                    swipePoints.clear()
                }
            }
            true
        }
    }

    private fun key(
        label: String,
        weight: Float,
        special: Boolean = false,
        keyId: String = label,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ): TextView {
        val isPunct = label in listOf(".", ",", "?", "!", "'")
        val baseSz = when {
            isPunct -> 22f
            label.contains('\n') -> 11f
            label.length > 2 -> 13.5f
            else -> 19f
        }
        var longPressRunnable: Runnable? = null
        var isLongPressed = false

        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            if (label.contains('\n')) {
                includeFontPadding = false
                setLineSpacing(0f, 0.78f)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSz * skin.fontScale * keyScaleFactor())
            setTypeface(skin.typeface, if (special || isPunct || skin.fontBold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(skin.textColorFor(keyId, special))
            background = keyInset(skin.keyBackgroundFor(keyId, density, special))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                    val wDp = w / density
                    val hDp = h / density
                    val targetW = (32f * weight).coerceAtLeast(24f)
                    val factor = minOf((wDp / targetW).coerceIn(0.48f, 1.25f), (hDp / 50f).coerceIn(0.52f, 1.25f))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSz * factor * skin.fontScale)
                }
            }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        flushPendingRollover()
                        v.isPressed = true
                        isLongPressed = false
                        feedback(v)
                        if (keyPopupEnabled && !special && label.length == 1) {
                            showCharacterPreview(v, label)
                        }
                        if (onLongClick != null) {
                            val r = Runnable {
                                isLongPressed = true
                                buzz()
                                onLongClick()
                            }
                            longPressRunnable = r
                            handler.postDelayed(r, longPressMs)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dist = hypot(e.x - v.width / 2f, e.y - v.height / 2f)
                        if (dist > dp(28f)) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressRunnable = null
                            v.isPressed = false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (keyPopupEnabled && !special && label.length == 1) {
                            hidePreviewSoon()
                        }
                        if (wasPressed && !isLongPressed) {
                            onClick()
                        }
                        isLongPressed = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                        v.isPressed = false
                        isLongPressed = false
                        dismissPreview()
                    }
                }
                true
            }
        }
    }

    private fun actionIconKey(
        resId: Int,
        weight: Float,
        repeat: Boolean = false,
        baseIconDp: Float = 21f,
        keyId: String = "ACTION",
        onClick: () -> Unit
    ): View {
        val d = ContextCompat.getDrawable(this, resId)?.mutate()
        d?.setTint(skin.textColorFor(keyId, true))
        val kScale = keyScaleFactor()
        val container = FrameLayout(this).apply {
            background = keyInset(skin.keyBackgroundFor(keyId, density, true))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            isClickable = true
            isFocusable = true
        }

        val initialSz = (dp(baseIconDp * skin.fontScale) * kScale).toInt().coerceIn(dp(10f), dp(26f))
        val iv = ImageView(this).apply {
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(initialSz, initialSz, Gravity.CENTER)
        }
        if (keyId == "SHIFT") {
            shiftIconView = iv
        }
        container.addView(iv)

        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                val wDp = w / density
                val hDp = h / density
                val targetW = (36f * weight).coerceAtLeast(24f)
                val factor = minOf((wDp / targetW).coerceIn(0.40f, 1.25f), (hDp / 50f).coerceIn(0.40f, 1.25f))
                val iconSz = (dp(baseIconDp * skin.fontScale) * factor).toInt().coerceIn(dp(10f), dp(26f))
                val lp = iv.layoutParams as? FrameLayout.LayoutParams
                if (lp != null && (lp.width != iconSz || lp.height != iconSz)) {
                    lp.width = iconSz
                    lp.height = iconSz
                    lp.gravity = Gravity.CENTER
                    iv.layoutParams = lp
                }
            }
        }

        if (repeat) {
            bindKeyRepeat(container, onClick)
        } else {
            container.setOnClickListener {
                flushPendingRollover()
                feedback(container)
                onClick()
            }
        }
        return container
    }

    private fun buildBackspaceKey(weight: Float): View {
        val d = ContextCompat.getDrawable(this, R.drawable.ic_delete)?.mutate()
        d?.setTint(skin.textColorFor("BACKSPACE", true))
        val kScale = keyScaleFactor()
        val container = FrameLayout(this).apply {
            background = keyInset(skin.keyBackgroundFor("BACKSPACE", density, true))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            isClickable = true
            isFocusable = true
        }

        val baseIconDp = 22f
        val initialSz = (dp(baseIconDp * skin.fontScale) * kScale).toInt().coerceIn(dp(10f), dp(26f))
        val opticalOffset = (dp(1.2f) * kScale).toInt()
        val iv = ImageView(this).apply {
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(initialSz, initialSz, Gravity.CENTER).apply {
                rightMargin = opticalOffset
            }
        }
        container.addView(iv)

        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                val wDp = w / density
                val hDp = h / density
                val targetW = (36f * weight).coerceAtLeast(24f)
                val factor = minOf((wDp / targetW).coerceIn(0.40f, 1.25f), (hDp / 50f).coerceIn(0.40f, 1.25f))
                val iconSz = (dp(baseIconDp * skin.fontScale) * factor).toInt().coerceIn(dp(10f), dp(26f))
                val optOffset = (dp(1.2f) * factor).toInt()
                val lp = iv.layoutParams as? FrameLayout.LayoutParams
                if (lp != null && (lp.width != iconSz || lp.height != iconSz || lp.rightMargin != optOffset)) {
                    lp.width = iconSz
                    lp.height = iconSz
                    lp.gravity = Gravity.CENTER
                    lp.rightMargin = optOffset
                    iv.layoutParams = lp
                }
            }
        }

        bindBackspaceTouch(container)
        return container
    }

    private var activeLayoutSwitcherPopup: PopupWindow? = null
    private var activeSwitcherOverlay: View? = null

    private fun showLayoutSwitcherPopup(anchor: View) {
        dismissAllPopups()
        val popupW = (screenW() * 0.90f).toInt().coerceAtMost(dp(360f))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(skin.surface)
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1.2f), skin.chipBorderColor)
            }
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            elevation = dp(14f).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10f))
        }
        val icon = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_keyboard)?.mutate()
            d?.setTint(skin.actionTextColor)
            setImageDrawable(d)
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                marginEnd = dp(8f)
            }
        }
        val title = TextView(this).apply {
            text = "Keyboard Style"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.textColor)
        }
        header.addView(icon)
        header.addView(title)
        root.addView(header)

        val styles = listOf(
            Skins.STYLE_REVAMPED to ("Revamped" to "Modern rounded keys with quick keys row"),
            Skins.STYLE_CLASSICS to ("Classics" to "Traditional standard keyboard layout")
        )
        for ((sId, info) in styles) {
            val (sName, sDesc) = info
            val isCur = (layoutStyle == sId)
            val itemContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = skin.chipDrawable(density, isCur)
                setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(6f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    feedback(this)
                    layoutStyle = sId
                    Skins.prefs(this@KeyboardService).edit().putString(Skins.KEY_LAYOUT_STYLE, sId).apply()
                    dismissAllPopups()
                    renderFullKeyboard()
                }
            }

            val radioBullet = TextView(this).apply {
                text = if (isCur) "●" else "○"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (isCur) skin.actionTextColor else skin.textColor)
                setPadding(0, 0, dp(12f), 0)
            }
            itemContainer.addView(radioBullet)

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleTv = TextView(this).apply {
                text = sName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, if (isCur) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isCur) skin.actionTextColor else skin.textColor)
            }
            val descTv = TextView(this).apply {
                text = sDesc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(ColorUtils.setAlphaComponent(skin.textColor, 180))
            }
            textCol.addView(titleTv)
            textCol.addView(descTv)
            itemContainer.addView(textCol)

            root.addView(itemContainer)
        }

        val host = imeHost
        if (host != null && host.isAttachedToWindow) {
            val scroller = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(root)
            }
            val backdrop = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.argb(175, 8, 12, 20))
                isClickable = true
                isFocusable = false
                setOnClickListener {
                    dismissAllPopups()
                }
            }
            val cardParams = FrameLayout.LayoutParams(
                popupW,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                val pad = dp(8f)
                setMargins(pad, pad, pad, pad)
            }
            scroller.isClickable = true
            backdrop.addView(scroller, cardParams)
            host.addView(backdrop)
            activeSwitcherOverlay = backdrop
            if (floatActive()) {
                window.window?.decorView?.requestLayout()
            }
            return
        }

        val popup = PopupWindow(root, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            isOutsideTouchable = true
            isFocusable = false
            elevation = dp(14f).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        activeLayoutSwitcherPopup = popup
        popup.setOnDismissListener {
            if (activeLayoutSwitcherPopup === popup) activeLayoutSwitcherPopup = null
        }
        val target = window.window?.decorView ?: imeHost ?: anchor
        try {
            popup.showAtLocation(target, Gravity.CENTER, 0, 0)
        } catch (_: Exception) {
            try {
                popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)
            } catch (_: Exception) {
                cycleLayout()
            }
        }
    }

    private fun spaceKey(weight: Float): TextView {
        val isRevamped = layoutStyle == Skins.STYLE_REVAMPED
        val model = LayoutExtensionManager.getLayout(this, currentLayoutName)
        val effectiveLang = GnosisLanguageManager.resolveEffectiveLanguage(this)
        val rawSpace = if (model.id == "qwerty" && effectiveLang.code != "en") {
            effectiveLang.spaceLabel
        } else {
            model.spaceLabel
        }
        val spaceLabel = if (weight < 2.5f) {
            "Space"
        } else if (isRevamped && !model.isNonLatin && rawSpace != "हिन्दी") {
            rawSpace.toCharArray().joinToString(" ")
        } else {
            rawSpace
        }
        val kScale = keyScaleFactor()
        val baseSp = if (weight < 2.5f) 12f else (if (isRevamped) 14f else 14.5f)
        val tv = TextView(this).apply {
            text = spaceLabel
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * skin.fontScale * kScale)
            setTypeface(skin.typeface, Typeface.BOLD)
            if (isRevamped && weight >= 2.5f) {
                letterSpacing = if (floatActive()) 0.03f else 0.08f
            }
            setTextColor(skin.textColorFor("SPACE", false))
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                    val hDp = h / density
                    val factor = (hDp / 50f).coerceIn(0.55f, 1.25f)
                    val sp = baseSp * factor * skin.fontScale
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
                    if (isRevamped) {
                        letterSpacing = if (w < dp(120f) || weight < 2.5f) 0.02f else 0.08f
                    }
                }
            }
            val p = Skins.prefs(this@KeyboardService)
            val isContrast = skin.id.startsWith("contrast")
            val spaceBg = skin.keyBackgroundFor("SPACE", density, false)
            background = keyInset(spaceBg)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        }

        var startX = 0f
        var cursorMoved = false
        var longPressed = false
        var longPressRunnable: Runnable? = null

        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    flushPendingRollover()
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.isPressed = true
                    feedback(v)
                    startX = e.rawX
                    cursorMoved = false
                    longPressed = false
                    longPressRunnable = Runnable {
                        if (!cursorMoved) {
                            longPressed = true
                            v.isPressed = false
                            buzz()
                            showLayoutSwitcherPopup(v)
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    val diff = e.rawX - startX
                    val slop = dp(8f)
                    if (Math.abs(diff) >= slop) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }
                    if (cursorControlEnabled && !longPressed) {
                        val step = dp(12f)
                        if (Math.abs(diff) >= step) {
                            val ic = currentInputConnection
                            if (ic != null) {
                                val steps = (diff / step).toInt()
                                moveCursor(ic, steps)
                                buzz()
                                cursorMoved = true
                                startX += steps * step
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    v.isPressed = false
                    if (!cursorMoved && !longPressed) {
                        handleSpacePress()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    v.isPressed = false
                }
            }
            true
        }
        return tv
    }

    private fun moveCursor(ic: InputConnection, steps: Int) {
        lastSpaceTapTime = 0L
        if (steps == 0) return
        var pos = lastSelStart
        if (pos < 0) {
            try {
                val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extracted != null) {
                    pos = extracted.startOffset + extracted.selectionStart
                }
            } catch (_: Exception) {}
        }
        if (pos >= 0) {
            val newPos = (pos + steps).coerceAtLeast(0)
            lastSelStart = newPos
            ic.setSelection(newPos, newPos)
        } else {
            val key = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            val count = Math.abs(steps)
            for (i in 0 until count) {
                sendDownUpKeyEvents(key)
            }
        }
    }

    private fun buildEnterKey(weight: Float): View {
        val enterTxtCol = skin.textColorFor("ENTER", special = true, isAction = true)
        val kScale = keyScaleFactor()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = keyInset(skin.keyBackgroundFor("ENTER", density, special = true, isAction = true))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            isClickable = true
            isFocusable = true
        }

        val initialIconSz = (dp(19f) * kScale).toInt().coerceIn(dp(12f), dp(22f))
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(initialIconSz, initialIconSz)
        }
        container.addView(icon)

        val titleTv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (9.5f * skin.fontScale * kScale).coerceIn(6.5f, 11f))
            setTypeface(skin.typeface, Typeface.BOLD)
            setTextColor(enterTxtCol)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            }
        }
        container.addView(titleTv)

        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                val wDp = w / density
                val hDp = h / density
                val targetW = (32f * weight).coerceAtLeast(24f)
                val factor = minOf((wDp / targetW).coerceIn(0.45f, 1.25f), (hDp / 50f).coerceIn(0.50f, 1.25f))
                val iconSz = (dp(19f) * factor).toInt().coerceIn(dp(12f), dp(22f))
                icon.layoutParams = LinearLayout.LayoutParams(iconSz, iconSz)
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (9.5f * factor * skin.fontScale).coerceIn(6.5f, 11f))
            }
        }

        val refs = EnterKeyRefs(container, icon, titleTv)
        enterKeys.add(refs)
        applyEnterState(refs)

        container.setOnClickListener {
            flushPendingRollover()
            feedback(container)
            if (refs.close) requestHideSelf(0) else performEnterAction()
        }

        return container
    }

    private fun isFieldEmpty(): Boolean {
        if (editingQuickKey) return false
        val info = currentInputEditorInfo ?: return false
        if (info.inputType == InputType.TYPE_NULL) return false
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(1, 0) ?: return false
        val after = ic.getTextAfterCursor(1, 0) ?: return false
        return before.isEmpty() && after.isEmpty()
    }

    private fun applyEnterState(r: EnterKeyRefs) {
        val enterTxtCol = skin.textColorFor("ENTER", special = true, isAction = true)
        val isRevamped = layoutStyle == Skins.STYLE_REVAMPED
        val fieldEmpty = !floatActive() && isFieldEmpty()
        r.close = fieldEmpty

        val (iconRes, label) = if (fieldEmpty) {
            Pair(R.drawable.ic_chevron_down, if (isRevamped) "CLOSE" else "Close")
        } else when (enterAction()) {
            EditorInfo.IME_ACTION_SEARCH -> Pair(R.drawable.ic_search, if (isRevamped) "SEARCH" else "Search")
            EditorInfo.IME_ACTION_SEND -> Pair(R.drawable.ic_send, if (isRevamped) "SEND" else "Send")
            EditorInfo.IME_ACTION_GO -> Pair(R.drawable.ic_corner_down_left, if (isRevamped) "GO" else "Go")
            EditorInfo.IME_ACTION_DONE -> Pair(R.drawable.ic_check, if (isRevamped) "DONE" else "Done")
            EditorInfo.IME_ACTION_NEXT -> Pair(R.drawable.ic_corner_down_left, if (isRevamped) "NEXT" else "Next")
            else -> Pair(R.drawable.ic_corner_down_left, if (isRevamped) "ENTER" else "Enter")
        }
        val d = ContextCompat.getDrawable(this, iconRes)?.mutate()
        d?.setTint(enterTxtCol)
        r.icon.setImageDrawable(d)
        r.title.setTextColor(enterTxtCol)
        r.title.text = label
    }

    private fun refreshEnterKeys() {
        for (r in enterKeys) applyEnterState(r)
    }

    private fun enterAction(): Int {
        val opts = currentInputEditorInfo?.imeOptions ?: return EditorInfo.IME_ACTION_NONE
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        return opts and EditorInfo.IME_MASK_ACTION
    }

    private fun performEnterAction() {
        val ic = currentInputConnection ?: return
        val action = enterAction()
        val handled = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            ic.performEditorAction(action)
        if (!handled) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    private val previewHideRunnable = Runnable { dismissPreview() }

    private fun hidePreviewSoon() {
        handler.removeCallbacks(previewHideRunnable)
        handler.postDelayed(previewHideRunnable, 110L)
    }

    private fun showCharacterPreview(anchor: View, text: String) {
        if (!keyPopupEnabled) return
        handler.removeCallbacks(previewHideRunnable)

        val host = imeHost
        if (host != null && host.isAttachedToWindow) {
            val bubble = keyPreviewBubble ?: TextView(this).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(null, Typeface.BOLD)
                elevation = dp(12f).toFloat()
                visibility = View.GONE
                host.addView(this, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                keyPreviewBubble = this
            }

            bubble.text = text
            bubble.setTextColor(skin.keyTextColor)
            bubble.background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(skin.keyColor)
                setStroke(dp(1.5f), skin.actionTextColor)
            }
            val padH = dp(14f)
            val padV = dp(6f)
            bubble.setPadding(padH, padV, padH, padV)

            bubble.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val bW = bubble.measuredWidth.coerceAtLeast(dp(46f))
            val bH = bubble.measuredHeight.coerceAtLeast(dp(52f))

            val anchorLoc = IntArray(2)
            anchor.getLocationInWindow(anchorLoc)
            val hostLoc = IntArray(2)
            host.getLocationInWindow(hostLoc)

            val relX = (anchorLoc[0] - hostLoc[0]) + (anchor.width - bW) / 2
            val relY = (anchorLoc[1] - hostLoc[1]) - bH - dp(6f)

            val lp = (bubble.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(bW, bH)
            lp.width = bW
            lp.height = bH
            lp.leftMargin = relX
            lp.topMargin = relY
            lp.gravity = Gravity.TOP or Gravity.START
            bubble.layoutParams = lp

            bubble.bringToFront()
            bubble.visibility = View.VISIBLE
            return
        }

        val reuse = activePreviewPopup?.takeIf { it.isShowing }
        val pill = (reuse?.contentView as? TextView) ?: TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.keyTextColor)
            background = if (skin.glassEnabled) {
                Skins.StudioKeyDrawable(
                    density = density,
                    fillColor = skin.keyColor,
                    shape = Skins.SHAPE_ROUNDED,
                    radiusDp = 16f,
                    strokeWidthDp = 1.2f,
                    strokeColor = skin.keyBorderColor,
                    shadowDp = 3f,
                    isLight = skin.isLight,
                    glassEnabled = true,
                    glassOpacity = 92,
                    glassSpecular = true,
                    accentColor = skin.accent
                )
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(16f).toFloat()
                    setColor(skin.keyColor)
                    if (skin.keyBorderColor != Color.TRANSPARENT) {
                        setStroke(dp(1.2f), skin.keyBorderColor)
                    }
                }
            }
            val padH = dp(14f)
            val padV = dp(6f)
            setPadding(padH, padV, padH, padV)
        }
        pill.text = text

        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val popupW = pill.measuredWidth.coerceAtLeast(dp(44f))
        val popupH = pill.measuredHeight.coerceAtLeast(dp(48f))

        val x = loc[0] + (anchor.width - popupW) / 2
        val y = loc[1] - popupH - dp(8f)

        if (reuse != null) {
            try {
                reuse.update(x, y, popupW, popupH)
                return
            } catch (_: Exception) {
                dismissPreview()
            }
        }

        val popup = PopupWindow(pill, popupW, popupH, false).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            isClippingEnabled = false
            isTouchable = false
            animationStyle = 0
        }

        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            activePreviewPopup = popup
        } catch (_: Exception) {}
    }

    private fun showAltPopup(anchor: View, items: List<String>) {
        dismissAllPopups()
        val cellW = dp(42f)
        val pad = dp(5f)
        val margin = dp(4f)
        val screenW = resources.displayMetrics.widthPixels
        val total = items.size * cellW + 2 * pad

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val centerX = loc[0] + anchor.width / 2

        var left = centerX - pad - cellW / 2
        var flipped = false
        if (left + total > screenW - margin) {
            left = centerX + cellW / 2 + pad - total
            flipped = true
        }
        left = left.coerceIn(margin, (screenW - margin - total).coerceAtLeast(margin))
        val ordered = if (flipped) items.reversed() else items

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad, pad, pad)
            background = if (skin.glassEnabled) {
                Skins.StudioKeyDrawable(
                    density = density,
                    fillColor = skin.surface,
                    shape = Skins.SHAPE_ROUNDED,
                    radiusDp = 24f,
                    strokeWidthDp = 1.2f,
                    strokeColor = skin.chipBorderColor,
                    shadowDp = 3f,
                    isLight = skin.isLight,
                    glassEnabled = true,
                    glassOpacity = 92,
                    glassSpecular = true,
                    accentColor = skin.accent
                )
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(24f).toFloat()
                    setColor(skin.surface)
                    setStroke(dp(1.2f), skin.chipBorderColor)
                }
            }
        }

        val cells = ordered.map { t ->
            TextView(this).apply {
                this.text = t
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(skin.textColor)
                layoutParams = LinearLayout.LayoutParams(cellW, cellW)
            }
        }
        cells.forEach { pill.addView(it) }

        val state = AltState(ordered, cells, left, pad, cellW, if (flipped) ordered.size - 1 else 0)
        altState = state
        paintAlt(state)

        val popup = PopupWindow(
            pill,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            isClippingEnabled = false
            isTouchable = false
            animationStyle = 0
        }

        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val yOff = -(anchor.height + pill.measuredHeight + dp(8f))

        try {
            popup.showAsDropDown(anchor, left - loc[0], yOff)
            activePopup = popup
        } catch (_: Exception) {}
    }

    private fun paintAlt(st: AltState) {
        st.cells.forEachIndexed { i, cell ->
            if (i == st.selected) {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(skin.accent)
                }
                cell.setTextColor(skin.actionTextColor)
            } else {
                cell.background = null
                cell.setTextColor(skin.textColor)
            }
        }
    }

    private fun updateAltSelection(rawX: Float) {
        val st = altState ?: return
        val idx = ((rawX - st.left - st.pad) / st.cellW).toInt().coerceIn(0, st.items.size - 1)
        if (idx != st.selected) {
            st.selected = idx
            paintAlt(st)
            buzz()
        }
    }

    private fun cancelPendingLongPress() {
        for (r in activeLongPresses) handler.removeCallbacks(r)
        activeLongPresses.clear()
    }

    private fun dismissPreview() {
        handler.removeCallbacks(previewHideRunnable)
        keyPreviewBubble?.visibility = View.GONE
        try { activePreviewPopup?.dismiss() } catch (_: Exception) {}
        activePreviewPopup = null
    }

    private fun dismissAltPopup() {
        try { activePopup?.dismiss() } catch (_: Exception) {}
        activePopup = null
        altState = null
    }

    private fun dismissAllPopups() {
        cancelPendingLongPress()
        dismissPreview()
        dismissAltPopup()
        dismissSkinTonePopup()
        try { clipOptionsPopup?.dismiss() } catch (_: Exception) {}
        clipOptionsPopup = null
        activeLayoutSwitcherPopup?.dismiss()
        activeLayoutSwitcherPopup = null
        activeSwitcherOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            activeSwitcherOverlay = null
            if (floatActive()) {
                window.window?.decorView?.requestLayout()
            }
        }
    }

    private fun getAltCandidates(ch: Char): List<String> {
        val lower = ch.lowercaseChar()
        val baseAlts = ALTERNATES[lower] ?: emptyList()
        val model = LayoutExtensionManager.getLayout(this, currentLayoutName)
        val sec = if (secondaryHintsEnabled) (model.secondaries[lower] ?: SecondaryKeyMapping.getSecondaryChar(lower, currentLayoutName)) else null
        return if (sec != null) {
            listOf(sec) + baseAlts.filter { it != sec }
        } else {
            baseAlts
        }
    }

    private fun buildEmojiPanel(): View {
        val fScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val baseEmojiH = if (isLand) 180f else 264f
        val effBarH = if (isLand) 36f else BAR_H_DP
        val bottomBarH = dp((if (isLand) 34f else 40f) * hScale).coerceAtLeast(dp(30f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(skin.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(baseEmojiH * hScale + (effBarH * hScale.coerceIn(0.7f, 1.1f))) + bottomBarH
            )
        }

        val topBarH = dp(effBarH * hScale.coerceIn(0.7f, 1.1f)).coerceAtLeast(dp(28f))
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(skin.surface)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topBarH)
        }

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dp(6f * fScale).coerceAtLeast(dp(2f))
            setPadding(padH, dp(2f), padH, dp(2f))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        fun updateTabStyles(selectedIdx: Int) {
            for (tIdx in 0 until tabRow.childCount) {
                val child = tabRow.getChildAt(tIdx) as? ImageView ?: continue
                val sel = (tIdx == selectedIdx)
                val iconRes = EmojiData.categoryIcons.getOrElse(tIdx) { R.drawable.ic_smile }
                val d = ContextCompat.getDrawable(this, iconRes)?.mutate()
                d?.setTint(if (sel) skin.actionTextColor else skin.textColor)
                child.setImageDrawable(d)
                child.background = skin.chipDrawable(density, sel)
            }
        }

        val tabBtnW = dp(36f * fScale).coerceAtLeast(dp(26f))
        val tabBtnH = dp(32f * hScale).coerceAtLeast(dp(24f))
        val tabPad = dp(7f * fScale).coerceAtLeast(dp(4f))
        val tabMargin = dp(5f * fScale).coerceAtLeast(dp(2f))

        for (i in EmojiData.categoryLabels.indices) {
            val label = EmojiData.categoryLabels[i]
            val iconRes = EmojiData.categoryIcons.getOrElse(i) { R.drawable.ic_smile }
            val isSelected = (i == currentEmojiCategoryIndex)
            val tabBtn = ImageView(this).apply {
                contentDescription = label
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(if (isSelected) skin.actionTextColor else skin.textColor)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(tabPad, tabPad, tabPad, tabPad)
                background = skin.chipDrawable(density, isSelected)
                layoutParams = LinearLayout.LayoutParams(tabBtnW, tabBtnH).apply {
                    marginEnd = tabMargin
                }
            }
            tabRow.addView(tabBtn)
        }
        tabScroll.addView(tabRow)
        topBar.addView(tabScroll)

        val actionBtnW = dp(34f * fScale).coerceAtLeast(dp(26f))
        val actionBtnH = dp(32f * hScale).coerceAtLeast(dp(24f))
        val actionPad = dp(6.5f * fScale).coerceAtLeast(dp(3.5f))

        val closeBtn = ImageView(this).apply {
            contentDescription = "Close emoji"
            val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(actionPad, actionPad, actionPad, actionPad)
            background = skin.chipDrawable(density, false)
            layoutParams = LinearLayout.LayoutParams(actionBtnW, actionBtnH).apply {
                marginStart = dp(4f * fScale).coerceAtLeast(dp(2f))
                marginEnd = dp(6f * fScale).coerceAtLeast(dp(3f))
            }
            setOnClickListener {
                feedback(this)
                switchLayer(Layer.LETTERS)
            }
        }
        topBar.addView(closeBtn)
        panel.addView(topBar)

        var onSwipe: (Int) -> Unit = {}
        val swipeDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || e2.eventTime - e1.eventTime > 350L) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > dp(60f) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f &&
                    kotlin.math.abs(vx) > dp(300f)
                ) {
                    onSwipe(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        val emojiScroll = object : ScrollView(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                swipeDetector.onTouchEvent(ev)
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = true
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4f), dp(4f), dp(4f), dp(8f))
        }
        emojiScroll.addView(scrollContent)
        panel.addView(emojiScroll)

        val categorySectionViews = mutableListOf<View>()
        var recentsContent: LinearLayout? = null

        fun refreshRecents() {
            val container = recentsContent ?: return
            container.removeAllViews()
            val recents = getRecentEmojis()
            if (recents.isEmpty()) {
                container.addView(TextView(this@KeyboardService).apply {
                    text = "No recently used emoji"
                    gravity = Gravity.CENTER
                    setTextColor(skin.subtleTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(14f), 0, dp(14f))
                })
            } else {
                renderEmojiGridRows(container, recents) {
                    refreshRecents()
                }
            }
        }

        for (i in EmojiData.categoryLabels.indices) {
            val sectionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val headerTitle = if (i == 0) {
                "RECENTLY USED"
            } else {
                (EmojiData.categories.getOrNull(i - 1) ?: EmojiData.categoryLabels[i]).uppercase()
            }
            sectionLayout.addView(TextView(this@KeyboardService).apply {
                text = headerTitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (11f * fScale).coerceIn(8.5f, 11f))
                setTypeface(null, Typeface.BOLD)
                setTextColor(skin.subtleTextColor)
                val hPad = dp(10f * fScale).coerceAtLeast(dp(4f))
                setPadding(hPad, dp(6f * hScale).coerceAtLeast(dp(3f)), hPad, dp(4f * hScale).coerceAtLeast(dp(2f)))
            })

            val content = LinearLayout(this@KeyboardService).apply { orientation = LinearLayout.VERTICAL }
            sectionLayout.addView(content)

            if (i == 0) {
                recentsContent = content
                refreshRecents()
            } else {
                renderEmojiGridRows(content, EmojiData.getSupportedSet(i - 1)) {
                    refreshRecents()
                }
            }

            categorySectionViews.add(sectionLayout)
            scrollContent.addView(sectionLayout)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(skin.surface)
            val padH = dp(10f * fScale).coerceAtLeast(dp(6f))
            setPadding(padH, dp(2f), padH, dp(2f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bottomBarH)
        }
        val btnH = bottomBarH - dp(6f)

        fun bottomBtn(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val btnPadH = dp(14f * fScale).coerceAtLeast(dp(10f))
            setPadding(btnPadH, 0, btnPadH, 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(ColorUtils.blendARGB(skin.surface, skin.background, 0.25f))
                setStroke(dp(1.2f), skin.accent)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btnH)
            isClickable = true
            isFocusable = true
        }

        val abcBtn = bottomBtn()
        abcBtn.addView(ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_keyboard)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val iconSize = dp(17f * fScale).coerceIn(dp(14f), dp(19f))
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dp(5f * fScale).coerceAtLeast(dp(3f))
            }
        })
        abcBtn.addView(TextView(this).apply {
            text = "ABC"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (12.5f * fScale).coerceIn(10.5f, 13f))
            setTypeface(skin.typeface, Typeface.BOLD)
            setTextColor(skin.textColor)
        })
        abcBtn.setOnClickListener {
            feedback(this)
            switchLayer(Layer.LETTERS)
        }
        bottomBar.addView(abcBtn)

        bottomBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val backspaceBtn = bottomBtn()
        backspaceBtn.addView(ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val iconSize = dp(18f * fScale).coerceIn(dp(15f), dp(20f))
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
        bindBackspaceTouch(backspaceBtn)
        bottomBar.addView(backspaceBtn)
        panel.addView(bottomBar)

        var isProgrammaticScroll = false
        var isTouchingTabScroll = false

        tabScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> isTouchingTabScroll = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tabScroll.postDelayed({ isTouchingTabScroll = false }, 250L)
                }
            }
            false
        }

        fun scrollToCategory(idx: Int, smooth: Boolean = true) {
            val target = categorySectionViews.getOrNull(idx) ?: return
            currentEmojiCategoryIndex = idx
            updateTabStyles(idx)
            isProgrammaticScroll = true
            val targetY = target.top
            if (smooth) {
                emojiScroll.smoothScrollTo(0, targetY)
            } else {
                emojiScroll.scrollTo(0, targetY)
            }
            emojiScroll.postDelayed({ isProgrammaticScroll = false }, 350L)

            if (!isTouchingTabScroll) {
                tabRow.getChildAt(idx)?.let { tab ->
                    val targetX = (tab.left - dp(24f)).coerceAtLeast(0)
                    if (smooth) tabScroll.smoothScrollTo(targetX, 0) else tabScroll.scrollTo(targetX, 0)
                }
            }
        }

        emojiScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (isProgrammaticScroll) return@setOnScrollChangeListener
            var activeIdx = 0
            val threshold = scrollY + dp(28f)
            for (idx in categorySectionViews.indices) {
                val secView = categorySectionViews[idx]
                if (secView.top <= threshold) {
                    activeIdx = idx
                } else {
                    break
                }
            }
            if (activeIdx != currentEmojiCategoryIndex) {
                currentEmojiCategoryIndex = activeIdx
                updateTabStyles(activeIdx)
                if (!isTouchingTabScroll) {
                    tabRow.getChildAt(activeIdx)?.let { tab ->
                        val targetX = (tab.left - dp(24f)).coerceAtLeast(0)
                        tabScroll.smoothScrollTo(targetX, 0)
                    }
                }
            }
        }

        onSwipe = { dir ->
            val nextIdx = (currentEmojiCategoryIndex + dir).coerceIn(0, EmojiData.categoryLabels.size - 1)
            if (nextIdx != currentEmojiCategoryIndex) {
                scrollToCategory(nextIdx, smooth = true)
            }
        }

        for (i in 0 until tabRow.childCount) {
            tabRow.getChildAt(i).setOnClickListener {
                feedback(this)
                scrollToCategory(i, smooth = true)
            }
        }

        val initialIdx = currentEmojiCategoryIndex.coerceIn(0, EmojiData.categoryLabels.size - 1)
        updateTabStyles(initialIdx)
        if (initialIdx > 0) {
            emojiScroll.post {
                scrollToCategory(initialIdx, smooth = false)
            }
        }

        return panel
    }

    private fun prewarmEmojiPanel() {
        if (cachedEmojiPanel == null) {
            try {
                cachedEmojiPanel = buildEmojiPanel()
            } catch (_: Exception) {}
        }
    }

    private var activeSkinTonePopup: PopupWindow? = null
    private var skinToneState: SkinToneState? = null

    private class SkinToneState(
        val baseEmoji: String,
        val variants: List<String>,
        val cells: List<TextView>,
        val left: Int,
        val cellW: Int,
        val pad: Int,
        var selected: Int
    )

    private fun showEmojiSkinTonePopup(anchor: View, col: Int, baseEmoji: String) {
        dismissSkinTonePopup()
        val variants = EmojiSkinTones.getVariants(baseEmoji)
        if (variants.size < 2) return

        val fScale = floatWidthScale()
        val cellW = dp(42f * fScale).coerceAtLeast(dp(32f))
        val pad = dp(4f)
        val pillW = cellW * variants.size + pad * 2
        val pillH = cellW + pad * 2

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = (pillH / 2f)
                setColor(skin.surface)
                setStroke(dp(1.2f), skin.chipBorderColor)
            }
            elevation = dp(8f).toFloat()
        }

        val preferred = EmojiSkinTones.getPreferredEmoji(this, baseEmoji)
        val initialSelected = variants.indexOf(preferred).let { if (it >= 0) it else 0 }

        val cells = variants.map { variant ->
            TextView(this).apply {
                text = variant
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (20f * fScale).coerceIn(15f, 22f))
                layoutParams = LinearLayout.LayoutParams(cellW, cellW)
            }
        }
        cells.forEach { pill.addView(it) }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val cols = (anchor as? EmojiRowView)?.cols ?: EMOJI_COLS
        val colW = anchor.width.toFloat() / cols
        val centerX = loc[0] + (col + 0.5f) * colW
        val screenW = resources.displayMetrics.widthPixels
        val left = (centerX - pillW / 2f).toInt().coerceIn(dp(6f), screenW - pillW - dp(6f))

        val state = SkinToneState(baseEmoji, variants, cells, left, cellW, pad, initialSelected)
        skinToneState = state
        paintSkinTone(state)

        val popup = PopupWindow(
            pill,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            isClippingEnabled = false
            isTouchable = false
            animationStyle = 0
        }

        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val desiredYOff = -(anchor.height + pill.measuredHeight + dp(8f))
        val yOff = if (loc[1] + desiredYOff < dp(24f)) anchor.height + dp(4f) else desiredYOff

        try {
            popup.showAsDropDown(anchor, left - loc[0], yOff)
            activeSkinTonePopup = popup
            buzz()
        } catch (_: Exception) {}
    }

    private fun paintSkinTone(st: SkinToneState) {
        st.cells.forEachIndexed { i, cell ->
            if (i == st.selected) {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(skin.accent)
                }
            } else {
                cell.background = null
            }
        }
    }

    private fun updateSkinToneSelection(rawX: Float) {
        val st = skinToneState ?: return
        val idx = ((rawX - st.left - st.pad) / st.cellW).toInt().coerceIn(0, st.variants.size - 1)
        if (idx != st.selected) {
            st.selected = idx
            paintSkinTone(st)
            buzz()
        }
    }

    private fun dismissSkinTonePopup() {
        try {
            activeSkinTonePopup?.dismiss()
        } catch (_: Exception) {}
        activeSkinTonePopup = null
        skinToneState = null
    }

    private inner class EmojiRowView(
        context: Context,
        private val rowEmojis: List<String>,
        val cols: Int = EMOJI_COLS,
        private val textPaint: Paint,
        private val pressPaint: Paint,
        private val onEmojiTap: (String) -> Unit
    ) : View(context) {
        private var pressedCol = -1
        private val hitRect = RectF()
        private var downRawX = 0f
        private var downRawY = 0f
        private var isScrolling = false
        private var isSkinToneActive = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        private val longPressRunnable = Runnable {
            if (pressedCol in rowEmojis.indices && !isScrolling) {
                val emoji = rowEmojis[pressedCol]
                if (EmojiSkinTones.supportsSkinTone(emoji)) {
                    isSkinToneActive = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    showEmojiSkinTonePopup(this, pressedCol, emoji)
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val colW = w / cols
            val textOffsetY = (textPaint.descent() + textPaint.ascent()) / 2f
            val cy = h / 2f - textOffsetY

            if (pressedCol in rowEmojis.indices && !isScrolling && !isSkinToneActive) {
                val pad = dp(3f * (if (floatActive()) 0.7f else 1f)).toFloat()
                val left = pressedCol * colW + pad
                val right = (pressedCol + 1) * colW - pad
                val top = pad
                val bottom = h - pad
                hitRect.set(left, top, right, bottom)
                val r = dp(10f * (if (floatActive()) 0.75f else 1f)).toFloat()
                canvas.drawRoundRect(hitRect, r, r, pressPaint)
            }

            for (i in rowEmojis.indices) {
                val cx = (i + 0.5f) * colW
                val displayEmoji = EmojiSkinTones.getPreferredEmoji(context, rowEmojis[i])
                canvas.drawText(displayEmoji, cx, cy, textPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            if (w <= 0f) return false
            val colW = w / cols
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val col = (event.x / colW).toInt()
                    if (col in rowEmojis.indices) {
                        pressedCol = col
                        isScrolling = false
                        isSkinToneActive = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        invalidate()
                        val lpMs = Skins.prefs(context).getInt(Skins.KEY_LONG_PRESS_MS, Skins.DEFAULT_LONG_PRESS_MS).toLong()
                        postDelayed(longPressRunnable, lpMs)
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pressedCol == -1 || isScrolling) return false

                    if (isSkinToneActive) {
                        updateSkinToneSelection(event.rawX)
                        return true
                    }

                    val dx = Math.abs(event.rawX - downRawX)
                    val dy = Math.abs(event.rawY - downRawY)

                    if (dx > touchSlop || dy > touchSlop) {
                        removeCallbacks(longPressRunnable)
                        isScrolling = true
                        pressedCol = -1
                        invalidate()
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }

                    val col = (event.x / colW).toInt()
                    if (event.x < 0f || event.x >= w || event.y < 0f || event.y >= height || col != pressedCol) {
                        removeCallbacks(longPressRunnable)
                        parent?.requestDisallowInterceptTouchEvent(false)
                        pressedCol = -1
                        invalidate()
                        return false
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    parent?.requestDisallowInterceptTouchEvent(false)

                    if (isSkinToneActive) {
                        val st = skinToneState
                        val selectedEmoji = st?.variants?.getOrNull(st.selected)
                        if (selectedEmoji != null) {
                            EmojiSkinTones.savePreferredTone(context, st.baseEmoji, selectedEmoji)
                            onEmojiTap(selectedEmoji)
                        }
                        dismissSkinTonePopup()
                        isSkinToneActive = false
                        pressedCol = -1
                        invalidate()
                        return true
                    }

                    val col = pressedCol
                    val wasScrolling = isScrolling
                    if (col != -1 && !wasScrolling) {
                        pressedCol = -1
                        invalidate()
                        if (col in rowEmojis.indices) {
                            performClick()
                            val chosen = EmojiSkinTones.getPreferredEmoji(context, rowEmojis[col])
                            onEmojiTap(chosen)
                        }
                    } else {
                        pressedCol = -1
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    dismissSkinTonePopup()
                    isSkinToneActive = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (pressedCol != -1) {
                        pressedCol = -1
                        invalidate()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            return super.performClick()
        }
    }

    private fun renderEmojiGridRows(content: LinearLayout, emojis: List<String>, onEmojiUsed: (() -> Unit)? = null) {
        val fScale = floatWidthScale()
        val hScale = heightScale().coerceIn(0.65f, 1.15f)
        val userEmojiScale = (Skins.prefs(this).getInt(Skins.KEY_EMOJI_SIZE, Skins.DEFAULT_EMOJI_SIZE) / 100f).coerceIn(0.60f, 1.60f)
        val emojiCols = when {
            userEmojiScale >= 1.30f -> 6
            userEmojiScale <= 0.80f -> 8
            else -> 7
        }
        val emojiSp = (29.5f * fScale * userEmojiScale).coerceIn(16.5f, 44f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, emojiSp, resources.displayMetrics)
            typeface = Typeface.DEFAULT
            color = skin.textColor
        }
        val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(skin.textColor, 30)
        }
        val rowHeightPx = dp((50f * hScale * fScale.coerceAtLeast(0.75f) * userEmojiScale).coerceIn(30f, 85f))

        emojis.chunked(emojiCols).forEach { chunk ->
            val rowView = EmojiRowView(this, chunk, emojiCols, textPaint, pressPaint) { emoji ->
                feedback(content)
                typeText(emoji)
                val isNew = saveRecentEmoji(emoji)
                if (isNew) {
                    onEmojiUsed?.invoke()
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx)
            }
            content.addView(rowView)
        }
    }

    private fun getRecentEmojis(): List<String> {
        val raw = Skins.prefs(this).getString("recent_emojis", "") ?: ""
        return raw.split(" ").filter { it.isNotEmpty() }
    }

    private fun saveRecentEmoji(emoji: String): Boolean {
        val current = getRecentEmojis()
        if (current.contains(emoji)) {
            return false
        }
        val updated = (current + emoji).takeLast(RECENT_MAX)
        Skins.prefs(this).edit().putString("recent_emojis", updated.joinToString(" ")).apply()
        return true
    }

    private fun buildClipboardPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(skin.background)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240f * heightScale()))
        }
        clipPanel = panel

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(skin.surface)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        }

        val title = TextView(this).apply {
            text = "Clipboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val clearBtn = TextView(this).apply {
            text = "Clear All"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(skin.subtleTextColor)
            background = skin.chipDrawable(density, false)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener {
                feedback(this)
                val textEnabled = ClipboardHistory.isEnabled(context, ClipboardHistory.CAT_TEXT)
                val screenshotsEnabled = ClipboardHistory.isEnabled(context, ClipboardHistory.CAT_SCREENSHOT)
                val unpinnedCount = ClipboardHistory.getItems(context, null).count { item ->
                    !item.isPinned && when (item.category) {
                        ClipboardHistory.CAT_TEXT -> textEnabled
                        ClipboardHistory.CAT_SCREENSHOT -> screenshotsEnabled
                        else -> textEnabled
                    }
                }
                if (unpinnedCount == 0) {
                    Toast.makeText(
                        context,
                        "Nothing to clear in clipboard",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    clipClearPending = true
                    renderClipboardItems(panel)
                }
            }
        }
        header.addView(clearBtn)

        val optionsBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_sliders)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            background = skin.chipDrawable(density, false)
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                marginStart = dp(8f)
            }
            contentDescription = "Clipboard Options"
            setOnClickListener {
                feedback(this)
                toggleClipboardOptionsPopup(this, panel)
            }
        }
        header.addView(optionsBtn)

        val closeBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            background = skin.chipDrawable(density, false)
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply {
                marginStart = dp(8f)
            }
            contentDescription = "Close"
            setOnClickListener {
                feedback(this)
                try { clipOptionsPopup?.dismiss() } catch (_: Exception) {}
                clipOptionsPopup = null
                clipClearPending = false
                toolboxOpen = false
                switchLayer(Layer.LETTERS)
            }
        }
        header.addView(closeBtn)
        panel.addView(header)

        renderClipboardItems(panel)
        return panel
    }

    private fun toggleClipboardOptionsPopup(anchor: View, panel: LinearLayout) {
        if (clipOptionsPopup?.isShowing == true) {
            try { clipOptionsPopup?.dismiss() } catch (_: Exception) {}
            clipOptionsPopup = null
            return
        }
        if (SystemClock.uptimeMillis() - clipOptionsDismissTime < 250L) {
            return
        }

        val popupW = dp(270f).coerceAtMost((screenW() * 0.9f).toInt())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(skin.surface)
                setStroke(dp(1f), ColorUtils.setAlphaComponent(skin.textColor, 36))
            }
            background = bg
            elevation = dp(14f).toFloat()
            val padH = dp(14f)
            val padV = dp(12f)
            setPadding(padH, padV, padH, padV)
            layoutParams = ViewGroup.LayoutParams(popupW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f)
            }
        }
        val titleTv = TextView(this).apply {
            text = "Clipboard Options"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val popupCloseBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
            d?.setTint(skin.subtleTextColor)
            setImageDrawable(d)
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f))
            contentDescription = "Close"
            setOnClickListener {
                feedback(this)
                try { clipOptionsPopup?.dismiss() } catch (_: Exception) {}
                clipOptionsPopup = null
            }
        }
        headerRow.addView(titleTv)
        headerRow.addView(popupCloseBtn)
        card.addView(headerRow)

        fun buildToggleRow(
            title: String,
            desc: String,
            category: String,
            onChanged: (Boolean) -> Unit
        ): View {
            val row = LinearLayout(this@KeyboardService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f)
                    bottomMargin = dp(4f)
                }
            }
            val textCol = LinearLayout(this@KeyboardService).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10f)
                }
            }
            val tView = TextView(this@KeyboardService).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(skin.textColor)
            }
            val dView = TextView(this@KeyboardService).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(ColorUtils.setAlphaComponent(skin.textColor, 180))
            }
            textCol.addView(tView)
            textCol.addView(dView)
            row.addView(textCol)

            val toggle = ModernToggleView(
                this@KeyboardService,
                ClipboardHistory.isEnabled(this@KeyboardService, category),
                density
            ) { isChecked ->
                onChanged(isChecked)
            }.apply {
                accentColor = skin.accentText
            }
            row.addView(toggle)
            return row
        }

        val rowText = buildToggleRow(
            title = "Text History",
            desc = "Save copied text clips",
            category = ClipboardHistory.CAT_TEXT
        ) { isChecked ->
            onClipSectionToggled(ClipboardHistory.CAT_TEXT, isChecked, panel)
        }
        card.addView(rowText)

        val div = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)).apply {
                topMargin = dp(8f)
                bottomMargin = dp(8f)
            }
            setBackgroundColor(ColorUtils.setAlphaComponent(skin.textColor, 30))
        }
        card.addView(div)

        val rowScreenshots = buildToggleRow(
            title = "Screenshots",
            desc = "Save recent screenshots",
            category = ClipboardHistory.CAT_SCREENSHOT
        ) { isChecked ->
            onClipSectionToggled(ClipboardHistory.CAT_SCREENSHOT, isChecked, panel)
        }
        card.addView(rowScreenshots)

        val popup = PopupWindow(card, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            isOutsideTouchable = true
            isFocusable = false
            elevation = dp(14f).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        clipOptionsPopup = popup
        popup.setTouchInterceptor { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                try { popup.dismiss() } catch (_: Exception) {}
                true
            } else {
                false
            }
        }
        popup.setOnDismissListener {
            clipOptionsDismissTime = SystemClock.uptimeMillis()
            if (clipOptionsPopup === popup) {
                clipOptionsPopup = null
            }
        }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val popupX = (loc[0] + anchor.width - popupW).coerceAtLeast(dp(8f))
        val popupY = loc[1] + anchor.height + dp(4f)

        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
        } catch (_: Exception) {
            try {
                popup.showAsDropDown(anchor, -(popupW - anchor.width), dp(4f))
            } catch (_: Exception) {}
        }
    }

    private fun launchClipPermission(category: String) {
        hideForOtherScreen()
        startActivity(
            Intent(this, ClipPermissionActivity::class.java)
                .putExtra(ClipPermissionActivity.EXTRA_CATEGORY, category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun onClipSectionToggled(category: String, checked: Boolean, panel: LinearLayout) {
        when {
            !checked -> {
                ClipboardHistory.setEnabled(this, category, false)
                ScreenshotWatcher.sync(this, scanNow = false)
            }
            category == ClipboardHistory.CAT_TEXT -> {
                ClipboardHistory.setEnabled(this, category, true)
                ClipboardHistory.syncFromSystem(this)
            }
            category == ClipboardHistory.CAT_SCREENSHOT -> {
                if (ScreenshotWatcher.hasPermission(this)) {
                    ClipboardHistory.setEnabled(this, category, true)
                    ScreenshotWatcher.sync(this, scanNow = false)
                } else {
                    try { clipOptionsPopup?.dismiss() } catch (_: Exception) {}
                    clipOptionsPopup = null
                    launchClipPermission(category)
                }
            }
        }
        clipClearPending = false
        renderClipboardItems(panel)
    }

    private fun clipChip(label: String, active: Boolean, onTap: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (active) skin.actionTextColor else skin.textColor)
            background = skin.chipDrawable(density, active)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6f) }
            setOnClickListener {
                feedback(this)
                onTap()
            }
        }

    private fun buildClipConfirmBar(panel: LinearLayout): View {
        val textEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_TEXT)
        val screenshotsEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_SCREENSHOT)
        val n = ClipboardHistory.getItems(this, null).count { item ->
            !item.isPinned && when (item.category) {
                ClipboardHistory.CAT_TEXT -> textEnabled
                ClipboardHistory.CAT_SCREENSHOT -> screenshotsEnabled
                else -> textEnabled
            }
        }
        val what = if (n == 1) "item" else "items"
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(skin.surface)
            setPadding(dp(12f), dp(6f), dp(8f), dp(6f))
            addView(TextView(context).apply {
                text = "Delete $n unpinned $what? Pinned items stay."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(skin.textColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(clipChip("Cancel", false) {
                clipClearPending = false
                renderClipboardItems(panel)
            })
            addView(clipChip("Delete", true) {
                if (textEnabled) ClipboardHistory.clear(context, ClipboardHistory.CAT_TEXT)
                if (screenshotsEnabled) ClipboardHistory.clear(context, ClipboardHistory.CAT_SCREENSHOT)
                clipClearPending = false
                renderClipboardItems(panel)
                refreshSuggestionsNow()
            })
        }
    }

    private fun renderClipboardItems(panel: LinearLayout) {
        if (panel.childCount > 1) {
            panel.removeViews(1, panel.childCount - 1)
        }
        if (clipClearPending) panel.addView(buildClipConfirmBar(panel))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(6f), dp(8f), dp(8f))
        }

        val textEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_TEXT)
        val screenshotsEnabled = ClipboardHistory.isEnabled(this, ClipboardHistory.CAT_SCREENSHOT)
        val anyEnabled = textEnabled || screenshotsEnabled

        val items = if (anyEnabled) {
            ClipboardHistory.getItems(this, null).filter { item ->
                when (item.category) {
                    ClipboardHistory.CAT_TEXT -> textEnabled
                    ClipboardHistory.CAT_SCREENSHOT -> screenshotsEnabled
                    else -> textEnabled
                }
            }
        } else {
            emptyList()
        }

        if (items.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = if (!anyEnabled) {
                    "Clipboard history is turned off\nTap the options icon above to turn it on"
                } else if (textEnabled && screenshotsEnabled) {
                    "Clipboard is empty\nCopied text and screenshots will appear here"
                } else if (textEnabled) {
                    "Clipboard is empty\nCopied text will appear here"
                } else {
                    "No screenshots yet\nScreenshots you take appear here"
                }
                setTextColor(skin.subtleTextColor)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(12f), dp(28f), dp(12f), dp(28f))
            }
            listContainer.addView(emptyTv)
        } else {
            val pinned = items.filter { it.isPinned }
            val recent = items.filter { !it.isPinned }

            if (pinned.isNotEmpty()) {
                listContainer.addView(buildClipSectionHeader("Pinned"))
                pinned.forEach { item ->
                    listContainer.addView(buildClipCard(item, panel))
                }
            }

            if (recent.isNotEmpty()) {
                listContainer.addView(buildClipSectionHeader("Recent"))
                recent.forEach { item ->
                    listContainer.addView(buildClipCard(item, panel))
                }
            }
        }

        scroll.addView(listContainer)
        panel.addView(scroll)
    }

    private fun buildClipSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.accentText)
            setPadding(dp(4f), dp(6f), dp(4f), dp(4f))
        }
    }

    private fun buildClipCard(item: ClipboardHistory.ClipItem, parentPanel: LinearLayout): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(skin.surface)
                setStroke(dp(1f), if (item.isPinned) skin.accent else skin.chipBorderColor)
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6f)
            }
        }

        if (item.isImage) {
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(54f), dp(54f)).apply {
                    marginEnd = dp(10f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f).toFloat()
                    setColor(Color.parseColor("#222222"))
                }
                clipToOutline = true
            }

            Thread {
                val thumb = ClipboardHistory.loadThumbnail(item.imagePath, dp(54f), dp(54f))
                handler.post {
                    if (thumb != null) iv.setImageBitmap(thumb)
                }
            }.start()

            card.addView(iv)

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleTv = TextView(this).apply {
                text = if (item.category == ClipboardHistory.CAT_SCREENSHOT) "Screenshot"
                else "Image (${item.mimeType.substringAfter("/")})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(skin.textColor)
                setTypeface(null, Typeface.BOLD)
            }
            val subTv = TextView(this).apply {
                text = "Tap to paste into app"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(skin.subtleTextColor)
            }
            infoLayout.addView(titleTv)
            infoLayout.addView(subTv)
            card.addView(infoLayout)

            card.setOnClickListener {
                feedback(card)
                ClipboardHistory.markConsumed(this, item)
                val ok = ClipboardHistory.commitContent(this, currentInputConnection, currentInputEditorInfo, item)
                if (!ok) {
                    Toast.makeText(this, "Copied image to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val tv = TextView(this).apply {
                text = item.text
                maxLines = 2
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val textColor = if (skin.textColor != 0 && skin.textColor != skin.background) skin.textColor else if (skin.isLight) Color.BLACK else Color.WHITE
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8f)
                }
            }
            card.addView(tv)

            card.setOnClickListener {
                feedback(card)
                ClipboardHistory.markConsumed(this, item)
                typeText(item.text)
            }
        }

        val pinBtn = ImageView(this).apply {
            val res = if (item.isPinned) R.drawable.ic_pin else R.drawable.ic_pin_off
            val d = ContextCompat.getDrawable(context, res)?.mutate()
            d?.setTint(if (item.isPinned) skin.accentText else skin.subtleTextColor)
            setImageDrawable(d)
            val p = dp(8f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
            isClickable = true
            isFocusable = true
            background = skin.chipDrawable(density, false)
            contentDescription = if (item.isPinned) "Unpin" else "Pin"
            setOnClickListener {
                feedback(this)
                ClipboardHistory.togglePin(context, item.id)
                renderClipboardItems(parentPanel)
                refreshSuggestionsNow()
            }
        }
        card.addView(pinBtn)

        val delBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
            d?.setTint(skin.subtleTextColor)
            setImageDrawable(d)
            val p = dp(8f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply {
                marginStart = dp(4f)
            }
            isClickable = true
            isFocusable = true
            background = skin.chipDrawable(density, false)
            contentDescription = "Delete"
            setOnClickListener {
                feedback(this)
                ClipboardHistory.delete(context, item.id)
                renderClipboardItems(parentPanel)
                refreshSuggestionsNow()
            }
        }
        card.addView(delBtn)

        return card
    }

    private fun buildEditPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(skin.background)
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240f * heightScale()))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(2f), dp(4f), dp(4f))
        }
        val title = TextView(this).apply {
            text = "Text Editing"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(skin.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_x)?.mutate()
            d?.setTint(skin.textColor)
            setImageDrawable(d)
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(30f))
            setOnClickListener {
                feedback(this)
                switchLayer(Layer.LETTERS)
            }
        }
        header.addView(closeBtn)
        panel.addView(header)

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        navRow.addView(createEditActionBtn("Char \u2190", R.drawable.ic_chevron_left) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        })
        navRow.addView(createEditActionBtn("Char \u2192", R.drawable.ic_chevron_right) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
        })
        navRow.addView(createEditActionBtn("Start", R.drawable.ic_chevron_left) {
            val ic = currentInputConnection
            ic?.setSelection(0, 0)
        })
        navRow.addView(createEditActionBtn("End", R.drawable.ic_chevron_right) {
            val ic = currentInputConnection
            val ex = ic?.getExtractedText(ExtractedTextRequest(), 0)
            val len = ex?.text?.length ?: 0
            ic?.setSelection(len, len)
        })
        panel.addView(navRow)

        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        actRow.addView(createEditActionBtn("Select All", R.drawable.ic_text_select) {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        })
        actRow.addView(createEditActionBtn("Copy", R.drawable.ic_copy) {
            currentInputConnection?.performContextMenuAction(android.R.id.copy)
        })
        actRow.addView(createEditActionBtn("Cut", R.drawable.ic_scissors) {
            currentInputConnection?.performContextMenuAction(android.R.id.cut)
        })
        actRow.addView(createEditActionBtn("Paste", R.drawable.ic_clipboard_paste) {
            currentInputConnection?.performContextMenuAction(android.R.id.paste)
        })
        panel.addView(actRow)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        bottomRow.addView(createEditActionBtn("New Line", R.drawable.ic_corner_down_left) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        })
        bottomRow.addView(createEditActionBtn("Delete", R.drawable.ic_delete) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        })
        panel.addView(bottomRow)

        return panel
    }

    private fun createEditActionBtn(label: String, iconRes: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = skin.chipDrawable(density, false)
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
                val m = dp(3f)
                setMargins(m, m, m, m)
            }

            val iv = ImageView(context).apply {
                val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                d?.setTint(skin.textColor)
                setImageDrawable(d)
                layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
                    marginEnd = dp(4f)
                }
            }
            addView(iv)

            val tv = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(skin.textColor)
            }
            addView(tv)

            setOnClickListener {
                feedback(this)
                onClick()
            }
        }
    }

    private fun buildOneHandedSideBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(skin.surface)
            val padH = dp(6f)
            val padV = dp(12f)
            setPadding(padH, padV, padH, padV)

            fun createSidePillButton(iconRes: Int, desc: String, tintCol: Int, onClick: () -> Unit): View {
                return FrameLayout(context).apply {
                    contentDescription = desc
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorUtils.setAlphaComponent(skin.accent, 28))
                        setStroke(dp(1f), ColorUtils.setAlphaComponent(skin.accent, 110))
                    }
                    elevation = dp(3f).toFloat()
                    val iv = ImageView(context).apply {
                        val d = ContextCompat.getDrawable(context, iconRes)?.mutate()
                        d?.setTint(tintCol)
                        setImageDrawable(d)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    val sz = dp(38f)
                    val iconSz = dp(20f)
                    addView(iv, FrameLayout.LayoutParams(iconSz, iconSz, Gravity.CENTER))
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                        bottomMargin = dp(14f)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        feedback(this)
                        onClick()
                    }
                }
            }

            val expandBtn = createSidePillButton(
                iconRes = R.drawable.ic_maximize_2,
                desc = "Expand Keyboard to Full Width",
                tintCol = if (skin.accentText != 0) skin.accentText else skin.accent
            ) {
                oneHandedMode = "off"
                Skins.prefs(context).edit().putString(Skins.KEY_ONE_HANDED, "off").apply()
                renderFullKeyboard()
            }
            addView(expandBtn)

            val switchSideBtn = createSidePillButton(
                iconRes = R.drawable.ic_move_horizontal,
                desc = "Switch Handedness Side",
                tintCol = skin.textColor
            ) {
                oneHandedMode = if (oneHandedMode == "left") "right" else "left"
                Skins.prefs(context).edit().putString(Skins.KEY_ONE_HANDED, oneHandedMode).apply()
                renderFullKeyboard()
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
            }
            addView(switchSideBtn)
        }
    }

    private fun isPasswordField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
               ((info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun isNumericInput(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_NUMBER ||
               inputClass == InputType.TYPE_CLASS_PHONE ||
               inputClass == InputType.TYPE_CLASS_DATETIME
    }

    private fun isPhoneField(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_PHONE
    }

    private fun isUrlOrEmail(ic: InputConnection): Boolean {
        val info = currentInputEditorInfo ?: return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
            return true
        }
        val before = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
        return before.contains("http://") || before.contains("https://") || before.contains("www.") || before.contains("@")
    }

    private fun handleSpacePress() {
        val ic = currentInputConnection ?: return
        val activeWord = currentWord.toString()
        val isPassword = isPasswordField()
        val isUrl = isUrlOrEmail(ic)

        val candidate = currentAutoCorrectCandidate ?: run {
            if (activeWord.isNotEmpty() && !isPassword && !isUrl) {
                val corr = gnosis.keyboard.suggest.GnosisSpellingScorer.findCorrection(activeWord)
                if (corr != null) {
                    Candidate(corr.first, corr.second, CandidateKind.CORRECTION)
                } else {
                    getTypingEngine()?.onInputUpdate(activeWord, extractContextWords(ic, activeWord))?.autoCorrect
                }
            } else null
        }

        if (autoCorrectEnabled && activeWord.isNotEmpty() && candidate != null &&
            autocorrectEngine.shouldAutoCorrect(candidate, activeWord, isPassword, isUrl)) {
            val corrected = candidate.word

            if (corrected.contains(" ")) {
                val before = ic.getTextBeforeCursor(60, 0)?.toString() ?: ""
                val context = extractContextWords(ic, activeWord)
                val phraseToDelete = if (context.isNotEmpty()) {
                    val prevWord = context.last()
                    "$prevWord $activeWord"
                } else activeWord
                if (before.endsWith(phraseToDelete)) {
                    ic.deleteSurroundingText(phraseToDelete.length, 0)
                } else {
                    ic.deleteSurroundingText(activeWord.length, 0)
                }
            } else {
                ic.deleteSurroundingText(activeWord.length, 0)
            }
            ic.commitText("$corrected ", 1)
            autocorrectEngine.recordCorrection(activeWord, corrected)
            try {
                val ctx = extractContextWords(ic, corrected)
                GnosisNeuralVectorEngine.recordUserSelection(ctx, corrected)
            } catch (_: Throwable) {}
            val prefs = Skins.prefs(this)
            if (prefs.getBoolean(TypingStatisticsRepository.PREF_STATS_ENABLED, true) && !isPassword) {
                statsRepository.recordAutoCorrection(activeWord, corrected)
            }
            lastCommittedWord = corrected
            isNewInputSession = false
            currentWord.clear()
            currentAutoCorrectCandidate = null
            lastSpaceTapTime = 0L
            updateSuggestions()
            updateAutoCaps()
            return
        }

        currentAutoCorrectCandidate = null
        val now = SystemClock.uptimeMillis()
        if (doubleSpacePeriodEnabled && (now - lastSpaceTapTime < 450L) && editorController.handleDoubleSpacePeriod(ic)) {
            lastSpaceTapTime = 0L
            isNewInputSession = false
            currentWord.clear()
            updateSuggestions()
            updateAutoCaps()
        } else {
            lastSpaceTapTime = now
            typeText(" ")
        }
    }

    private fun extractContextWords(ic: InputConnection, activeWord: String): List<String> {
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: return emptyList()
        val withoutActive = if (activeWord.isNotEmpty() && before.endsWith(activeWord)) {
            before.substring(0, before.length - activeWord.length)
        } else {
            before
        }

        val trimmed = withoutActive.trimEnd()
        if (trimmed.isEmpty() || trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?') || trimmed.endsWith('\n')) {
            return emptyList()
        }

        return trimmed
            .split(Regex("\\s+"))
            .map { token -> token.replace(Regex("[^a-zA-Z0-9']"), "").trim() }
            .filter { it.isNotEmpty() }
            .takeLast(3)
    }

    private fun formatSuggestionsForDisplay(
        typed: String,
        candidates: List<String>,
        autoCorrectWord: String?
    ): List<String> {
        if (candidates.isEmpty()) return listOf(typed)

        val primary = autoCorrectWord ?: candidates.first()
        val others = candidates.filter { !it.equals(primary, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }

        return if (primary.equals(typed, ignoreCase = true)) {
            when {
                others.size >= 2 -> listOf(others[0], primary, others[1])
                others.size == 1 -> listOf(primary, others[0])
                else -> listOf(primary)
            }
        } else {
            when {
                others.isNotEmpty() -> listOf(typed, primary, others[0])
                else -> listOf(typed, primary)
            }
        }
    }

    private fun typeText(text: String, isLetter: Boolean = false) {
        if (text != " ") {
            lastSpaceTapTime = 0L
        }
        try {
            if (isListeningVoice) stopVoiceRecognition()
            val ic = currentInputConnection ?: return
            val isPunctuation = !isLetter && text.length == 1 && (text == "." || text == "," || text == "!" || text == "?" || text == ":" || text == ";")
            if (isPunctuation && currentWord.isNotEmpty() && autoCorrectEnabled) {
                val activeWord = currentWord.toString()
                val candidate = currentAutoCorrectCandidate ?: run {
                    if (!isPasswordField() && !isUrlOrEmail(ic)) {
                        val corr = gnosis.keyboard.suggest.GnosisSpellingScorer.findCorrection(activeWord)
                        if (corr != null) {
                            Candidate(corr.first, corr.second, CandidateKind.CORRECTION)
                        } else {
                            getTypingEngine()?.onInputUpdate(activeWord, extractContextWords(ic, activeWord))?.autoCorrect
                        }
                    } else null
                }
                if (candidate != null && autocorrectEngine.shouldAutoCorrect(candidate, activeWord, isPasswordField(), isUrlOrEmail(ic))) {
                    val corrected = candidate.word
                    ic.deleteSurroundingText(currentWord.length, 0)
                    ic.commitText(corrected, 1)
                    autocorrectEngine.recordCorrection(activeWord, corrected)
                    val prefs = Skins.prefs(this)
                    if (prefs.getBoolean(TypingStatisticsRepository.PREF_STATS_ENABLED, true) && !isPasswordField()) {
                        statsRepository.recordAutoCorrection(activeWord, corrected)
                    }
                    lastCommittedWord = corrected
                    currentWord.clear()
                    currentAutoCorrectCandidate = null
                }
            }

            val inputClass = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
            if (inputClass == InputType.TYPE_CLASS_PHONE && text.length == 1) {
                val sent = when (text[0]) {
                    '0' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_0); true }
                    '1' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_1); true }
                    '2' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_2); true }
                    '3' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_3); true }
                    '4' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_4); true }
                    '5' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_5); true }
                    '6' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_6); true }
                    '7' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_7); true }
                    '8' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_8); true }
                    '9' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_9); true }
                    '*' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_STAR); true }
                    '#' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_POUND); true }
                    '+' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_PLUS); true }
                    else -> false
                }
                if (!sent) {
                    ic.commitText(text, 1)
                }
            } else {
                ic.commitText(text, 1)
            }
            isNewInputSession = false
            val charPrefs = Skins.prefs(this)
            if (charPrefs.getBoolean(TypingStatisticsRepository.PREF_STATS_ENABLED, true) && !isPasswordField()) {
                statsRepository.recordCharactersTyped(text.length)
            }

            if (isLetter && text.length == 1 && text[0].isLetter()) {
                if (currentWord.isNotEmpty()) {
                    currentWord.append(text[0])
                } else {
                    val wordBefore = try { getWordBeforeCursor(ic) } catch (_: Throwable) { "" }
                    if (wordBefore.isNotEmpty()) {
                        currentWord.append(wordBefore)
                    } else {
                        currentWord.append(text[0])
                    }
                }
                updateSuggestions()
            } else if (text == "'" && currentWord.isNotEmpty()) {
                currentWord.append('\'')
                updateSuggestions()
            } else {
                if (currentWord.isNotEmpty()) {
                    val w = currentWord.toString()
                    val prefs = Skins.prefs(this)
                    if (prefs.getBoolean(TypingStatisticsRepository.PREF_STATS_ENABLED, true) && !isPasswordField()) {
                        statsRepository.recordWordTyped(w)
                    }
                    try {
                        if (!previewMode) {
                            getTypingEngine()?.onWordCommitted(w, extractContextWords(ic, w))
                            WordDictionary.learn(this, w)
                        }
                        if (lastCommittedWord.isNotEmpty() && !previewMode) {
                            WordDictionary.learnTransition(this, lastCommittedWord, w)
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("KeyboardService", "typeText learning error", e)
                    }
                    lastCommittedWord = w
                    currentWord.clear()
                }
                updateSuggestions()
            }

            if (shift == Shift.ONCE) {
                shift = Shift.OFF
                updateShiftView()
                refreshLetters()
            }
        } catch (t: Throwable) {
            android.util.Log.e("KeyboardService", "Error in typeText", t)
        }
    }

    private fun handleBackspace() {
        lastSpaceTapTime = 0L
        if (isListeningVoice) stopVoiceRecognition()
        val ic = currentInputConnection ?: return
        isNewInputSession = false

        if (autocorrectEngine.canUndo()) {
            val record = autocorrectEngine.consumeUndo()
            if (record != null) {
                val before = ic.getTextBeforeCursor(record.corrected.length + 2, 0)?.toString() ?: ""
                if (before.endsWith("${record.corrected} ")) {
                    ic.deleteSurroundingText(record.corrected.length + 1, 0)
                    ic.commitText(record.original, 1)
                    val wordBefore = getWordBeforeCursor(ic)
                    currentWord.clear()
                    currentWord.append(if (wordBefore.isNotEmpty()) wordBefore else record.original)
                    updateSuggestions()
                    updateAutoCaps()
                    return
                }
            }
        }

        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        val inputClass = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_PHONE) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            val deleted = editorController.deleteCodePointsBeforeCursor(ic, 1)
            if (!deleted) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        val wordBefore = getWordBeforeCursor(ic)
        currentWord.clear()
        if (wordBefore.isNotEmpty()) {
            currentWord.append(wordBefore)
        }
        updateSuggestions()
        updateAutoCaps()
    }

    private fun lastCharLength(ic: InputConnection): Int {
        val before = ic.getTextBeforeCursor(16, 0)?.toString() ?: return 1
        if (before.isEmpty()) return 1
        return try {
            val it = java.text.BreakIterator.getCharacterInstance()
            it.setText(before)
            val start = it.preceding(before.length)
            if (start == java.text.BreakIterator.DONE) 1 else (before.length - start).coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    }

    private fun bindBackspaceTouch(view: View) {
        var repeater: Runnable? = null
        var startX = 0f
        var originX = 0f
        var isSwipeDelete = false
        val startTime = longArrayOf(0L)
        var anchorEnd = 0
        var markedTokens = 0
        val cumulative = ArrayList<Int>()
        var repeated = false

        fun stop() {
            repeater?.let { handler.removeCallbacks(it) }
            repeater = null
            view.isPressed = false
        }

        fun buildTokens(text: String) {
            cumulative.clear()
            var i = text.length - 1
            var total = 0
            while (i >= 0) {
                val space = text[i].isWhitespace()
                while (i >= 0 && text[i].isWhitespace() == space) {
                    total++
                    i--
                }
                cumulative.add(total)
            }
        }

        fun markedChars(): Int = if (markedTokens <= 0) 0 else cumulative[markedTokens - 1]

        fun applySelection() {
            val ic = currentInputConnection ?: return
            ic.setSelection(anchorEnd - markedChars(), anchorEnd)
        }

        fun beginSwipeSelect(): Boolean {
            val ic = currentInputConnection ?: return false
            val before = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            if (before.isEmpty()) return false
            val ext = ic.getExtractedText(ExtractedTextRequest(), 0)
            anchorEnd = if (ext != null && ext.selectionStart >= 0) {
                ext.startOffset + ext.selectionStart
            } else if (lastSelStart >= 0) lastSelStart else return false
            buildTokens(before)
            markedTokens = 0
            currentWord.clear()
            return true
        }

        fun updateMarks(x: Float) {
            val step = dp(30f).toFloat()
            val moved = Math.floor(((originX - x) + step * 0.35f) / step.toDouble()).toInt()
            val target = (moved + 1).coerceIn(0, cumulative.size)
            if (target != markedTokens) {
                markedTokens = target
                applySelection()
                buzz()
            }
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    flushPendingRollover()
                    v.isPressed = true
                    startX = event.rawX
                    isSwipeDelete = false
                    markedTokens = 0
                    startTime[0] = SystemClock.uptimeMillis()
                    feedback(v)
                    repeated = false

                    repeater = object : Runnable {
                        override fun run() {
                            if (!isSwipeDelete) {
                                repeated = true
                                val held = SystemClock.uptimeMillis() - startTime[0]
                                if (held > 1100L) {
                                    handleBackspaceWord()
                                } else {
                                    handleBackspace()
                                }
                                feedback(v)
                                val interval = if (held > 1100L) 90L else 45L
                                handler.postDelayed(this, interval)
                            }
                        }
                    }
                    handler.postDelayed(repeater!!, 320L)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (gestureDeleteEnabled) {
                        if (!isSwipeDelete && startX - event.rawX > dp(24f)) {
                            stop()
                            v.isPressed = true
                            if (beginSwipeSelect()) {
                                isSwipeDelete = true
                                originX = event.rawX
                                updateMarks(event.rawX)
                            }
                        } else if (isSwipeDelete) {
                            updateMarks(event.rawX)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    stop()
                    if (!isSwipeDelete && !repeated) handleBackspace()
                    if (isSwipeDelete) {
                        if (markedTokens > 0) {
                            currentInputConnection?.commitText("", 1)
                        } else {
                            currentInputConnection?.setSelection(anchorEnd, anchorEnd)
                        }
                        markedTokens = 0
                        isSwipeDelete = false
                        updateSuggestions()
                        updateAutoCaps()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    stop()
                    if (isSwipeDelete) {
                        currentInputConnection?.setSelection(anchorEnd, anchorEnd)
                        markedTokens = 0
                        isSwipeDelete = false
                    }
                }
            }
            true
        }
    }

    private fun handleBackspaceWord() {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            val len = currentWord.length
            currentWord.clear()
            ic.deleteSurroundingText(len, 0)
        } else {
            val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            if (before.isNotEmpty()) {
                var deleteCount = 0
                var i = before.length - 1
                while (i >= 0 && before[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                while (i >= 0 && !before[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                ic.deleteSurroundingText(if (deleteCount > 0) deleteCount else 1, 0)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        val wordBefore = getWordBeforeCursor(ic)
        currentWord.clear()
        if (wordBefore.isNotEmpty()) {
            currentWord.append(wordBefore)
        }
        updateSuggestions()
        updateAutoCaps()
    }

    private fun pathLength(points: List<PointF>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return total
    }

    private fun processSwipeGesture(points: List<PointF>): Boolean {
        if (points.size < 4) return false
        computeKeyBoundingBoxes()
        val matches = WordDictionary.matchGesture(points, keyBoundingBoxes, 3)
        if (matches.isNotEmpty()) {
            val word = matches.first()
            val formatted = if (shift != Shift.OFF) word.replaceFirstChar { it.uppercase() } else word
            val ic = currentInputConnection
            toolboxOpen = false
            ic?.commitText("$formatted ", 1)
            try {
                if (!previewMode) {
                    if (ic != null) {
                        getTypingEngine()?.onWordCommitted(word, extractContextWords(ic, ""))
                    }
                    WordDictionary.learn(this, word)
                }
            } catch (e: Throwable) {
                android.util.Log.e("KeyboardService", "processSwipeGesture learning error", e)
            }
            currentWord.clear()
            currentAutoCorrectCandidate = null
            updateSuggestions()
            if (shift == Shift.ONCE) {
                shift = Shift.OFF
                updateShiftView()
                refreshLetters()
            }
            return true
        }
        return false
    }

    private fun computeKeyBoundingBoxes() {
        if (keyBoundingBoxes.isNotEmpty()) return
        val rootPos = IntArray(2)
        rootLayout.getLocationOnScreen(rootPos)

        for (v in letterViews) {
            val ch = v.tag as? Char ?: continue
            val pos = IntArray(2)
            v.getLocationOnScreen(pos)
            val left = (pos[0] - rootPos[0]).toFloat()
            val top = (pos[1] - rootPos[1]).toFloat()
            keyBoundingBoxes[ch.lowercaseChar()] = RectF(left, top, left + v.width, top + v.height)
        }
    }

    private fun handleShiftPress() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == Shift.OFF -> Shift.ONCE
            shift == Shift.ONCE && (now - lastShiftTap < DOUBLE_TAP_MS) -> Shift.LOCK
            else -> Shift.OFF
        }
        lastShiftTap = now
        updateShiftView()
        refreshLetters()
    }

    private fun updateShiftView() {
        val iv = shiftIconView ?: (shiftView as? ImageView) ?: return
        val iconRes = if (shift == Shift.LOCK) R.drawable.ic_chevrons_up else R.drawable.ic_arrow_up
        val d = ContextCompat.getDrawable(this, iconRes)?.mutate()
        val color = if (shift == Shift.OFF) skin.textColorFor("SHIFT", true) else skin.accentOnKey
        d?.setTint(color)
        iv.setImageDrawable(d)
    }

    private fun refreshLetters() {
        val isNonLatin = LayoutExtensionManager.getLayout(this, currentLayoutName).isNonLatin || currentLayoutName == "deva"
        for (v in letterViews) {
            val lk = v as? LetterKeyView
            if (lk != null) {
                val ch = lk.char
                lk.updatePrimaryText(if (isNonLatin) ch.toString() else cased(ch).toString())
            } else if (v is TextView) {
                val ch = v.tag as? Char ?: continue
                v.text = if (isNonLatin) ch.toString() else cased(ch).toString()
            }
        }
    }

    private fun cased(ch: Char): Char = if (shift != Shift.OFF) ch.uppercaseChar() else ch.lowercaseChar()

    private fun updateAutoCaps() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val isText = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isText && shift == Shift.OFF) {
            val caps = ic.getCursorCapsMode(info.inputType)
            if (caps != 0) {
                shift = Shift.ONCE
                updateShiftView()
                refreshLetters()
            }
        }
    }

    private fun switchLayer(next: Layer) {
        layer = next
        if (next == Layer.LETTERS) {
            toolboxOpen = false
        }
        renderActiveLayer()
        refreshSuggestionsNow()
        if (::suggestionStrip.isInitialized) {
            val inTyping = (layer == Layer.LETTERS || layer == Layer.NUMBERS || layer == Layer.SYMBOLS || layer == Layer.NUMPAD)
            suggestionStrip.visibility = if (inTyping) View.VISIBLE else View.GONE
            suggestionStrip.requestLayout()
        }
        if (::rootLayout.isInitialized) {
            rootLayout.requestLayout()
        }
    }

    private fun cycleLanguageAndLayout() {
        val installedNonQwerty = LayoutExtensionManager.getInstalledLayouts(this).filter { it.id != "qwerty" }
        if (installedNonQwerty.isNotEmpty()) {
            cycleLayout()
            return
        }

        val newLang = GnosisLanguageManager.cycleLanguage(this)
        currentLayoutName = Skins.prefs(this).getString(Skins.KEY_LAYOUT, newLang.defaultLayout) ?: newLang.defaultLayout
        EngineFactory.invalidateCache()
        typingEngine = null
        suggestionEngine = null
        try {
            Toast.makeText(this, "${newLang.name} (${newLang.shortLabel})", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        renderFullKeyboard()
        refreshSuggestionsNow()
    }

    private fun cycleLayout() {
        val layouts = LayoutExtensionManager.getInstalledLayouts(this).map { it.id }
        if (layouts.isEmpty()) return
        val currentIdx = layouts.indexOf(currentLayoutName)
        val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % layouts.size else 0
        currentLayoutName = layouts[nextIdx]
        Skins.prefs(this).edit().putString(Skins.KEY_LAYOUT, currentLayoutName).apply()
        val model = LayoutExtensionManager.getLayout(this, currentLayoutName)

        val lang = GnosisLanguageManager.getLanguageForLayout(currentLayoutName)
        GnosisLanguageManager.setActiveLanguage(this, lang.code)
        EngineFactory.invalidateCache()
        typingEngine = null
        suggestionEngine = null

        try {
            Toast.makeText(this, "${model.name} (${lang.shortLabel})", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        renderFullKeyboard()
        refreshSuggestionsNow()
    }

    private fun promptVoicePermission() {
        try {
            val intent = Intent(this, VoicePermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (_: Exception) {}

        if (::suggestionStrip.isInitialized) {
            suggestionStrip.visibility = View.VISIBLE
            suggestionStrip.removeAllViews()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8f), 0, dp(8f), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val micIcon = ImageView(this).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_mic)?.mutate()
                d?.setTint(Color.parseColor("#F59E0B"))
                setImageDrawable(d)
                layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                    marginEnd = dp(8f)
                }
            }
            row.addView(micIcon)

            val statusTv = TextView(this).apply {
                text = "Microphone permission needed"
                setTextColor(skin.textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(statusTv)

            val grantBtn = TextView(this).apply {
                text = "Grant"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = dp(6f).toFloat()
                    setColor(skin.accent)
                }
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
                setOnClickListener {
                    try {
                        val intent = Intent(this@KeyboardService, VoicePermissionActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            row.addView(grantBtn)

            suggestionStrip.addView(row)
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            promptVoicePermission()
            return
        }

        if (isListeningVoice) {
            stopVoiceRecognition()
            return
        }

        val comp = findRecognitionServiceComponent()
        if (comp == null) {
            showGoogleSpeechServiceRequiredUI()
            return
        }

        startSystemVoiceRecognition()
    }

    private fun openGoogleSpeechPlayStore() {
        val playStoreUri = Uri.parse("market://details?id=com.google.android.tts")
        val playIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
            setPackage("com.android.vending")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(playIntent)
        } catch (_: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(genericIntent)
            } catch (_: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(webIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Could not open Play Store", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGoogleSpeechServiceRequiredUI() {
        showVoiceListeningUI(
            text = "Speech Recognition & Synthesis by Google required",
            isError = true,
            actionText = "Install"
        ) {
            openGoogleSpeechPlayStore()
        }
    }

    private fun extractSpeechText(bundle: Bundle?): String? {
        if (bundle == null) return null
        val results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!results.isNullOrEmpty() && results[0].isNotBlank()) {
            return results[0].trim()
        }
        val unstableList = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
        if (!unstableList.isNullOrEmpty() && unstableList[0].isNotBlank()) {
            return unstableList[0].trim()
        }
        val unstableStr = bundle.getCharSequence("android.speech.extra.UNSTABLE_TEXT")?.toString()
        if (!unstableStr.isNullOrBlank()) {
            return unstableStr.trim()
        }
        val charSeqs = bundle.getCharSequenceArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!charSeqs.isNullOrEmpty() && charSeqs[0].isNotBlank()) {
            return charSeqs[0].toString().trim()
        }
        val rawResults = bundle.getStringArrayList("results_recognition")
        if (!rawResults.isNullOrEmpty() && rawResults[0].isNotBlank()) {
            return rawResults[0].trim()
        }
        return null
    }

    private fun findRecognitionServiceComponent(): ComponentName? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (resolveInfos.isNotEmpty()) {
            val preferredPackages = listOf(
                "com.google.android.tts",
                "com.google.android.googlequicksearchbox"
            )
            for (pkg in preferredPackages) {
                val match = resolveInfos.firstOrNull { it.serviceInfo.packageName.equals(pkg, ignoreCase = true) }
                if (match != null) {
                    return ComponentName(match.serviceInfo.packageName, match.serviceInfo.name)
                }
            }
        }
        return null
    }

    private fun getOrCreateSpeechRecognizer(): SpeechRecognizer? {
        val existing = speechRecognizer
        if (existing != null) {
            return existing
        }
        val comp = findRecognitionServiceComponent()
        val recognizer = try {
            if (comp != null) {
                SpeechRecognizer.createSpeechRecognizer(this, comp)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        } catch (_: Exception) {
            null
        }
        if (recognizer != null) {
            setupRecognitionListener(recognizer)
            speechRecognizer = recognizer
        }
        return recognizer
    }

    private fun handleVoiceCommand(cmd: VoiceCommand) {
        val ic = currentInputConnection ?: return
        when (cmd) {
            VoiceCommand.DELETE_THAT -> {
                val len = currentVoiceSpokenText.length
                if (len > 0) {
                    ic.deleteSurroundingText(len, 0)
                    currentVoiceSpokenText = ""
                } else {
                    handleBackspaceWord()
                }
            }
            VoiceCommand.DELETE_WORD -> {
                handleBackspaceWord()
            }
            VoiceCommand.CLEAR_ALL -> {
                ic.deleteSurroundingText(4000, 4000)
                currentWord.clear()
            }
            VoiceCommand.UNDO -> {
                handleBackspace()
            }
        }
    }

    private fun setupRecognitionListener(recognizer: SpeechRecognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListeningVoice = true
                hasCommittedVoiceResults = false
                isVoiceComposing = false
                currentVoiceSpokenText = ""
                val ic = currentInputConnection
                if (ic != null) {
                    val before = ic.getTextBeforeCursor(1, 0)
                    voiceNeedsLeadingSpace = !before.isNullOrEmpty() && !before.last().isWhitespace() && before.last() != '\n'
                } else {
                    voiceNeedsLeadingSpace = false
                }
                updateToolbarState()
                showVoiceListeningUI("Speak now...")
            }

            override fun onBeginningOfSpeech() {
                if (currentVoiceSpokenText.isEmpty()) {
                    showVoiceListeningUI("Listening...")
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > 0f) {
                    val micIv = suggestionStrip.findViewWithTag<ImageView>("voice_mic_icon")
                    val scale = 1.0f + (rmsdB / 14f).coerceIn(0f, 0.35f)
                    micIv?.scaleX = scale
                    micIv?.scaleY = scale
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                val micIv = suggestionStrip.findViewWithTag<ImageView>("voice_mic_icon")
                micIv?.scaleX = 1.0f
                micIv?.scaleY = 1.0f
                if (currentVoiceSpokenText.isNotEmpty()) {
                    showVoiceListeningUI(currentVoiceSpokenText)
                } else {
                    showVoiceListeningUI("Processing...")
                }
            }

            override fun onError(error: Int) {
                val micIv = suggestionStrip.findViewWithTag<ImageView>("voice_mic_icon")
                micIv?.scaleX = 1.0f
                micIv?.scaleY = 1.0f

                if (hasCommittedVoiceResults || !isListeningVoice) {
                    return
                }

                val spoken = currentVoiceSpokenText.trim()
                if (spoken.isNotEmpty()) {
                    val ic = currentInputConnection
                    if (ic != null) {
                        val textToCommit = if (voiceNeedsLeadingSpace) " $spoken " else "$spoken "
                        ic.setComposingText(textToCommit, 1)
                        ic.finishComposingText()
                    }
                    isVoiceComposing = false
                    voiceNeedsLeadingSpace = false
                    isListeningVoice = false
                    hasCommittedVoiceResults = true
                    updateToolbarState()
                    showVoiceListeningUI(spoken)
                    try { speechRecognizer?.cancel() } catch (_: Exception) {}
                    handler.postDelayed({
                        if (!isListeningVoice) updateSuggestions()
                    }, 1200L)
                    return
                }

                if (isVoiceComposing) {
                    currentInputConnection?.finishComposingText()
                    isVoiceComposing = false
                }
                voiceNeedsLeadingSpace = false
                isListeningVoice = false
                currentVoiceSpokenText = ""
                updateToolbarState()

                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && !voiceRetriedDefaultLang) {
                    voiceRetriedDefaultLang = true
                    try { speechRecognizer?.destroy() } catch (_: Exception) {}
                    speechRecognizer = null
                    handler.post {
                        startSystemVoiceRecognition(fallbackToDefaultLang = true)
                    }
                    return
                }

                if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT) {
                    try { speechRecognizer?.destroy() } catch (_: Exception) {}
                    speechRecognizer = null
                } else {
                    try { speechRecognizer?.cancel() } catch (_: Exception) {}
                }

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    promptVoicePermission()
                    return
                }

                if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                    showVoiceListeningUI("Ready, tap to speak", isError = false)
                    handler.postDelayed({
                        if (!isListeningVoice) updateSuggestions()
                    }, 800L)
                    return
                }

                val errMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network required"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy, tap again"
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Disconnected, tap again"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable offline"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                    else -> "Recognition error"
                }
                showVoiceListeningUI(errMsg, isError = true)
                handler.postDelayed({
                    if (!isListeningVoice) updateSuggestions()
                }, 2000)
            }

            override fun onResults(results: Bundle?) {
                val micIv = suggestionStrip.findViewWithTag<ImageView>("voice_mic_icon")
                micIv?.scaleX = 1.0f
                micIv?.scaleY = 1.0f
                val rawRecognized = extractSpeechText(results) ?: currentVoiceSpokenText
                val ic = currentInputConnection
                if (rawRecognized.isNotEmpty()) {
                    val agentic = AgenticVoiceCommander.process(rawRecognized)
                    if (agentic.isCommandOnly && agentic.command != null) {
                        handleVoiceCommand(agentic.command)
                        showVoiceListeningUI("Command: ${agentic.command.name.replace("_", " ").lowercase()}")
                    } else {
                        val recognized = agentic.processedText
                        currentVoiceSpokenText = recognized
                        showVoiceListeningUI(recognized)
                        if (ic != null && recognized.isNotEmpty()) {
                            val textToCommit = if (voiceNeedsLeadingSpace) " $recognized " else "$recognized "
                            ic.setComposingText(textToCommit, 1)
                            ic.finishComposingText()
                        }
                    }
                } else {
                    ic?.finishComposingText()
                }
                isVoiceComposing = false
                voiceNeedsLeadingSpace = false
                isListeningVoice = false
                hasCommittedVoiceResults = true
                updateToolbarState()
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {}
                handler.postDelayed({
                    if (!isListeningVoice) {
                        updateSuggestions()
                    }
                }, 1200L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val rawPartial = extractSpeechText(partialResults)
                if (!rawPartial.isNullOrEmpty()) {
                    val agentic = AgenticVoiceCommander.process(rawPartial)
                    if (agentic.isCommandOnly && agentic.command != null) {
                        showVoiceListeningUI("Command: ${agentic.command.name.replace("_", " ").lowercase()}")
                    } else {
                        val partial = agentic.processedText
                        currentVoiceSpokenText = partial
                        showVoiceListeningUI(partial)
                        val ic = currentInputConnection
                        if (ic != null) {
                            val textToCompose = if (voiceNeedsLeadingSpace) " $partial" else partial
                            ic.setComposingText(textToCompose, 1)
                            isVoiceComposing = true
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startSystemVoiceRecognition(fallbackToDefaultLang: Boolean = false) {
        if (isListeningVoice && !fallbackToDefaultLang) {
            stopVoiceRecognition()
            return
        }

        try {
            hasCommittedVoiceResults = false
            currentVoiceSpokenText = ""
            if (!fallbackToDefaultLang) {
                voiceRetriedDefaultLang = false
            }

            val recognizer = getOrCreateSpeechRecognizer()
            if (recognizer == null) {
                showGoogleSpeechServiceRequiredUI()
                return
            }

            val langTag = if (fallbackToDefaultLang) {
                java.util.Locale.getDefault().language
            } else {
                when (currentLayoutName.lowercase()) {
                    "hi", "hindi" -> "hi-IN"
                    "es", "spanish" -> "es-ES"
                    "fr", "french" -> "fr-FR"
                    "de", "german" -> "de-DE"
                    "ru", "russian" -> "ru-RU"
                    "ja", "japanese" -> "ja-JP"
                    "zh", "chinese" -> "zh-CN"
                    "ar", "arabic" -> "ar-SA"
                    "pt", "portuguese" -> "pt-BR"
                    "it", "italian" -> "it-IT"
                    "ko", "korean" -> "ko-KR"
                    else -> java.util.Locale.getDefault().toLanguageTag()
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                if (langTag.isNotEmpty() && !langTag.equals("und", ignoreCase = true)) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                }
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            try {
                recognizer.cancel()
                recognizer.startListening(intent)
            } catch (_: Exception) {
                try { recognizer.destroy() } catch (_: Exception) {}
                speechRecognizer = null
                val fresh = getOrCreateSpeechRecognizer()
                if (fresh != null) {
                    fresh.startListening(intent)
                } else {
                    showGoogleSpeechServiceRequiredUI()
                    return
                }
            }

            isListeningVoice = true
            updateToolbarState()
            showVoiceListeningUI("Speak now...")
        } catch (_: Exception) {
            isListeningVoice = false
            updateToolbarState()
            showVoiceListeningUI("Could not start voice recognition", isError = true)
            handler.postDelayed({ if (!isListeningVoice) updateSuggestions() }, 2000)
        }
    }

    private fun stopVoiceRecognition(refreshUi: Boolean = true) {
        if (isVoiceComposing) {
            currentInputConnection?.finishComposingText()
            isVoiceComposing = false
        }
        voiceNeedsLeadingSpace = false
        isListeningVoice = false
        hasCommittedVoiceResults = false
        currentVoiceSpokenText = ""
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        updateToolbarState()
        if (refreshUi) {
            refreshSuggestionsNow()
        }
    }

    private fun destroyVoiceRecognition() {
        stopVoiceRecognition(refreshUi = false)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun feedback(view: View? = null) {
        showFloatControlsTemporarily()
        val target = view ?: if (::rootLayout.isInitialized) rootLayout else null
        if (hapticEnabled && target != null) {
            Haptics.buzz(this, hapticPressure, target)
        }
        if (soundEnabled && sounds != null) {
            sounds?.play(soundTone, (target as? TextView)?.text, soundVolume)
        }
    }

    private fun feedback(service: KeyboardService) {
        feedback(if (::rootLayout.isInitialized) rootLayout else null)
    }

    private fun buzz() {
        if (hapticEnabled) {
            Haptics.buzz(this, hapticPressure, rootLayout)
        }
    }

    private fun bindKeyRepeat(view: View, action: () -> Unit) {
        var repeater: Runnable? = null
        fun stop() {
            repeater?.let { handler.removeCallbacks(it) }
            repeater = null
            view.isPressed = false
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    flushPendingRollover()
                    v.isPressed = true
                    feedback(v)
                    action()
                    repeater = object : Runnable {
                        override fun run() {
                            action()
                            feedback(v)
                            handler.postDelayed(this, REPEAT_MS)
                        }
                    }
                    handler.postDelayed(repeater!!, REPEAT_DELAY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
            }
            true
        }
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    class KeyboardRootLayout(context: Context) : LinearLayout(context) {
        var accentColor = Color.parseColor("#7C9CFF")
        var onTouchDownListener: (() -> Unit)? = null
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private class TimedPoint(val x: Float, val y: Float, val time: Long)
        private val trailPoints = mutableListOf<TimedPoint>()

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                onTouchDownListener?.invoke()
            }
            val handled = super.dispatchTouchEvent(ev)
            if (handled) return true
            if (childCount > 0) {
                val child = getChildAt(0)
                val pad = 48f * resources.displayMetrics.density
                val cx = ev.x - child.left
                val cy = ev.y - child.top
                if (KeyboardService.isResizingCorner || KeyboardService.isMovingFloating || (cx in -pad..(child.width + pad) && cy in -pad..(child.height + pad))) {
                    val childEv = MotionEvent.obtain(ev)
                    childEv.offsetLocation(-child.left.toFloat(), -child.top.toFloat())
                    val childHandled = child.dispatchTouchEvent(childEv)
                    childEv.recycle()
                    if (childHandled) return true
                }
            }
            return false
        }

        fun addTrailPoint(x: Float, y: Float) {
            trailPoints.add(TimedPoint(x, y, SystemClock.uptimeMillis()))
            postInvalidateOnAnimation()
        }

        fun clearTrail() {
            trailPoints.clear()
            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (trailPoints.size < 2) return

            val now = SystemClock.uptimeMillis()
            trailPoints.removeAll { now - it.time > 320 }
            if (trailPoints.size < 2) return

            val density = resources.displayMetrics.density
            val count = trailPoints.size
            for (i in 1 until count) {
                val p0 = trailPoints[i - 1]
                val p1 = trailPoints[i]
                val age = (now - p1.time).toFloat() / 320f
                val alpha = ((1f - age.coerceIn(0f, 1f)) * 230).toInt().coerceIn(0, 255)
                val progress = i.toFloat() / count.toFloat()
                val strokeW = (2.0f + progress * 5.5f) * density

                trailPaint.color = accentColor
                trailPaint.alpha = alpha
                trailPaint.strokeWidth = strokeW
                canvas.drawLine(p0.x, p0.y, p1.x, p1.y, trailPaint)
            }
            postInvalidateOnAnimation()
        }
    }
}
