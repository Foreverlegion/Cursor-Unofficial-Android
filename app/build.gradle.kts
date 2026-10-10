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

fun escapeBuildConfig(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
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
        versionCode = 127
        versionName = "1.0.27"
        buildConfigField(
            "String",
            "APP_ISSUES_TOKEN",
            "\"${escapeBuildConfig(signingProp("CURSOR_ANDROID_ISSUES_TOKEN"))}\"",
        )
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
            isMinifyEnabled = true
            isShrinkResources = true
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        textReport = true
        htmlReport = true
    }
}

tasks.register<Exec>("checkReleaseReflectiveCtors") {
    group = "verification"
    description = "Fail if R8 drops constructors Room, startup, or WorkManager call by reflection."
    dependsOn("minifyReleaseWithR8")
    workingDir = rootProject.projectDir
    inputs.file(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    commandLine(
        "bash",
        "scripts/check-r8-reflective-ctors.sh",
        "app/build/outputs/mapping/release/mapping.txt",
    )
}

gradle.projectsEvaluated {
    listOf("assembleRelease", "bundleRelease").forEach { name ->
        tasks.named(name).configure {
            dependsOn(tasks.named("checkReleaseReflectiveCtors"))
        }
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
