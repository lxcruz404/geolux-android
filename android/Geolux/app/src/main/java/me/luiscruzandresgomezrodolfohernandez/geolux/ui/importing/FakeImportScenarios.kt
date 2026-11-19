package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository

/**
 * SOLO PRUEBAS. Respuestas simuladas de `inspect` para ver cada pantalla de
 * importación (02a–02e) en el emulador sin paquetes reales. Se activan solo en
 * la versión de depuración y solo con el repositorio Fake:
 *
 * `adb shell am start -n <paquete>/.MainActivity --es escenario_importacion reemplazar`
 *
 * Ningún dato de aquí sale del lector real: es finca-demo y errores de ejemplo.
 */
object FakeImportScenarios {

    const val EXTRA = "escenario_importacion"

    fun apply(repository: ProjectRepository, scenario: String?) {
        val fake = repository as? FakeProjectRepository ?: return
        val demo = FakeProjectRepository.demo
        fake.nextInspect = when (scenario) {
            "nuevo" -> { _ -> FakeProjectRepository.previewOf(demo, ImportDecision.NEW) }
            "ya_importado" -> { _ -> FakeProjectRepository.previewOf(demo, ImportDecision.ALREADY_IMPORTED) }
            // El teléfono tiene un paquete del 12/10; el archivo (09/10) es más antiguo.
            "reemplazar" -> { _ ->
                FakeProjectRepository.previewOf(demo, ImportDecision.ASK_REPLACE, existingPackageCreatedAt = "2026-10-12T12:00:00+00:00")
            }
            "danado" -> { _ ->
                ImportPreview.Rejected(
                    "finca-demo.geolux",
                    Issue("hash_mismatch", "El archivo layers/zones.geojson está dañado o fue modificado.", "layers/zones.geojson"),
                )
            }
            "version_nueva" -> { _ ->
                ImportPreview.Rejected(
                    "finca-demo.geolux",
                    Issue("too_new", "El paquete se creó con una versión más nueva de GeoLux (formato 2); actualiza la aplicación para abrirlo."),
                )
            }
            else -> null
        }
    }
}
