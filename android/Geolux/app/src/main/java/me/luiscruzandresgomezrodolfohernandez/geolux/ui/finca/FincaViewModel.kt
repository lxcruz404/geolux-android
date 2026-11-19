package me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository

/** Estados de una finca abierta: lo comparten Mi finca, Potreros y el detalle de potrero. */
sealed interface FincaUiState {
    data object Loading : FincaUiState
    data class Ready(val data: ProjectData) : FincaUiState
    data object Error : FincaUiState
}

class FincaViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val _state = MutableStateFlow<FincaUiState>(FincaUiState.Loading)
    val state: StateFlow<FincaUiState> = _state.asStateFlow()

    private var openId: String? = null
    private var loading: Job? = null

    /** Carga la finca [projectId]; si ya está cargada no hace nada. */
    fun open(projectId: String) {
        if (projectId == openId && _state.value !is FincaUiState.Error) return
        openId = projectId
        loading?.cancel()
        _state.value = FincaUiState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                FincaUiState.Ready(repository.project(projectId))
            } catch (e: CancellationException) {
                throw e // una carga cancelada no debe pisar el estado de la nueva
            } catch (e: Exception) {
                FincaUiState.Error
            }
        }
    }

    /**
     * Aplica una edición (una operación de `FarmEditor`) a la finca abierta y la guarda
     * en el teléfono. Devuelve null si se guardó, o el mensaje para el usuario si la
     * edición no es válida (las reglas de Desktop) o no se pudo guardar; en ese caso
     * la finca queda como estaba.
     */
    suspend fun edit(change: (ProjectData) -> ProjectData): String? {
        val id = openId ?: return "No hay ninguna finca abierta."
        return try {
            _state.value = FincaUiState.Ready(repository.edit(id, change))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            e.message ?: "El cambio no es válido."
        } catch (e: Exception) {
            "No se pudo guardar el cambio en el teléfono. Vuelve a intentarlo."
        }
    }

    /** Vuelve a intentar tras un error. */
    fun retry() {
        val id = openId ?: return
        openId = null
        open(id)
    }
}
