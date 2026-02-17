import java.io.File
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
}

android {
    namespace = "org.thatdev.arduinobuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.thatdev.arduinobuddy"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

chaquopy {
    defaultConfig { }
    productFlavors { }
    sourceSets { }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(files("libs/arduinocli.aar"))
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.foundation.layout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// --- CONFIGURATION ---
val arduinoCliRepo = "Anthony-Maxwell1/arduino-cli_arduinobuddyfork"
val esptoolRepo = "Anthony-Maxwell1/esptool-arduinobuddy_fork"

// Release tags (change to desired versions)
val arduinoCliVersion = "v1.0.0"
val esptoolVersion = "v1.0.0"

// Target folders
val libsDir = file("$projectDir/libs")
val esptoolTargetDir = file("$projectDir/src/main/python/esptool")

// --- Helper function to download files if missing ---
fun downloadFile(url: String, output: File) {
    if (!output.exists()) {
        println("Downloading $url → ${output.absolutePath}")
        URL(url).openStream().use { it.copyTo(output.outputStream()) }
    } else {
        println("File already exists: ${output.name}")
    }
}

// --- Helper function to download zip and unpack ---
fun downloadAndUnzip(url: String, outputDir: File) {
    val zipFile = File(buildDir, url.substringAfterLast("/"))
    downloadFile(url, zipFile)
    outputDir.mkdirs()
    project.copy {
        from(zipTree(zipFile))
        into(outputDir)
    }
}

// --- Custom Gradle task ---
tasks.register("fetchTooling") {
    group = "setup"
    description = "Fetches Arduino CLI AAR/JAR and esptool for Chaquopy"

    doLast {
        libsDir.mkdirs()
        esptoolTargetDir.mkdirs()

        // 1️⃣ Arduino CLI artifacts
        val arduinoCliAarUrl = "https://github.com/$arduinoCliRepo/releases/latest/download/arduinocli.aar"
        val arduinoCliSourcesUrl = "https://github.com/$arduinoCliRepo/releases/latest/download/arduinocli-sources.jar"

        downloadFile(arduinoCliAarUrl, File(libsDir, "arduinocli.aar"))
        downloadFile(arduinoCliSourcesUrl, File(libsDir, "arduinocli-sources.jar"))

        // 2️⃣ esptool Python bundle
        val esptoolZipUrl = "https://github.com/$esptoolRepo/releases/download/$esptoolVersion/esptool-stripped.zip"
        downloadAndUnzip(esptoolZipUrl, esptoolTargetDir)

        println("Tooling fetched successfully.")
    }
}

// --- Ensure tooling is fetched before any build ---
tasks.named("preBuild") {
    dependsOn("fetchTooling")
}
