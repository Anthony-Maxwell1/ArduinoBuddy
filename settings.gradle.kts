import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream
import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ArduinoBuddy"
include(":app")

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

// --- RUN PRE-SYNC + PRE-BUILD ---
gradle.settingsEvaluated {
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
