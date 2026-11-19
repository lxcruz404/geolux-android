// Repositorio de proyectos, importación, almacenamiento y ubicación (06 §3).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Esquemas de Room versionados en el repositorio: base de las migraciones.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "me.luiscruzandresgomezrodolfohernandez.geolux.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Los tests leen los fixtures compartidos del contrato sin copiarlos (06 §8).
    testOptions {
        unitTests.all {
            it.systemProperty("geolux.fixtures", rootProject.file("../../contracts/geolux-v1/fixtures").absolutePath)
            it.systemProperty("geolux.glxFixtures", rootProject.file("../../tests/core/fixtures/glx-v1").absolutePath)
        }
    }
}

/**
 * Solo algunos `.glx` del fixture de Desktop (no la carpeta entera: trae otro
 * `expected.json`, que chocaría con el del contrato en los assets): `finca-demo-v1.glx`
 * y `finca-ida-vuelta-android-v1.glx` (otra finca, con ganado y gestión: eliminar fincas).
 */
abstract class GlxFixtureAsset : DefaultTask() {
    @get:InputFiles
    abstract val fixtures: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        fixtures.files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }
}

val glxFixtureAsset = tasks.register<GlxFixtureAsset>("glxFixtureAsset") {
    fixtures.from(
        rootProject.file("../../tests/core/fixtures/glx-v1/finca-demo-v1.glx"),
        rootProject.file("../../tests/core/fixtures/glx-v1/finca-ida-vuelta-android-v1.glx"),
    )
    outputDir.set(layout.buildDirectory.dir("generated/glxFixtureAsset"))
}

// En el emulador, los mismos fixtures como assets del APK de pruebas instrumentadas.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory(
            rootProject.file("../../contracts/geolux-v1/fixtures").absolutePath,
        )
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(glxFixtureAsset, GlxFixtureAsset::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    api(project(":core"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
