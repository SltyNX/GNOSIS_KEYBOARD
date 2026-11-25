package gnosis.keyboard.ui

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import gnosis.keyboard.Skins

data class ConceptThemeTokens(
    val mode: String,
    val isLight: Boolean,
    val accent: Int,
    val onAccentText: Int,
    val bg: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val cardBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val chipBg: Int,
    val chipBorder: Int,
    val subtleDivider: Int,
    val topBarBg: Int,
    val topBarStroke: Int,
    val iconContainerBg: Int,
    val iconContainerStroke: Int,
    val cardRadiusDp: Float = 20f,
    val strokeWidthDp: Float = 1.0f
)

object ConceptDesignTokens {

    val ACCENT_PALETTE = listOf(
        Color.parseColor("#2563EB"),
        Color.parseColor("#7C3AED"),
        Color.parseColor("#0D9488"),
        Color.parseColor("#EA580C"),
        Color.parseColor("#DC2626"),
        Color.parseColor("#DB2777"),
        Color.parseColor("#475569"),
        Color.parseColor("#059669")
    )

    fun resolve(context: Context): ConceptThemeTokens {
        val p = Skins.prefs(context)
        val mode = if (p.contains(Skins.KEY_THEME_MODE)) {
            p.getString(Skins.KEY_THEME_MODE, Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        } else {
            p.getString("settings_theme_mode", Skins.MODE_AUTO) ?: Skins.MODE_AUTO
        }
        val isLight = when (mode) {
            Skins.MODE_LIGHT -> true
            Skins.MODE_DARK -> false
            else -> !Skins.isSystemDark(context)
        }

        val savedManualAccent = if (p.contains("settings_accent_color")) {
            p.getInt("settings_accent_color", Skins.DEFAULT_ACCENT)
        } else {
            p.getInt(Skins.KEY_ACCENT, Skins.DEFAULT_ACCENT)
        }

        val defaultAccent = if (isLight) Color.parseColor("#2563EB") else Color.parseColor("#3B82F6")
        val accent = if (savedManualAccent != 0 && savedManualAccent != Skins.DEFAULT_ACCENT) savedManualAccent else defaultAccent

        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.45) Color.parseColor("#0F172A") else Color.WHITE

        val bg = if (isLight) Color.parseColor("#F6F7FA") else Color.parseColor("#000000")
        val surf = if (isLight) Color.parseColor("#FFFFFF") else Color.parseColor("#121212")
        val surfElevated = if (isLight) Color.parseColor("#FFFFFF") else Color.parseColor("#1A1A1A")
        val stroke = if (isLight) Color.parseColor("#E2E8F0") else Color.parseColor("#262626")
        val text = if (isLight) Color.parseColor("#0F172A") else Color.parseColor("#FFFFFF")
        val subText = if (isLight) Color.parseColor("#64748B") else Color.parseColor("#9E9E9E")
        val divider = if (isLight) Color.parseColor("#E8ECF2") else Color.parseColor("#1F1F1F")
        val chipFill = if (isLight) Color.parseColor("#F1F5F9") else Color.parseColor("#1A1A1A")
        val chipStroke = if (isLight) Color.parseColor("#E2E8F0") else Color.parseColor("#2C2C2C")
        val topBar = if (isLight) Color.argb(248, 255, 255, 255) else Color.argb(250, 14, 14, 14)
        val topBarEdge = if (isLight) Color.parseColor("#E2E8F0") else Color.parseColor("#242424")
        val iconBg = ColorUtils.setAlphaComponent(accent, if (isLight) 25 else 40)
        val iconStroke = ColorUtils.setAlphaComponent(accent, if (isLight) 60 else 75)

        return ConceptThemeTokens(
            mode = mode,
            isLight = isLight,
            accent = accent,
            onAccentText = onAccent,
            bg = bg,
            surface = surf,
            surfaceElevated = surfElevated,
            cardBorder = stroke,
            textPrimary = text,
            textSecondary = subText,
            chipBg = chipFill,
            chipBorder = chipStroke,
            subtleDivider = divider,
            topBarBg = topBar,
            topBarStroke = topBarEdge,
            iconContainerBg = iconBg,
            iconContainerStroke = iconStroke
        )
    }
}
