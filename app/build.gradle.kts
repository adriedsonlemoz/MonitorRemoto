plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Nunca incluir credenciais ou keystore no repositório. A CI injeta esses campos via ambiente.
val releaseSigningNames = listOf(
    "MONITOR_SIGNING_STORE_FILE", "MONITOR_SIGNING_STORE_PASSWORD",
    "MONITOR_SIGNING_KEY_ALIAS", "MONITOR_SIGNING_KEY_PASSWORD"
)
val hasReleaseSigning = releaseSigningNames.all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "br.dev.monitorremoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.dev.monitorremoto"
        minSdk = 26
        targetSdk = 36
        versionCode = 58
        versionName = "0.1.0-alpha.58"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (hasReleaseSigning) {
            create("privateRelease") {
                storeFile = file(System.getenv("MONITOR_SIGNING_STORE_FILE"))
                storePassword = System.getenv("MONITOR_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("MONITOR_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("MONITOR_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("privateRelease")
            }
        }
    }
}

dependencies {
    implementation(project(":adb-core"))
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
}
