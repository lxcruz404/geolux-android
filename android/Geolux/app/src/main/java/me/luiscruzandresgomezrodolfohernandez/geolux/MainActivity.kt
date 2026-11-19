package me.luiscruzandresgomezrodolfohernandez.geolux

import me.luiscruzandresgomezrodolfohernandez.geolux.reminders.ReminderScheduler
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d.GpuLimits
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.GeoLuxApp
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.FincaViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.FakeImportScenarios
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.ImportViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects.ProjectsViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as GeoLuxApplication).projectRepository

        // Solo en depuración y con el repositorio Fake: respuestas simuladas para
        // ver cada pantalla de importación (ver FakeImportScenarios).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra(FakeImportScenarios.EXTRA)?.let { FakeImportScenarios.apply(repository, it) }
        }

        // Los ViewModels viven con la Activity (sobreviven al giro de pantalla).
        // Se crean aquí porque el proyecto aún no tiene lifecycle-viewmodel-compose.
        val factory = viewModelFactory {
            initializer {
                val app = applicationContext
                ProjectsViewModel(repository, onDeleted = { id -> ReminderScheduler.forgetProject(app, id) })
            }
            initializer { FincaViewModel(repository) }
            initializer { ImportViewModel(repository) }
            initializer {
                // Solo el contexto de la aplicación: el ViewModel vive más que la Activity.
                val app = applicationContext
                ManagementViewModel(repository, onSaved = { data -> ReminderScheduler.reschedule(app, data) })
            }
        }
        val provider = ViewModelProvider(this, factory)
        val projectsViewModel = provider[ProjectsViewModel::class.java]
        val fincaViewModel = provider[FincaViewModel::class.java]
        val importViewModel = provider[ImportViewModel::class.java]
        val managementViewModel = provider[ManagementViewModel::class.java]

        setContent {
            GeoLuxTheme {
                GeoLuxApp(
                    projectsViewModel = projectsViewModel,
                    fincaViewModel = fincaViewModel,
                    importViewModel = importViewModel,
                    managementViewModel = managementViewModel,
                    locationSource = (application as GeoLuxApplication).locationSource,
                    orthoTiles = repository::orthoTiles,
                    productFile = repository::productFile,
                    exportPackage = { projectId, destination ->
                        try {
                            repository.exportPackage(projectId, destination)
                            null
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "No se pudo exportar la finca: ${e.message ?: "error desconocido"}"
                        }
                    },
                    viewingModel = { projectId, productId, onProgress ->
                        repository.viewingModel(
                            projectId, productId,
                            maxTextureSize = GpuLimits.maxTextureSize(),
                            budgetBytes = GpuLimits.textureBudgetBytes(applicationContext),
                            onProgress = onProgress,
                        )
                    },
                )
            }
        }
    }

    /** Al volver a la app (p. ej. tras importar o reinstalar una finca) los avisos se ajustan a los datos. */
    override fun onResume() {
        super.onResume()
        ReminderScheduler.rescheduleAll(applicationContext)
    }
}
