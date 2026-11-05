import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Marque : ibo-app/brand.properties (UTF-8 pour l'arabe). Surcharge possible avec -PNOM=valeur.
val brandProps = Properties().apply {
    rootProject.file("brand.properties").takeIf { it.exists() }?.reader(Charsets.UTF_8)?.use { load(it) }
}
fun brand(name: String, default: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: brandProps.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: default
fun javaString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appName = brand("BRAND_APP_NAME", "Kanaani")
val appId = brand("BRAND_APP_ID", "com.kanaani.tv.ibo")
val host = brand("BRAND_HOST", "").trimEnd('/')
val backupHost = brand("BRAND_BACKUP_HOST", "").trimEnd('/')
val providerName = brand("BRAND_PROVIDER_NAME", appName)
// Fichier update.json publié par la CI dans la dernière release (vide = pas de mises à jour intégrées).
val updateUrl = brand("BRAND_UPDATE_URL", "")
// ads.json des thèmes d'accueil (vide = pas de publicités).
val adsUrl = brand("BRAND_ADS_URL", "")
// Messages instantanés (ntfy) : serveur et sujet secret (vide = désactivé).
val msgServer = brand("BRAND_MSG_SERVER", "https://ntfy.sh")
val msgTopic = brand("BRAND_MSG_TOPIC", "")
// Clé TMDB (images et notes des films du panneau publicitaire) : secret TMDB_KEY.
val tmdbKey = brand("BRAND_TMDB_KEY", "")
// Traductions des catégories (hébreu), modifiables sans mise à jour.
val i18nUrl = brand("BRAND_I18N_URL", "")
// Panneau de contrôle (Cloudflare) : adresse et identifiant de l'application.
val panelUrl = brand("BRAND_PANEL_URL", "")
val panelApp = brand("BRAND_PANEL_APP", "")

// Version : fichier VERSION à la racine du dépôt (x.y.z) → versionCode = x*10000 + y*100 + z.
val versionName0: String = rootProject.file("../VERSION").readText().trim()
val versionCode0: Int = versionName0.split(".").map { it.trim().toInt() }.let { it[0] * 10_000 + it[1] * 100 + it[2] }

android {
    namespace = "com.kanaani.ibo"
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        // Android 6+ : beaucoup de box TV bon marché tournent encore en Android 6 / 7.
        minSdk = 23
        targetSdk = 35
        versionCode = versionCode0
        versionName = versionName0
        resValue("string", "app_name", appName)
        buildConfigField("String", "BRAND_HOST", javaString(host))
        buildConfigField("String", "BRAND_PROVIDER_NAME", javaString(providerName))
        buildConfigField("String", "BRAND_BACKUP_HOST", javaString(backupHost))
        buildConfigField("String", "UPDATE_URL", javaString(updateUrl))
        buildConfigField("String", "ADS_URL", javaString(adsUrl))
        buildConfigField("String", "MSG_SERVER", javaString(msgServer))
        buildConfigField("String", "MSG_TOPIC", javaString(msgTopic))
        buildConfigField("String", "TMDB_KEY", javaString(tmdbKey))
        buildConfigField("String", "I18N_URL", javaString(i18nUrl))
        buildConfigField("String", "PANEL_URL", javaString(panelUrl))
        buildConfigField("String", "PANEL_APP", javaString(panelApp))
    }

    signingConfigs {
        create("brand") {
            val ks = System.getenv("BRAND_KEYSTORE")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("BRAND_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BRAND_KEY_ALIAS")
                keyPassword = System.getenv("BRAND_KEY_PASSWORD")
                // v1 en plus de v2/v3 : certains installeurs de box TV n'acceptent que la signature « jar ».
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = System.getenv("BRAND_KEYSTORE")
            signingConfig = if (!ks.isNullOrBlank() && file(ks).exists()) signingConfigs.getByName("brand") else signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    implementation(libs.media3.exoplayer)
    // Décodeur audio FFmpeg (construit par .github/workflows/ffmpeg-aar.yml de kanaani-sport, media3 1.11.1)
    implementation(files("libs/media3-ffmpeg-decoder-1.11.1.aar"))
    implementation("androidx.media3:media3-decoder:${libs.versions.media3.get()}")
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
}
