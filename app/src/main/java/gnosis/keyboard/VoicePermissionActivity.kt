package gnosis.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class VoicePermissionActivity : ComponentActivity() {

    companion object {
        const val ACTION_VOICE_PERMISSION_RESULT = "gnosis.keyboard.VOICE_PERMISSION_RESULT"
        const val EXTRA_GRANTED = "extra_granted"
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Microphone enabled for GNOSIS KEYBOARD", Toast.LENGTH_SHORT).show()
        } else {
            val blocked = !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            if (blocked) {
                Toast.makeText(this, "Microphone permission required. Please enable in Settings.", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            broadcastResult(true)
            finish()
            return
        }

        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun broadcastResult(granted: Boolean) {
        val intent = Intent(ACTION_VOICE_PERMISSION_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_GRANTED, granted)
        }
        sendBroadcast(intent)
    }
}
