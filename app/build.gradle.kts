import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    // NO pongas nada de kapt aquí dentro para evitar el error de "Version not found"
}

// ESTO VA FUERA DE LAS LLAVES (Línea 6 o 7):
apply(plugin = "kotlin-kapt")

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val useLocalhostBff = localProperties.getProperty("USE_LOCALHOST_BFF", "false").toBoolean()
val localBffBaseUrl = localProperties.getProperty(
    "BFF_BASE_URL",
    "http://10.0.2.2:8080/api/v1/"
)
val spotifyClientId = localProperties.getProperty("SPOTIFY_CLIENT_ID", "").trim()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.auramusic.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.auramusic.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("boolean", "ALLOW_LOCALHOST", "false")
        buildConfigField("String", "BFF_BASE_URL", "\"https://api.piped.private.coffee/\"")
        buildConfigField("String", "PIPED_BASE_URL", "\"https://api.piped.private.coffee/\"")
        buildConfigField("String", "LRCLIB_BASE_URL", "\"https://lrclib.net/\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", spotifyClientId.asBuildConfigString())
    }

    buildTypes {

        getByName("debug") {
            buildConfigField("boolean", "ALLOW_LOCALHOST", useLocalhostBff.toString())
            val baseUrl = if (useLocalhostBff) "\"$localBffBaseUrl\"" else "\"https://api.piped.private.coffee/\""
            buildConfigField("String", "BFF_BASE_URL", baseUrl)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ALLOW_LOCALHOST", "false")
            buildConfigField("String", "BFF_BASE_URL", "\"https://api.piped.private.coffee/\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Verify match with Kotlin version
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    
    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    //kapt(libs.hilt.compiler)
    add("kapt", libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Networking & Images
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    //kapt(libs.androidx.room.compiler) // Or KSP if configured
    add("kapt", libs.androidx.room.compiler)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Local YouTube extraction (no mandatory BFF).
    implementation(libs.newpipe.extractor)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.1")

    // WorkManager + Hilt Worker support
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    add("kapt", libs.androidx.hilt.compiler)
}
