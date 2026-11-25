package gnosis.keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

class LetterKeyView(
    context: Context,
    val char: Char,
    val secondaryChar: String?,
    private val density: Float,
    private var skin: Skin,
    keyHeightDp: Float,
    private val isDeva: Boolean = false,
    private val showSecondary: Boolean = true,
    widthScale: Float = 1f
) : FrameLayout(context) {

    val primaryTv: TextView = TextView(context)
    val secondaryTv: TextView = TextView(context)

    private var hasSec = secondaryChar != null && showSecondary
    private var lastW = -1
    private var lastH = -1

    init {
        tag = char
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        val keyTxtCol = skin.textColorFor(char.toString(), special = false)

        primaryTv.apply {
            gravity = Gravity.CENTER
            setTextColor(keyTxtCol)
            typeface = skin.typeface
            includeFontPadding = false
            isClickable = false
            isFocusable = false
        }
        addView(
            primaryTv,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        val secColor = computeSecondaryColor(skin, keyTxtCol)
        secondaryTv.apply {
            gravity = Gravity.CENTER
            setTextColor(secColor)
            typeface = skin.boldTypeface
            includeFontPadding = true
            setPadding(dp(2f), dp(1.5f), dp(2f), dp(1.5f))
            isClickable = false
            isFocusable = false

            if (hasSec) {
                text = secondaryChar
                visibility = View.VISIBLE
            } else {
                text = ""
                visibility = View.GONE
            }
        }

        addView(
            secondaryTv,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        )

        applySizing(wDp = (38f * widthScale).coerceAtLeast(18f), hDp = keyHeightDp.coerceAtLeast(24f))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
            lastW = w
            lastH = h
            applySizing(wDp = w / density, hDp = h / density)
        }
    }

    private fun applySizing(wDp: Float, hDp: Float) {
        val sizeFactor = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)
        val scale = (skin.fontScale * sizeFactor).coerceIn(0.75f, 1.45f)
        val wRatio = (wDp / 36f).coerceIn(0.50f, 1.25f)
        val hRatio = (hDp / 50f).coerceIn(0.50f, 1.25f)
        val dimRatio = minOf(wRatio, hRatio)

        val basePrimary = if (isDeva) 18.0f else 19.5f
        val primarySp = if (hasSec) {
            (basePrimary * dimRatio * scale * 0.94f).coerceIn(11.5f, 22.0f)
        } else {
            (basePrimary * dimRatio * scale).coerceIn(12f, 23.5f)
        }
        val secondarySp = (8.4f * dimRatio * scale).coerceIn(7.0f, 10.2f)

        val sFactor = (skin.keyTileSize / 100f).coerceIn(0.70f, 1.30f)
        val vGapFactor = (2f - sFactor).coerceIn(0.15f, 2.2f)
        val spV = (skin.keySpacingV * vGapFactor).coerceAtLeast(0.5f)

        val wFactor = (skin.keyTileWidth / 100f).coerceIn(0.70f, 1.30f) * sFactor
        val hGapFactor = (2f - wFactor).coerceIn(0.15f, 2.2f)
        val spH = (skin.keySpacingH * hGapFactor).coerceAtLeast(0.5f)

        val spHPx = (spH * density).coerceAtLeast(1f * density)
        val spVPx = (spV * density).coerceAtLeast(1f * density)
        val cornerRadiusPx = (skin.cornerRadiusDp * density).coerceIn(3f * density, 16f * density)

        primaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, primarySp)
        if (hasSec) {
            val topShift = (spVPx * 0.40f + 2.8f * density * dimRatio).toInt()
            val endShift = (spHPx * 0.35f + 1.8f * density * dimRatio).toInt()
            primaryTv.setPadding(0, topShift, endShift, 0)
        } else {
            primaryTv.setPadding(0, 0, 0, 0)
        }

        secondaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, secondarySp)
        val topSecMargin = (spVPx + 2.0f * density + cornerRadiusPx * 0.12f).toInt().coerceAtLeast((2f * density).toInt())
        val endSecMargin = (spHPx + 3.0f * density + cornerRadiusPx * 0.20f).toInt().coerceAtLeast((3f * density).toInt())
        (secondaryTv.layoutParams as? LayoutParams)?.let { lp ->
            if (lp.topMargin != topSecMargin || lp.marginEnd != endSecMargin) {
                lp.topMargin = topSecMargin
                lp.marginEnd = endSecMargin
                secondaryTv.layoutParams = lp
            }
        }
    }

    private fun dp(v: Float): Int = (v * density).toInt()

    private fun computeSecondaryColor(skin: Skin, baseColor: Int = skin.textColorFor(char.toString())): Int {
        if (skin.id.startsWith("contrast")) {
            return baseColor
        }
        val contrast = Skins.safeContrast(baseColor, skin.keyColor)
        val alpha = if (contrast > 4.5) 195 else 230
        return ColorUtils.setAlphaComponent(baseColor, alpha)
    }

    fun updatePrimaryText(text: String) {
        primaryTv.text = text
    }

    fun updateSecondaryText(sec: String?, show: Boolean) {
        hasSec = sec != null && show
        if (hasSec) {
            secondaryTv.text = sec
            secondaryTv.visibility = View.VISIBLE
        } else {
            secondaryTv.text = ""
            secondaryTv.visibility = View.GONE
        }
        val currentW = if (lastW > 0) lastW / density else 38f
        val currentH = if (lastH > 0) lastH / density else 50f
        applySizing(currentW, currentH)
    }

    fun updateSkin(newSkin: Skin) {
        skin = newSkin
        val keyTxtCol = newSkin.textColorFor(char.toString(), special = false)
        primaryTv.setTextColor(keyTxtCol)
        primaryTv.typeface = newSkin.typeface
        secondaryTv.setTextColor(computeSecondaryColor(newSkin, keyTxtCol))
        secondaryTv.typeface = newSkin.boldTypeface
        val currentW = if (lastW > 0) lastW / density else 38f
        val currentH = if (lastH > 0) lastH / density else 50f
        applySizing(currentW, currentH)
    }
}
