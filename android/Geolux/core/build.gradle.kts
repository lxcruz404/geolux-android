import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Contrato .geolux: modelos, lector y validador. Kotlin puro, sin Android (06 §3).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Los tests leen los fixtures compartidos del contrato sin copiarlos (06 §8).
tasks.test {
    systemProperty("geolux.fixtures", rootProject.file("../../contracts/geolux-v1/fixtures").absolutePath)
    // Fixture `.glx` del exportador de Desktop (main c04b710), leído donde lo deja Desktop.
    systemProperty("geolux.glxFixtures", rootProject.file("../../tests/core/fixtures/glx-v1").absolutePath)
    // Catálogos del contrato de gestión (fuente única con Desktop).
    systemProperty("geolux.managementContract", rootProject.file("../../contracts/geolux-management-v1").absolutePath)
}
