import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.fitnessrider"
    compileSdk = 35

    val versionPropsFile = rootProject.projectDir.parentFile.resolve("version.properties")
    val versionProps = Properties().apply {
        if (versionPropsFile.exists()) {
            FileInputStream(versionPropsFile).use { load(it) }
        }
    }
    val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").trim().toIntOrNull() ?: 1
    val appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0").trim()

    defaultConfig {
        applicationId = "com.fitnessrider.coach"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        val gitTimestamp = providers.exec {
            commandLine("git", "log", "-1", "--format=%ct")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { out ->
            out.trim().toLongOrNull()?.let { it * 1000L } ?: 1789994982000L
        }
        val resolvedBuildTime = providers.gradleProperty("buildTimestamp")
            .map { it.toLong() }
            .orElse(gitTimestamp)
            .orElse(1789994982000L)
            .get()

        buildConfigField("long", "BUILD_TIME_MS", "${resolvedBuildTime}L")
        buildConfigField("int", "LIFECYCLE_DAYS", "30")
        buildConfigField("String", "UPDATE_URL", "\"https://appdistribution.firebase.dev/i/d740076f27b77ab0\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Media3 (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Room SQLite
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Firebase (Analytics & BoM)
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-analytics")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    debugImplementation(libs.androidx.ui.tooling)
}

