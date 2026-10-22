import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fairScanAssetsDir = layout.buildDirectory.dir("generated/fairscanAssets")
val fairScanModelFile = fairScanAssetsDir.map { it.file("fairscan-segmentation-model.tflite") }
val fairScanLicenseFile = fairScanAssetsDir.map { it.file("FAIRSCAN-LICENSE.txt") }
val bundledLicenseFile = layout.projectDirectory.file("fairscan/FAIRSCAN-LICENSE.txt")

val cairoResourcesDir = layout.buildDirectory.dir("generated/cairoRes")
val cairoFontFile = cairoResourcesDir.map { it.file("font/cairo.ttf") }
val cairoFontSha256 = "667c987182391c91f4e57a2f455b1794fb5e3ee6ca4ef3383e86bb690fa9c964"

val prepareCairoFont by tasks.registering {
    val output = cairoFontFile.get().asFile
    outputs.file(output)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        if (output.isFile && sha256(output) == cairoFontSha256) return@doLast

        output.parentFile.mkdirs()
        val temporary = File(output.parentFile, "cairo.ttf.download")
        try {
            URL("https://raw.githubusercontent.com/google/fonts/d2528f6d1f43e7d9d0d2e1794afe2ad6fd7d56ba/ofl/cairo/Cairo%5Bslnt%2Cwght%5D.ttf")
                .openConnection()
                .apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "PhotoUnifier-build/1.0")
                }
                .getInputStream().use { input ->
                    temporary.outputStream().use { destination -> input.copyTo(destination) }
                }

            val actualSha256 = sha256(temporary)
            check(actualSha256 == cairoFontSha256) {
                "Cairo font SHA-256 mismatch: expected $cairoFontSha256, got $actualSha256"
            }

            val signature = temporary.inputStream().use { input ->
                ByteArray(4).also { bytes ->
                    check(input.read(bytes) == 4) { "Downloaded Cairo font is incomplete" }
                }
            }
            check(
                temporary.length() > 100_000L &&
                    (signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        signature.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)))
            ) {
                "Downloaded Cairo font is not a valid OpenType font"
            }
            if (output.exists()) check(output.delete()) { "Could not replace invalid Cairo font" }
            check(temporary.renameTo(output)) { "Could not install verified Cairo font" }
        } finally {
            temporary.delete()
        }
    }
}
val prepareFairScanModel by tasks.registering {
    // Re-check the cached file's digest every build; download only if absent or invalid.
    outputs.files(fairScanModelFile, fairScanLicenseFile)
    outputs.upToDateWhen { false }
    doLast {
        fairScanAssetsDir.get().asFile.mkdirs()

        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun fetchVerified(
            url: String,
            target: java.io.File,
            expectedSha256: String,
            fallbackFile: java.io.File? = null
        ) {
            if (target.isFile && sha256(target) == expectedSha256) return

            // 1. Use local bundled fallback if present and valid
            if (fallbackFile != null && fallbackFile.isFile && sha256(fallbackFile) == expectedSha256) {
                fallbackFile.copyTo(target, overwrite = true)
                return
            }

            // 2. Use persistent Gradle cache if present and valid
            val cacheDir = File(gradle.gradleUserHomeDir, "caches/fairscan-assets").apply { mkdirs() }
            val cachedFile = File(cacheDir, target.name)
            if (cachedFile.isFile && sha256(cachedFile) == expectedSha256) {
                cachedFile.copyTo(target, overwrite = true)
                return
            }

            // 3. Download from network with retries, redirect handling, and timeouts
            val maxRetries = 4
            var lastError: Exception? = null

            for (attempt in 1..maxRetries) {
                val temporary = File(target.parentFile, "${target.name}.download.$attempt")
                temporary.delete()
                try {
                    var currentUrl = url
                    var redirects = 0
                    var connection: HttpURLConnection

                    while (true) {
                        val u = URL(currentUrl)
                        connection = u.openConnection() as HttpURLConnection
                        connection.instanceFollowRedirects = true
                        connection.connectTimeout = 30_000
                        connection.readTimeout = 60_000
                        connection.setRequestProperty("User-Agent", "PhotoUnifier-build/1.0")
                        connection.setRequestProperty("Accept", "*/*")

                        val code = connection.responseCode
                        if (code in 300..399 && redirects < 5) {
                            val location = connection.getHeaderField("Location")
                            if (!location.isNullOrBlank()) {
                                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                                    location
                                } else {
                                    URL(u, location).toString()
                                }
                                redirects++
                                connection.disconnect()
                                continue
                            }
                        }

                        if (code !in 200..299) {
                            throw IOException("Server returned HTTP response code: $code for URL: $currentUrl")
                        }
                        break
                    }

                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    val actual = sha256(temporary)
                    check(actual == expectedSha256) {
                        "FairScan asset SHA-256 mismatch for ${target.name}: expected $expectedSha256, got $actual"
                    }
                    if (target.exists()) target.delete()
                    check(temporary.renameTo(target)) { "Could not install verified asset ${target.name}" }

                    // Cache for subsequent builds
                    try {
                        target.copyTo(cachedFile, overwrite = true)
                    } catch (_: Exception) {
                    }

                    return
                } catch (e: Exception) {
                    lastError = e
                    temporary.delete()
                    if (attempt < maxRetries) {
                        Thread.sleep(attempt * 2000L)
                    }
                }
            }

            // If network failed and fallback is available, try fallback as last resort
            if (fallbackFile != null && fallbackFile.isFile && sha256(fallbackFile) == expectedSha256) {
                fallbackFile.copyTo(target, overwrite = true)
                return
            }

            throw IllegalStateException(
                "Failed to download and verify ${target.name} after $maxRetries attempts: ${lastError?.message}",
                lastError
            )
        }

        fetchVerified(
            "https://github.com/pynicolas/fairscan-segmentation-model/releases/download/v1.2.0/fairscan-segmentation-model.tflite",
            fairScanModelFile.get().asFile,
            "96e14d7e610dd0c27b768b228fbc553b4ec119ebe68f3a3594029a25400691d2"
        )
        fetchVerified(
            "https://github.com/pynicolas/fairscan-segmentation-model/releases/download/v1.2.0/LICENSE.txt",
            fairScanLicenseFile.get().asFile,
            "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
            bundledLicenseFile.asFile
        )
    }
}

android {
    namespace = "com.photounifier"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.photounifier"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Include x86_64 so the document-crop instrumentation test can run on CI emulators.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    sourceSets.getByName("main").assets.srcDir(fairScanAssetsDir)
    sourceSets.getByName("main").res.srcDir(cairoResourcesDir)

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareFairScanModel, prepareCairoFont)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.opencv:opencv:4.9.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
