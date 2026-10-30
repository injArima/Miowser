plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystore = providers.environmentVariable("MIOWSER_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("MIOWSER_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MIOWSER_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MIOWSER_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(releaseKeystore, releaseStorePassword,
    releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.miowser.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.miowser.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) create("publisher") {
            storeFile = file(requireNotNull(releaseKeystore))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        getByName("debug") {
            // Never let an Android Studio/debug certificate occupy the permanent release package.
            // This keeps development installs side-by-side with the signed public application and
            // prevents a retained debug install in another Android profile from blocking release.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("publisher")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("development") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "debug"
        }
        // Isolated, locally signed release-like target. Toggle R8 only after capturing baseline.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = providers.gradleProperty("benchmark.r8").orNull == "true"
            isShrinkResources = isMinifyEnabled
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin's JVM target is inferred from compileOptions above under AGP 9's built-in
    // Kotlin support — there's no separate `kotlinOptions` DSL without the kotlin-android
    // plugin (which we deliberately don't apply; see root build.gradle.kts).

    buildFeatures {
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.work.runtime)
    // Explicit ownership of profile delivery, rather than relying on a transitive dependency.
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
