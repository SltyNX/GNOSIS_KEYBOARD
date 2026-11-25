package gnosis.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class VoicePermissionActivity : Activity() {

    companion object {
        const val REQ_CODE_RECORD_AUDIO = 2001
        const val ACTION_VOICE_PERMISSION_RESULT = "gnosis.keyboard.VOICE_PERMISSION_RESULT"
        const val EXTRA_GRANTED = "extra_granted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            broadcastResult(true)
            finish()
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_CODE_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Toast.makeText(this, "Microphone enabled for GNOSIS KEYBOARD", Toast.LENGTH_SHORT).show()
            } else {
                val blocked = !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
                if (blocked) {
                    Toast.makeText(this, "Microphone permission required. Please enable in Settings.", Toast.LENGTH_LONG).show()
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice typing", Toast.LENGTH_SHORT).show()
                }
            }
            broadcastResult(granted)
            finish()
        }
    }

    private fun broadcastResult(granted: Boolean) {
        val intent = Intent(ACTION_VOICE_PERMISSION_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_GRANTED, granted)
        }
        sendBroadcast(intent)
    }
}
