plugins {
    alias(libs.plugins.android.application)
}

// El plugin de Google exige google-services.json. Se aplica solo si el archivo esta,
// asi el proyecto compila igual para quien clone el repositorio sin credenciales.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "ar.edu.utn.frsf.tarjetazo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ar.edu.utn.frsfco.tarjetazo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // IDs de prueba que publica Google: muestran anuncios de muestra y no generan ingresos.
        // Al publicar se reemplazan por los de la cuenta de AdMob. Usar los reales durante el
        // desarrollo cuenta como clics inválidos y puede suspender la cuenta.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.billing)
    implementation(libs.play.services.ads)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}