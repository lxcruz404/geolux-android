package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportResult
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatFileSize

/**
 * Estados del flujo de importación (DESIGN.md §9, pantallas 02a–02e; SPEC §7–8).
 *
 * - [Checking]: `inspect` en curso (pasos 1 y 2: abrir y comprobar el archivo).
 * - [Preview] 02a: finca nueva, esperando Importar / Cancelar.
 * - [AlreadyImported] 02c: mismo paquete ya importado; no se hace nada.
 * - [AskReplace] 02d: otro paquete de la misma finca; [AskReplace.confirming] es
 *   el diálogo de confirmación. Nunca se reemplaza sin las dos respuestas.
 * - [Importing] 02b: `import` en curso (paso 3).
 * - [Done] 02b final: «Finca importada».
 * - [Failed] 02e: el paquete se rechazó o no se pudo guardar.
 */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Checking : ImportUiState
    data class Preview(val preview: ImportPreview.Ready) : ImportUiState
    data class AlreadyImported(val preview: ImportPreview.Ready) : ImportUiState
    data class AskReplace(val preview: ImportPreview.Ready, val confirming: Boolean = false) : ImportUiState
    data class Importing(val preview: ImportPreview.Ready) : ImportUiState
    data class Done(val projectId: String, val projectName: String) : ImportUiState
    data class Failed(val fileName: String?, val error: Issue) : ImportUiState
}

/**
 * Máquina de estados de la importación, sin Android para poder probarla con
 * JUnit. [ImportViewModel] la usa con `viewModelScope`.
 */
class ImportController(
    private val repository: ProjectRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /**
     * Avance en bytes del paso en curso de [ImportUiState.Checking] o
     * [ImportUiState.Importing]; null en los demás estados o si aún no hay datos.
     */
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    /**
     * Valida el archivo elegido con [read] (normalmente `repository.inspect(uri, onProgress)`),
     * que recibe dónde avisar del avance.
     */
    fun inspect(read: suspend (onProgress: (ImportProgress) -> Unit) -> ImportPreview) {
        if (_state.value is ImportUiState.Importing) return
        job?.cancel()
        _progress.value = null
        _state.value = ImportUiState.Checking
        job = scope.launch {
            val preview = try {
                read { _progress.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _progress.value = null
            _state.value = when (preview) {
                null -> ImportUiState.Failed(null, READ_FAILED)
                is ImportPreview.Rejected -> ImportUiState.Failed(preview.fileName, preview.error)
                is ImportPreview.NoSpace -> ImportUiState.Failed(preview.fileName, noSpace(preview))
                is ImportPreview.Ready -> when (preview.decision) {
                    ImportDecision.NEW -> ImportUiState.Preview(preview)
                    ImportDecision.ALREADY_IMPORTED -> ImportUiState.AlreadyImported(preview)
                    ImportDecision.ASK_REPLACE -> ImportUiState.AskReplace(preview)
                }
            }
        }
    }

    /** «Importar» en la vista previa de una finca nueva. */
    fun confirmImport() {
        val current = _state.value as? ImportUiState.Preview ?: return
        install(current.preview, replace = false)
    }

    /** «Reemplazar» en 02d: abre la confirmación; todavía no reemplaza. */
    fun requestReplace() {
        val current = _state.value as? ImportUiState.AskReplace ?: return
        _state.value = current.copy(confirming = true)
    }

    fun dismissReplaceConfirmation() {
        val current = _state.value as? ImportUiState.AskReplace ?: return
        _state.value = current.copy(confirming = false)
    }

    /** Segunda respuesta del usuario: solo aquí se reemplaza (SPEC §8.2). */
    fun confirmReplace() {
        val current = _state.value as? ImportUiState.AskReplace ?: return
        if (!current.confirming) return
        install(current.preview, replace = true)
    }

    /** Cierra el flujo. Durante la importación no se puede cancelar. */
    fun cancel() {
        if (_state.value is ImportUiState.Importing) return
        job?.cancel()
        _progress.value = null
        _state.value = ImportUiState.Idle
    }

    private fun install(preview: ImportPreview.Ready, replace: Boolean) {
        _progress.value = null
        _state.value = ImportUiState.Importing(preview)
        job = scope.launch {
            val result = try {
                repository.import(preview, replace) { _progress.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportResult.Failed(INSTALL_FAILED)
            }
            _progress.value = null
            _state.value = when (result) {
                is ImportResult.Imported -> ImportUiState.Done(result.projectId, preview.projectName)
                ImportResult.AlreadyImported -> ImportUiState.AlreadyImported(preview)
                ImportResult.Cancelled -> ImportUiState.Idle
                is ImportResult.Failed -> ImportUiState.Failed(preview.fileName, result.error)
            }
        }
    }

    companion object {
        // Mismos códigos y textos que GeoluxProjectRepository para fallos del teléfono.
        val READ_FAILED = Issue("read_failed", "No se pudo leer el archivo. Vuelve a elegirlo.")
        val INSTALL_FAILED = Issue("install_failed", "No se pudo guardar la finca en el teléfono. La que tenías sigue igual.")

        private fun noSpace(preview: ImportPreview.NoSpace) = Issue(
            "no_space",
            "Se necesitan al menos ${formatFileSize(preview.neededBytes)} libres y el teléfono tiene " +
                "${formatFileSize(preview.availableBytes)}. Libera espacio y vuelve a intentarlo.",
        )
    }
}
