package gnosis.keyboard

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {
    private var vibrator: Vibrator? = null

    private fun vibrator(ctx: Context): Vibrator? {
        vibrator?.let { return it }
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ctx.getSystemService(Vibrator::class.java)
        }
        vibrator = v
        return v
    }

    private const val HAPTIC_FEEDBACK_SETTING = "haptic_feedback_enabled"

    fun problem(ctx: Context): String? {
        val v = vibrator(ctx)
        if (v == null || !v.hasVibrator()) {
            return "This device has no vibration motor, so haptic feedback isn't supported."
        }
        val systemOn = try {
            Settings.System.getInt(ctx.contentResolver, HAPTIC_FEEDBACK_SETTING, 1) != 0
        } catch (_: Exception) {
            true
        }
        if (!systemOn) {
            return "Touch feedback is turned off in your device settings. Turn on Touch feedback / Vibration & haptics under Sound & vibration to feel key presses."
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) {
            return "Battery saver is on, and it can switch vibration off on many devices."
        }
        if (!v.hasAmplitudeControl()) {
            return "This device can't vary vibration strength, so the intensity slider will have little effect."
        }
        return null
    }

    fun buzz(ctx: Context, pressure: Int, view: View? = null) {
        if (pressure <= 0) return
        if (view != null && view.isAttachedToWindow && performOnView(view, pressure)) return
        vibrate(ctx, pressure)
    }

    /**
     * Produces a crisp, precise micro-tick for granular gestures (e.g. spacebar cursor scrolling).
     * Replicates physical hardware dial/detent resistance.
     */
    fun tick(ctx: Context, view: View? = null) {
        if (view != null && view.isAttachedToWindow) {
            val viewTick = when {
                Build.VERSION.SDK_INT >= 34 -> view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                Build.VERSION.SDK_INT >= 27 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                else -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            if (viewTick) return
        }
        val v = vibrator(ctx)?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f)
                .compose()
            v.vibrate(effect)
            return
        }
        val fallbackEffect = VibrationEffect.createOneShot(4L, 45)
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            v.vibrate(fallbackEffect, attrs)
        } else {
            v.vibrate(fallbackEffect)
        }
    }

    private fun performOnView(view: View, pressure: Int): Boolean {
        val constant = when {
            pressure <= 40 -> HapticFeedbackConstants.KEYBOARD_TAP
            pressure <= 75 -> HapticFeedbackConstants.VIRTUAL_KEY
            else -> HapticFeedbackConstants.LONG_PRESS
        }
        return view.performHapticFeedback(constant)
    }

    private fun vibrate(ctx: Context, pressure: Int) {
        val v = vibrator(ctx)?.takeIf { it.hasVibrator() } ?: return
        val clamped = pressure.coerceIn(1, 100)
        val ms = (4L + (clamped * 18L) / 100L).coerceAtLeast(1L)

        val amp = (40 + (clamped * 215) / 100).coerceIn(1, 255)
        val effect = VibrationEffect.createOneShot(ms, amp)
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            v.vibrate(effect, attrs)
        } else {
            v.vibrate(effect)
        }
    }
}
