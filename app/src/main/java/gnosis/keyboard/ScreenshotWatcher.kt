package gnosis.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

object ScreenshotWatcher {
    const val KEY_LAST_SCAN = "clip_screenshot_last_scan"

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var appContext: Context? = null
    private val isScanning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val inProgressIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    private val scanRunnable = Runnable { appContext?.let { scan(it) } }
    private var lastScanMs = 0L

    fun sync(ctx: Context, scanNow: Boolean = true) {
        val app = ctx.applicationContext
        appContext = app
        val wanted = ClipboardHistory.isEnabled(app, ClipboardHistory.CAT_SCREENSHOT) && hasPermission(app)
        if (wanted && observer == null) {
            val obs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    handler.removeCallbacks(scanRunnable)
                    handler.postDelayed(scanRunnable, 200L)
                }
            }
            try {
                app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
                observer = obs
            } catch (_: Exception) {}
        } else if (!wanted && observer != null) {
            try { app.contentResolver.unregisterContentObserver(observer!!) } catch (_: Exception) {}
            observer = null
        }
        val now = System.currentTimeMillis()
        if (wanted && scanNow && (now - lastScanMs > 15_000L)) {
            lastScanMs = now
            scan(app)
        }
    }

    private fun looksLikeScreenshot(name: String?, relPath: String?): Boolean {
        val hay = ((name ?: "") + "|" + (relPath ?: "")).lowercase()
        return hay.contains("screenshot") || hay.contains("screen_shot") ||
            hay.contains("screen shot") || hay.contains("screencap")
    }

    private fun scan(ctx: Context) {
        if (!ClipboardHistory.isEnabled(ctx, ClipboardHistory.CAT_SCREENSHOT) || !hasPermission(ctx)) return
        if (!isScanning.compareAndSet(false, true)) return
        val prefs = Skins.prefs(ctx)
        val nowSec = System.currentTimeMillis() / 1000L
        val since = prefs.getLong(KEY_LAST_SCAN, nowSec)
        Thread {
            try {
                val projection = buildList {
                    add(MediaStore.Images.Media._ID)
                    add(MediaStore.Images.Media.DISPLAY_NAME)
                    add(MediaStore.Images.Media.MIME_TYPE)
                    add(MediaStore.Images.Media.DATE_ADDED)
                    if (Build.VERSION.SDK_INT >= 29) {
                        add(MediaStore.Images.Media.RELATIVE_PATH)
                        add(MediaStore.Images.Media.IS_PENDING)
                    }
                }.toTypedArray()
                var newest = since
                ctx.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                    arrayOf((since - 10L).toString()),
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val relCol = if (Build.VERSION.SDK_INT >= 29)
                        c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1
                    val pendingCol = if (Build.VERSION.SDK_INT >= 29)
                        c.getColumnIndex(MediaStore.Images.Media.IS_PENDING) else -1

                    var seen = 0
                    while (c.moveToNext() && seen < 10) {
                        seen++
                        if (pendingCol >= 0 && c.getInt(pendingCol) != 0) continue
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol)
                        val rel = if (relCol >= 0) c.getString(relCol) else null
                        if (!looksLikeScreenshot(name, rel)) continue
                        newest = maxOf(newest, c.getLong(dateCol))
                        if (ClipboardHistory.isDismissedSource(ctx, id)) continue
                        if (ClipboardHistory.hasSource(ctx, ClipboardHistory.CAT_SCREENSHOT, id)) continue
                        if (!inProgressIds.add(id)) continue
                        try {
                            importShot(ctx, id, c.getString(mimeCol) ?: "image/png")
                        } finally {
                            inProgressIds.remove(id)
                        }
                    }
                }
                if (newest >= since) prefs.edit().putLong(KEY_LAST_SCAN, maxOf(newest, nowSec)).apply()
            } catch (_: Exception) {
            } finally {
                isScanning.set(false)
            }
        }.start()
    }

    private fun importShot(ctx: Context, id: Long, mime: String) {
        try {
            if (ClipboardHistory.hasSource(ctx, ClipboardHistory.CAT_SCREENSHOT, id)) return
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val ext = if (mime.contains("jpeg") || mime.contains("jpg")) ".jpg" else ".png"
            val target = File(ClipboardHistory.imagesDir(ctx), "shot_$id$ext")
            if (!target.exists() || target.length() == 0L) {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
            if (target.length() > 0) {
                ClipboardHistory.addImage(
                    ctx, target.absolutePath, mime,
                    category = ClipboardHistory.CAT_SCREENSHOT, sourceId = id
                )
            } else {
                target.delete()
            }
        } catch (_: Exception) {}
    }
}
