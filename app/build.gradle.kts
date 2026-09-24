import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// SafeTrack connection, from local.properties (git-ignored):
//   SAFETRACK_URL=https://<project-ref>.supabase.co
//   SAFETRACK_PUBLISHABLE_KEY=sb_publishable_...
// The publishable key is public by design (same as the Guardian app). Never put
// a secret/service-role key here: the watch authenticates to its database
// functions with its own device token.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val safeTrackUrl: String = (localProps.getProperty("SAFETRACK_URL") ?: "").trimEnd('/')
val safeTrackPublishableKey: String = localProps.getProperty("SAFETRACK_PUBLISHABLE_KEY") ?: ""

require(!safeTrackPublishableKey.startsWith("sb_secret_") && !safeTrackPublishableKey.contains("service_role")) {
    "SAFETRACK_PUBLISHABLE_KEY must be the publishable key, never a secret or service-role key."
}

android {
    namespace = "com.safetrack.watch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safetrack.watch"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SAFETRACK_URL", "\"$safeTrackUrl\"")
        buildConfigField("String", "SAFETRACK_PUBLISHABLE_KEY", "\"$safeTrackPublishableKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Replace with a real release signing config before distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
