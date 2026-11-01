import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version and channel come from CI (see .github/workflows). Local builds fall back to
// the commit count so that an installed local build can still be updated by CI builds.
val baseVersion = providers.gradleProperty("kontor.version").get()
val commitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
val versionCodeValue = providers.environmentVariable("KONTOR_VERSION_CODE").map { it.toInt() }.orElse(commitCount)
val versionNameValue = providers.environmentVariable("KONTOR_VERSION_NAME").orElse("$baseVersion-local")
val channelValue = providers.environmentVariable("KONTOR_CHANNEL").orElse("dev")

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

android {
    namespace = "de.felitendo.kontor"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.felitendo.kontor"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeValue.get()
        versionName = versionNameValue.get()
        buildConfigField("String", "CHANNEL", "\"${channelValue.get()}\"")
        buildConfigField("String", "GITHUB_REPO", "\"Felitendo/kontor\"")
    }

    signingConfigs {
        val storePath = signingValue("KONTOR_KEYSTORE", "storeFile")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("KONTOR_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KONTOR_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KONTOR_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    dependenciesInfo {
        // Keeps the APK free of the Google-encrypted dependency blob (F-Droid friendly).
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
