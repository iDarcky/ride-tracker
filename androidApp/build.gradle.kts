import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName: String = Properties()
    .apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("VERSION_NAME")

/** MAJOR*1_000_000 + MINOR*1_000 + PATCH: always increases with SemVer, so each release installs over the last. */
val appVersionCode: Int = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 1000 && patch < 1000) { "MINOR and PATCH must be < 1000" }
    major * 1_000_000 + minor * 1_000 + patch
}

/**
 * Release signing. The same key must sign every build forever, or Android refuses to
 * install the new version over the old one. Values come from ~/.gradle/gradle.properties
 * locally or from environment variables in CI.
 */
fun signingValue(name: String): String? =
    providers.gradleProperty("ridetracker.$name").orNull ?: System.getenv("RIDETRACKER_" + name.uppercase())

val keystorePath = signingValue("keystore")

android {
    namespace = "app.ridetracker"
    compileSdk = 37

    defaultConfig {
        // Never change: changing it makes a different app that cannot upgrade the installed one.
        applicationId = "app.ridetracker"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        // Phones are ARM. Leaving out x86 halves the size of the bundled text recognition model.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        val releaseSigning = signingConfigs.findByName("release")
        debug {
            // Sign debug builds with the release key too, so debug and release install over each other.
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        // "Ride Tracker Beta": the same release build as a second app (app.ridetracker.beta) with its own data, orange
        // icon and name (src/beta/res), installed next to the live app for testing. New versions go here first.
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            matchingFallbacks += listOf("release")
        }
    }

    androidResources {
        // Lists the app's languages for Android 13+ per-app language settings.
        generateLocaleConfig = true
    }

    lint {
        // ARM only on purpose (see abiFilters): the app is for phones, not Chromebooks.
        disable += "ChromeOsAbiSupport"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.reorderable)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
