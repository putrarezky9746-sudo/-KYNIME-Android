plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val siteUrl: String = (project.findProperty("SITE_URL") as String?) ?: "https://example.com/"

android {
    namespace = "id.kynime.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.kynime.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SITE_URL", "\"$siteUrl\"")
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Chrome Custom Tab: membuka halaman "Pilih akun" Google yang sama dengan di browser
    implementation("androidx.browser:browser:1.8.0")
}
