plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "af.hazaragi.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "af.hazaragi.keyboard"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }
    // امضای نسخه‌ی نهایی: با همان کلید ثابت (از Secrets گیت‌هاب) تا به‌روزرسانی روی نصب قبلی ممکن باشد.
    // بدون این متغیرها (ساخت محلی) با کلید debug امضا می‌شود.
    val ksPath: String? = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        create("hkRelease") {
            if (!ksPath.isNullOrEmpty()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (!ksPath.isNullOrEmpty()) signingConfigs.getByName("hkRelease") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
