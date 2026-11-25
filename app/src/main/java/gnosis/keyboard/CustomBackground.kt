package gnosis.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File

object CustomBackground {
    const val KEY_ENABLED = "custom_bg_enabled"
    const val KEY_VERSION = "custom_bg_version"
    const val KEY_DIM = "custom_bg_dim"
    const val DEFAULT_DIM = 35
    const val KEY_ZOOM = "custom_bg_zoom"
    const val KEY_CX = "custom_bg_cx"
    const val KEY_CY = "custom_bg_cy"
    const val KEY_EXTEND = "custom_bg_extend"
    private const val BARS_DP = 46
    const val MAX_ZOOM = 6f

    private const val FILE_NAME = "keyboard_bg.jpg"
    private const val MAX_EDGE = 1440

    private var cached: Bitmap? = null
    private var cachedVersion = -1L

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun isEnabled(ctx: Context): Boolean =
        Skins.prefs(ctx).getBoolean(KEY_ENABLED, false) && file(ctx).exists()

    fun extendToBars(ctx: Context): Boolean =
        isEnabled(ctx) && Skins.prefs(ctx).getBoolean(KEY_EXTEND, false)

    fun dim(ctx: Context): Int = Skins.prefs(ctx).getInt(KEY_DIM, DEFAULT_DIM).coerceIn(0, 80)

    fun import(ctx: Context, uri: Uri): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            val rotation = try {
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) { 0f }

            val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
            if (rotation != 0f || scale < 1f) {
                val m = Matrix().apply {
                    if (rotation != 0f) postRotate(rotation)
                    if (scale < 1f) postScale(scale, scale)
                }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Skins.prefs(ctx).edit()
                .putBoolean(KEY_ENABLED, true)
                .putFloat(KEY_ZOOM, 1f)
                .putFloat(KEY_CX, 0.5f)
                .putFloat(KEY_CY, 0.5f)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun remove(ctx: Context) {
        file(ctx).delete()
        cached = null
        cachedVersion = -1L
        Skins.prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, false)
            .putLong(KEY_VERSION, System.currentTimeMillis())
            .apply()
    }

    fun bitmap(ctx: Context): Bitmap? {
        if (!isEnabled(ctx)) return null
        val version = Skins.prefs(ctx).getLong(KEY_VERSION, 0L)
        if (cached == null || cachedVersion != version) {
            cached = try { BitmapFactory.decodeFile(file(ctx).path) } catch (_: Exception) { null }
            cachedVersion = version
        }
        return cached
    }

    class Crop(val zoom: Float, val cx: Float, val cy: Float)

    fun crop(ctx: Context): Crop {
        val p = Skins.prefs(ctx)
        return Crop(
            p.getFloat(KEY_ZOOM, 1f).coerceIn(1f, MAX_ZOOM),
            p.getFloat(KEY_CX, 0.5f).coerceIn(0f, 1f),
            p.getFloat(KEY_CY, 0.5f).coerceIn(0f, 1f)
        )
    }

    fun saveCrop(ctx: Context, zoom: Float, cx: Float, cy: Float) {
        Skins.prefs(ctx).edit()
            .putFloat(KEY_ZOOM, zoom.coerceIn(1f, MAX_ZOOM))
            .putFloat(KEY_CX, cx.coerceIn(0f, 1f))
            .putFloat(KEY_CY, cy.coerceIn(0f, 1f))
            .putLong(KEY_VERSION, System.currentTimeMillis())
            .apply()
    }

    fun keyboardRatio(ctx: Context): Float {
        val d = ctx.resources.displayMetrics
        val p = Skins.prefs(ctx)
        val keyH = p.getInt(Skins.KEY_KEYBOARD_HEIGHT, 52)
        val rows = if (p.getBoolean(Skins.KEY_NUMBER_ROW, false)) 5 else 4
        val bars = if (p.getBoolean(KEY_EXTEND, false)) BARS_DP else 0
        val heightDp = bars + 10 + rows * (keyH + 8)
        return d.widthPixels.toFloat() / (heightDp * d.density)
    }

    fun visibleFraction(bw: Int, bh: Int, ratio: Float, zoom: Float): Pair<Float, Float> {
        val bmpRatio = bw.toFloat() / bh
        val (w, h) = if (bmpRatio > ratio) (bh * ratio) to bh.toFloat() else bw.toFloat() to (bw / ratio)
        return (w / zoom / bw) to (h / zoom / bh)
    }

    fun sourceRect(bw: Int, bh: Int, ratio: Float, zoom: Float, cx: Float, cy: Float, out: Rect) {
        val (fw, fh) = visibleFraction(bw, bh, ratio, zoom)
        val w = fw * bw
        val h = fh * bh
        val left = (cx * bw - w / 2).coerceIn(0f, bw - w)
        val top = (cy * bh - h / 2).coerceIn(0f, bh - h)
        out.set(left.toInt(), top.toInt(), (left + w).toInt(), (top + h).toInt())
    }

    fun drawable(ctx: Context, skin: Skin, dimOverride: Int? = null): Drawable? {
        val bmp = bitmap(ctx) ?: return null
        val overlay = if (skin.isLight) Color.WHITE else Color.BLACK
        val c = crop(ctx)
        return CenterCropDrawable(bmp, overlay, (dimOverride ?: dim(ctx)).coerceIn(0, 80) * 255 / 100)
            .also { it.setCrop(c.zoom, c.cx, c.cy) }
    }
}

class CenterCropDrawable(
    private val bitmap: Bitmap,
    private val overlay: Int,
    overlayAlpha: Int
) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint().apply { color = overlay; alpha = overlayAlpha }
    private var overlayAlphaValue = overlayAlpha
    private val src = Rect()
    private var zoom = 1f
    private var cx = 0.5f
    private var cy = 0.5f

    fun setCrop(zoom: Float, cx: Float, cy: Float) {
        this.zoom = zoom; this.cx = cx; this.cy = cy
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val ratio = b.width().toFloat() / b.height()
        CustomBackground.sourceRect(bitmap.width, bitmap.height, ratio, zoom, cx, cy, src)
        canvas.drawBitmap(bitmap, src, b, paint)
        if (overlayAlphaValue > 0) canvas.drawRect(b, overlayPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}
