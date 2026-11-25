import java.io.File

plugins {
    id("com.android.application")
}

val keystoreProps = java.util.Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val keystoreFile: File? = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotEmpty() }?.let { File(it) }
val keystorePass: String? = keystoreProps.getProperty("storePassword")
val signingKeyPass: String? = keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotEmpty() } ?: keystorePass
val signingAlias: String? = keystoreProps.getProperty("keyAlias")

fun shared(key: String): String = rootProject.file("placeholder.txt").readLines()
    .firstOrNull { it.trim().startsWith(key, ignoreCase = true) }
    ?.substringAfter(":")?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("$key missing in placeholder.txt")

val appVersion = shared("Appversion")
val appVersionCode = shared("Versioncode").toIntOrNull() ?: error("Versioncode must be a number")
val canSign = keystoreFile != null && keystoreFile.length() > 0 && !keystorePass.isNullOrEmpty() && !signingAlias.isNullOrEmpty()

android {
    namespace = "gnosis.keyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "gnosis.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePass
                keyAlias = signingAlias
                keyPassword = signingKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    androidResources {
        noCompress += listOf("ttf", "flac")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
