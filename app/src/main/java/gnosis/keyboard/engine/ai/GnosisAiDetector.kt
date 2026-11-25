package gnosis.keyboard.engine.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import gnosis.keyboard.engine.delight.GnosisDelightBridge

/**
 * Gnosis AI Device & Hardware Compatibility Detector
 *
 * Discovers available hardware acceleration, Android version features,
 * and system AI services to select the best execution backend.
 */
object GnosisAiDetector {

    data class AiCapabilityReport(
        val isHardwareAccelerated: Boolean,
        val isSystemNeuralCoreAvailable: Boolean,
        val isUniversalEngineActive: Boolean,
        val accelerationBackend: String,
        val memoryTier: String,
        val statusSummary: String,
        val details: List<String>
    ) {
        val isSystemAiCoreAvailable: Boolean get() = isSystemNeuralCoreAvailable
    }

    fun inspectCapabilities(context: Context): AiCapabilityReport {
        val details = mutableListOf<String>()
        val osVersion = Build.VERSION.SDK_INT
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        details.add("Device: $deviceModel (Android ${Build.VERSION.RELEASE}, API $osVersion)")

        // 1. Check System Neural Service / OnDeviceIntelligence availability
        var systemNeuralPresent = false
        var hasPrivilegedPermission = false

        if (osVersion >= 34) {
            try {
                val service = context.getSystemService("on_device_intelligence")
                if (service != null) {
                    details.add("System Neural Intelligence Service: Active")
                    systemNeuralPresent = true
                    hasPrivilegedPermission = true
                }
            } catch (_: Exception) {}
        }
        if (!systemNeuralPresent) {
            details.add("Neural Architecture: Standalone Neural Engine")
        }

        // 2. Hardware and CPU capabilities
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val memoryTier = when {
            totalRamMb >= 7000 -> "High (8GB+ RAM)"
            totalRamMb >= 3500 -> "Standard (4-6GB RAM)"
            else -> "Lightweight (<4GB RAM)"
        }
        details.add("CPU Hardware: $cores Cores, Total RAM: ${totalRamMb}MB ($memoryTier)")

        // 3. Native Prediction Pipeline
        val nativeLoaded = GnosisDelightBridge.isLibraryLoaded("libintegrated_shared_object.so") ||
                GnosisDelightBridge.isLibraryLoaded("libgrammar-checker_jni.so")
        if (nativeLoaded) {
            details.add("Neural Pipeline Engine: Active (Optimized Native Binaries)")
        } else {
            details.add("Neural Pipeline Engine: Managed Runtime Fallback")
        }

        // 4. Determine Active Acceleration Backend
        val (backend, hwAcc, status) = when {
            systemNeuralPresent && hasPrivilegedPermission -> {
                Triple(
                    "System Neural Acceleration",
                    true,
                    "Hardware Accelerated (Neural Service Active)"
                )
            }
            cores >= 4 && totalRamMb >= 3000 -> {
                Triple(
                    "Universal Multi-Core Neural Engine",
                    true,
                    "Active (Neural & Contextual Intelligence)"
                )
            }
            else -> {
                Triple(
                    "Universal Lightweight Engine",
                    false,
                    "Active (Energy-Efficient Intelligence)"
                )
            }
        }

        return AiCapabilityReport(
            isHardwareAccelerated = hwAcc,
            isSystemNeuralCoreAvailable = systemNeuralPresent,
            isUniversalEngineActive = true,
            accelerationBackend = backend,
            memoryTier = memoryTier,
            statusSummary = status,
            details = details
        )
    }
}
