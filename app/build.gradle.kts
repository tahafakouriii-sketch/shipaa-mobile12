plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ir.shipaa.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.shipaa.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1-pilot"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
