import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun Project.signingProp(name: String): String {
    val file = rootProject.file("local.properties")
    val fromFile = if (file.isFile) {
        Properties().apply { file.inputStream().use { load(it) } }.getProperty(name)
    } else {
        null
    }
    return fromFile?.takeIf { it.isNotBlank() } ?: System.getenv(name).orEmpty()
}

val stableKeystore = file("signing/stable.p12")
val stableStorePassword = signingProp("CURSOR_ANDROID_STORE_PASSWORD")
val stableKeyPassword = signingProp("CURSOR_ANDROID_KEY_PASSWORD")
val stableKeyAlias = signingProp("CURSOR_ANDROID_KEY_ALIAS").ifBlank { "upload" }
val canSignStable = stableKeystore.isFile &&
    stableStorePassword.isNotBlank() &&
    stableKeyPassword.isNotBlank()

android {
    namespace = "com.cursorandroid.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cursorandroid.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 104
        versionName = "1.0.4"
    }

    if (canSignStable) {
        signingConfigs {
            create("stable") {
                storeFile = stableKeystore
                storePassword = stableStorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (canSignStable) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
            if (canSignStable) {
                signingConfig = signingConfigs.getByName("stable")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        textReport = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
