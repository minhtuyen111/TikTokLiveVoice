import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Che chuỗi cấu hình (XOR + Base64) để không lộ dạng chữ trong APK. Giải mã bằng com.tlv.app.security.Obf.
fun obf(s: String): String {
    val key = MessageDigest.getInstance("SHA-256").digest("com.tlv.app/vlt".toByteArray())
    val b = s.toByteArray(Charsets.UTF_8)
    return Base64.getEncoder().encodeToString(ByteArray(b.size) { (b[it].toInt() xor key[it % key.size].toInt()).toByte() })
}

fun cfg(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: ""

base { archivesName.set("voicelive") }

android {
    namespace = "com.tlv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tlv.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "2.${System.getenv("VERSION_CODE") ?: "0"}"
        buildConfigField("String", "SB_U", "\"${obf(cfg("SUPABASE_URL"))}\"")
        buildConfigField("String", "SB_K", "\"${obf(cfg("SUPABASE_ANON_KEY"))}\"")
        buildConfigField("String", "BRIDGE_E", "\"${obf(cfg("BRIDGE_URL"))}\"")
        // Vân tay SHA-256 chứng chỉ ký bản phát hành (nhiều giá trị cách nhau dấu phẩy). Để trống = không kiểm tra.
        buildConfigField("String", "SIG_E", "\"${obf(cfg("APK_SIG_SHA256"))}\"")
    }

    signingConfigs {
        create("rel") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("rel") // CI luôn cung cấp keystore (secret hoặc tự sinh)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += "onnx" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Sherpa-ONNX runtime (downloaded by CI; used for bundled offline Vietnamese TTS).
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}
