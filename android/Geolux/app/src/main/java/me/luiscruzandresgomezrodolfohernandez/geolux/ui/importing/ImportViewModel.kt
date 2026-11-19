package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository

/** Une el selector de archivos del sistema con [ImportController]. */
class ImportViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val controller = ImportController(repository, viewModelScope)
    val state: StateFlow<ImportUiState> = controller.state
    val progress: StateFlow<ImportProgress?> = controller.progress

    /** Resultado del selector; null si el usuario no eligió nada. */
    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        controller.inspect { onProgress -> repository.inspect(uri, onProgress) }
    }

    fun confirmImport() = controller.confirmImport()
    fun requestReplace() = controller.requestReplace()
    fun dismissReplaceConfirmation() = controller.dismissReplaceConfirmation()
    fun confirmReplace() = controller.confirmReplace()
    fun cancel() = controller.cancel()
}
