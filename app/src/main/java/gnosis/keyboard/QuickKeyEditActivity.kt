package gnosis.keyboard

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class QuickKeyEditActivity : Activity() {

    companion object {
        const val EXTRA_INDEX = "quick_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        val keys = ToolbarConfig.loadQuick(this)
        if (index !in keys.indices) {
            finish()
            return
        }

        val skin = Skins.buildSkin(this)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        window.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))

        val input = EditText(this).apply {
            setText(keys[index])
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            gravity = Gravity.CENTER
            hint = "Type or paste"
            privateImeOptions = ToolbarConfig.IME_QUICK_EDIT
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(skin.textColor)
            setHintTextColor(skin.subtleTextColor)
            background = skin.chipDrawable(density, false)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f))
        }

        ToolbarConfig.limitLength(input)

        fun save() {
            val list = ToolbarConfig.loadQuick(this).toMutableList()
            if (index in list.indices) {
                val default = ToolbarConfig.DEFAULT_QUICK.getOrElse(index) { list[index] }
                list[index] = ToolbarConfig.clip(input.text.toString())
                    .ifEmpty { default }
                ToolbarConfig.saveQuick(this, list)
            }
            finish()
        }

        input.setOnEditorActionListener { _, _, _ -> save(); true }

        fun button(label: String, active: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (active) skin.actionTextColor else skin.textColor)
            background = skin.chipDrawable(density, active)
            layoutParams = LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
                marginStart = dp(4f)
                marginEnd = dp(4f)
            }
            setOnClickListener { onClick() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(14f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(skin.surface)
                setStroke(dp(1f), skin.chipBorderColor)
            }
            layoutParams = FrameLayout.LayoutParams(dp(300f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(64f)
            }

            addView(TextView(context).apply {
                text = "Quick key"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(skin.textColor)
                setPadding(0, 0, 0, dp(10f))
            })
            addView(input)
            addView(TextView(context).apply {
                text = "Up to ${ToolbarConfig.MAX_QUICK_LEN} characters. Leave empty for the default."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(skin.subtleTextColor)
                setPadding(0, dp(6f), 0, dp(12f))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(button("Cancel", false) { finish() })
                addView(button("Save", true) { save() })
            })
        }

        setContentView(FrameLayout(this).apply {
            addView(card)
            setOnClickListener { finish() }
        })
    }
}
