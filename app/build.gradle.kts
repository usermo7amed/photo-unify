import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fairScanAssetsDir = layout.buildDirectory.dir("generated/fairscanAssets")
val fairScanModelFile = fairScanAssetsDir.map { it.file("fairscan-segmentation-model.tflite") }
val fairScanLicenseFile = fairScanAssetsDir.map { it.file("FAIRSCAN-LICENSE.txt") }

val prepareFairScanModel by tasks.registering {
    // Re-check the cached file's digest every build; download only if absent or invalid.
    outputs.files(fairScanModelFile, fairScanLicenseFile)
    outputs.upToDateWhen { false }
    doLast {
        val assetsDir = fairScanAssetsDir.get().asFile.apply { mkdirs() }

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

        fun fetchVerified(url: String, target: java.io.File, expectedSha256: String) {
            if (target.isFile && sha256(target) == expectedSha256) return
            val temporary = File(target.parentFile, target.name + ".download")
            temporary.delete()
            try {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "PhotoUnifier-build")
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                val actual = sha256(temporary)
                check(actual == expectedSha256) {
                    "FairScan asset SHA-256 mismatch for ${target.name}: expected $expectedSha256, got $actual"
                }
                check(temporary.renameTo(target)) { "Could not install verified asset ${target.name}" }
            } finally {
                temporary.delete()
            }
        }

        fetchVerified(
            "https://github.com/pynicolas/fairscan-segmentation-model/releases/download/v1.2.0/fairscan-segmentation-model.tflite",
            fairScanModelFile.get().asFile,
            "96e14d7e610dd0c27b768b228fbc553b4ec119ebe68f3a3594029a25400691d2"
        )
        fetchVerified(
            "https://github.com/pynicolas/fairscan-segmentation-model/releases/download/v1.2.0/LICENSE.txt",
            fairScanLicenseFile.get().asFile,
            "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"
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

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    sourceSets.getByName("main").assets.srcDir(fairScanAssetsDir)

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
    dependsOn(prepareFairScanModel)
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

    testImplementation("junit:junit:4.13.2")
}
