package me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectSummary
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository

/** Una tarjeta de «Mis fincas»: el resumen y, si se pudo leer, la finca para la miniatura. */
data class ProjectCardUi(val summary: ProjectSummary, val data: ProjectData?)

/** Estados de la pantalla 01 / 01b (DESIGN.md §2: cargando, vacío, error, éxito). */
sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data object Empty : ProjectsUiState
    data class Content(val projects: List<ProjectCardUi>) : ProjectsUiState
    data object Error : ProjectsUiState
}

class ProjectsViewModel(
    private val repository: ProjectRepository,
    /** Tras eliminar una finca (p. ej. cancelar sus recordatorios). */
    private val onDeleted: (projectId: String) -> Unit = {},
) : ViewModel() {

    /**
     * Elimina la finca [projectId] del teléfono; [onResult] recibe null si se eliminó o el
     * mensaje para el usuario si no (la finca queda como estaba). La lista se actualiza sola.
     */
    fun delete(projectId: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val message = try {
                repository.deleteProject(projectId)
                onDeleted(projectId)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo eliminar «$name». La finca sigue igual en el teléfono."
            }
            onResult(message)
        }
    }

    val state: StateFlow<ProjectsUiState> = repository.projects()
        .map { summaries ->
            if (summaries.isEmpty()) {
                ProjectsUiState.Empty
            } else {
                ProjectsUiState.Content(
                    summaries.map { summary ->
                        ProjectCardUi(summary, runCatching { repository.project(summary.id) }.getOrNull())
                    },
                )
            }
        }
        .catch { emit(ProjectsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState.Loading)
}
