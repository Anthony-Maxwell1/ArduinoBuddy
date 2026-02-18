import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream
import kotlin.collections.component1
import kotlin.collections.component2

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

val libsDir = File(rootDir, "app/libs")
val esptoolTargetDir = File(rootDir, "app/src/main/python/esptool")
val buildTmpDir = File(rootDir, ".gradle/tooling")
val hashFile = File(buildTmpDir, "tooling.hash")

// --- HELPER: download file ---
fun downloadFile(url: String, output: File) {
    output.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input ->
        output.outputStream().use { input.copyTo(it) }
    }
}

// --- HELPER: unzip ---
fun unzip(zipFile: File, outputDir: File) {
    ZipInputStream(zipFile.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            val outFile = File(outputDir, entry.name)
            if (entry.isDirectory) outFile.mkdirs()
            else {
                outFile.parentFile.mkdirs()
                outFile.outputStream().use { zip.copyTo(it) }
            }
        }
    }
}

// --- FETCH LATEST RELEASE ASSETS ---
fun fetchLatestReleaseAssets(repo: String): Map<String, String> {
    val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
    val jsonText = URI(apiUrl).toURL().readText()
    val json = JsonSlurper().parseText(jsonText) as Map<*, *>

    // assets: a list of maps with "name" and "browser_download_url"
    @Suppress("UNCHECKED_CAST")
    val assets = json["assets"] as List<Map<*, *>>
    return assets.associate { it["name"] as String to it["browser_download_url"] as String }
}


tasks.register("configureTooling") {
    group = "setup"
    description = "Manually check and update ArduinoBuddy tooling."
    notCompatibleWithConfigurationCache("Accesses network, files, etc.")

    doLast {
        println("Checking ArduinoBuddy tooling…")
        libsDir.mkdirs()
        esptoolTargetDir.mkdirs()
        buildTmpDir.mkdirs()

        val arduinoCliAssets = fetchLatestReleaseAssets(arduinoCliRepo)
        val esptoolAssets = fetchLatestReleaseAssets(esptoolRepo)

        val currentHash = (arduinoCliAssets.keys + esptoolAssets.keys).sorted().joinToString()
        val lastHash = if (hashFile.exists()) hashFile.readText() else ""

        if (currentHash != lastHash) {
            println("Tooling changed or first run → downloading…")

            // Arduino CLI artifacts
            arduinoCliAssets.forEach { (name, url) ->
                if (name.contains("arduinocli")) {
                    downloadFile(url, File(libsDir, name))
                }
            }

            // esptool Python bundle (pick zip)
            val zipEntry = esptoolAssets.entries.find { it.key.endsWith(".zip") }
            if (zipEntry != null) {
                val zipFile = File(buildTmpDir, zipEntry.key)
                downloadFile(zipEntry.value, zipFile)
                unzip(zipFile, esptoolTargetDir)
            }

            // store new hash
            hashFile.writeText(currentHash)
            println("Tooling downloaded and hash updated.")
        } else {
            println("Tooling up-to-date, skipping download.")
        }
    }
}