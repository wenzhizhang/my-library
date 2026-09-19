package top.dingfengbo.mylibrary.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.data.ExportRepository
import top.dingfengbo.mylibrary.data.ExportRepository.Format
import top.dingfengbo.mylibrary.data.ExportRepository.Scope
import top.dingfengbo.mylibrary.data.net.ApiException
import top.dingfengbo.mylibrary.data.net.ErrorKind

/** What a pending SAF "save as" is for, so the result can be routed back to the right request. */
sealed interface ExportRequest {
    data class Data(val format: Format, val scope: Scope) : ExportRequest
    data object Database : ExportRequest
}

data class ExportUiState(
    val format: Format = Format.Csv,
    val scope: Scope = Scope.Books,
    val running: Boolean = false,
    val done: String? = null,
    val error: Throwable? = null,
)

class ExportViewModel(
    private val repository: ExportRepository,
    private val context: Context,
    private val onSessionRejected: () -> Unit,
) : ViewModel() {
    private val _ui = MutableStateFlow(ExportUiState())
    val ui: StateFlow<ExportUiState> = _ui.asStateFlow()

    fun onFormatChange(format: Format) = _ui.update { it.copy(format = format, done = null, error = null) }

    fun onScopeChange(scope: Scope) = _ui.update { it.copy(scope = scope, done = null, error = null) }

    fun suggestedName(request: ExportRequest): String = when (request) {
        is ExportRequest.Data ->
            repository.suggestedName(request.scope.value, request.format.extension)

        ExportRequest.Database -> repository.suggestedName("database", "db")
    }

    /** Streams straight into the file the user picked — exports can be megabytes. */
    fun run(request: ExportRequest, target: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(running = true, done = null, error = null) }
            // Opening the target only when the request succeeded is the repository's job, so a
            // failed export cannot leave an empty file behind.
            val sink: () -> OutputStream = {
                context.contentResolver.openOutputStream(target) ?: throw ApiException(ErrorKind.Unknown)
            }
            val outcome = when (request) {
                is ExportRequest.Data -> repository.download(request.format, request.scope, sink)
                ExportRequest.Database -> repository.downloadDatabase(sink)
            }.exceptionOrNull()

            // Cancellation never reaches here: the repository's ApiErrors.call rethrows it so a
            // screen the user left does not paint an error.
            if ((outcome as? ApiException)?.kind == ErrorKind.Expired) onSessionRejected()

            _ui.update {
                it.copy(
                    running = false,
                    done = if (outcome == null) suggestedName(request) else null,
                    error = outcome,
                )
            }
        }
    }
}
