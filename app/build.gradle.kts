import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val idraakVersionCode = providers.environmentVariable("IDRAAK_VERSION_CODE")
    .orNull?.toIntOrNull() ?: 1
val idraakVersionName = providers.environmentVariable("IDRAAK_VERSION_NAME")
    .orNull ?: "1.0.0"

val releaseKeystoreBase64 = providers.environmentVariable("IDRAAK_KEYSTORE_BASE64").orNull
val releaseKeystorePassword = providers.environmentVariable("IDRAAK_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("IDRAAK_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("IDRAAK_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystoreBase64,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val releaseKeystoreFile = layout.buildDirectory.file("signing/idraak-release.jks").get().asFile
if (hasReleaseSigning) {
    releaseKeystoreFile.parentFile.mkdirs()
    releaseKeystoreFile.writeBytes(Base64.getDecoder().decode(releaseKeystoreBase64))
}

val sherpaAarFile = layout.projectDirectory.file("libs/sherpa-onnx.aar").asFile
val sherpaAarSha256 = "4c766e2e74c9d33d595338c21fe3b305c73ba45d47e96a235675c33142a2ec50"

fun sha256File(file: File): String {
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

val downloadSherpaAar by tasks.registering {
    outputs.file(sherpaAarFile)
    outputs.upToDateWhen {
        sherpaAarFile.isFile && sha256File(sherpaAarFile) == sherpaAarSha256
    }
    doLast {
        if (sherpaAarFile.isFile && sha256File(sherpaAarFile) == sherpaAarSha256) {
            return@doLast
        }
        sherpaAarFile.parentFile.mkdirs()
        val temporaryFile = File(sherpaAarFile.parentFile, "sherpa-onnx.aar.part")
        temporaryFile.delete()

        val connection = (URL(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-2.0.3.aar"
        ).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Idraak-Quantum-Build")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw GradleException("Could not download the sherpa-onnx Android library.")
            }
            var totalBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > 70L * 1024L * 1024L) {
                            throw GradleException("The sherpa-onnx library exceeded its expected size limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (sha256File(temporaryFile) != sherpaAarSha256) {
            temporaryFile.delete()
            throw GradleException("The sherpa-onnx library checksum did not match; build stopped.")
        }
        if (!temporaryFile.renameTo(sherpaAarFile)) {
            temporaryFile.delete()
            throw GradleException("Could not save the verified sherpa-onnx library.")
        }
    }
}

android {
    namespace = "com.idraak.quantum"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idraak.quantum"
        minSdk = 26
        targetSdk = 35
        versionCode = idraakVersionCode
        versionName = idraakVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("idraakRelease") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("idraakRelease")
            }
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(files(sherpaAarFile))
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.named("preBuild").configure {
    dependsOn(downloadSherpaAar)
}
