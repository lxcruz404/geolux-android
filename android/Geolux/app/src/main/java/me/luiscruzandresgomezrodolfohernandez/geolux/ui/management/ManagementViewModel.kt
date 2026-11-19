package me.luiscruzandresgomezrodolfohernandez.geolux.ui.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ValidationException
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository

/** Gestión de la finca abierta (actividades, compras, trabajadores, dinero). */
sealed interface ManagementUiState {
    data object Loading : ManagementUiState
    data class Ready(val data: ManagementData) : ManagementUiState
    data object Error : ManagementUiState
}

/**
 * Lee y guarda la gestión con [ProjectRepository]; las reglas están en [ManagementOps]
 * (las mismas que Desktop). Cada cambio se guarda entero o no se guarda.
 */
class ManagementViewModel(
    private val repository: ProjectRepository,
    val ops: ManagementOps = ManagementOps(),
    /** Se avisa tras cada cambio guardado (p. ej. para reprogramar los recordatorios). */
    private val onSaved: (ManagementData) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<ManagementUiState>(ManagementUiState.Loading)
    val state: StateFlow<ManagementUiState> = _state.asStateFlow()
    private var openId: String? = null
    private var loading: Job? = null

    fun open(projectId: String) {
        // Siempre se relee: la finca pudo reinstalarse con gestión fusionada desde otro paquete.
        openId = projectId
        loading?.cancel()
        _state.value = ManagementUiState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                ManagementUiState.Ready(repository.management(projectId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ManagementUiState.Error
            }
        }
    }

    /** Aplica [change]; null si se guardó, o el mensaje para la persona. */
    suspend fun edit(change: (ManagementData) -> ManagementData): String? {
        val id = openId ?: return "No hay ninguna finca abierta."
        return try {
            val saved = repository.editManagement(id, change)
            _state.value = ManagementUiState.Ready(saved)
            onSaved(saved)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ValidationException) {
            e.message ?: "El dato no es válido."
        } catch (e: IllegalArgumentException) {
            e.message ?: "El dato no es válido."
        } catch (e: Exception) {
            "No se pudo guardar en el teléfono. Vuelve a intentarlo."
        }
    }

    companion object {
        /** Ids de la finca a los que una actividad puede enlazarse, por tipo de enlace. */
        fun geoIds(project: ProjectData): Map<String, Set<String>> = mapOf(
            "zone" to project.zones.map { it.id }.toSet(),
            "map_line" to project.lines.map { it.id }.toSet(),
            "map_element" to project.elements.map { it.id }.toSet(),
            "work_area" to setOfNotNull(project.workArea?.id),
        )
    }
}
