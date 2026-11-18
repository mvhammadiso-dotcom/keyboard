plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// Versiya: version.properties dagi major.minor + build raqami (10.0.1, 10.0.2, ...)
val versionProps = java.util.Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val versionMajor = versionProps.getProperty("major", "10").trim().toInt()
val versionMinor = versionProps.getProperty("minor", "0").trim().toInt()

android {
    namespace = "uz.mros.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.mros.keyboard"
        minSdk = 26
        targetSdk = 35
        // versionCode doim o'sib boradi (yangi repo ochilsa ham eski versiya ustiga o'rnatiladi)
        versionCode = versionMajor * 1_000_000 + versionMinor * 10_000 + runNumber
        versionName = "$versionMajor.$versionMinor.$runNumber"
        // Yangilanishlar olinadigan ochiq GitHub repo (owner/repo) — workflow'dan keladi
        buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("UPDATE_REPO") ?: ""}\"")
        // Yopiq repo uchun: faqat o'qish huquqli token (GitHub Secrets → UPDATE_TOKEN). Ochiq repo bo'lsa — bo'sh.
        buildConfigField("String", "UPDATE_TOKEN", "\"${(System.getenv("UPDATE_TOKEN") ?: "").filter { it.isLetterOrDigit() || it == '_' }}\"")
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
