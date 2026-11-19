plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.luiscruzandresgomezrodolfohernandez.geolux"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.luiscruzandresgomezrodolfohernandez.geolux"
        minSdk = 28
        targetSdk = 36
        versionCode = 25
        versionName = "0.24.1-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Solo 2 arquitecturas: arm64-v8a (teléfonos) y x86_64 (emulador). MapLibre y
        // SceneView traen código nativo para 4 y el APK de depuración llegaba a 111 MB.
        // La entrega usará App Bundle (decisión de Andrés).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.maplibre.android)
    implementation(libs.androidx.recyclerview)
    // Navegación entre pantallas con rutas tipadas (pila, argumentos y botón atrás).
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sceneview)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}