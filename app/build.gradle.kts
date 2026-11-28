plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.google.services) apply false
}

// Firebase is optional for local builds. Drive and the offline library remain usable
// when a developer has not supplied the private google-services.json file.
val hasGoogleServicesConfig = listOf(
    "google-services.json",
    "src/main/google-services.json",
    "src/debug/google-services.json",
    "src/release/google-services.json"
).any { file(it).isFile }
if (hasGoogleServicesConfig) {
    pluginManager.apply("com.google.gms.google-services")
}

val stableSigningPath = System.getenv("ANDROID_KEYSTORE_PATH")
val localDebugKeystore = file("${rootDir}/debug.keystore").takeIf { it.exists() }
val debugSigningStore = stableSigningPath?.takeIf { it.isNotBlank() }?.let { file(it) }
    ?: localDebugKeystore
val debugSigningStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    ?: if (localDebugKeystore != null) "android" else null
val debugSigningKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    ?: if (localDebugKeystore != null) "androiddebugkey" else null
val debugSigningKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    ?: if (localDebugKeystore != null) "android" else null

android {
    namespace = "com.yourname.colornest"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.aistudio.colornest.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        if (debugSigningStore != null &&
            debugSigningStorePassword != null &&
            debugSigningKeyAlias != null &&
            debugSigningKeyPassword != null
        ) {
            create("debugConfig") {
                storeFile = debugSigningStore
                storePassword = debugSigningStorePassword
                keyAlias = debugSigningKeyAlias
                keyPassword = debugSigningKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Use the configured stable key when available. Otherwise AGP uses its
            // normal local/CI debug key so compilation is not blocked by absent secrets.
            if (debugSigningStore != null &&
                debugSigningStorePassword != null &&
                debugSigningKeyAlias != null &&
                debugSigningKeyPassword != null
            ) {
                signingConfig = signingConfigs.getByName("debugConfig")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Several Google client jars each ship META-INF/DEPENDENCIES
            // (plus license/notice files); the APK packager rejects
            // duplicate paths, so drop these metadata files.
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Drive sync (account picker + Drive API client)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive) {
        // google-api-client (pulled in by the Drive client) depends on
        // Apache HttpClient (httpclient/httpcore) and the Apache-based
        // transport (google-http-client-apache-v2). Android already
        // provides org.apache.http classes, so those jars break the
        // build (DuplicatePlatformClasses / dex conflicts). The app
        // uses AndroidHttp (HttpURLConnection) and never the Apache
        // transport, so excluding them is safe.
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.http-client", module = "google-http-client-apache-v2")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // Firebase AI Logic & App Check
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.appcheck.debug)
}
