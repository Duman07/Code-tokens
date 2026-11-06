import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Servidor compartido: se lee de local.properties (SUPABASE_URL, SUPABASE_KEY) o de -P en gradle.
// La clave es la publishable (pública). Si falta, la app pide URL y clave en «Servidor propio».
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }
fun ajuste(n: String) = (local.getProperty(n) ?: (project.findProperty(n) as String?) ?: "").replace("\"", "")

android {
    namespace = "com.contadortokens.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.contadortokens.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
        // IDs de PRUEBA de Google: muestran anuncios de prueba y no generan ingresos. Los reales van en
        // local.properties (ADMOB_APP_ID=ca-app-pub-…~…, ADMOB_BANNER=ca-app-pub-…/…).
        manifestPlaceholders["admobAppId"] = ajuste("ADMOB_APP_ID").ifEmpty { "ca-app-pub-3940256099942544~3347511713" }
        // Sin IDs reales, el release no muestra anuncios (el debug sí, con los de prueba, para ver el diseño).
        buildConfigField("boolean", "ADS_REALES", "${ajuste("ADMOB_APP_ID").isNotEmpty() && ajuste("ADMOB_BANNER").isNotEmpty()}")
        buildConfigField("String", "ADMOB_BANNER", "\"${ajuste("ADMOB_BANNER").ifEmpty { "ca-app-pub-3940256099942544/9214589741" }}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${ajuste("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${ajuste("SUPABASE_KEY")}\"")
    }

    // Firma de publicación: crea android/keystore.properties (fuera de git) con
    //   storeFile=/ruta/mi-llave.jks  storePassword=…  keyAlias=…  keyPassword=…
    val ks = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use(::load) }
    signingConfigs {
        if (ks.isNotEmpty()) create("publicacion") {
            storeFile = file(ks.getProperty("storeFile"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin keystore.properties cae a la firma de depuración (solo para probar el release en tu teléfono).
            signingConfig = signingConfigs.findByName("publicacion") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
