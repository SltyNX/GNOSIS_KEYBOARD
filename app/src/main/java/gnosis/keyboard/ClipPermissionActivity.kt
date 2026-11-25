package gnosis.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ClipPermissionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val ACTION_RESULT = "gnosis.keyboard.CLIP_PERMISSION_RESULT"
        const val EXTRA_GRANTED = "granted"
    }

    private lateinit var category: String

    private val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        finishWith(result.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: ClipboardHistory.CAT_SCREENSHOT

        val needed = when (category) {
            ClipboardHistory.CAT_SCREENSHOT -> arrayOf(ScreenshotWatcher.requiredPermission())
            else -> emptyArray()
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) {
            finishWith(true)
        } else {
            launcher.launch(needed.toTypedArray())
        }
    }

    private fun finishWith(granted: Boolean) {
        val enable = granted
        if (enable) ClipboardHistory.setEnabled(this, category, true)
        if (category == ClipboardHistory.CAT_SCREENSHOT) {
            if (granted) {
                ScreenshotWatcher.sync(this, scanNow = false)
            } else {
                Toast.makeText(
                    this,
                    "Photo access is needed to pick up screenshots. You can allow it in the app's system settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        sendBroadcast(Intent(ACTION_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_GRANTED, enable)
        })
        finish()
    }
}
