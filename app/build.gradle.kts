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
    implementation("com.github.k2-fsa:sherpa-onnx:v2.0.3")
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
}
