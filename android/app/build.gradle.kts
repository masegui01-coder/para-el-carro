plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.paraelcarro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paraelcarro"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.6.1"

        // AdMob: IDs de PRUEBA de Google. Pon los tuyos en ~/.gradle/gradle.properties
        // (admobAppId, admobInterstitialId, admobBannerId) antes de publicar en Google Play.
        manifestPlaceholders["admobAppId"] = (project.findProperty("admobAppId") as String?) ?: "ca-app-pub-3940256099942544~3347511713"
        fun adUnit(prop: String, testId: String) = "\"${(project.findProperty(prop) as String?) ?: testId}\""
        // Intersticial (pantalla completa) cada 2 rondas y antes del podio
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", adUnit("admobInterstitialId", "ca-app-pub-3940256099942544/1033173712"))
        // Banner adaptable abajo, solo en pantallas sin temporizador (portada, sala, puntos, podio)
        buildConfigField("String", "ADMOB_BANNER_ID", adUnit("admobBannerId", "ca-app-pub-3940256099942544/9214589741"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Para probar en el móvil se firma con la clave de depuración. Antes de subir a
            // Google Play, crea tu propia clave de subida y cambia esto por su signingConfig.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    implementation(libs.play.services.ads)
    implementation(libs.ump)

    // Salas online: el mismo proyecto Firebase que la web (ver data/online/FirebaseSetup.kt)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)

    testImplementation(libs.junit)
    // org.json real para los tests de JVM (en Android lo da el sistema)
    testImplementation(libs.json)
}
