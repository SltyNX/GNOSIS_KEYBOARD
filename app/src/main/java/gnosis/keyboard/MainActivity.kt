package gnosis.keyboard

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import gnosis.keyboard.engine.EngineFactory
import gnosis.keyboard.engine.language.GnosisLanguageManager
import gnosis.keyboard.stats.TypingStatisticsRepository
import gnosis.keyboard.ui.ConceptComponents
import gnosis.keyboard.ui.ConceptDesignTokens
import gnosis.keyboard.ui.ConceptScreens
import gnosis.keyboard.ui.ConceptThemeTokens
import gnosis.keyboard.ui.SwipeableHomePager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
        const val TARGET_VOICE_MODELS = "voice_models"
        const val TARGET_LANGUAGES = "languages"
        const val TARGET_STATS = "stats"
        const val TARGET_TYPING = "typing"
        const val TARGET_APPEARANCE = "appearance"
        const val TARGET_THEMES = "themes"
        const val TARGET_SEARCH = "search"
        const val TARGET_ONBOARDING = "onboarding"
    }

    private lateinit var statsRepo: TypingStatisticsRepository
    private lateinit var tokens: ConceptThemeTokens
    private lateinit var screens: ConceptScreens

    lateinit var homePager: SwipeableHomePager
    lateinit var homeScrollView: ScrollView
    lateinit var homeContentFrame: FrameLayout
    lateinit var statsScrollView: ScrollView
    lateinit var statsContentFrame: FrameLayout
    lateinit var subScreenScrollContainer: ScrollView
    lateinit var subScreenContentFrame: FrameLayout

    private lateinit var rootLayout: FrameLayout
    private lateinit var scrollContainer: ScrollView
    private lateinit var contentFrame: FrameLayout
    private lateinit var topBarContainer: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var topBarTitle: TextView
    private lateinit var backBtn: ImageView
    private lateinit var searchActionBtn: ImageView
    private lateinit var livePreviewBtn: ImageView
    private lateinit var livePreviewDrawer: LinearLayout
    private lateinit var livePreviewInput: EditText
    private lateinit var bottomNavContainer: FrameLayout
    private var floatingBottomNav: ConceptComponents.FloatingBottomNavView? = null

    private data class NavEntry(
        val title: String,
        val destinationTag: String?,
        val view: View? = null
    )

    private var activeToast: Toast? = null
    private var currentTab = "home"
    private var currentScreenTitle = "Gnosis Keyboard"
    private var currentDestinationTag: String? = null
    private val navStack = ArrayDeque<NavEntry>()
    private var previewSounds: KeySounds? = null

    private var pendingExportJson: String? = null

    val exportThemeLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri == null || json == null) return@registerForActivityResult
        activityScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    showToast("Theme exported successfully!")
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to save theme file")
                }
            }
        }
    }

    val importThemeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        activityScope.launch(Dispatchers.IO) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inp ->
                    inp.bufferedReader(Charsets.UTF_8).readText()
                }
                withContext(Dispatchers.Main) {
                    if (!json.isNullOrBlank()) {
                        val imported = Skins.importThemeJson(this@MainActivity, json)
                        if (imported) {
                            showToast("Theme imported and applied!")
                            notifySettingsChanged(restart = true)
                            reloadTheme()
                        } else {
                            showToast("Invalid Gnosis theme file", true)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to read theme file", true)
                }
            }
        }
    }

    val pickBackground = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                showCropDialog(uri, source = "gallery") {
                    notifySettingsChanged(restart = true)
                }
            }
        }
    }

    val pickProfilePhoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val file = File(filesDir, "user_avatar.jpg")
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                showToast("Profile photo updated")
                if (currentTab == "home" && navStack.isEmpty() && subScreenScrollContainer.visibility != View.VISIBLE) {
                    openHomeScreen()
                }
            } catch (_: Exception) {
                showToast("Failed to save profile photo", true)
            }
        }
    }

    val voiceAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showToast("Microphone permission granted")
        } else {
            showToast("Microphone permission required for voice typing", true)
        }
    }

    val screenshotPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showToast("Media permission granted for screenshots")
        } else {
            showToast("Media permission required to capture screenshots", true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tokens = ConceptDesignTokens.resolve(this)
        applyEdgeToEdgeMode()

        super.onCreate(savedInstanceState)

        statsRepo = TypingStatisticsRepository.getInstance(this)
        previewSounds = KeySounds(this)
        initScreens()

        setLauncherIconHidden(Skins.prefs(this).getBoolean(Skins.KEY_HIDE_LAUNCHER_ICON, false))

        buildUiTree()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (livePreviewDrawer.visibility == View.VISIBLE) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(livePreviewInput.windowToken, 0)
                    livePreviewInput.clearFocus()
                    toggleLivePreviewDrawer(false)
                    return
                }
                if (popScreen()) {
                    return
                }
                if (currentTab != "home" || (::homePager.isInitialized && homePager.currentPage != 0)) {
                    if (::homePager.isInitialized) {
                        homePager.smoothScrollToPage(0)
                    } else {
                        openHomeScreen()
                    }
                    return
                }
                finish()
            }
        })

        openHomeScreen()
        handleIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (livePreviewDrawer.visibility == View.VISIBLE) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(livePreviewInput.windowToken, 0)
            livePreviewInput.clearFocus()
            toggleLivePreviewDrawer(false)
            return
        }
        if (popScreen()) {
            return
        }
        if (currentTab != "home" || (::homePager.isInitialized && homePager.currentPage != 0)) {
            if (::homePager.isInitialized) {
                homePager.smoothScrollToPage(0)
            } else {
                openHomeScreen()
            }
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (navStack.isEmpty() && subScreenScrollContainer.visibility != View.VISIBLE) {
            when (currentTab) {
                "stats" -> openStatsScreen()
                else -> openHomeScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && navStack.isEmpty() && subScreenScrollContainer.visibility != View.VISIBLE && currentTab == "home") {
            openHomeScreen()
        }
    }

    override fun onDestroy() {
        previewSounds?.release()
        previewSounds = null
        activityScope.cancel()
        super.onDestroy()
    }

    fun playSoundPreview(tone: String? = null, volume: Float? = null) {
        val p = Skins.prefs(this)
        val t = tone ?: p.getString(Skins.KEY_SOUND_TONE, "default") ?: "default"
        val vol = volume ?: (p.getInt(Skins.KEY_SOUND_VOLUME, 80) / 100f).coerceIn(0.1f, 1.0f)
        try {
            if (previewSounds == null) {
                previewSounds = KeySounds(this)
            }
            previewSounds?.play(t, "a", vol)
        } catch (_: Exception) {}
    }

    private fun applyEdgeToEdgeMode() {
        if (!tokens.isLight) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.parseColor("#1B2A4A")),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.parseColor("#1B2A4A"))
            )
        }
        window.setBackgroundDrawable(ColorDrawable(tokens.bg))
    }

    private fun initScreens() {
        tokens = ConceptDesignTokens.resolve(this)
        val density = resources.displayMetrics.density
        screens = ConceptScreens(
            activity = this,
            tokens = tokens,
            density = density,
            statsRepo = statsRepo,
            navigateTo = { view, title -> pushScreen(view, title) },
            navigateBack = { popScreen() },
            navigateToRoot = { resetToRoot() },
            onThemeChanged = { reloadTheme() }
        )
    }

    private fun buildUiTree() {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(tokens.bg)
        }

        subScreenScrollContainer = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            visibility = View.GONE
        }
        subScreenContentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        subScreenScrollContainer.addView(subScreenContentFrame)
        scrollContainer = subScreenScrollContainer
        contentFrame = subScreenContentFrame

        homePager = SwipeableHomePager(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
        }

        homeScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        homeContentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        homeScrollView.addView(homeContentFrame)
        homePager.addView(homeScrollView)

        statsScrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        statsContentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        statsScrollView.addView(statsContentFrame)
        homePager.addView(statsScrollView)

        homePager.onPageScrollProgress = { progress ->
            floatingBottomNav?.setProgress(progress)
        }

        homePager.onPageChanged = { pageIdx ->
            if (pageIdx == 1) {
                currentTab = "stats"
                currentScreenTitle = "Typing Statistics"
                topBarTitle.text = currentScreenTitle
                refreshStatsScreen()
            } else {
                currentTab = "home"
                currentScreenTitle = "Gnosis Keyboard"
                topBarTitle.text = currentScreenTitle
            }
            if (floatingBottomNav != null) {
                floatingBottomNav?.setSelectedTab(pageIdx, animateBounce = true)
            } else {
                renderBottomNav(pageIdx)
            }
        }

        rootLayout.addView(homePager)
        rootLayout.addView(subScreenScrollContainer)

        topBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
            elevation = dp(3f).toFloat()
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(tokens.topBarBg)
        }

        backBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_chevron_left)?.mutate()
            d?.setTint(tokens.textPrimary)
            setImageDrawable(d)
            val pad = dp(8f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
                marginEnd = dp(6f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            contentDescription = "Back"
            visibility = View.GONE
            ConceptComponents.applyTactileFeedback(this, cornerRadiusDp = 20f, pressScale = 0.92f, tokens = tokens)
            setOnClickListener {
                popScreen()
            }
        }
        topBar.addView(backBtn)

        topBarTitle = TextView(this).apply {
            text = "Gnosis Keyboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(topBarTitle)

        livePreviewBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_keyboard)?.mutate()
            d?.setTint(tokens.accent)
            setImageDrawable(d)
            val pad = dp(8f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
                marginEnd = dp(6f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            visibility = View.GONE
            contentDescription = "Live Preview"
            ConceptComponents.applyTactileFeedback(this, cornerRadiusDp = 20f, pressScale = 0.90f, tokens = tokens)
            setOnClickListener {
                toggleLivePreviewDrawer()
            }
        }
        topBar.addView(livePreviewBtn)

        searchActionBtn = ImageView(this).apply {
            val d = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_search)?.mutate()
            d?.setTint(tokens.textSecondary)
            setImageDrawable(d)
            val pad = dp(8f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            ConceptComponents.applyTactileFeedback(this, cornerRadiusDp = 20f, pressScale = 0.90f, tokens = tokens)
            setOnClickListener {
                pushScreen(screens.buildSearchSettingsScreen(), "Search Settings", "Search Settings")
            }
        }
        topBar.addView(searchActionBtn)
        topBarContainer.addView(topBar)

        livePreviewDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                setStroke((tokens.strokeWidthDp * density).toInt().coerceAtLeast(1), tokens.cardBorder)
            }
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        livePreviewInput = EditText(this).apply {
            hint = "Type here to test live changes..."
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
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
        }
        livePreviewDrawer.addView(livePreviewInput)
        topBarContainer.addView(livePreviewDrawer)

        rootLayout.addView(topBarContainer)

        bottomNavContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        }
        rootLayout.addView(bottomNavContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            topBar.setPadding(dp(16f), statusBar.top + dp(6f), dp(16f), dp(8f))
            val topBarTotalH = statusBar.top + dp(54f)
            val bottomPad = navBar.bottom + dp(84f)
            homeScrollView.setPadding(0, topBarTotalH, 0, bottomPad)
            statsScrollView.setPadding(0, topBarTotalH, 0, bottomPad)
            subScreenScrollContainer.setPadding(0, topBarTotalH, 0, bottomPad)
            bottomNavContainer.setPadding(0, 0, 0, navBar.bottom + dp(8f))

            insets
        }

        setContentView(rootLayout)
        renderBottomNav()
    }

    private fun renderBottomNav(activeTab: Int = if (currentTab == "stats") 1 else 0) {
        if (floatingBottomNav == null) {
            bottomNavContainer.removeAllViews()
            val nav = ConceptComponents.createFloatingBottomNav(
                context = this,
                currentTab = activeTab,
                tokens = tokens,
                density = resources.displayMetrics.density,
                onTabSelect = { idx ->
                    if (navStack.isNotEmpty() || subScreenScrollContainer.visibility == View.VISIBLE) {
                        resetToRoot(if (idx == 1) "stats" else "home")
                    } else {
                        if (idx == 0) {
                            if (homePager.currentPage == 0) {
                                homeScrollView.smoothScrollTo(0, 0)
                            } else {
                                homePager.smoothScrollToPage(0)
                            }
                        } else {
                            if (homePager.currentPage == 1) {
                                statsScrollView.smoothScrollTo(0, 0)
                            } else {
                                homePager.smoothScrollToPage(1)
                            }
                        }
                    }
                }
            )
            floatingBottomNav = nav
            bottomNavContainer.addView(nav)
        } else {
            floatingBottomNav?.setSelectedTab(activeTab, animateBounce = true)
        }
    }

    fun toggleLivePreviewDrawer(show: Boolean? = null) {
        val isVis = livePreviewDrawer.visibility == View.VISIBLE
        val shouldShow = show ?: !isVis
        val density = resources.displayMetrics.density
        if (shouldShow) {
            livePreviewDrawer.visibility = View.VISIBLE
            livePreviewDrawer.alpha = 0f
            livePreviewDrawer.translationY = -12f * density
            livePreviewDrawer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
            livePreviewBtn.animate().rotationBy(360f).scaleX(1.12f).scaleY(1.12f).setDuration(200L).withEndAction {
                livePreviewBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100L).start()
            }.start()
            livePreviewInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(livePreviewInput, 0)
        } else if (isVis) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(livePreviewInput.windowToken, 0)
            livePreviewInput.clearFocus()
            livePreviewDrawer.animate()
                .alpha(0f)
                .translationY(-12f * density)
                .setDuration(170L)
                .withEndAction {
                    livePreviewDrawer.visibility = View.GONE
                }
                .start()
        }
    }

    fun switchTab(tab: String) {
        currentTab = tab
        navStack.clear()
        currentDestinationTag = null
        backBtn.visibility = View.GONE
        searchActionBtn.visibility = View.VISIBLE
        livePreviewBtn.visibility = View.GONE
        livePreviewDrawer.visibility = View.GONE
        bottomNavContainer.visibility = View.VISIBLE

        when (tab) {
            "stats" -> openStatsScreen()
            else -> openHomeScreen()
        }
    }

    fun openHomeScreen() {
        currentTab = "home"
        currentDestinationTag = null
        currentScreenTitle = "Gnosis Keyboard"
        topBarTitle.text = currentScreenTitle
        backBtn.visibility = View.GONE
        searchActionBtn.visibility = View.VISIBLE
        livePreviewBtn.visibility = View.GONE
        livePreviewDrawer.visibility = View.GONE
        bottomNavContainer.visibility = View.VISIBLE

        subScreenScrollContainer.visibility = View.GONE
        homePager.visibility = View.VISIBLE

        homeContentFrame.removeAllViews()
        val homeView = screens.buildHomeScreen(
            onOpenTyping = { pushScreen(screens.buildTypingScreen(), "Typing & Suggestions", "Typing & Suggestions") },
            onOpenAppearance = {
                pushScreen(
                    screens.buildAppearanceScreen(
                        onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
                    ),
                    "Appearance",
                    "Appearance"
                )
            },
            onOpenLanguage = {
                pushScreen(
                    screens.buildLanguageScreen(
                        onOpenDictionary = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") }
                    ),
                    "Language & Dictionary",
                    "Language & Dictionary"
                )
            },
            onOpenPersonalization = {
                pushScreen(
                    screens.buildPersonalizationScreen(
                        onOpenShortcuts = { pushScreen(screens.buildShortcutsScreen(), "Text Shortcuts", "Text Shortcuts") },
                        onOpenLearnedData = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") },
                        onOpenFavoriteWords = { pushScreen(screens.buildFavoriteWordsScreen(), "Favorite Words", "Favorite Words") }
                    ),
                    "Personalization",
                    "Personalization"
                )
            },
            onOpenAdvanced = { pushScreen(screens.buildAdvancedScreen(), "Advanced Settings", "Advanced Settings") },
            onOpenQuickSettings = {
                pushScreen(
                    screens.buildAppearanceScreen(
                        onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
                    ),
                    "Appearance",
                    "Appearance"
                )
            }
        )
        homeContentFrame.addView(homeView)

        if (statsContentFrame.childCount == 0) {
            statsContentFrame.addView(screens.buildTypingStatisticsScreen())
        }

        homePager.setCurrentPageImmediate(0)
        renderBottomNav(0)
    }

    fun openStatsScreen() {
        currentTab = "stats"
        currentDestinationTag = null
        currentScreenTitle = "Typing Statistics"
        topBarTitle.text = currentScreenTitle
        backBtn.visibility = View.GONE
        searchActionBtn.visibility = View.VISIBLE
        livePreviewBtn.visibility = View.GONE
        livePreviewDrawer.visibility = View.GONE
        bottomNavContainer.visibility = View.VISIBLE

        subScreenScrollContainer.visibility = View.GONE
        homePager.visibility = View.VISIBLE

        if (homeContentFrame.childCount == 0) {
            val homeView = screens.buildHomeScreen(
                onOpenTyping = { pushScreen(screens.buildTypingScreen(), "Typing & Suggestions", "Typing & Suggestions") },
                onOpenAppearance = {
                    pushScreen(
                        screens.buildAppearanceScreen(
                            onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
                        ),
                        "Appearance",
                        "Appearance"
                    )
                },
                onOpenLanguage = {
                    pushScreen(
                        screens.buildLanguageScreen(
                            onOpenDictionary = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") }
                        ),
                        "Language & Dictionary",
                        "Language & Dictionary"
                    )
                },
                onOpenPersonalization = {
                    pushScreen(
                        screens.buildPersonalizationScreen(
                            onOpenShortcuts = { pushScreen(screens.buildShortcutsScreen(), "Text Shortcuts", "Text Shortcuts") },
                            onOpenLearnedData = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") },
                            onOpenFavoriteWords = { pushScreen(screens.buildFavoriteWordsScreen(), "Favorite Words", "Favorite Words") }
                        ),
                        "Personalization",
                        "Personalization"
                    )
                },
                onOpenAdvanced = { pushScreen(screens.buildAdvancedScreen(), "Advanced Settings", "Advanced Settings") },
                onOpenQuickSettings = {
                    pushScreen(
                        screens.buildAppearanceScreen(
                            onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
                        ),
                        "Appearance",
                        "Appearance"
                    )
                }
            )
            homeContentFrame.addView(homeView)
        }

        refreshStatsScreen()
        homePager.setCurrentPageImmediate(1)
        renderBottomNav(1)
    }

    private fun refreshStatsScreen() {
        statsContentFrame.removeAllViews()
        statsContentFrame.addView(screens.buildTypingStatisticsScreen())
    }

    fun pushScreen(view: View, title: String, destinationTag: String = title) {
        if (subScreenScrollContainer.visibility != View.VISIBLE || navStack.isEmpty()) {
            val rootTitle = if (currentTab == "stats" || (::homePager.isInitialized && homePager.currentPage == 1)) "Typing Statistics" else "Gnosis Keyboard"
            navStack.push(NavEntry(title = rootTitle, destinationTag = null, view = null))
        } else {
            val currentChild = if (subScreenContentFrame.childCount > 0) subScreenContentFrame.getChildAt(0) else null
            navStack.push(NavEntry(title = currentScreenTitle, destinationTag = currentDestinationTag, view = currentChild))
        }

        currentScreenTitle = title
        currentDestinationTag = destinationTag

        topBarTitle.text = title
        topBarTitle.alpha = 0f
        topBarTitle.translationY = -resources.displayMetrics.density * 8f
        topBarTitle.animate().alpha(1f).translationY(0f).setDuration(220).start()

        backBtn.visibility = View.VISIBLE
        searchActionBtn.visibility = View.GONE
        livePreviewBtn.visibility = View.VISIBLE
        livePreviewDrawer.visibility = View.GONE
        bottomNavContainer.animate()
            .translationY(resources.displayMetrics.density * 50f)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                bottomNavContainer.visibility = View.GONE
            }
            .start()

        homePager.visibility = View.GONE
        subScreenScrollContainer.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        view.alpha = 0f
        view.translationX = 24f * density

        (view.parent as? ViewGroup)?.removeView(view)
        subScreenContentFrame.removeAllViews()
        subScreenContentFrame.addView(view)
        subScreenScrollContainer.scrollTo(0, 0)

        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun renderScreenForTag(tag: String?): View? {
        return when (tag) {
            "Appearance" -> screens.buildAppearanceScreen(
                onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
            )
            "Typing & Suggestions" -> screens.buildTypingScreen()
            "Language & Dictionary" -> screens.buildLanguageScreen(
                onOpenDictionary = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") }
            )
            "Personalization" -> screens.buildPersonalizationScreen(
                onOpenShortcuts = { pushScreen(screens.buildShortcutsScreen(), "Text Shortcuts", "Text Shortcuts") },
                onOpenLearnedData = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") },
                onOpenFavoriteWords = { pushScreen(screens.buildFavoriteWordsScreen(), "Favorite Words", "Favorite Words") }
            )
            "Advanced Settings" -> screens.buildAdvancedScreen()
            "Theme Gallery" -> screens.buildThemeGridScreen()
            "Search Settings" -> screens.buildSearchSettingsScreen()
            "Text Shortcuts" -> screens.buildShortcutsScreen()
            "Learned Data" -> screens.buildLearnedDataScreen()
            "Favorite Words" -> screens.buildFavoriteWordsScreen()
            "About & Privacy" -> screens.buildAboutPrivacyScreen()
            else -> null
        }
    }

    fun popScreen(): Boolean {
        if (navStack.isEmpty()) {
            if (subScreenScrollContainer.visibility == View.VISIBLE) {
                resetToRoot(if (currentTab == "stats") "stats" else "home")
                return true
            }
            return false
        }

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(livePreviewInput.windowToken, 0)
        livePreviewInput.clearFocus()
        toggleLivePreviewDrawer(false)

        val prevEntry = navStack.pop()
        val isRoot = navStack.isEmpty() || prevEntry.destinationTag == null

        if (isRoot) {
            navStack.clear()
            currentDestinationTag = null
            currentScreenTitle = if (currentTab == "stats" || (::homePager.isInitialized && homePager.currentPage == 1)) "Typing Statistics" else "Gnosis Keyboard"

            topBarTitle.text = currentScreenTitle
            topBarTitle.alpha = 0f
            topBarTitle.translationY = -resources.displayMetrics.density * 8f
            topBarTitle.animate().alpha(1f).translationY(0f).setDuration(220).start()

            backBtn.visibility = View.GONE
            searchActionBtn.visibility = View.VISIBLE
            livePreviewBtn.visibility = View.GONE

            bottomNavContainer.visibility = View.VISIBLE
            bottomNavContainer.translationY = resources.displayMetrics.density * 50f
            bottomNavContainer.alpha = 0f
            bottomNavContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()

            subScreenContentFrame.removeAllViews()
            subScreenScrollContainer.visibility = View.GONE
            homePager.visibility = View.VISIBLE
            if (currentTab == "stats" || (::homePager.isInitialized && homePager.currentPage == 1)) {
                renderBottomNav(1)
            } else {
                renderBottomNav(0)
            }
        } else {
            currentScreenTitle = prevEntry.title
            currentDestinationTag = prevEntry.destinationTag

            topBarTitle.text = prevEntry.title
            topBarTitle.alpha = 0f
            topBarTitle.translationY = -resources.displayMetrics.density * 8f
            topBarTitle.animate().alpha(1f).translationY(0f).setDuration(220).start()

            backBtn.visibility = View.VISIBLE
            searchActionBtn.visibility = View.GONE
            livePreviewBtn.visibility = View.VISIBLE
            bottomNavContainer.visibility = View.GONE

            val freshView = renderScreenForTag(prevEntry.destinationTag ?: prevEntry.title) ?: prevEntry.view
            subScreenContentFrame.removeAllViews()
            if (freshView != null) {
                (freshView.parent as? ViewGroup)?.removeView(freshView)
                val density = resources.displayMetrics.density
                freshView.alpha = 0f
                freshView.translationX = -24f * density
                subScreenContentFrame.addView(freshView)
                subScreenScrollContainer.scrollTo(0, 0)
                freshView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        return true
    }

    fun resetToRoot(tab: String = "home") {
        navStack.clear()
        currentDestinationTag = null
        switchTab(tab)
    }

    fun reloadTheme() {
        tokens = ConceptDesignTokens.resolve(this)
        applyEdgeToEdgeMode()
        rootLayout.setBackgroundColor(tokens.bg)
        topBar.setBackgroundColor(tokens.topBarBg)
        topBarTitle.setTextColor(tokens.textPrimary)
        backBtn.drawable?.mutate()?.setTint(tokens.textPrimary)
        searchActionBtn.drawable?.mutate()?.setTint(tokens.textSecondary)
        livePreviewBtn.drawable?.mutate()?.setTint(tokens.accent)

        val density = resources.displayMetrics.density
        backBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tokens.chipBg)
            setStroke((1f * density).toInt(), tokens.cardBorder)
        }
        searchActionBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tokens.chipBg)
            setStroke((1f * density).toInt(), tokens.cardBorder)
        }
        livePreviewBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tokens.chipBg)
            setStroke((1f * density).toInt(), tokens.cardBorder)
        }
        livePreviewDrawer.background = GradientDrawable().apply {
            setColor(tokens.surface)
            setStroke((tokens.strokeWidthDp * density).toInt().coerceAtLeast(1), tokens.cardBorder)
        }
        livePreviewInput.setTextColor(tokens.textPrimary)
        livePreviewInput.setHintTextColor(tokens.textSecondary)
        livePreviewInput.background = GradientDrawable().apply {
            cornerRadius = (12f * density)
            setColor(tokens.chipBg)
            setStroke((1f * density).toInt(), tokens.cardBorder)
        }

        floatingBottomNav = null
        initScreens()
        renderBottomNav()

        if (navStack.isEmpty() && subScreenScrollContainer.visibility != View.VISIBLE) {
            when (currentTab) {
                "stats" -> openStatsScreen()
                else -> openHomeScreen()
            }
        } else {
            val view = renderScreenForTag(currentDestinationTag)
            if (view != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                subScreenContentFrame.removeAllViews()
                subScreenContentFrame.addView(view)
            } else {
                switchTab("home")
            }
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val target = intent.getStringExtra(EXTRA_NAVIGATE_TO) ?: return false
        when (target) {
            TARGET_LANGUAGES -> {
                pushScreen(
                    screens.buildLanguageScreen(
                        onOpenDictionary = { pushScreen(screens.buildLearnedDataScreen(), "Learned Data", "Learned Data") }
                    ),
                    "Language & Dictionary",
                    "Language & Dictionary"
                )
                return true
            }
            TARGET_STATS -> {
                switchTab("stats")
                return true
            }
            TARGET_TYPING -> {
                pushScreen(screens.buildTypingScreen(), "Typing & Suggestions", "Typing & Suggestions")
                return true
            }
            TARGET_APPEARANCE -> {
                pushScreen(
                    screens.buildAppearanceScreen(
                        onOpenThemeGallery = { pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery") }
                    ),
                    "Appearance",
                    "Appearance"
                )
                return true
            }
            TARGET_THEMES -> {
                pushScreen(screens.buildThemeGridScreen(), "Theme Gallery", "Theme Gallery")
                return true
            }
            TARGET_SEARCH -> {
                pushScreen(screens.buildSearchSettingsScreen(), "Search Settings", "Search Settings")
                return true
            }
            TARGET_ONBOARDING -> {
                pushScreen(
                    screens.buildOnboardingScreen(1) { resetToRoot("home") },
                    "First-Time Setup",
                    "First-Time Setup"
                )
                return true
            }
            TARGET_VOICE_MODELS -> {
                pushScreen(screens.buildAdvancedScreen(), "Advanced Settings", "Advanced Settings")
                return true
            }
        }
        return false
    }

    fun notifySettingsChanged(restart: Boolean = false, lightUpdate: Boolean = !restart) {
        try {
            val intent = Intent(if (restart) Skins.ACTION_RESTART_KEYBOARD else "com.gnosis.keyboard.SETTINGS_UPDATED").apply {
                putExtra("light_update", lightUpdate)
                `package` = packageName
            }
            sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    fun restartKeyboardEngine(showToastMessage: Boolean = true) {
        notifySettingsChanged(restart = true)
        if (showToastMessage) {
            showToast("Keyboard restarted successfully!")
        }
    }

    fun resetToDefault() {
        showConfirmDialog(
            title = "Reset All Settings?",
            msg = "This will restore every keyboard preference, theme, and style to factory default. Your learned words and dictionary will remain safe.",
            confirmLabel = "Reset to Default",
            destructive = true
        ) {
            Skins.prefs(this).edit().clear().apply()
            CustomBackground.remove(this)
            notifySettingsChanged(restart = true)
            showToast("All settings reset to default")
            reloadTheme()
        }
    }

    fun showToast(msg: String, long: Boolean = false) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
        activeToast?.show()
    }

    fun showConfirmDialog(
        title: String,
        msg: String,
        confirmLabel: String = "Confirm",
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(320f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        box.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        })

        box.addView(TextView(this).apply {
            text = msg
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(tokens.textSecondary)
            setPadding(0, dp(8f), 0, dp(20f))
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            setOnClickListener { dialog.dismiss() }
        }

        val confirmBtn = TextView(this).apply {
            text = confirmLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            val fill = if (destructive) Color.parseColor("#EF4444") else tokens.accent
            setTextColor(if (destructive) Color.WHITE else tokens.onAccentText)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(fill)
            }
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(confirmBtn)
        box.addView(btnRow)

        dialog.setContentView(box)
        dialog.show()
    }

        fun showCropDialog() {
        showCropDialog(null, source = "existing") {}
    }

    fun showCropDialog(uri: Uri? = null, source: String = "gallery", onFinish: (() -> Unit)? = null) {
        val d = resources.displayMetrics
        val p = Skins.prefs(this)
        val keyH = p.getInt(Skins.KEY_KEYBOARD_HEIGHT, 52)
        val rows = if (p.getBoolean(Skins.KEY_NUMBER_ROW, false)) 5 else 4
        val bars = if (CustomBackground.extendToBars(this)) 46 else 0
        val heightDp = bars + 10 + rows * (keyH + 8)
        val ratio = (d.widthPixels.toFloat() / d.density) / heightDp.toFloat()

        var bmp = CustomBackground.bitmap(this)
        if (uri != null && source == "gallery") {
            val ok = CustomBackground.import(this, uri)
            if (!ok) {
                showToast("Failed to load background image", true)
                return
            }
            bmp = CustomBackground.bitmap(this)
        }
        if (bmp == null) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }; pickBackground.launch(intent); return

        }

        val currentCrop = CustomBackground.crop(this)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        box.addView(TextView(this).apply {
            text = "Crop Background"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        box.addView(TextView(this).apply {
            text = "Pinch to zoom, drag to position your wallpaper."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        var cropZoom = currentCrop.zoom
        val cropDrawable = CenterCropDrawable(bmp, Color.BLACK, (currentCrop.zoom * 100).toInt())
        val cropView = BackgroundCropView(
            ctx = this,
            bitmap = bmp,
            ratio = ratio,
            drawable = cropDrawable,
            zoom = currentCrop.zoom,
            cx = currentCrop.cx,
            cy = currentCrop.cy
        ) { z ->
            cropZoom = z
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160f)).apply {
                bottomMargin = dp(12f)
            }
        }
        box.addView(cropView)

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12f))
        }
        zoomRow.addView(TextView(this).apply {
            text = "Zoom"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(dp(44f), ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        val zoomBar = SeekBar(this).apply {
            max = 100
            progress = ((currentCrop.zoom - 1f) / (CustomBackground.MAX_ZOOM - 1f) * 100).toInt().coerceIn(0, 100)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            progressTintList = android.content.res.ColorStateList.valueOf(tokens.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(tokens.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val z = 1f + (p / 100f) * (CustomBackground.MAX_ZOOM - 1f)
                        cropView.setZoomFromSlider(z)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        zoomRow.addView(zoomBar)
        box.addView(zoomRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(tokens.textSecondary)
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { dialog.dismiss() }
        }
        val applyBtn = TextView(this).apply {
            text = "Apply Crop"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.onAccentText)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.accent)
            }
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            setOnClickListener {
                CustomBackground.saveCrop(this@MainActivity, cropView.zoom, cropView.cx, cropView.cy)
                notifySettingsChanged(restart = true)
                showToast("Background crop saved")
                dialog.dismiss()
                onFinish?.invoke()
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(applyBtn)
        box.addView(btnRow)

        dialog.setContentView(box)
        dialog.show()
    }

    fun showActiveLayoutSelectorDialog(onChanged: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), dp(480f))
        }

        root.addView(TextView(this).apply {
            text = "Active Keyboard Layout"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        root.addView(TextView(this).apply {
            text = "Select your active physical key arrangement. Only downloaded layouts are listed here."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        val itemsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        itemsScroll.addView(itemsContainer)
        root.addView(itemsScroll)

        fun renderInstalledLayouts() {
            itemsContainer.removeAllViews()
            val curLayout = Skins.prefs(this).getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
            val installed = LayoutExtensionManager.getInstalledLayouts(this)

            for (layout in installed) {
                val isCurrent = curLayout == layout.id
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8f)
                    }
                }

                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val titleCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6f)
                    }
                }
                titleCol.addView(TextView(this).apply {
                    text = layout.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isCurrent) tokens.accent else tokens.textPrimary)
                })
                titleCol.addView(TextView(this).apply {
                    text = layout.language
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(tokens.textSecondary)
                })
                topRow.addView(titleCol)

                val applyBtn = TextView(this).apply {
                    text = if (isCurrent) "Active" else "Apply"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isCurrent) tokens.accent else tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        if (isCurrent) {
                            setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                            setStroke(dp(1f), tokens.accent)
                        } else {
                            setColor(tokens.accent)
                        }
                    }
                    setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
                    if (!isCurrent) {
                        setOnClickListener {
                            Skins.prefs(this@MainActivity).edit().putString(Skins.KEY_LAYOUT, layout.id).apply()
                            notifySettingsChanged(restart = true)
                            showToast("Applied layout: ${layout.name}")
                            onChanged()
                            renderInstalledLayouts()
                        }
                    }
                }
                topRow.addView(applyBtn)

                if (layout.id != "qwerty") {
                    val deleteBtn = FrameLayout(this).apply {
                        val s = dp(30f)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            marginStart = dp(6f)
                        }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.RED, if (tokens.isLight) 25 else 40))
                        }
                        val iv = ImageView(this@MainActivity).apply {
                            val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                                ?: ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
                            d?.setTint(Color.parseColor("#FF5252"))
                            setImageDrawable(d)
                            val p = dp(6f)
                            setPadding(p, p, p, p)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        addView(iv)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            LayoutExtensionManager.deleteLayout(this@MainActivity, layout.id)
                            if (curLayout == layout.id) {
                                Skins.prefs(this@MainActivity).edit().putString(Skins.KEY_LAYOUT, "qwerty").apply()
                            }
                            notifySettingsChanged(restart = true)
                            showToast("Deleted layout: ${layout.name}")
                            onChanged()
                            renderInstalledLayouts()
                        }
                    }
                    topRow.addView(deleteBtn)
                }

                card.addView(topRow)

                val previewKeys = LayoutExtensionManager.getPreviewKeys(layout.id)
                val keyStrip = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6f), 0, dp(2f))
                }
                for (k in previewKeys) {
                    val keyCap = TextView(this).apply {
                        text = k
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                            setColor(tokens.surface)
                            setStroke(dp(0.7f), tokens.cardBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dp(20f), 1f).apply {
                            marginStart = dp(1f)
                            marginEnd = dp(1f)
                        }
                    }
                    keyStrip.addView(keyCap)
                }
                card.addView(keyStrip)
                itemsContainer.addView(card)
            }
        }

        renderInstalledLayouts()

        val browseMoreBtn = TextView(this).apply {
            text = "Browse & Download More Layouts"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.accent)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.accent)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42f)).apply {
                topMargin = dp(10f)
            }
            setOnClickListener {
                dialog.dismiss()
                showLayoutDownloaderDialog(onChanged)
            }
        }
        root.addView(browseMoreBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    fun showLanguageSelectorDialog(onChanged: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), dp(520f))
        }

        root.addView(TextView(this).apply {
            text = "Keyboard Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        root.addView(TextView(this).apply {
            text = "Select your active language. Neural predictions, autocorrect, and grammar check will automatically adapt."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val isSystemMode = GnosisLanguageManager.isSystemLanguageMode(this)
        val activeLang = GnosisLanguageManager.resolveEffectiveLanguage(this)

        val sysCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(12f)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), if (isSystemMode) tokens.accent else tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                GnosisLanguageManager.setLanguageMode(this@MainActivity, Skins.MODE_LANG_SYSTEM)
                EngineFactory.invalidateCache()
                notifySettingsChanged(restart = true)
                showToast("Language: Follow System (${java.util.Locale.getDefault().displayLanguage})")
                dialog.dismiss()
                onChanged()
            }
        }

        val sysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sysCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sysCol.addView(TextView(this).apply {
            text = "Follow Phone Region / Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
        })
        sysCol.addView(TextView(this).apply {
            text = "Auto-detect: ${java.util.Locale.getDefault().displayName}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(tokens.textSecondary)
        })
        sysRow.addView(sysCol)

        if (isSystemMode) {
            sysRow.addView(TextView(this).apply {
                text = "Active"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
            })
        }
        sysCard.addView(sysRow)
        itemsContainer.addView(sysCard)

        for (lang in GnosisLanguageManager.SUPPORTED_LANGUAGES) {
            val isCurrent = !isSystemMode && activeLang.code == lang.code
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = dp(12f)
                setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14f).toFloat()
                    setColor(tokens.chipBg)
                    setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    GnosisLanguageManager.setActiveLanguage(this@MainActivity, lang.code)
                    EngineFactory.invalidateCache()
                    notifySettingsChanged(restart = true)
                    showToast("Active Language: ${lang.name}")
                    dialog.dismiss()
                    onChanged()
                }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = "${lang.name} (${lang.nativeName})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.textPrimary)
            })
            col.addView(TextView(this).apply {
                text = "Code: ${lang.shortLabel}  •  Default Layout: ${lang.defaultLayout}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(tokens.textSecondary)
            })
            row.addView(col)

            if (isCurrent) {
                row.addView(TextView(this).apply {
                    text = "Active"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.accent)
                })
            }
            card.addView(row)
            itemsContainer.addView(card)
        }

        scroll.addView(itemsContainer)
        root.addView(scroll)

        val closeBtn = TextView(this).apply {
            text = "Done"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.accent)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.accent)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42f)).apply {
                topMargin = dp(10f)
            }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(closeBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    fun showLayoutDownloaderDialog(onChanged: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), dp(480f))
        }

        root.addView(TextView(this).apply {
            text = "Keyboard Layout Gallery"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        root.addView(TextView(this).apply {
            text = "Manage downloaded layouts or install community key arrangements."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        val itemsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        itemsScroll.addView(itemsContainer)
        root.addView(itemsScroll)

        fun renderAllLayouts(remoteCatalog: List<RemoteLayoutInfo> = LayoutExtensionManager.STATIC_CATALOG) {
            itemsContainer.removeAllViews()
            val curLayout = Skins.prefs(this).getString(Skins.KEY_LAYOUT, "qwerty") ?: "qwerty"
            val installed = LayoutExtensionManager.getInstalledLayouts(this)

            val headerInstalled = TextView(this).apply {
                text = "INSTALLED LAYOUTS"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(tokens.accent)
                setPadding(0, dp(4f), 0, dp(6f))
            }
            itemsContainer.addView(headerInstalled)

            for (layout in installed) {
                val isCurrent = curLayout == layout.id
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), if (isCurrent) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8f)
                    }
                }

                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val titleCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6f)
                    }
                }
                titleCol.addView(TextView(this).apply {
                    text = layout.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isCurrent) tokens.accent else tokens.textPrimary)
                })
                titleCol.addView(TextView(this).apply {
                    text = layout.language
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(tokens.textSecondary)
                })
                topRow.addView(titleCol)

                val applyBtn = TextView(this).apply {
                    text = if (isCurrent) "Active" else "Apply"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isCurrent) tokens.accent else tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        if (isCurrent) {
                            setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                            setStroke(dp(1f), tokens.accent)
                        } else {
                            setColor(tokens.accent)
                        }
                    }
                    setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
                    if (!isCurrent) {
                        setOnClickListener {
                            Skins.prefs(this@MainActivity).edit().putString(Skins.KEY_LAYOUT, layout.id).apply()
                            notifySettingsChanged(restart = true)
                            showToast("Applied layout: ${layout.name}")
                            onChanged()
                            renderAllLayouts(remoteCatalog)
                        }
                    }
                }
                topRow.addView(applyBtn)

                if (layout.id != "qwerty") {
                    val deleteBtn = FrameLayout(this).apply {
                        val s = dp(30f)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            marginStart = dp(6f)
                        }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.RED, if (tokens.isLight) 25 else 40))
                        }
                        val iv = ImageView(this@MainActivity).apply {
                            val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                                ?: ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
                            d?.setTint(Color.parseColor("#FF5252"))
                            setImageDrawable(d)
                            val p = dp(6f)
                            setPadding(p, p, p, p)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        addView(iv)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            LayoutExtensionManager.deleteLayout(this@MainActivity, layout.id)
                            if (curLayout == layout.id) {
                                Skins.prefs(this@MainActivity).edit().putString(Skins.KEY_LAYOUT, "qwerty").apply()
                            }
                            notifySettingsChanged(restart = true)
                            showToast("Deleted layout: ${layout.name}")
                            onChanged()
                            renderAllLayouts(remoteCatalog)
                        }
                    }
                    topRow.addView(deleteBtn)
                }

                card.addView(topRow)

                val previewKeys = LayoutExtensionManager.getPreviewKeys(layout.id)
                val keyStrip = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6f), 0, dp(2f))
                }
                for (k in previewKeys) {
                    val keyCap = TextView(this).apply {
                        text = k
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                            setColor(tokens.surface)
                            setStroke(dp(0.7f), tokens.cardBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(0, dp(20f), 1f).apply {
                            marginStart = dp(1f)
                            marginEnd = dp(1f)
                        }
                    }
                    keyStrip.addView(keyCap)
                }
                card.addView(keyStrip)
                itemsContainer.addView(card)
            }

            val uninstalled = remoteCatalog.filter { !LayoutExtensionManager.isInstalled(this, it.id) }
            if (uninstalled.isNotEmpty()) {
                val headerDownloads = TextView(this).apply {
                    text = "COMMUNITY DOWNLOADS"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.accent)
                    setPadding(0, dp(14f), 0, dp(6f))
                }
                itemsContainer.addView(headerDownloads)

                for (item in uninstalled) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12f).toFloat()
                            setColor(tokens.chipBg)
                        }
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp(8f)
                        }
                    }

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val infoCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6f)
                        }
                    }
                    infoCol.addView(TextView(this).apply {
                        text = item.name
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.textPrimary)
                    })
                    infoCol.addView(TextView(this).apply {
                        text = item.language
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                        setTextColor(tokens.textSecondary)
                    })
                    row.addView(infoCol)

                    val actionBtn = TextView(this).apply {
                        text = "Download"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(tokens.onAccentText)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8f).toFloat()
                            setColor(tokens.accent)
                        }
                        setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
                        setOnClickListener {
                            text = "..."
                            LayoutExtensionManager.downloadAndInstall(this@MainActivity, item) { ok, _ ->
                                runOnUiThread {
                                    if (ok) {
                                        showToast("Installed layout: ${item.name}")
                                        notifySettingsChanged(restart = true)
                                        onChanged()
                                        renderAllLayouts(remoteCatalog)
                                        showRestartPromptDialog("Installed ${item.name} layout. Gnosis Keyboard needs to restart to apply the new physical key arrangement.")
                                    } else {
                                        showToast("Failed to download layout", true)
                                        text = "Retry"
                                    }
                                }
                            }
                        }
                    }
                    row.addView(actionBtn)
                    card.addView(row)

                    val previewKeys = LayoutExtensionManager.getPreviewKeys(item.id)
                    val keyStrip = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(6f), 0, dp(2f))
                    }
                    for (k in previewKeys) {
                        val keyCap = TextView(this).apply {
                            text = k
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(tokens.textSecondary)
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                cornerRadius = dp(4f).toFloat()
                                setColor(tokens.surface)
                                setStroke(dp(0.7f), tokens.cardBorder)
                            }
                            layoutParams = LinearLayout.LayoutParams(0, dp(20f), 1f).apply {
                                marginStart = dp(1f)
                                marginEnd = dp(1f)
                            }
                        }
                        keyStrip.addView(keyCap)
                    }
                    card.addView(keyStrip)
                    itemsContainer.addView(card)
                }
            }
        }

        renderAllLayouts(LayoutExtensionManager.STATIC_CATALOG)

        LayoutExtensionManager.fetchRemoteCatalog { catalog ->
            runOnUiThread {
                if (catalog.isNotEmpty()) {
                    renderAllLayouts(catalog)
                }
            }
        }

        dialog.setContentView(root)
        dialog.show()
    }

    fun showFontStoreDialog(onChanged: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), dp(480f))
        }

        root.addView(TextView(this).apply {
            text = "Custom Fonts & Typography Store"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        root.addView(TextView(this).apply {
            text = "Preview and download custom typographies for key labels."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        val itemsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        itemsScroll.addView(itemsContainer)
        root.addView(itemsScroll)

        fun renderCatalog(catalog: List<RemoteFontInfo>) {
            itemsContainer.removeAllViews()
            if (catalog.isEmpty()) {
                itemsContainer.addView(TextView(this).apply {
                    text = "No custom font catalog available right now."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(tokens.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(40f), 0, 0)
                })
                return
            }

            for (font in catalog) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), tokens.cardBorder)
                    }
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8f)
                    }
                }

                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                infoCol.addView(TextView(this).apply {
                    text = font.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(tokens.textPrimary)
                })
                infoCol.addView(TextView(this).apply {
                    text = "$((font.size / 1024)) KB \u2022 Custom Typeface"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(tokens.textSecondary)
                })
                topRow.addView(infoCol)

                val installed = FontExtensionManager.isInstalled(this, font.file)
                val curApplied = Skins.prefs(this).getString(Skins.KEY_STUDIO_FONT, Skins.FONT_DEFAULT) == font.file
                val actionBtn = TextView(this).apply {
                    text = if (curApplied) "Active" else if (installed) "Apply" else "Download"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (curApplied) tokens.accent else tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        if (curApplied) {
                            setColor(ColorUtils.setAlphaComponent(tokens.accent, if (tokens.isLight) 30 else 45))
                            setStroke(dp(1f), tokens.accent)
                        } else {
                            setColor(tokens.accent)
                        }
                    }
                    setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                    if (!curApplied) {
                        setOnClickListener {
                            if (!installed) {
                                text = "..."
                                FontExtensionManager.downloadFont(this@MainActivity, font) { _ ->
                                    runOnUiThread {
                                        showToast("Downloaded ${font.name}")
                                        renderCatalog(catalog)
                                    }
                                }
                            } else {
                                Skins.prefs(this@MainActivity).edit()
                                    .putString(Skins.KEY_STUDIO_FONT, font.file)
                                    .putBoolean(Skins.KEY_USE_SYSTEM_FONT, false)
                                    .apply()
                                notifySettingsChanged(restart = true)
                                showToast("Applied font: ${font.name}")
                                onChanged()
                                dialog.dismiss()
                            }
                        }
                    }
                }
                topRow.addView(actionBtn)
                row.addView(topRow)

                val sampleTv = TextView(this).apply {
                    text = "The quick brown fox jumps over 123"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setTextColor(tokens.textPrimary)
                    setPadding(0, dp(4f), 0, dp(2f))
                }
                FontExtensionManager.loadPreviewTypeface(this@MainActivity, font) { tf ->
                    runOnUiThread {
                        if (tf != null) sampleTv.typeface = tf
                    }
                }
                row.addView(sampleTv)

                itemsContainer.addView(row)
            }
        }

        FontExtensionManager.fetchRemoteCatalog { catalog ->
            runOnUiThread { renderCatalog(catalog ?: emptyList()) }
        }

        dialog.setContentView(root)
        dialog.show()
    }

    fun showDictionaryStoreDialog(onChanged: () -> Unit = {}) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = ViewGroup.LayoutParams(dp(340f), dp(480f))
        }

        root.addView(TextView(this).apply {
            text = "Additional Dictionaries Store"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(4f))
        })

        root.addView(TextView(this).apply {
            text = "Download verified multi-language autocorrect dictionaries."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(tokens.textSecondary)
            setPadding(0, 0, 0, dp(12f))
        })

        val itemsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val itemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        itemsScroll.addView(itemsContainer)
        root.addView(itemsScroll)

        fun renderPacks() {
            itemsContainer.removeAllViews()
            for (pack in DictionaryExtensionManager.AVAILABLE_PACKS) {
                val installed = DictionaryExtensionManager.isInstalled(this, pack.id)
                val enabled = DictionaryExtensionManager.isEnabled(this, pack.id)
                val count = DictionaryExtensionManager.getWordCount(this, pack.id)

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(tokens.chipBg)
                        setStroke(dp(1f), if (enabled) tokens.accent else tokens.cardBorder)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8f)
                    }
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                infoCol.addView(TextView(this).apply {
                    text = pack.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (enabled) tokens.accent else tokens.textPrimary)
                })
                infoCol.addView(TextView(this).apply {
                    text = if (installed) "${pack.language} \u2022 $count words ready" else "${pack.language} \u2022 Available from GitHub"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(tokens.textSecondary)
                })
                row.addView(infoCol)

                val actionBtn = TextView(this).apply {
                    text = if (installed) (if (enabled) "Active" else "Disabled") else "Download"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (installed && !enabled) tokens.textSecondary else tokens.onAccentText)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        setColor(if (installed && !enabled) tokens.cardBorder else tokens.accent)
                    }
                    setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                    setOnClickListener {
                        if (!installed) {
                            text = "..."
                            DictionaryExtensionManager.downloadPack(this@MainActivity, pack) { ok, wordCnt, _ ->
                                runOnUiThread {
                                    if (ok) {
                                        showToast("Installed ${pack.name} ($wordCnt words)!")
                                        notifySettingsChanged(restart = true)
                                        onChanged()
                                        renderPacks()
                                        showRestartPromptDialog("Installed ${pack.name}. Gnosis Keyboard needs to restart to initialize the new dictionary and reload lexical pipelines.")
                                    } else {
                                        showToast("Download failed. Please check internet connection.", true)
                                        text = "Retry"
                                    }
                                }
                            }
                        } else {
                            val newEn = !enabled
                            DictionaryExtensionManager.setEnabled(this@MainActivity, pack.id, newEn)
                            notifySettingsChanged(restart = true)
                            showToast("${pack.name} ${if (newEn) "enabled" else "disabled"}")
                            onChanged()
                            renderPacks()
                        }
                    }
                }
                row.addView(actionBtn)

                if (installed) {
                    val deleteBtn = FrameLayout(this).apply {
                        val s = dp(32f)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            marginStart = dp(8f)
                        }
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8f).toFloat()
                            setColor(ColorUtils.setAlphaComponent(Color.RED, if (tokens.isLight) 25 else 40))
                        }
                        val iv = ImageView(this@MainActivity).apply {
                            val d = ContextCompat.getDrawable(context, R.drawable.ic_trash_2)?.mutate()
                                ?: ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()
                            d?.setTint(Color.parseColor("#FF5252"))
                            setImageDrawable(d)
                            val p = dp(7f)
                            setPadding(p, p, p, p)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        addView(iv)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            DictionaryExtensionManager.deletePack(this@MainActivity, pack.id)
                            notifySettingsChanged(restart = true)
                            showToast("Deleted ${pack.name} dictionary")
                            onChanged()
                            renderPacks()
                        }
                    }
                    row.addView(deleteBtn)
                }

                card.addView(row)
                itemsContainer.addView(card)
            }
        }

        renderPacks()
        dialog.setContentView(root)
        dialog.show()
    }



    fun refreshCurrentScreen() {
        if (currentDestinationTag != null) {
            val freshView = renderScreenForTag(currentDestinationTag)
            if (freshView != null) {
                contentFrame.removeAllViews()
                contentFrame.addView(freshView)
            }
        }
    }

    fun showRestartPromptDialog(
        message: String = "Gnosis Keyboard needs to restart to initialize newly installed prediction engines and reload lexical pipelines."
    ) {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dm = resources.displayMetrics
        val dialogW = (dm.widthPixels * 0.88f).toInt().coerceIn(dp(300f), dp(360f))

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(24f)
            val padV = dp(24f)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(24f).toFloat()
                setColor(tokens.surface)
                setStroke(dp(1.2f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val iconBox = FrameLayout(this).apply {
            val s = dp(52f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14f)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(ColorUtils.setAlphaComponent(tokens.accent, 35))
                setStroke(dp(1.2f), tokens.accent)
            }
            val iv = ImageView(this@MainActivity).apply {
                val d = ContextCompat.getDrawable(context, R.drawable.ic_refresh_cw)?.mutate()
                    ?: ContextCompat.getDrawable(context, R.drawable.ic_sparkles)?.mutate()
                d?.setTint(tokens.accent)
                setImageDrawable(d)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = dp(13f)
                setPadding(p, p, p, p)
            }
            addView(iv)
        }
        root.addView(iconBox)

        root.addView(TextView(this).apply {
            text = "Restart Required"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        root.addView(TextView(this).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
            setLineSpacing(dp(2f).toFloat(), 1.25f)
            setPadding(dp(4f), 0, dp(4f), dp(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4f)
            }
        }

        val laterBtn = TextView(this).apply {
            text = "Later"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.chipBg)
                setStroke(dp(1f), tokens.cardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(46f), 1f).apply {
                marginEnd = dp(10f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(laterBtn)

        val restartBtn = TextView(this).apply {
            text = "Restart Now"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(tokens.onAccentText)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(tokens.accent)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(46f), 1.25f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                restartAppAndKeyboard()
            }
        }
        btnRow.addView(restartBtn)
        root.addView(btnRow)

        dialog.setContentView(root, ViewGroup.LayoutParams(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.show()
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(dialogW, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
        }
    }

    fun restartAppAndKeyboard() {
        EngineFactory.invalidateCache()
        SuggestionExtensionManager.invalidateCache()
        DictionaryExtensionManager.clearCache()
        try {
            KeyboardService.activeInstance?.restartKeyboard()
        } catch (_: Exception) {}
        try {
            sendBroadcast(Intent(Skins.ACTION_RESTART_KEYBOARD).setPackage(packageName))
        } catch (_: Exception) {}
        try {
            sendBroadcast(Intent("com.gnosis.keyboard.SETTINGS_UPDATED").setPackage(packageName))
        } catch (_: Exception) {}

        showToast("Restarting Gnosis Keyboard...")

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        finish()
        if (intent != null) {
            startActivity(intent)
        }
    }

    fun setLauncherIconHidden(hidden: Boolean) {
        try {
            val alias = ComponentName(this, "$packageName.LauncherAlias")
            val targetState = if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                alias,
                targetState,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }

    fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        val enabledList = imm.enabledInputMethodList ?: return false
        return enabledList.any { it.packageName == packageName }
    }

    fun isKeyboardSelected(): Boolean {
        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return currentIme.contains(packageName)
    }
}