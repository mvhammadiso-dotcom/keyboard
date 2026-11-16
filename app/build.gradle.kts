plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "uz.mros.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.mros.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        // Yangilanishlar olinadigan ochiq GitHub repo (owner/repo) — workflow'dan keladi
        buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("UPDATE_REPO") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("mros") {
            storeFile = file("mros-keystore.jks")
            storePassword = "mroskeyboard"
            keyAlias = "mros"
            keyPassword = "mroskeyboard"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mros")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("mros")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += listOf("txt", "tsv")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
