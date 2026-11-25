package gnosis.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.json.JSONObject
import kotlin.math.min

class Skin(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val background: Int,
    val surface: Int,
    val keyColor: Int,
    val keyBorderColor: Int,
    val textColor: Int,
    val keyTextColor: Int,
    val subtleTextColor: Int,
    val accent: Int,
    val accentText: Int,
    val accentOnKey: Int,
    val elevationDp: Float,
    val barBackground: Int,
    val chipBackground: Int,
    val chipBorderColor: Int,
    val keyBackground: (density: Float, special: Boolean) -> Drawable,
    val actionKeyBackground: (density: Float) -> Drawable,
    val actionTextColor: Int,
    val chipDrawable: (density: Float, active: Boolean) -> Drawable,
    val keyShape: String = Skins.SHAPE_ROUNDED,
    val cornerRadiusDp: Float = 13f,
    val borderWidthDp: Float = 1.0f,
    val shadowDp: Float = 0f,
    val typeface: Typeface = Typeface.DEFAULT,
    val fontBold: Boolean = true,
    val fontScale: Float = 1.0f,
    val keySpacingH: Float = 3f,
    val keySpacingV: Float = 6f,
    val keyHeightDp: Int = 48,
    val keyTileSize: Int = 100,
    val keyTileWidth: Int = 100,
    val bottomPaddingDp: Float = 0f,
    val sidePaddingDp: Float = 2f,
    val glassEnabled: Boolean = false,
    val glassOpacity: Int = 55,
    val glassSpecular: Boolean = true,
    val hyperDynamic: Boolean = false,
    val hyperAurora: Boolean = false,
    val backgroundDrawable: Drawable? = null,
    val blurRadius: Float = 0f,
    val keyOverrides: Map<String, Skins.KeyStyleOverride> = emptyMap(),
    private val perKeyBgProvider: (keyId: String, density: Float, special: Boolean, isAction: Boolean) -> Drawable = { _, d, s, a ->
        if (a) actionKeyBackground(d) else keyBackground(d, s)
    }
) {
    val boldTypeface: Typeface by lazy { Typeface.create(typeface, Typeface.BOLD) }

    fun keyBackgroundFor(
        keyId: String,
        density: Float,
        special: Boolean = false,
        isAction: Boolean = false
    ): Drawable = perKeyBgProvider(keyId, density, special, isAction)

    fun backgroundDrawable(ctx: Context, widthPx: Int = 0, heightPx: Int = 0): Drawable? =
        CustomBackground.drawable(ctx, this) ?: backgroundDrawable

    fun textColorFor(keyId: String, special: Boolean = false, isAction: Boolean = false): Int {
        val normId = keyId.lowercase()
        keyOverrides[normId]?.textColor?.let { return it }
        keyOverrides[keyId.uppercase()]?.textColor?.let { return it }
        if (isAction || normId == "enter") {
            keyOverrides["enter"]?.textColor?.let { return it }
            keyOverrides["ENTER"]?.textColor?.let { return it }
            keyOverrides["group:enter"]?.textColor?.let { return it }
        }
        if (normId == "space") {
            keyOverrides["space"]?.textColor?.let { return it }
            keyOverrides["SPACE"]?.textColor?.let { return it }
            keyOverrides["group:space"]?.textColor?.let { return it }
        }
        if (special || isAction) {
            keyOverrides["group_special"]?.textColor?.let { return it }
            keyOverrides["GROUP_SPECIAL"]?.textColor?.let { return it }
            keyOverrides["group:special"]?.textColor?.let { return it }
        } else {
            keyOverrides["group_letters"]?.textColor?.let { return it }
            keyOverrides["GROUP_LETTERS"]?.textColor?.let { return it }
            keyOverrides["group:letter"]?.textColor?.let { return it }
        }
        return if (isAction) actionTextColor else keyTextColor
    }
}

object Skins {
    const val PREFS = "gnosis_kb"
    const val KEY_THEME = "theme"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_APP_THEME_MODE = "app_theme_mode"
    const val KEY_KEYBOARD_THEME_MODE = "keyboard_theme_mode"
    const val KEY_LAST_DARK = "theme_last_dark"
    const val KEY_KEY_STYLE = "key_style"
    const val MODE_DARK = "dark"
    const val MODE_LIGHT = "light"
    const val MODE_AUTO = "auto"
    const val STYLE_DEFAULT = "default"
    const val STYLE_STROKE = "stroke"
    const val STYLE_MATERIAL = "material"
    const val STYLE_NEUMORPHIC = "neumorphic"
    const val KEY_ACCENT = "accent_color"
    const val KEY_KEY_BORDERS = "key_borders"
    const val KEY_REVAMPED_TOOLBAR_ICON_SIZE = "revamped_toolbar_icon_size"
    const val KEY_CLASSIC_TOOLBAR_ICON_SIZE = "classic_toolbar_icon_size"
    const val DEFAULT_REVAMPED_TOOLBAR_ICON_SIZE = 32
    const val DEFAULT_CLASSIC_TOOLBAR_ICON_SIZE = 32
    const val KEY_TOOLBAR_HEIGHT_REVAMPED = "toolbar_height_revamped"
    const val KEY_TOOLBAR_HEIGHT_CLASSIC = "toolbar_height_classic"
    const val DEFAULT_TOOLBAR_HEIGHT_REVAMPED = 42
    const val DEFAULT_TOOLBAR_HEIGHT_CLASSIC = 42
    const val KEY_KEYBOARD_HEIGHT_CLASSIC = "keyboard_height_classic"
    const val DEFAULT_KEYBOARD_HEIGHT_CLASSIC = 45
    const val KEY_SPACE_HOLD_SWITCH_LAYOUT = "space_hold_switch_layout"
    const val DEFAULT_SPACE_HOLD_SWITCH_LAYOUT = true
    const val KEY_ONLINE_PREDICTIONS = "online_predictions_enabled"
    const val DEFAULT_ONLINE_PREDICTIONS = true
    const val DEFAULT_AUTOCORRECT = true
    const val KEY_HIGH_CONTRAST = "high_contrast"
    const val KEY_ONE_HANDED = "one_handed"
    const val KEY_FLOATING = "floating_keyboard"
    const val KEY_FLOAT_WIDTH = "floating_width"
    const val KEY_FLOAT_HEIGHT = "floating_height"
    const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
    const val KEY_NUMBER_ROW = "number_row"
    const val KEY_KEY_POPUP = "key_popup"
    const val KEY_SOUND = "sound_enabled"
    const val KEY_SOUND_VOLUME = "sound_volume"
    const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"
    const val KEY_SOUND_TONE = "sound_tone"
    const val KEY_HAPTIC = "haptic_enabled"
    const val KEY_HAPTIC_PRESSURE = "haptic_pressure"
    const val KEY_GLIDE = "glide_typing"
    const val KEY_GESTURE_DELETE = "gesture_delete"
    const val KEY_CURSOR_CONTROL = "cursor_control"
    const val KEY_SUGGESTIONS = "suggestions_enabled"
    const val KEY_AUTOCORRECT = "autocorrect_enabled"
    const val KEY_PREDICTIONS = "predictions_enabled"
    const val KEY_EMOJI_SUGGESTIONS = "emoji_suggestions_enabled"
    const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
    const val KEY_EMOJI_SIZE = "emoji_scale_percent"
    const val DEFAULT_EMOJI_SIZE = 100
    const val KEY_GRAMMAR = "grammar_check_enabled"
    const val KEY_LANGUAGE_MODE = "language_mode"
    const val MODE_LANG_SYSTEM = "system"
    const val MODE_LANG_MANUAL = "manual"
    const val KEY_ACTIVE_LANGUAGE = "active_language"
    const val KEY_LAYOUT = "keyboard_layout"
    const val KEY_LAYOUT_STYLE = "keyboard_layout_style"
    const val STYLE_REVAMPED = "revamped"
    const val STYLE_CLASSICS = "classics"
    const val ACTION_RESTART_KEYBOARD = "gnosis.keyboard.RESTART_KEYBOARD"
    const val KEY_DIM = "custom_bg_dim"
    const val KEY_QUICK_KEYS = "quick_keys"
    const val KEY_QUICK_KEYS_SIZE = "quick_keys_size"
    const val DEFAULT_QUICK_KEYS_SIZE = 90
    const val KEY_LONG_PRESS_MS = "key_long_press_ms"
    const val DEFAULT_LONG_PRESS_MS = 300
    const val KEY_SECONDARY_HINTS = "secondary_hints"
    const val DEFAULT_SECONDARY_HINTS = true
    const val KEY_USE_SYSTEM_FONT = "studio_use_system_font"
    const val DEFAULT_USE_SYSTEM_FONT = false
    const val KEY_PARTICLE_EFFECTS = "particle_effects_style"
    const val DEFAULT_PARTICLE_EFFECTS = "off"
    const val KEY_COOL_FONT = "cool_font_active_style"
    const val DEFAULT_COOL_FONT = "normal"

    const val KEY_STUDIO_SHAPE = "studio_key_shape"
    const val SHAPE_ROUNDED = "rounded"
    const val SHAPE_SQUIRCLE = "squircle"
    const val SHAPE_PILL = "pill"
    const val SHAPE_SHARP = "sharp"
    const val SHAPE_CUT_CORNER = "cut_corner"
    const val SHAPE_GEM = "gem"

    const val KEY_STUDIO_RADIUS = "studio_corner_radius"
    const val KEY_STUDIO_BORDER_WIDTH = "studio_border_width"
    const val KEY_STUDIO_SHADOW = "studio_shadow_dp"
    const val KEY_STUDIO_H_GAP = "studio_h_gap"
    const val KEY_STUDIO_V_GAP = "studio_v_gap"
    const val KEY_STUDIO_BOTTOM_PADDING = "studio_bottom_padding"
    const val KEY_STUDIO_SIDE_PADDING = "studio_side_padding"

    const val KEY_KEY_TILE_SIZE = "key_tile_size"
    const val DEFAULT_KEY_TILE_SIZE = 100
    const val KEY_KEY_TILE_WIDTH = "key_tile_width"
    const val DEFAULT_KEY_TILE_WIDTH = 100

    const val KEY_STUDIO_FONT = "studio_font_style"
    const val FONT_DEFAULT = "default"
    const val FONT_ROUNDED = "rounded"
    const val FONT_SERIF = "serif"
    const val FONT_MONO = "monospace"
    const val FONT_CONDENSED = "condensed"
    const val FONT_LIGHT = "light"
    const val KEY_STUDIO_FONT_BOLD = "studio_font_bold"
    const val KEY_STUDIO_FONT_SCALE = "studio_font_scale"

    const val KEY_LANDSCAPE_SEPARATE = "landscape_separate_profile"
    const val KEY_LANDSCAPE_CUSTOM = KEY_LANDSCAPE_SEPARATE
    const val KEY_LAYOUT_LANDSCAPE = "keyboard_layout_landscape"
    const val KEY_LANDSCAPE_LAYOUT = KEY_LAYOUT_LANDSCAPE
    const val KEY_LAYOUT_STYLE_LANDSCAPE = "keyboard_layout_style_landscape"
    const val KEY_HEIGHT_LANDSCAPE = "keyboard_height_landscape"
    const val KEY_LANDSCAPE_HEIGHT = KEY_HEIGHT_LANDSCAPE
    const val KEY_LANDSCAPE_H_GAP = "studio_h_gap_landscape"
    const val KEY_LANDSCAPE_V_GAP = "studio_v_gap_landscape"
    const val KEY_BOTTOM_PADDING_LANDSCAPE = "studio_bottom_padding_landscape"
    const val KEY_LANDSCAPE_BOTTOM_PAD = KEY_BOTTOM_PADDING_LANDSCAPE
    const val KEY_SIDE_PADDING_LANDSCAPE = "studio_side_padding_landscape"
    const val KEY_LANDSCAPE_SIDE_PAD = KEY_SIDE_PADDING_LANDSCAPE
    const val KEY_LANDSCAPE_SPLIT = "landscape_split_enabled"

    const val KEY_HYPER_DYNAMIC = "hyper_dynamic_colors"
    const val KEY_HYPER_AURORA = "hyper_aurora_bg"
    const val KEY_GLASS_ENABLED = "hyper_glass_enabled"
    const val KEY_GLASS_OPACITY = "hyper_glass_opacity"
    const val KEY_GLASS_SPECULAR = "hyper_glass_specular"
    const val KEY_STUDIO_OVERRIDES_JSON = "studio_per_key_overrides"

    data class KeyStyleOverride(
        val fillColor: Int? = null,
        val textColor: Int? = null,
        val borderColor: Int? = null,
        val shadowColor: Int? = null,
        val cornerRadiusDp: Float? = null,
        val borderWidthDp: Float? = null,
        val shadowRadiusDp: Float? = null
    ) {
        val shadowDp: Float? get() = shadowRadiusDp
    }

    val DEFAULT_ACCENT = Color.parseColor("#7C9CFF")

    val ACCENT_PALETTE = listOf(
        "#7C9CFF" to "Electric Blue",
        "#00E5FF" to "Cyan",
        "#22C55E" to "Emerald Green",
        "#FF6B4A" to "Sunset Coral",
        "#A855F7" to "Neon Purple",
        "#EC4899" to "Hot Pink",
        "#F59E0B" to "Amber Gold",
        "#EF4444" to "Crimson Red",
        "#FFFFFF" to "Pure White",
        "#121316" to "Midnight Black"
    )

    data class ThemePreset(
        val id: String,
        val name: String,
        val bg: String,
        val surface: String,
        val key: String,
        val keyBorder: String,
        val text: String,
        val muted: String,
        val defaultAccent: String,
        val chip: String,
        val chipBorder: String,
        val isLight: Boolean
    )

    const val THEME_MD3_DYNAMIC = "md3_dynamic"
    const val THEME_MD3_DYNAMIC_LIGHT = "md3_dynamic_light"
    const val DEFAULT_THEME = THEME_MD3_DYNAMIC
    const val THEME_LUCID_VOID = "lucid-void"
    const val THEME_LUCID_VOID_LIGHT = "lucid-void_light"

    const val KEY_LUCID_VOID_OPACITY = "lucid_void_opacity"
    const val KEY_LUCID_VOID_BLUR = "lucid_void_blur"
    const val KEY_LUCID_VOID_OPACITY_DARK = "lucid_void_opacity_dark"
    const val KEY_LUCID_VOID_OPACITY_LIGHT = "lucid_void_opacity_light"
    const val KEY_LUCID_VOID_BLUR_DARK = "lucid_void_blur_dark"
    const val KEY_LUCID_VOID_BLUR_LIGHT = "lucid_void_blur_light"
    const val KEY_LUCID_VOID_TINT_DARK = "lucid_void_tint_dark"
    const val KEY_LUCID_VOID_TINT_LIGHT = "lucid_void_tint_light"

    const val DEFAULT_LUCID_VOID_OPACITY_DARK = 48
    const val DEFAULT_LUCID_VOID_OPACITY_LIGHT = 44
    const val DEFAULT_LUCID_VOID_BLUR_DARK = 48
    const val DEFAULT_LUCID_VOID_BLUR_LIGHT = 42
    const val DEFAULT_LUCID_VOID_TINT_DARK = 25
    const val DEFAULT_LUCID_VOID_TINT_LIGHT = 20

    object KeyboardTheme {
        const val LUCID_VOID = "lucid-void"
        const val LUCID_VOID_LIGHT = "lucid-void_light"
    }

    val PRESETS = mapOf(
        "lucid-void" to ThemePreset(
            id = "lucid-void", name = "Lucid Void",
            bg = "#330A0D14", surface = "#240A0D14", key = "#291E293B", keyBorder = "#2E94A3B8",
            text = "#F8FAFC", muted = "#94A3B8", defaultAccent = "#4D0091FF",
            chip = "#240A0D14", chipBorder = "#2E94A3B8", isLight = false
        ),
        "lucid-void_light" to ThemePreset(
            id = "lucid-void_light", name = "Lucid Void Light",
            bg = "#2EFFFFFF", surface = "#1FFFFFFF", key = "#47FFFFFF", keyBorder = "#66FFFFFF",
            text = "#E60F172A", muted = "#64748B", defaultAccent = "#52007AFF",
            chip = "#1FFFFFFF", chipBorder = "#66FFFFFF", isLight = true
        ),
        "md3_dynamic" to ThemePreset(
            id = "md3_dynamic", name = "Material You",
            bg = "#1B1B1F", surface = "#202024", key = "#2B2B30", keyBorder = "#44474E",
            text = "#E3E2E6", muted = "#8E9099", defaultAccent = "#A8C7FA",
            chip = "#2B2B30", chipBorder = "#44474E", isLight = false
        ),
        "md3_dynamic_light" to ThemePreset(
            id = "md3_dynamic_light", name = "Material You Light",
            bg = "#FEF7FF", surface = "#F3EDF7", key = "#FFFFFF", keyBorder = "#CAC4D0",
            text = "#1D1B20", muted = "#49454F", defaultAccent = "#00639B",
            chip = "#E8DEF8", chipBorder = "#CAC4D0", isLight = true
        ),
        "onyx" to ThemePreset(
            id = "onyx", name = "Onyx",
            bg = "#000000", surface = "#0A0C0C", key = "#1A1D1D", keyBorder = "#2B3030",
            text = "#FFFFFF", muted = "#8B9494", defaultAccent = "#7C9CFF",
            chip = "#151918", chipBorder = "#272D2D", isLight = false
        ),
        "onyx_light" to ThemePreset(
            id = "onyx_light", name = "Daylight",
            bg = "#EEF0F3", surface = "#F7F8FA", key = "#FFFFFF", keyBorder = "#D8DCE2",
            text = "#0D0F12", muted = "#6B7280", defaultAccent = "#1D4ED8",
            chip = "#FFFFFF", chipBorder = "#D8DCE2", isLight = true
        ),
        "light" to ThemePreset(
            id = "light", name = "Daylight",
            bg = "#EEF0F3", surface = "#F7F8FA", key = "#FFFFFF", keyBorder = "#D8DCE2",
            text = "#0D0F12", muted = "#6B7280", defaultAccent = "#1D4ED8",
            chip = "#FFFFFF", chipBorder = "#D8DCE2", isLight = true
        ),
        "graphite" to ThemePreset(
            id = "graphite", name = "Graphite",
            bg = "#0C0E0F", surface = "#141819", key = "#232829", keyBorder = "#353C3C",
            text = "#F2F5F5", muted = "#8D9797", defaultAccent = "#2F6F60",
            chip = "#191E1F", chipBorder = "#2D3434", isLight = false
        ),
        "graphite_light" to ThemePreset(
            id = "graphite_light", name = "Graphite Light",
            bg = "#E8EAED", surface = "#F1F3F4", key = "#FFFFFF", keyBorder = "#CCD0D5",
            text = "#171A1C", muted = "#606770", defaultAccent = "#1F6F5E",
            chip = "#FFFFFF", chipBorder = "#CCD0D5", isLight = true
        ),
        "midnight" to ThemePreset(
            id = "midnight", name = "Midnight",
            bg = "#05070F", surface = "#0B1020", key = "#161D33", keyBorder = "#2A3454",
            text = "#EEF1FF", muted = "#8F9BC4", defaultAccent = "#3B5BDB",
            chip = "#0E1428", chipBorder = "#26304A", isLight = false
        ),
        "midnight_light" to ThemePreset(
            id = "midnight_light", name = "Midnight Light",
            bg = "#EAF0FB", surface = "#F2F6FD", key = "#FFFFFF", keyBorder = "#C8D6F2",
            text = "#0F172A", muted = "#5B6E96", defaultAccent = "#2563EB",
            chip = "#FFFFFF", chipBorder = "#C8D6F2", isLight = true
        ),
        "ocean" to ThemePreset(
            id = "ocean", name = "Ocean",
            bg = "#02121A", surface = "#07202A", key = "#0E2A33", keyBorder = "#1B4B57",
            text = "#EAFCFF", muted = "#8FB6BF", defaultAccent = "#0E7490",
            chip = "#0A242E", chipBorder = "#1B4650", isLight = false
        ),
        "ocean_light" to ThemePreset(
            id = "ocean_light", name = "Ocean Light",
            bg = "#E6F4F7", surface = "#EFF9FA", key = "#FFFFFF", keyBorder = "#BCE2E8",
            text = "#04262E", muted = "#4D7983", defaultAccent = "#0891B2",
            chip = "#FFFFFF", chipBorder = "#BCE2E8", isLight = true
        ),
        "ember" to ThemePreset(
            id = "ember", name = "Ember",
            bg = "#140B0B", surface = "#211010", key = "#2B1A17", keyBorder = "#452A24",
            text = "#FFF1EA", muted = "#BB958A", defaultAccent = "#B45309",
            chip = "#241413", chipBorder = "#402722", isLight = false
        ),
        "ember_light" to ThemePreset(
            id = "ember_light", name = "Ember Light",
            bg = "#FBF1EB", surface = "#FFF7F2", key = "#FFFFFF", keyBorder = "#F2D0C2",
            text = "#29140C", muted = "#8A6458", defaultAccent = "#C25E09",
            chip = "#FFFFFF", chipBorder = "#F2D0C2", isLight = true
        ),
        "rose" to ThemePreset(
            id = "rose", name = "Rose",
            bg = "#140A10", surface = "#210F19", key = "#2B1A24", keyBorder = "#472C3A",
            text = "#FFEEF5", muted = "#BB8FA3", defaultAccent = "#9D4B6B",
            chip = "#241320", chipBorder = "#412737", isLight = false
        ),
        "rose_light" to ThemePreset(
            id = "rose_light", name = "Rose Light",
            bg = "#FAF0F5", surface = "#FFF5FA", key = "#FFFFFF", keyBorder = "#F2CEE0",
            text = "#2B0F20", muted = "#8A5774", defaultAccent = "#BE185D",
            chip = "#FFFFFF", chipBorder = "#F2CEE0", isLight = true
        ),
        "contrast" to ThemePreset(
            id = "contrast", name = "High Contrast",
            bg = "#000000", surface = "#000000", key = "#181818", keyBorder = "#FFFFFF",
            text = "#FFFFFF", muted = "#E0E0E0", defaultAccent = "#FFD400",
            chip = "#181818", chipBorder = "#FFFFFF", isLight = false
        ),
        "contrast_light" to ThemePreset(
            id = "contrast_light", name = "Contrast Light",
            bg = "#FFFFFF", surface = "#FFFFFF", key = "#F2F2F2", keyBorder = "#000000",
            text = "#000000", muted = "#222222", defaultAccent = "#0038FF",
            chip = "#F2F2F2", chipBorder = "#000000", isLight = true
        ),
        "cyberpunk" to ThemePreset(
            id = "cyberpunk", name = "Neon Cyberpunk",
            bg = "#0B0416", surface = "#15092A", key = "#220E40", keyBorder = "#FF007F",
            text = "#FFFFFF", muted = "#B388FF", defaultAccent = "#FF007F",
            chip = "#1B0B33", chipBorder = "#00F0FF", isLight = false
        ),
        "cyberpunk_light" to ThemePreset(
            id = "cyberpunk_light", name = "Cyberpunk Light",
            bg = "#FAF5FF", surface = "#F3E8FF", key = "#FFFFFF", keyBorder = "#D8B4FE",
            text = "#2E1065", muted = "#7E22CE", defaultAccent = "#C026D3",
            chip = "#F3E8FF", chipBorder = "#D8B4FE", isLight = true
        ),
        "amoled" to ThemePreset(
            id = "amoled", name = "AMOLED Pure",
            bg = "#000000", surface = "#080808", key = "#141414", keyBorder = "#282828",
            text = "#FFFFFF", muted = "#9CA3AF", defaultAccent = "#3B82F6",
            chip = "#101010", chipBorder = "#242424", isLight = false
        ),
        "amoled_light" to ThemePreset(
            id = "amoled_light", name = "AMOLED Daylight",
            bg = "#F8FAFC", surface = "#FFFFFF", key = "#FFFFFF", keyBorder = "#CBD5E1",
            text = "#0F172A", muted = "#64748B", defaultAccent = "#2563EB",
            chip = "#F1F5F9", chipBorder = "#E2E8F0", isLight = true
        ),
        "forest" to ThemePreset(
            id = "forest", name = "Emerald Forest",
            bg = "#05140B", surface = "#0D2316", key = "#163A25", keyBorder = "#235E3C",
            text = "#F0FDF4", muted = "#86EFAC", defaultAccent = "#10B981",
            chip = "#112E1D", chipBorder = "#235E3C", isLight = false
        ),
        "forest_light" to ThemePreset(
            id = "forest_light", name = "Forest Light",
            bg = "#F0FDF4", surface = "#DCFCE7", key = "#FFFFFF", keyBorder = "#BBF7D0",
            text = "#14532D", muted = "#15803D", defaultAccent = "#059669",
            chip = "#DCFCE7", chipBorder = "#BBF7D0", isLight = true
        ),
        "coral" to ThemePreset(
            id = "coral", name = "Warm Coral",
            bg = "#160B08", surface = "#27140F", key = "#3D1F17", keyBorder = "#673224",
            text = "#FFF7ED", muted = "#FDBA74", defaultAccent = "#F97316",
            chip = "#301812", chipBorder = "#673224", isLight = false
        ),
        "coral_light" to ThemePreset(
            id = "coral_light", name = "Coral Light",
            bg = "#FFF7ED", surface = "#FFEDD5", key = "#FFFFFF", keyBorder = "#FED7AA",
            text = "#7C2D12", muted = "#C2410C", defaultAccent = "#EA580C",
            chip = "#FFEDD5", chipBorder = "#FED7AA", isLight = true
        )
    )

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val MIN_ACCENT_CONTRAST = 3.0
    const val MIN_TEXT_CONTRAST = 4.5

    fun safeContrast(color: Int, background: Int): Double {
        val opaqueBg = if (Color.alpha(background) < 255) ColorUtils.setAlphaComponent(background, 255) else background
        return try {
            ColorUtils.calculateContrast(color, opaqueBg)
        } catch (_: Throwable) {
            1.0
        }
    }

    private fun minContrast(color: Int, backgrounds: List<Int>): Double =
        backgrounds.minOf { safeContrast(color, it) }

    fun ensureContrast(color: Int, backgrounds: List<Int>, ratio: Double = MIN_ACCENT_CONTRAST): Int {
        if (minContrast(color, backgrounds) >= ratio) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        var best = color
        var bestScore = minContrast(color, backgrounds)
        var delta = 0.02f
        while (delta <= 1f) {
            for (sign in intArrayOf(1, -1)) {
                val l = (hsl[2] + sign * delta).coerceIn(0f, 1f)
                val candidate = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], l))
                val score = minContrast(candidate, backgrounds)
                if (score >= ratio) return candidate
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            delta += 0.02f
        }
        return best
    }

    fun readableOn(color: Int, backgrounds: List<Int>, ratio: Double = MIN_TEXT_CONTRAST): Int {
        if (minContrast(color, backgrounds) >= ratio) return color
        val onWhite = minContrast(Color.WHITE, backgrounds)
        val onBlack = minContrast(Color.BLACK, backgrounds)
        return if (onWhite >= onBlack) Color.WHITE else Color.BLACK
    }

    fun accentForText(color: Int, backgrounds: List<Int>): Int {
        val tuned = ensureContrast(color, backgrounds, MIN_TEXT_CONTRAST)
        return if (minContrast(tuned, backgrounds) >= MIN_TEXT_CONTRAST) tuned else readableOn(color, backgrounds)
    }

    fun getContrastingTextColor(bgColor: Int): Int {
        val onWhite = safeContrast(Color.WHITE, bgColor)
        val onBlack = safeContrast(Color.BLACK, bgColor)
        return if (onBlack >= onWhite) Color.BLACK else Color.WHITE
    }

    private fun flat(
        d: Float,
        fill: Int,
        radiusDp: Float,
        strokeDp: Float = 0f,
        strokeColor: Int = 0
    ) = GradientDrawable().apply {
        cornerRadius = radiusDp * d
        setColor(fill)
        if (strokeDp > 0f && strokeColor != Color.TRANSPARENT) {
            setStroke((strokeDp * d).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    private fun states(normal: Drawable, pressed: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    fun getPairedThemeId(currentThemeId: String, targetLight: Boolean): String {
        val baseId = when (currentThemeId) {
            "light", "onyx_light" -> "onyx"
            "md3_dynamic_light" -> "md3_dynamic"
            "lucid-void_light" -> "lucid-void"
            "void_lucent", "void_lucent_light", "void_glass", "void_glass_light", "liquid_glass", "liquid_glass_light" -> "onyx"
            else -> currentThemeId.removeSuffix("_light")
        }
        return if (targetLight) {
            when (baseId) {
                "onyx" -> "light"
                "md3_dynamic" -> "md3_dynamic_light"
                "lucid-void" -> "lucid-void_light"
                else -> "${baseId}_light"
            }
        } else {
            baseId
        }
    }

    fun isSystemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun isAutoMode(p: SharedPreferences): Boolean = p.getString(KEY_THEME_MODE, MODE_AUTO) == MODE_AUTO

    fun lastDarkTheme(p: SharedPreferences): String {
        val id = p.getString(KEY_LAST_DARK, null)
        if (id != null && PRESETS[id]?.isLight == false) return id
        val chosen = p.getString(KEY_THEME, null)
        if (chosen != null) {
            val pairedDark = getPairedThemeId(chosen, targetLight = false)
            if (PRESETS[pairedDark]?.isLight == false) return pairedDark
        }
        return THEME_MD3_DYNAMIC
    }

    fun resolveThemeId(ctx: Context, p: SharedPreferences = prefs(ctx)): String {
        val chosen = p.getString(KEY_THEME, THEME_MD3_DYNAMIC) ?: THEME_MD3_DYNAMIC
        val effectiveChosen = if (chosen.contains("void_lucent") || chosen.contains("void_glass") || chosen.contains("liquid_glass")) {
            p.edit().putString(KEY_THEME, THEME_MD3_DYNAMIC).apply()
            THEME_MD3_DYNAMIC
        } else {
            chosen
        }
        val mode = if (p.contains(KEY_KEYBOARD_THEME_MODE)) {
            p.getString(KEY_KEYBOARD_THEME_MODE, MODE_AUTO) ?: MODE_AUTO
        } else {
            p.getString(KEY_THEME_MODE, MODE_AUTO) ?: MODE_AUTO
        }
        return when (mode) {
            MODE_LIGHT -> getPairedThemeId(effectiveChosen, targetLight = true)
            MODE_DARK -> getPairedThemeId(effectiveChosen, targetLight = false)
            else -> getPairedThemeId(effectiveChosen, targetLight = !isSystemDark(ctx))
        }
    }

    private fun bestTextOn(backgrounds: List<Int>): Int {
        val onWhite = minContrast(Color.WHITE, backgrounds)
        val onBlack = minContrast(Color.BLACK, backgrounds)
        return if (onBlack >= onWhite) Color.BLACK else Color.WHITE
    }

    private fun raised(d: Float, from: Int, to: Int, strokeColor: Int, radiusDp: Float, strokeWidthDp: Float = 1f) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
            cornerRadius = radiusDp * d
            if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke((strokeWidthDp * d).toInt().coerceAtLeast(1), strokeColor)
            }
        }

    private fun neuLight(c: Int) = ColorUtils.blendARGB(c, Color.WHITE, 0.10f)
    private fun neuDark(c: Int) = ColorUtils.blendARGB(c, Color.BLACK, 0.12f)

    fun resolveTypeface(
        ctx: Context? = null,
        fontStyle: String = FONT_DEFAULT,
        bold: Boolean = true,
        useSystemFont: Boolean = false
    ): Typeface {
        val styleInt = if (bold) Typeface.BOLD else Typeface.NORMAL
        if (useSystemFont) {
            return Typeface.create(Typeface.DEFAULT, styleInt)
        }
        if (ctx != null && fontStyle != FONT_DEFAULT && fontStyle.isNotBlank()) {
            val customTf = FontExtensionManager.getTypeface(ctx, fontStyle, bold)
            if (customTf != null) return customTf
        }
        return when (fontStyle) {
            FONT_SERIF -> Typeface.create(Typeface.SERIF, styleInt)
            FONT_MONO -> Typeface.create(Typeface.MONOSPACE, styleInt)
            FONT_ROUNDED -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(Typeface.create("sans-serif-rounded", Typeface.NORMAL), if (bold) 700 else 400, false)
            } else {
                Typeface.create("sans-serif-medium", styleInt)
            }
            FONT_CONDENSED -> Typeface.create("sans-serif-condensed", styleInt)
            FONT_LIGHT -> if (bold) Typeface.create("sans-serif", Typeface.NORMAL) else Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.create(Typeface.DEFAULT, styleInt)
        }
    }

    fun resolveTypeface(fontStyle: String, bold: Boolean): Typeface {
        return resolveTypeface(null, fontStyle, bold, false)
    }

    fun prefFloat(p: SharedPreferences, key: String, def: Float): Float {
        return try {
            p.getFloat(key, def)
        } catch (_: ClassCastException) {
            try {
                p.getInt(key, def.toInt()).toFloat()
            } catch (_: Exception) {
                def
            }
        }
    }

    fun prefInt(p: SharedPreferences, key: String, def: Int): Int {
        return try {
            p.getInt(key, def)
        } catch (_: ClassCastException) {
            try {
                p.getFloat(key, def.toFloat()).toInt()
            } catch (_: Exception) {
                def
            }
        }
    }

    fun loadKeyOverrides(p: SharedPreferences): MutableMap<String, KeyStyleOverride> {
        val raw = p.getString(KEY_STUDIO_OVERRIDES_JSON, null) ?: return mutableMapOf()
        if (raw.isBlank()) return mutableMapOf()
        val map = mutableMapOf<String, KeyStyleOverride>()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.optJSONObject(k) ?: continue
                val ov = KeyStyleOverride(
                    fillColor = if (obj.has("fill")) obj.getInt("fill") else null,
                    textColor = if (obj.has("text")) obj.getInt("text") else null,
                    borderColor = if (obj.has("border")) obj.getInt("border") else null,
                    shadowColor = if (obj.has("shadowCol")) obj.getInt("shadowCol") else null,
                    cornerRadiusDp = if (obj.has("radius")) obj.getDouble("radius").toFloat() else null,
                    borderWidthDp = if (obj.has("borderW")) obj.getDouble("borderW").toFloat() else null,
                    shadowRadiusDp = if (obj.has("shadow")) obj.getDouble("shadow").toFloat() else null
                )
                map[k] = ov
                map[k.lowercase()] = ov
                map[k.uppercase()] = ov
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveKeyOverrides(p: SharedPreferences, map: Map<String, KeyStyleOverride>) {
        val root = JSONObject()
        val seen = HashSet<String>()
        for ((k, v) in map) {
            val canon = if (k.startsWith("group_", ignoreCase = true) || k in setOf("SPACE", "ENTER", "SHIFT", "BACKSPACE", "EMOJI", "space", "enter", "shift", "backspace", "emoji")) {
                k.uppercase()
            } else {
                k.lowercase()
            }
            if (!seen.add(canon)) continue
            val obj = JSONObject()
            v.fillColor?.let { obj.put("fill", it) }
            v.textColor?.let { obj.put("text", it) }
            v.borderColor?.let { obj.put("border", it) }
            v.shadowColor?.let { obj.put("shadowCol", it) }
            v.cornerRadiusDp?.let { obj.put("radius", it.toDouble()) }
            v.borderWidthDp?.let { obj.put("borderW", it.toDouble()) }
            v.shadowRadiusDp?.let { obj.put("shadow", it.toDouble()) }
            if (obj.length() > 0) {
                root.put(canon, obj)
            }
        }
        p.edit().putString(KEY_STUDIO_OVERRIDES_JSON, root.toString()).apply()
    }

    fun setKeyOverride(p: SharedPreferences, keyId: String, override: KeyStyleOverride?) {
        val map = loadKeyOverrides(p)
        map.remove(keyId)
        map.remove(keyId.lowercase())
        map.remove(keyId.uppercase())
        if (override != null && (override.fillColor != null || override.textColor != null ||
                override.borderColor != null || override.shadowColor != null ||
                override.cornerRadiusDp != null || override.borderWidthDp != null ||
                override.shadowRadiusDp != null)
        ) {
            map[keyId] = override
            map[keyId.lowercase()] = override
            map[keyId.uppercase()] = override
        }
        saveKeyOverrides(p, map)
    }

    fun setKeyOverride(ctx: Context, keyId: String, override: KeyStyleOverride?) {
        setKeyOverride(prefs(ctx), keyId, override)
    }

    fun clearAllKeyOverrides(p: SharedPreferences) {
        p.edit().remove(KEY_STUDIO_OVERRIDES_JSON).apply()
    }

    fun clearAllKeyOverrides(ctx: Context) {
        clearAllKeyOverrides(prefs(ctx))
    }

    class StudioKeyDrawable(
        private val density: Float,
        private val fillColor: Int,
        private val shape: String,
        private val radiusDp: Float,
        private val strokeWidthDp: Float,
        private val strokeColor: Int,
        private val shadowDp: Float,
        private val shadowCustomColor: Int? = null,
        private val isLight: Boolean,
        private val glassEnabled: Boolean,
        private val glassOpacity: Int,
        private val glassSpecular: Boolean,
        private val accentColor: Int,
        private val isActionKey: Boolean = false,
        private val isPressed: Boolean = false
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val path = Path()
        private val shadowPath = Path()
        private val innerGemPath = Path()
        private val rect = RectF()
        private val tempRect = RectF()

        private fun buildShapePath(out: Path, r: RectF, shapeType: String, radPx: Float) {
            out.reset()
            val w = r.width()
            val h = r.height()
            if (w <= 0f || h <= 0f) return
            val maxR = min(w, h) / 2f

            when (shapeType) {
                SHAPE_PILL -> {
                    out.addRoundRect(r, maxR, maxR, Path.Direction.CW)
                }
                SHAPE_SHARP -> {
                    val sr = (2.2f * density).coerceAtMost(maxR)
                    out.addRoundRect(r, sr, sr, Path.Direction.CW)
                }
                SHAPE_CUT_CORNER, SHAPE_GEM -> {
                    val cut = (radPx * 0.65f).coerceIn(3f * density, maxR * 0.72f)
                    out.moveTo(r.left + cut, r.top)
                    out.lineTo(r.right - cut, r.top)
                    out.lineTo(r.right, r.top + cut)
                    out.lineTo(r.right, r.bottom - cut)
                    out.lineTo(r.right - cut, r.bottom)
                    out.lineTo(r.left + cut, r.bottom)
                    out.lineTo(r.left, r.bottom - cut)
                    out.lineTo(r.left, r.top + cut)
                    out.close()
                }
                SHAPE_SQUIRCLE -> {
                    val cr = radPx.coerceIn(2f * density, maxR)
                    val k = cr * 0.22f
                    out.moveTo(r.left + cr, r.top)
                    out.lineTo(r.right - cr, r.top)
                    out.cubicTo(r.right - k, r.top, r.right, r.top + k, r.right, r.top + cr)
                    out.lineTo(r.right, r.bottom - cr)
                    out.cubicTo(r.right, r.bottom - k, r.right - k, r.bottom, r.right - cr, r.bottom)
                    out.lineTo(r.left + cr, r.bottom)
                    out.cubicTo(r.left + k, r.bottom, r.left, r.bottom - k, r.left, r.bottom - cr)
                    out.lineTo(r.left, r.top + cr)
                    out.cubicTo(r.left, r.top + k, r.left + k, r.top, r.left + cr, r.top)
                    out.close()
                }
                else -> {
                    val cr = radPx.coerceAtMost(maxR)
                    out.addRoundRect(r, cr, cr, Path.Direction.CW)
                }
            }
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return

            val strokePx = if (glassEnabled && glassSpecular) {
                strokeWidthDp.coerceAtLeast(1.0f) * density
            } else if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                strokeWidthDp * density
            } else 0f

            val shadowPx = (shadowDp * density).coerceAtLeast(0f)
            val halfStroke = strokePx / 2f
            rect.set(
                b.left + halfStroke,
                b.top + halfStroke,
                b.right - halfStroke,
                b.bottom - halfStroke
            )
            if (rect.width() <= 0f || rect.height() <= 0f) return

            val radPx = radiusDp * density
            buildShapePath(path, rect, shape, radPx)

            if (shadowPx > 0.4f && Color.alpha(fillColor) > 0) {
                tempRect.set(rect)
                tempRect.offset(0f, shadowPx * 0.45f)
                buildShapePath(shadowPath, tempRect, shape, radPx)
                shadowPaint.color = if (shadowCustomColor != null) {
                    ColorUtils.setAlphaComponent(shadowCustomColor, if (isLight) 110 else 150)
                } else if (isLight) {
                    Color.argb(46, 15, 23, 42)
                } else {
                    Color.argb(96, 0, 0, 0)
                }
                canvas.drawPath(shadowPath, shadowPaint)
            }

            if (Color.alpha(fillColor) > 0) {
                if (glassEnabled) {
                    val alpha = ((glassOpacity.coerceIn(15, 95) / 100f) * 255f).toInt().coerceIn(25, 245)
                    if (isActionKey) {
                        val actTop = ColorUtils.blendARGB(Color.WHITE, fillColor, if (isLight) 0.30f else 0.35f)
                        val actBottom = ColorUtils.blendARGB(fillColor, Color.BLACK, 0.12f)
                        val effAlpha = if (isLight) (alpha * 0.85f).toInt() else (alpha * 0.70f).toInt().coerceIn(35, 205)
                        fillPaint.shader = LinearGradient(
                            rect.left, rect.top, rect.right, rect.bottom,
                            ColorUtils.setAlphaComponent(actTop, (effAlpha * 1.15f).toInt().coerceAtMost(255)),
                            ColorUtils.setAlphaComponent(actBottom, effAlpha),
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        val topTint = if (isLight) Color.WHITE else ColorUtils.blendARGB(Color.WHITE, fillColor, 0.22f)
                        val botTint = if (isLight) ColorUtils.blendARGB(fillColor, Color.WHITE, 0.65f) else ColorUtils.blendARGB(fillColor, Color.BLACK, 0.15f)
                        val effAlpha = if (isLight) (alpha * 0.70f).toInt().coerceIn(40, 190) else (alpha * 0.45f).toInt().coerceIn(25, 140)
                        fillPaint.shader = LinearGradient(
                            rect.left, rect.top, rect.left, rect.bottom,
                            ColorUtils.setAlphaComponent(topTint, (effAlpha * 1.25f).toInt().coerceAtMost(255)),
                            ColorUtils.setAlphaComponent(botTint, effAlpha),
                            Shader.TileMode.CLAMP
                        )
                    }
                } else {
                    fillPaint.shader = null
                    fillPaint.color = fillColor
                }
                canvas.drawPath(path, fillPaint)

                if (glassEnabled) {
                    val sheenFraction = if (isActionKey) 0.50f else 0.44f
                    val topSheen = if (isLight) {
                        if (isActionKey) Color.argb(130, 255, 255, 255) else Color.argb(95, 255, 255, 255)
                    } else {
                        if (isActionKey) Color.argb(90, 255, 255, 255) else Color.argb(65, 255, 255, 255)
                    }
                    sheenPaint.shader = LinearGradient(
                        rect.left, rect.top, rect.left, rect.top + rect.height() * sheenFraction,
                        topSheen, Color.TRANSPARENT, Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(path, sheenPaint)
                }

                if (shape == SHAPE_GEM) {
                    val inset = (3.2f * density).coerceAtMost(min(rect.width(), rect.height()) * 0.2f)
                    tempRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                    buildShapePath(innerGemPath, tempRect, SHAPE_CUT_CORNER, radPx * 0.7f)
                    strokePaint.shader = null
                    strokePaint.strokeWidth = 0.8f * density
                    strokePaint.color = if (isLight) Color.argb(70, 255, 255, 255) else Color.argb(45, 255, 255, 255)
                    canvas.drawPath(innerGemPath, strokePaint)
                }
            }

            if (strokePx > 0f) {
                strokePaint.strokeWidth = strokePx
                if (glassEnabled && glassSpecular) {
                    val specTop = if (isLight) {
                        Color.argb(if (isActionKey) 215 else 175, 255, 255, 255)
                    } else {
                        Color.argb(if (isActionKey) 175 else 135, 255, 255, 255)
                    }
                    val specBot = if (isLight) {
                        Color.argb(if (isActionKey) 85 else 55, 255, 255, 255)
                    } else {
                        Color.argb(if (isActionKey) 55 else 35, 255, 255, 255)
                    }
                    strokePaint.shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        specTop, specBot, Shader.TileMode.CLAMP
                    )
                } else {
                    strokePaint.shader = null
                    strokePaint.color = strokeColor
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    class HyperAuroraDrawable(
        private val baseColor: Int,
        private val accentPrimary: Int,
        private val accentSecondary: Int,
        private val isLight: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            canvas.drawColor(baseColor)

            val r1 = maxOf(w, h) * 0.78f
            val c1 = ColorUtils.setAlphaComponent(accentPrimary, if (isLight) 48 else 68)
            paint.shader = RadialGradient(
                w * 0.18f, h * 0.22f, r1,
                c1, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRect(b, paint)

            val r2 = maxOf(w, h) * 0.72f
            val c2 = ColorUtils.setAlphaComponent(accentSecondary, if (isLight) 42 else 58)
            paint.shader = RadialGradient(
                w * 0.85f, h * 0.78f, r2,
                c2, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRect(b, paint)

            val topGlow = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(accentPrimary, Color.WHITE, 0.35f),
                if (isLight) 40 else 28
            )
            paint.shader = LinearGradient(
                0f, 0f, 0f, h * 0.28f,
                topGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRect(b, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }




    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[0] = (hsl[0] + degrees + 360f) % 360f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun resolveHyperDynamicColors(
        ctx: Context,
        isLight: Boolean,
        fallbackBg: Int,
        fallbackSurface: Int,
        fallbackKey: Int,
        fallbackAccent: Int
    ): IntArray {
        var dynAccent = fallbackAccent
        var dynBg = fallbackBg
        var dynSurf = fallbackSurface
        var dynKey = fallbackKey

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                dynAccent = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_accent1_600 else android.R.color.system_accent1_300
                )
                dynBg = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900
                )
                dynSurf = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_800
                )
                dynKey = if (isLight) {
                    Color.WHITE
                } else {
                    ColorUtils.blendARGB(dynSurf, Color.WHITE, 0.08f)
                }
            } catch (_: Exception) {}
        } else {
            dynBg = ColorUtils.blendARGB(fallbackBg, fallbackAccent, if (isLight) 0.08f else 0.14f)
            dynSurf = ColorUtils.blendARGB(fallbackSurface, fallbackAccent, if (isLight) 0.10f else 0.18f)
            dynKey = ColorUtils.blendARGB(fallbackKey, fallbackAccent, if (isLight) 0.05f else 0.12f)
        }
        return intArrayOf(dynBg, dynSurf, dynKey, dynAccent)
    }

    class DynamicMd3Palette(
        val bg: Int,
        val surf: Int,
        val key: Int,
        val border: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val chip: Int,
        val chipBorder: Int
    )

    fun resolveMd3DynamicColors(
        ctx: Context,
        isLight: Boolean,
        fallbackBg: Int,
        fallbackSurf: Int,
        fallbackKey: Int,
        fallbackAccent: Int
    ): DynamicMd3Palette {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val bg = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900
                )
                val surf = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_800
                )
                val key = if (isLight) {
                    ContextCompat.getColor(ctx, android.R.color.system_neutral1_0)
                } else {
                    ContextCompat.getColor(ctx, android.R.color.system_neutral1_700)
                }
                val border = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_600
                )
                val text = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
                )
                val muted = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_neutral2_500 else android.R.color.system_neutral2_300
                )
                val accent = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_accent1_600 else android.R.color.system_accent1_300
                )
                val chip = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_accent2_100 else android.R.color.system_accent2_800
                )
                val chipBorder = ContextCompat.getColor(
                    ctx,
                    if (isLight) android.R.color.system_accent2_200 else android.R.color.system_accent2_600
                )
                return DynamicMd3Palette(bg, surf, key, border, text, muted, accent, chip, chipBorder)
            } catch (_: Exception) {}
        }
        val bg = if (isLight) Color.parseColor("#FEF7FF") else Color.parseColor("#1B1B1F")
        val surf = if (isLight) Color.parseColor("#F3EDF7") else Color.parseColor("#202024")
        val key = if (isLight) Color.WHITE else Color.parseColor("#2B2B30")
        val border = if (isLight) Color.parseColor("#CAC4D0") else Color.parseColor("#44474E")
        val text = if (isLight) Color.parseColor("#1D1B20") else Color.parseColor("#E3E2E6")
        val muted = if (isLight) Color.parseColor("#49454F") else Color.parseColor("#8E9099")
        val accent = if (isLight) Color.parseColor("#00639B") else Color.parseColor("#A8C7FA")
        val chip = if (isLight) Color.parseColor("#E8DEF8") else Color.parseColor("#2B2B30")
        val chipBorder = border
        return DynamicMd3Palette(bg, surf, key, border, text, muted, accent, chip, chipBorder)
    }

    fun current(ctx: Context, forceLandscape: Boolean? = null): Skin = buildSkin(ctx, forceLandscape)

    fun buildSkin(ctx: Context, forceLandscape: Boolean? = null): Skin {
        val p = prefs(ctx)
        val themeId = resolveThemeId(ctx, p)
        val preset = PRESETS[themeId] ?: PRESETS["onyx"]!!
        val style = p.getString(KEY_KEY_STYLE, STYLE_DEFAULT) ?: STYLE_DEFAULT

        val rawAccentPref = p.getInt(KEY_ACCENT, Color.parseColor(preset.defaultAccent))
        val keyBorders = p.getBoolean(KEY_KEY_BORDERS, true)

        val keyShape = p.getString(KEY_STUDIO_SHAPE, SHAPE_ROUNDED) ?: SHAPE_ROUNDED
        val cornerRadiusDp = prefFloat(p, KEY_STUDIO_RADIUS, 13f).coerceIn(0f, 28f)
        val borderWidthDp = prefFloat(p, KEY_STUDIO_BORDER_WIDTH, 0f).coerceIn(0f, 4f)
        val shadowDp = prefFloat(p, KEY_STUDIO_SHADOW, 0f).coerceIn(0f, 12f)
        val fontStyle = p.getString(KEY_STUDIO_FONT, FONT_DEFAULT) ?: FONT_DEFAULT
        val fontBold = p.getBoolean(KEY_STUDIO_FONT_BOLD, false)
        val rawFontScale = prefFloat(p, KEY_STUDIO_FONT_SCALE, 0.80f)
        val fontScale = (if (rawFontScale > 10f) rawFontScale / 100f else rawFontScale).coerceIn(0.80f, 1.30f)
        val useSystemFont = p.getBoolean(KEY_USE_SYSTEM_FONT, DEFAULT_USE_SYSTEM_FONT)
        val typeface = resolveTypeface(ctx, fontStyle, fontBold, useSystemFont)

        val isLandscape = forceLandscape ?: (ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val useLandscapeProfile = isLandscape && p.getBoolean(KEY_LANDSCAPE_SEPARATE, false)

        val keySpacingH = if (useLandscapeProfile) {
            prefFloat(p, KEY_LANDSCAPE_H_GAP, 3.0f).coerceIn(1f, 10f)
        } else if (isLandscape) {
            2.5f
        } else {
            prefFloat(p, KEY_STUDIO_H_GAP, 3.0f).coerceIn(1f, 8f)
        }
        val keySpacingV = if (useLandscapeProfile) {
            prefFloat(p, KEY_LANDSCAPE_V_GAP, 3.5f).coerceIn(1f, 10f)
        } else if (isLandscape) {
            3.0f
        } else {
            prefFloat(p, KEY_STUDIO_V_GAP, 6.0f).coerceIn(2f, 12f)
        }
        val keyHeightDp = if (useLandscapeProfile) {
            prefInt(p, KEY_HEIGHT_LANDSCAPE, 36).coerceIn(28, 54)
        } else if (isLandscape) {
            36
        } else {
            prefInt(p, KEY_KEYBOARD_HEIGHT, 48).coerceIn(36, 68)
        }
        val bottomPaddingDp = if (useLandscapeProfile) {
            prefInt(p, KEY_BOTTOM_PADDING_LANDSCAPE, 0).toFloat().coerceIn(0f, 36f)
        } else if (isLandscape) {
            0f
        } else {
            prefInt(p, KEY_STUDIO_BOTTOM_PADDING, 0).toFloat().coerceIn(0f, 48f)
        }
        val sidePaddingDp = if (useLandscapeProfile) {
            prefInt(p, KEY_SIDE_PADDING_LANDSCAPE, 40).toFloat().coerceIn(0f, 120f)
        } else if (isLandscape) {
            val explicitLandPad = p.getInt(KEY_SIDE_PADDING_LANDSCAPE, -1)
            if (explicitLandPad >= 0) explicitLandPad.toFloat().coerceIn(0f, 120f) else 44f
        } else {
            prefInt(p, KEY_STUDIO_SIDE_PADDING, 2).toFloat().coerceIn(0f, 24f)
        }

        val keyTileSize = prefInt(p, KEY_KEY_TILE_SIZE, DEFAULT_KEY_TILE_SIZE).coerceIn(70, 130)
        val keyTileWidth = prefInt(p, KEY_KEY_TILE_WIDTH, DEFAULT_KEY_TILE_WIDTH).coerceIn(70, 130)

        val hyperDynamic = p.getBoolean(KEY_HYPER_DYNAMIC, false)
        val hyperAurora = p.getBoolean(KEY_HYPER_AURORA, false)
        val glassEnabled = p.getBoolean(KEY_GLASS_ENABLED, false)
        val rawGlassOp = prefFloat(p, KEY_GLASS_OPACITY, 0.55f)
        val glassOpacity = (if (rawGlassOp <= 1.5f) (rawGlassOp * 100f).toInt() else rawGlassOp.toInt()).coerceIn(15, 95)
        val glassSpecular = p.getBoolean(KEY_GLASS_SPECULAR, true)
        val keyOverrides = loadKeyOverrides(p)

        var bgCol = Color.parseColor(preset.bg)
        var surfCol = Color.parseColor(preset.surface)
        var keyCol = Color.parseColor(preset.key)
        var rawAccent = rawAccentPref

        val isLucidVoid = (themeId == THEME_LUCID_VOID || themeId == THEME_LUCID_VOID_LIGHT)
        val defaultBlur = if (preset.isLight) DEFAULT_LUCID_VOID_BLUR_LIGHT else DEFAULT_LUCID_VOID_BLUR_DARK
        val blurKey = if (preset.isLight) KEY_LUCID_VOID_BLUR_LIGHT else KEY_LUCID_VOID_BLUR_DARK
        val lucidBlurRadius = if (isLucidVoid) {
            prefInt(p, blurKey, prefInt(p, KEY_LUCID_VOID_BLUR, defaultBlur)).toFloat().coerceIn(0f, 100f)
        } else 0f

        val defaultOp = if (preset.isLight) DEFAULT_LUCID_VOID_OPACITY_LIGHT else DEFAULT_LUCID_VOID_OPACITY_DARK
        val opKey = if (preset.isLight) KEY_LUCID_VOID_OPACITY_LIGHT else KEY_LUCID_VOID_OPACITY_DARK
        val userOpPct = if (isLucidVoid) {
            prefInt(p, opKey, prefInt(p, KEY_LUCID_VOID_OPACITY, defaultOp)).coerceIn(5, 95)
        } else 100
        val opFactor = userOpPct / 100f

        val tintPct = if (isLucidVoid) {
            if (preset.isLight) {
                prefInt(p, KEY_LUCID_VOID_TINT_LIGHT, DEFAULT_LUCID_VOID_TINT_LIGHT).coerceIn(0, 100)
            } else {
                prefInt(p, KEY_LUCID_VOID_TINT_DARK, DEFAULT_LUCID_VOID_TINT_DARK).coerceIn(0, 100)
            }
        } else 0
        val tintFactor = tintPct / 100f

        if (isLucidVoid) {
            rawAccent = if (p.contains(KEY_ACCENT)) rawAccentPref else Color.parseColor(preset.defaultAccent)
            val alpha255 = (opFactor * 255f).toInt().coerceIn(16, 248)
            val baseBg = if (preset.isLight) {
                if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor(preset.bg), Color.parseColor("#94A3B8"), tintFactor * 0.40f)
                else Color.parseColor(preset.bg)
            } else {
                if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor(preset.bg), Color.parseColor("#334155"), tintFactor * 0.45f)
                else Color.parseColor(preset.bg)
            }
            val baseSurf = if (preset.isLight) {
                if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor(preset.surface), Color.parseColor("#64748B"), tintFactor * 0.40f)
                else Color.parseColor(preset.surface)
            } else {
                if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor(preset.surface), Color.parseColor("#1E293B"), tintFactor * 0.45f)
                else Color.parseColor(preset.surface)
            }
            bgCol = ColorUtils.setAlphaComponent(baseBg, alpha255)
            surfCol = ColorUtils.setAlphaComponent(baseSurf, (alpha255 * 0.80f).toInt().coerceIn(12, 242))
        }
        val isMd3Dynamic = (themeId == THEME_MD3_DYNAMIC || themeId == THEME_MD3_DYNAMIC_LIGHT)
        val md3Palette = if (isMd3Dynamic) {
            resolveMd3DynamicColors(ctx, preset.isLight, bgCol, surfCol, keyCol, rawAccentPref)
        } else null

        if (md3Palette != null) {
            bgCol = md3Palette.bg
            surfCol = md3Palette.surf
            keyCol = md3Palette.key
            rawAccent = md3Palette.accent
        } else if (hyperDynamic) {
            val dyn = resolveHyperDynamicColors(ctx, preset.isLight, bgCol, surfCol, keyCol, rawAccentPref)
            bgCol = dyn[0]
            surfCol = dyn[1]
            keyCol = dyn[2]
            rawAccent = dyn[3]
        }

        val presetBorder = when {
            md3Palette != null -> md3Palette.border
            hyperDynamic -> ColorUtils.blendARGB(keyCol, rawAccent, 0.32f)
            else -> Color.parseColor(preset.keyBorder)
        }
        val effectiveGlobalBorderW = if (borderWidthDp > 0f) borderWidthDp else if (keyBorders || style == STYLE_STROKE) 1.0f else 0f
        val hasVisibleBorder = keyBorders || borderWidthDp > 0.05f || (glassEnabled && glassSpecular) || style == STYLE_STROKE
        val borderCol = if (hasVisibleBorder) presetBorder else Color.TRANSPARENT
        val textCol = md3Palette?.text ?: Color.parseColor(preset.text)
        val mutedCol = md3Palette?.muted ?: Color.parseColor(preset.muted)
        val chipCol = md3Palette?.chip ?: (if (hyperDynamic) surfCol else Color.parseColor(preset.chip))
        val chipBorderCol = md3Palette?.chipBorder ?: (if (hyperDynamic) presetBorder else Color.parseColor(preset.chipBorder))
        val effectiveAccent = ensureContrast(rawAccent, listOf(bgCol, surfCol, keyCol, chipCol))

        val barSurfaces = listOf(bgCol, surfCol, chipCol)
        val barText = readableOn(textCol, barSurfaces)
        val specialKeyCol = if (style == STYLE_MATERIAL || style == STYLE_NEUMORPHIC || glassEnabled) {
            ColorUtils.blendARGB(keyCol, bgCol, 0.18f)
        } else keyCol
        val keySurfaces = when {
            style == STYLE_NEUMORPHIC -> listOf(keyCol, specialKeyCol).flatMap { listOf(it, neuLight(it), neuDark(it)) }
            style == STYLE_MATERIAL -> listOf(keyCol, specialKeyCol)
            else -> listOf(keyCol)
        }
        val hasKeyFill = keyBorders || glassEnabled || shadowDp > 0f || borderWidthDp > 0.05f || style == STYLE_NEUMORPHIC || style == STYLE_MATERIAL
        val actualKeySurfaces = if (hasKeyFill) keySurfaces else listOf(bgCol)
        val keyText = readableOn(textCol, actualKeySurfaces)
        val subtleText = accentForText(mutedCol, barSurfaces)
        val accentTextCol = accentForText(rawAccent, barSurfaces)
        val accentOnKeyCol = accentForText(rawAccent, actualKeySurfaces)

        val pressedKeyCol = if (preset.isLight) {
            Color.parseColor("#E5E7EB")
        } else {
            val r = (Color.red(keyCol) + 30).coerceAtMost(255)
            val g = (Color.green(keyCol) + 30).coerceAtMost(255)
            val b = (Color.blue(keyCol) + 30).coerceAtMost(255)
            Color.rgb(r, g, b)
        }

        val accentPressed = ColorUtils.blendARGB(effectiveAccent, Color.BLACK, 0.12f)
        val actionSurfaces = when (style) {
            STYLE_MATERIAL -> listOf(effectiveAccent, accentPressed)
            STYLE_NEUMORPHIC -> listOf(effectiveAccent, neuLight(effectiveAccent), neuDark(effectiveAccent))
            else -> listOf(effectiveAccent)
        }
        val actionText = bestTextOn(actionSurfaces)
        val actionEdge = ColorUtils.blendARGB(effectiveAccent, actionText, 0.45f)
        val neuEdge = if (hasVisibleBorder) presetBorder else Color.TRANSPARENT
        val chipPressed = ColorUtils.blendARGB(chipCol, textCol, 0.12f)

        val bgDrawable: Drawable? = if (hyperAurora) {
            HyperAuroraDrawable(bgCol, effectiveAccent, shiftHue(effectiveAccent, 48f), preset.isLight)
        } else if (isLucidVoid) {
            val d = ctx.resources.displayMetrics.density
            val topRadius = 20f * d
            val strokeW = (1.2f * d).toInt().coerceAtLeast(1)
            val strokeAlpha = if (preset.isLight) {
                ((opFactor * 0.40f + 0.35f) * 255).toInt().coerceIn(40, 240)
            } else {
                ((opFactor * 0.30f + 0.20f) * 255).toInt().coerceIn(30, 220)
            }
            val strokeCol = if (preset.isLight) {
                val tintedStroke = ColorUtils.blendARGB(Color.WHITE, Color.parseColor("#94A3B8"), tintFactor * 0.25f)
                ColorUtils.setAlphaComponent(tintedStroke, strokeAlpha)
            } else {
                ColorUtils.setAlphaComponent(Color.parseColor("#94A3B8"), strokeAlpha)
            }
            val startCol = if (preset.isLight) {
                val tintedStart = ColorUtils.blendARGB(Color.WHITE, Color.parseColor("#94A3B8"), tintFactor * 0.35f)
                ColorUtils.setAlphaComponent(tintedStart, (opFactor * 0.85f * 255).toInt().coerceIn(20, 240))
            } else {
                ColorUtils.setAlphaComponent(Color.parseColor("#0A0D14"), (opFactor * 0.95f * 255).toInt().coerceIn(25, 250))
            }
            val endCol = if (preset.isLight) {
                val tintedEnd = ColorUtils.blendARGB(Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"), tintFactor * 0.35f)
                ColorUtils.setAlphaComponent(tintedEnd, (opFactor * 0.70f * 255).toInt().coerceIn(15, 230))
            } else {
                ColorUtils.setAlphaComponent(Color.parseColor("#141923"), (opFactor * 0.85f * 255).toInt().coerceIn(20, 245))
            }
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(startCol, endCol)).apply {
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    0f, 0f,
                    0f, 0f
                )
                setStroke(strokeW, strokeCol)
            }
        } else null

        fun buildCustomKeyStates(
            d: Float,
            special: Boolean,
            override: KeyStyleOverride?
        ): Drawable {
            val effRadius = override?.cornerRadiusDp ?: when (style) {
                STYLE_STROKE, STYLE_MATERIAL -> if (cornerRadiusDp == 13f) 8f else cornerRadiusDp
                STYLE_NEUMORPHIC -> if (cornerRadiusDp == 13f) 14f else cornerRadiusDp
                else -> cornerRadiusDp
            }
            val effShadow = override?.shadowRadiusDp ?: shadowDp
            val effStrokeW = override?.borderWidthDp ?: when {
                override?.borderColor != null -> effectiveGlobalBorderW.coerceAtLeast(1.2f)
                style == STYLE_STROKE -> if (hasVisibleBorder) effectiveGlobalBorderW.coerceAtLeast(1.4f) else 0f
                hasVisibleBorder -> effectiveGlobalBorderW.coerceAtLeast(1.0f)
                else -> 0f
            }

            if (style == STYLE_NEUMORPHIC && !glassEnabled && keyShape == SHAPE_ROUNDED && override == null && effShadow == 0f) {
                if (hasKeyFill) {
                    val base = if (special) specialKeyCol else keyCol
                    val hi = neuLight(base)
                    val lo = neuDark(base)
                    return states(raised(d, hi, lo, neuEdge, effRadius, effStrokeW), raised(d, lo, hi, neuEdge, effRadius, effStrokeW))
                }
            }

            val baseFill = override?.fillColor ?: when {
                hasKeyFill -> if (special) specialKeyCol else keyCol
                else -> if (special) ColorUtils.blendARGB(keyCol, bgCol, 0.25f) else Color.TRANSPARENT
            }
            val baseStroke = override?.borderColor ?: when {
                style == STYLE_STROKE && hasVisibleBorder -> if (special) effectiveAccent else presetBorder
                hasVisibleBorder -> borderCol
                else -> Color.TRANSPARENT
            }
            val pressFill = override?.fillColor?.let {
                ColorUtils.blendARGB(it, if (preset.isLight) Color.BLACK else Color.WHITE, 0.16f)
            } ?: pressedKeyCol

            val normalD = StudioKeyDrawable(
                density = d,
                fillColor = baseFill,
                shape = keyShape,
                radiusDp = effRadius,
                strokeWidthDp = effStrokeW,
                strokeColor = baseStroke,
                shadowDp = effShadow,
                shadowCustomColor = override?.shadowColor,
                isLight = preset.isLight,
                glassEnabled = glassEnabled,
                glassOpacity = glassOpacity,
                glassSpecular = glassSpecular,
                accentColor = effectiveAccent,
                isActionKey = false,
                isPressed = false
            )
            val pressedD = StudioKeyDrawable(
                density = d,
                fillColor = pressFill,
                shape = keyShape,
                radiusDp = effRadius,
                strokeWidthDp = effStrokeW.coerceAtLeast(1f),
                strokeColor = effectiveAccent,
                shadowDp = (effShadow * 0.5f),
                shadowCustomColor = override?.shadowColor,
                isLight = preset.isLight,
                glassEnabled = glassEnabled,
                glassOpacity = (glassOpacity + 15).coerceAtMost(95),
                glassSpecular = glassSpecular,
                accentColor = effectiveAccent,
                isActionKey = false,
                isPressed = true
            )
            return states(normalD, pressedD)
        }

        fun buildLucidVoidKeyStates(
            d: Float,
            isAction: Boolean,
            special: Boolean
        ): Drawable {
            val rad = 10f * d
            var strokeW = (1f * d).toInt().coerceAtLeast(1)
            val pressStrokeW = (1.2f * d).toInt().coerceAtLeast(1)

            var normalStart: Int
            var normalEnd: Int
            var normalStroke: Int
            var pressStart: Int
            var pressEnd: Int
            val pressStroke: Int

            if (preset.isLight) {
                val whiteBase = if (tintFactor > 0f) ColorUtils.blendARGB(Color.WHITE, Color.parseColor("#CBD5E1"), tintFactor * 0.35f) else Color.WHITE
                val slateBase = if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor("#E2E8F0"), Color.parseColor("#94A3B8"), tintFactor * 0.35f) else Color.parseColor("#E2E8F0")
                val strokeBase = if (tintFactor > 0f) ColorUtils.blendARGB(Color.WHITE, Color.parseColor("#CBD5E1"), tintFactor * 0.20f) else Color.WHITE

                if (isAction) {
                    normalStart = ColorUtils.setAlphaComponent(whiteBase, ((opFactor * 0.85f + 0.18f) * 255).toInt().coerceIn(35, 250))
                    normalEnd = ColorUtils.setAlphaComponent(slateBase, ((opFactor * 0.65f + 0.12f) * 255).toInt().coerceIn(28, 235))
                    normalStroke = ColorUtils.setAlphaComponent(strokeBase, ((opFactor * 0.55f + 0.30f) * 255).toInt().coerceIn(60, 255))
                    pressStart = ColorUtils.setAlphaComponent(whiteBase, 245)
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#F1F5F9"), 235)
                    pressStroke = strokeBase
                } else if (special) {
                    val funcBase = if (tintFactor > 0f) ColorUtils.blendARGB(Color.parseColor("#F1F5F9"), Color.parseColor("#94A3B8"), tintFactor * 0.30f) else Color.parseColor("#F1F5F9")
                    normalStart = ColorUtils.setAlphaComponent(funcBase, ((opFactor * 0.65f + 0.10f) * 255).toInt().coerceIn(25, 230))
                    normalEnd = ColorUtils.setAlphaComponent(slateBase, ((opFactor * 0.50f + 0.08f) * 255).toInt().coerceIn(20, 220))
                    normalStroke = ColorUtils.setAlphaComponent(strokeBase, ((opFactor * 0.40f + 0.18f) * 255).toInt().coerceIn(40, 240))
                    pressStart = ColorUtils.setAlphaComponent(whiteBase, 235)
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#E2E8F0"), 220)
                    pressStroke = strokeBase
                } else {
                    normalStart = ColorUtils.setAlphaComponent(whiteBase, ((opFactor * 0.80f + 0.15f) * 255).toInt().coerceIn(30, 245))
                    normalEnd = ColorUtils.setAlphaComponent(slateBase, ((opFactor * 0.60f + 0.10f) * 255).toInt().coerceIn(25, 230))
                    normalStroke = ColorUtils.setAlphaComponent(strokeBase, ((opFactor * 0.50f + 0.25f) * 255).toInt().coerceIn(50, 250))
                    pressStart = ColorUtils.setAlphaComponent(whiteBase, ((opFactor * 0.90f + 0.35f) * 255).toInt().coerceIn(60, 255))
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#F8FAFC"), ((opFactor * 0.80f + 0.30f) * 255).toInt().coerceIn(55, 255))
                    pressStroke = strokeBase
                }
            } else {
                if (isAction) {
                    normalStart = ColorUtils.setAlphaComponent(Color.parseColor("#334155"), ((opFactor * 0.80f + 0.15f) * 255).toInt().coerceIn(25, 245))
                    normalEnd = ColorUtils.setAlphaComponent(Color.parseColor("#1E293B"), ((opFactor * 0.65f + 0.10f) * 255).toInt().coerceIn(20, 235))
                    normalStroke = ColorUtils.setAlphaComponent(Color.parseColor("#94A3B8"), ((opFactor * 0.40f + 0.18f) * 255).toInt().coerceIn(30, 230))
                    pressStart = ColorUtils.setAlphaComponent(Color.parseColor("#475569"), ((opFactor * 0.90f + 0.30f) * 255).toInt().coerceIn(50, 255))
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#334155"), ((opFactor * 0.75f + 0.25f) * 255).toInt().coerceIn(45, 250))
                    pressStroke = ColorUtils.setAlphaComponent(Color.parseColor("#CBD5E1"), 230)
                } else if (special) {
                    normalStart = ColorUtils.setAlphaComponent(Color.parseColor("#1E293B"), ((opFactor * 0.55f + 0.08f) * 255).toInt().coerceIn(18, 220))
                    normalEnd = ColorUtils.setAlphaComponent(Color.parseColor("#0F172A"), ((opFactor * 0.45f + 0.06f) * 255).toInt().coerceIn(14, 210))
                    normalStroke = ColorUtils.setAlphaComponent(Color.parseColor("#64748B"), ((opFactor * 0.30f + 0.12f) * 255).toInt().coerceIn(20, 200))
                    pressStart = ColorUtils.setAlphaComponent(Color.parseColor("#334155"), ((opFactor * 0.70f + 0.20f) * 255).toInt().coerceIn(35, 240))
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#1E293B"), ((opFactor * 0.60f + 0.15f) * 255).toInt().coerceIn(30, 230))
                    pressStroke = ColorUtils.setAlphaComponent(Color.parseColor("#94A3B8"), ((opFactor * 0.45f + 0.25f) * 255).toInt().coerceIn(40, 240))
                } else {
                    normalStart = ColorUtils.setAlphaComponent(Color.parseColor("#334155"), ((opFactor * 0.70f + 0.12f) * 255).toInt().coerceIn(20, 240))
                    normalEnd = ColorUtils.setAlphaComponent(Color.parseColor("#1E293B"), ((opFactor * 0.55f + 0.08f) * 255).toInt().coerceIn(16, 230))
                    normalStroke = ColorUtils.setAlphaComponent(Color.parseColor("#94A3B8"), ((opFactor * 0.35f + 0.15f) * 255).toInt().coerceIn(25, 220))
                    pressStart = ColorUtils.setAlphaComponent(Color.parseColor("#475569"), ((opFactor * 0.85f + 0.25f) * 255).toInt().coerceIn(40, 255))
                    pressEnd = ColorUtils.setAlphaComponent(Color.parseColor("#334155"), ((opFactor * 0.70f + 0.20f) * 255).toInt().coerceIn(35, 250))
                    pressStroke = ColorUtils.setAlphaComponent(Color.parseColor("#CBD5E1"), ((opFactor * 0.45f + 0.30f) * 255).toInt().coerceIn(50, 255))
                }
            }

            if (tintFactor > 0f) {
                val milkyTone = if (preset.isLight) Color.WHITE else Color.parseColor("#475569")
                normalStart = ColorUtils.blendARGB(normalStart, milkyTone, tintFactor * 0.45f)
                normalEnd = ColorUtils.blendARGB(normalEnd, milkyTone, tintFactor * 0.40f)
                pressStart = ColorUtils.blendARGB(pressStart, milkyTone, tintFactor * 0.45f)
                pressEnd = ColorUtils.blendARGB(pressEnd, milkyTone, tintFactor * 0.40f)
            }
            if (lucidBlurRadius > 0f) {
                val blurFraction = (lucidBlurRadius / 80f).coerceIn(0f, 1f)
                normalStroke = ColorUtils.setAlphaComponent(
                    normalStroke,
                    (Color.alpha(normalStroke) * (1f - blurFraction * 0.35f)).toInt().coerceIn(20, 255)
                )
                strokeW = (strokeW * (1f + blurFraction * 0.5f)).toInt().coerceAtLeast(1)
            }

            val normalD = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(normalStart, normalEnd)).apply {
                cornerRadius = rad
                if (keyBorders || borderWidthDp > 0.05f) {
                    setStroke(strokeW, normalStroke)
                }
            }
            val pressedD = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(pressStart, pressEnd)).apply {
                cornerRadius = rad
                if (keyBorders || borderWidthDp > 0.05f) {
                    setStroke(pressStrokeW, pressStroke)
                }
            }
            return states(normalD, pressedD)
        }

        val defaultKeyBackground: (Float, Boolean) -> Drawable = { d, special ->
            if (isLucidVoid && keyOverrides.isEmpty()) {
                buildLucidVoidKeyStates(d, isAction = false, special = special)
            } else {
                val ov = if (special) {
                    keyOverrides["group_special"] ?: keyOverrides["GROUP_SPECIAL"] ?: keyOverrides["group:special"]
                } else {
                    keyOverrides["group_letters"] ?: keyOverrides["GROUP_LETTERS"] ?: keyOverrides["group:letter"]
                }
                buildCustomKeyStates(d, special, ov)
            }
        }

        val perKeyBackground: (String, Float, Boolean, Boolean) -> Drawable = { keyId, d, special, isAction ->
            val norm = keyId.lowercase()
            if (isLucidVoid && keyOverrides.isEmpty()) {
                buildLucidVoidKeyStates(d, isAction = (isAction || norm == "enter"), special = special)
            } else if (isAction || norm == "enter") {
                val enterOv = keyOverrides[norm] ?: keyOverrides[keyId.uppercase()] ?: keyOverrides["group_special"] ?: keyOverrides["group:enter"]
                val effRadius = enterOv?.cornerRadiusDp ?: cornerRadiusDp
                val effShadow = enterOv?.shadowRadiusDp ?: shadowDp
                val effStrokeW = enterOv?.borderWidthDp ?: if (hasVisibleBorder) effectiveGlobalBorderW.coerceAtLeast(1.0f) else 0f
                val fill = enterOv?.fillColor ?: effectiveAccent
                val stroke = enterOv?.borderColor ?: actionEdge
                val press = ColorUtils.blendARGB(fill, Color.BLACK, 0.14f)
                states(
                    StudioKeyDrawable(d, fill, keyShape, effRadius, effStrokeW, stroke, effShadow, enterOv?.shadowColor, preset.isLight, glassEnabled, (glassOpacity + 20).coerceAtMost(95), glassSpecular, effectiveAccent, isActionKey = true, isPressed = false),
                    StudioKeyDrawable(d, press, keyShape, effRadius, effStrokeW.coerceAtLeast(1.2f), Color.WHITE, effShadow * 0.5f, enterOv?.shadowColor, preset.isLight, glassEnabled, 95, glassSpecular, effectiveAccent, isActionKey = true, isPressed = true)
                )
            } else {
                val groupOv = when {
                    norm == "space" -> keyOverrides["space"] ?: keyOverrides["SPACE"] ?: keyOverrides["group_letters"] ?: keyOverrides["GROUP_LETTERS"] ?: keyOverrides["group:space"]
                    special -> keyOverrides["group_special"] ?: keyOverrides["GROUP_SPECIAL"] ?: keyOverrides["group:special"]
                    else -> keyOverrides["group_letters"] ?: keyOverrides["GROUP_LETTERS"] ?: keyOverrides["group:letter"]
                }
                val ov = keyOverrides[norm] ?: keyOverrides[keyId.uppercase()] ?: groupOv
                buildCustomKeyStates(d, special, ov)
            }
        }

        return Skin(
            id = preset.id,
            label = preset.name,
            isLight = preset.isLight,
            background = bgCol,
            surface = surfCol,
            keyColor = ColorUtils.setAlphaComponent(keyCol, 255),
            keyBorderColor = borderCol,
            textColor = barText,
            keyTextColor = keyText,
            subtleTextColor = subtleText,
            accent = effectiveAccent,
            accentText = accentTextCol,
            accentOnKey = accentOnKeyCol,
            elevationDp = if (shadowDp > 0f) shadowDp else 0f,
            barBackground = surfCol,
            chipBackground = chipCol,
            chipBorderColor = chipBorderCol,
            keyBackground = defaultKeyBackground,
            actionKeyBackground = { d ->
                perKeyBackground("ENTER", d, true, true)
            },
            actionTextColor = if (isLucidVoid) {
                if (preset.isLight) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
            } else actionText,
            chipDrawable = { d, active ->
                when {
                    isLucidVoid -> {
                        val pillRadius = 14f
                        val normalCol = if (active) {
                            if (preset.isLight) Color.parseColor("#33FFFFFF") else Color.parseColor("#3D334155")
                        } else {
                            if (preset.isLight) Color.parseColor("#1FFFFFFF") else Color.parseColor("#240A0D14")
                        }
                        val strokeCol = if (active) {
                            if (preset.isLight) Color.parseColor("#80FFFFFF") else Color.parseColor("#6694A3B8")
                        } else {
                            if (preset.isLight) Color.parseColor("#4DFFFFFF") else Color.parseColor("#2E94A3B8")
                        }
                        val pressedCol = ColorUtils.blendARGB(normalCol, if (preset.isLight) Color.WHITE else Color.BLACK, 0.25f)
                        states(
                            raised(d, normalCol, normalCol, strokeCol, pillRadius, 1f),
                            raised(d, pressedCol, pressedCol, strokeCol, pillRadius, 1f)
                        )
                    }
                    glassEnabled -> {
                        val fill = if (active) effectiveAccent else chipCol
                        val edge = if (active) effectiveAccent else chipBorderCol
                        val normal = StudioKeyDrawable(
                            density = d,
                            fillColor = fill,
                            shape = SHAPE_PILL,
                            radiusDp = 16f,
                            strokeWidthDp = 1.0f,
                            strokeColor = edge,
                            shadowDp = 0.5f,
                            isLight = preset.isLight,
                            glassEnabled = true,
                            glassOpacity = (glassOpacity + 10).coerceAtMost(95),
                            glassSpecular = glassSpecular,
                            accentColor = effectiveAccent,
                            isActionKey = active
                        )
                        val pressed = StudioKeyDrawable(
                            density = d,
                            fillColor = if (active) accentPressed else chipPressed,
                            shape = SHAPE_PILL,
                            radiusDp = 16f,
                            strokeWidthDp = 1.2f,
                            strokeColor = effectiveAccent,
                            shadowDp = 0.2f,
                            isLight = preset.isLight,
                            glassEnabled = true,
                            glassOpacity = 95,
                            glassSpecular = glassSpecular,
                            accentColor = effectiveAccent,
                            isActionKey = active
                        )
                        states(normal, pressed)
                    }
                    style == STYLE_STROKE -> {
                        val fill = if (active) effectiveAccent else chipCol
                        val edge = if (active) effectiveAccent else chipBorderCol
                        states(
                            flat(d, fill, 12f, 1.2f, edge),
                            flat(d, if (active) fill else chipPressed, 12f, 1.2f, effectiveAccent)
                        )
                    }
                    style == STYLE_MATERIAL -> {
                        val fill = if (active) effectiveAccent else chipCol
                        states(
                            flat(d, fill, 12f),
                            flat(d, if (active) accentPressed else chipPressed, 12f)
                        )
                    }
                    style == STYLE_NEUMORPHIC -> {
                        val base = if (active) effectiveAccent else chipCol
                        val edge = if (active) actionEdge else neuEdge
                        states(
                            raised(d, neuLight(base), neuDark(base), edge, 12f),
                            raised(d, neuDark(base), neuLight(base), edge, 12f)
                        )
                    }
                    else -> {
                        val fill = if (active) effectiveAccent else chipCol
                        val stroke = if (active) effectiveAccent else chipBorderCol
                        val normal = flat(d, fill, 18f, 1.0f, stroke)
                        val pressed = flat(d, fill, 18f, 1.2f, effectiveAccent)
                        states(normal, pressed)
                    }
                }
            },
            keyShape = keyShape,
            cornerRadiusDp = cornerRadiusDp,
            borderWidthDp = effectiveGlobalBorderW,
            shadowDp = shadowDp,
            typeface = typeface,
            fontBold = fontBold,
            fontScale = fontScale,
            keySpacingH = keySpacingH,
            keySpacingV = keySpacingV,
            keyHeightDp = keyHeightDp,
            keyTileSize = keyTileSize,
            keyTileWidth = keyTileWidth,
            bottomPaddingDp = bottomPaddingDp,
            sidePaddingDp = sidePaddingDp,
            glassEnabled = glassEnabled || isLucidVoid,
            glassOpacity = glassOpacity,
            glassSpecular = glassSpecular,
            hyperDynamic = hyperDynamic,
            hyperAurora = hyperAurora,
            backgroundDrawable = bgDrawable,
            blurRadius = lucidBlurRadius,
            keyOverrides = keyOverrides,
            perKeyBgProvider = perKeyBackground
        )
    }

    fun exportThemeJson(ctx: Context): String {
        val p = prefs(ctx)
        val root = JSONObject()
        root.put("gnosis_theme_version", 1)
        root.put(KEY_THEME, p.getString(KEY_THEME, "onyx") ?: "onyx")
        root.put(KEY_THEME_MODE, p.getString(KEY_THEME_MODE, MODE_AUTO) ?: MODE_AUTO)
        root.put(KEY_KEY_STYLE, p.getString(KEY_KEY_STYLE, STYLE_DEFAULT) ?: STYLE_DEFAULT)
        root.put(KEY_ACCENT, p.getInt(KEY_ACCENT, DEFAULT_ACCENT))
        root.put(KEY_KEY_BORDERS, p.getBoolean(KEY_KEY_BORDERS, true))
        root.put(KEY_STUDIO_SHAPE, p.getString(KEY_STUDIO_SHAPE, SHAPE_ROUNDED) ?: SHAPE_ROUNDED)
        root.put(KEY_STUDIO_RADIUS, prefFloat(p, KEY_STUDIO_RADIUS, 13f).toDouble())
        root.put(KEY_STUDIO_BORDER_WIDTH, prefFloat(p, KEY_STUDIO_BORDER_WIDTH, 0f).toDouble())
        root.put(KEY_STUDIO_SHADOW, prefFloat(p, KEY_STUDIO_SHADOW, 0f).toDouble())
        root.put(KEY_STUDIO_H_GAP, prefFloat(p, KEY_STUDIO_H_GAP, 3f).toDouble())
        root.put(KEY_STUDIO_V_GAP, prefFloat(p, KEY_STUDIO_V_GAP, 6f).toDouble())
        root.put(KEY_STUDIO_BOTTOM_PADDING, prefInt(p, KEY_STUDIO_BOTTOM_PADDING, 0))
        root.put(KEY_STUDIO_SIDE_PADDING, prefInt(p, KEY_STUDIO_SIDE_PADDING, 2))
        root.put(KEY_KEYBOARD_HEIGHT, prefInt(p, KEY_KEYBOARD_HEIGHT, 48))
        root.put(KEY_KEY_TILE_SIZE, prefInt(p, KEY_KEY_TILE_SIZE, DEFAULT_KEY_TILE_SIZE))
        root.put(KEY_KEY_TILE_WIDTH, prefInt(p, KEY_KEY_TILE_WIDTH, DEFAULT_KEY_TILE_WIDTH))
        root.put(KEY_USE_SYSTEM_FONT, p.getBoolean(KEY_USE_SYSTEM_FONT, DEFAULT_USE_SYSTEM_FONT))
        root.put(KEY_STUDIO_FONT, p.getString(KEY_STUDIO_FONT, FONT_DEFAULT) ?: FONT_DEFAULT)
        root.put(KEY_STUDIO_FONT_BOLD, p.getBoolean(KEY_STUDIO_FONT_BOLD, true))
        root.put(KEY_STUDIO_FONT_SCALE, prefFloat(p, KEY_STUDIO_FONT_SCALE, 0.80f).toDouble())
        root.put(KEY_HYPER_DYNAMIC, p.getBoolean(KEY_HYPER_DYNAMIC, false))
        root.put(KEY_HYPER_AURORA, p.getBoolean(KEY_HYPER_AURORA, false))
        root.put(KEY_GLASS_ENABLED, p.getBoolean(KEY_GLASS_ENABLED, false))
        root.put(KEY_GLASS_OPACITY, prefFloat(p, KEY_GLASS_OPACITY, 0.55f).toDouble())
        root.put(KEY_GLASS_SPECULAR, p.getBoolean(KEY_GLASS_SPECULAR, true))
        if (p.contains(KEY_LUCID_VOID_OPACITY)) root.put(KEY_LUCID_VOID_OPACITY, p.getInt(KEY_LUCID_VOID_OPACITY, 48))
        if (p.contains(KEY_LUCID_VOID_BLUR)) root.put(KEY_LUCID_VOID_BLUR, p.getInt(KEY_LUCID_VOID_BLUR, 48))
        if (p.contains(KEY_LUCID_VOID_TINT_LIGHT)) root.put(KEY_LUCID_VOID_TINT_LIGHT, p.getInt(KEY_LUCID_VOID_TINT_LIGHT, DEFAULT_LUCID_VOID_TINT_LIGHT))
        if (p.contains(KEY_LUCID_VOID_TINT_DARK)) root.put(KEY_LUCID_VOID_TINT_DARK, p.getInt(KEY_LUCID_VOID_TINT_DARK, DEFAULT_LUCID_VOID_TINT_DARK))
        root.put(KEY_LANDSCAPE_SEPARATE, p.getBoolean(KEY_LANDSCAPE_SEPARATE, false))
        root.put(KEY_LAYOUT_LANDSCAPE, p.getString(KEY_LAYOUT_LANDSCAPE, "qwerty") ?: "qwerty")
        root.put(KEY_LAYOUT_STYLE_LANDSCAPE, p.getString(KEY_LAYOUT_STYLE_LANDSCAPE, STYLE_REVAMPED) ?: STYLE_REVAMPED)
        root.put(KEY_HEIGHT_LANDSCAPE, prefInt(p, KEY_HEIGHT_LANDSCAPE, 42))
        root.put(KEY_LANDSCAPE_H_GAP, prefFloat(p, KEY_LANDSCAPE_H_GAP, 4.0f).toDouble())
        root.put(KEY_LANDSCAPE_V_GAP, prefFloat(p, KEY_LANDSCAPE_V_GAP, 4.5f).toDouble())
        root.put(KEY_BOTTOM_PADDING_LANDSCAPE, prefInt(p, KEY_BOTTOM_PADDING_LANDSCAPE, 0))
        root.put(KEY_SIDE_PADDING_LANDSCAPE, prefInt(p, KEY_SIDE_PADDING_LANDSCAPE, 8))
        root.put(KEY_LANDSCAPE_SPLIT, p.getBoolean(KEY_LANDSCAPE_SPLIT, false))
        val overridesStr = p.getString(KEY_STUDIO_OVERRIDES_JSON, null)
        if (!overridesStr.isNullOrBlank()) {
            try {
                root.put("per_key_overrides", JSONObject(overridesStr))
            } catch (_: Exception) {}
        }
        return root.toString(2)
    }

    fun importThemeJson(ctx: Context, jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            if (!root.has(KEY_THEME) && !root.has("gnosis_theme_version")) return false
            val e = prefs(ctx).edit()
            if (root.has(KEY_THEME)) e.putString(KEY_THEME, root.getString(KEY_THEME))
            if (root.has(KEY_THEME_MODE)) e.putString(KEY_THEME_MODE, root.getString(KEY_THEME_MODE))
            if (root.has(KEY_KEY_STYLE)) e.putString(KEY_KEY_STYLE, root.getString(KEY_KEY_STYLE))
            if (root.has(KEY_ACCENT)) e.putInt(KEY_ACCENT, root.getInt(KEY_ACCENT))
            if (root.has(KEY_KEY_BORDERS)) e.putBoolean(KEY_KEY_BORDERS, root.getBoolean(KEY_KEY_BORDERS))
            if (root.has(KEY_STUDIO_SHAPE)) e.putString(KEY_STUDIO_SHAPE, root.getString(KEY_STUDIO_SHAPE))
            if (root.has(KEY_STUDIO_RADIUS)) e.putFloat(KEY_STUDIO_RADIUS, root.getDouble(KEY_STUDIO_RADIUS).toFloat().coerceIn(0f, 28f))
            if (root.has(KEY_STUDIO_BORDER_WIDTH)) e.putFloat(KEY_STUDIO_BORDER_WIDTH, root.getDouble(KEY_STUDIO_BORDER_WIDTH).toFloat().coerceIn(0f, 4f))
            if (root.has(KEY_STUDIO_SHADOW)) e.putFloat(KEY_STUDIO_SHADOW, root.getDouble(KEY_STUDIO_SHADOW).toFloat().coerceIn(0f, 12f))
            if (root.has(KEY_KEY_TILE_SIZE)) e.putInt(KEY_KEY_TILE_SIZE, root.getInt(KEY_KEY_TILE_SIZE).coerceIn(70, 130))
            if (root.has(KEY_KEY_TILE_WIDTH)) e.putInt(KEY_KEY_TILE_WIDTH, root.getInt(KEY_KEY_TILE_WIDTH).coerceIn(70, 130))
            if (root.has(KEY_USE_SYSTEM_FONT)) e.putBoolean(KEY_USE_SYSTEM_FONT, root.getBoolean(KEY_USE_SYSTEM_FONT))
            if (root.has(KEY_STUDIO_FONT)) e.putString(KEY_STUDIO_FONT, root.getString(KEY_STUDIO_FONT))
            if (root.has(KEY_STUDIO_FONT_BOLD)) e.putBoolean(KEY_STUDIO_FONT_BOLD, root.getBoolean(KEY_STUDIO_FONT_BOLD))
            if (root.has(KEY_STUDIO_FONT_SCALE)) {
                val sc = root.getDouble(KEY_STUDIO_FONT_SCALE).toFloat()
                e.putFloat(KEY_STUDIO_FONT_SCALE, (if (sc > 10f) sc / 100f else sc).coerceIn(0.80f, 1.30f))
            }
            if (root.has(KEY_HYPER_DYNAMIC)) e.putBoolean(KEY_HYPER_DYNAMIC, root.getBoolean(KEY_HYPER_DYNAMIC))
            if (root.has(KEY_HYPER_AURORA)) e.putBoolean(KEY_HYPER_AURORA, root.getBoolean(KEY_HYPER_AURORA))
            if (root.has(KEY_GLASS_ENABLED)) e.putBoolean(KEY_GLASS_ENABLED, root.getBoolean(KEY_GLASS_ENABLED))
            if (root.has(KEY_GLASS_OPACITY)) {
                val op = root.getDouble(KEY_GLASS_OPACITY).toFloat()
                e.putFloat(KEY_GLASS_OPACITY, (if (op > 1.5f) op / 100f else op).coerceIn(0.15f, 0.95f))
            }
            if (root.has(KEY_GLASS_SPECULAR)) e.putBoolean(KEY_GLASS_SPECULAR, root.getBoolean(KEY_GLASS_SPECULAR))
            if (root.has(KEY_LUCID_VOID_OPACITY)) e.putInt(KEY_LUCID_VOID_OPACITY, root.getInt(KEY_LUCID_VOID_OPACITY).coerceIn(5, 95))
            if (root.has(KEY_LUCID_VOID_BLUR)) e.putInt(KEY_LUCID_VOID_BLUR, root.getInt(KEY_LUCID_VOID_BLUR).coerceIn(0, 100))
            if (root.has(KEY_LUCID_VOID_TINT_LIGHT)) e.putInt(KEY_LUCID_VOID_TINT_LIGHT, root.getInt(KEY_LUCID_VOID_TINT_LIGHT).coerceIn(0, 100))
            if (root.has(KEY_LUCID_VOID_TINT_DARK)) e.putInt(KEY_LUCID_VOID_TINT_DARK, root.getInt(KEY_LUCID_VOID_TINT_DARK).coerceIn(0, 100))
            if (root.has(KEY_LANDSCAPE_SEPARATE)) e.putBoolean(KEY_LANDSCAPE_SEPARATE, root.getBoolean(KEY_LANDSCAPE_SEPARATE))
            if (root.has(KEY_LAYOUT_LANDSCAPE)) e.putString(KEY_LAYOUT_LANDSCAPE, root.getString(KEY_LAYOUT_LANDSCAPE))
            if (root.has(KEY_LAYOUT_STYLE_LANDSCAPE)) e.putString(KEY_LAYOUT_STYLE_LANDSCAPE, root.getString(KEY_LAYOUT_STYLE_LANDSCAPE))
            if (root.has(KEY_HEIGHT_LANDSCAPE)) e.putInt(KEY_HEIGHT_LANDSCAPE, root.getInt(KEY_HEIGHT_LANDSCAPE).coerceIn(32, 58))
            if (root.has(KEY_LANDSCAPE_H_GAP)) e.putFloat(KEY_LANDSCAPE_H_GAP, root.getDouble(KEY_LANDSCAPE_H_GAP).toFloat().coerceIn(1f, 10f))
            if (root.has(KEY_LANDSCAPE_V_GAP)) e.putFloat(KEY_LANDSCAPE_V_GAP, root.getDouble(KEY_LANDSCAPE_V_GAP).toFloat().coerceIn(1f, 10f))
            if (root.has(KEY_BOTTOM_PADDING_LANDSCAPE)) e.putInt(KEY_BOTTOM_PADDING_LANDSCAPE, root.getInt(KEY_BOTTOM_PADDING_LANDSCAPE).coerceIn(0, 36))
            if (root.has(KEY_SIDE_PADDING_LANDSCAPE)) e.putInt(KEY_SIDE_PADDING_LANDSCAPE, root.getInt(KEY_SIDE_PADDING_LANDSCAPE).coerceIn(0, 64))
            if (root.has(KEY_LANDSCAPE_SPLIT)) e.putBoolean(KEY_LANDSCAPE_SPLIT, root.getBoolean(KEY_LANDSCAPE_SPLIT))
            if (root.has("per_key_overrides")) {
                e.putString(KEY_STUDIO_OVERRIDES_JSON, root.getJSONObject("per_key_overrides").toString())
            }
            e.apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}
