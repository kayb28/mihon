package eu.kanade.tachiyomi.data.library

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

@Serializable
data class UpdateError(
    val mangaId: Long,
    val sourceId: Long,
    val httpCode: Int? = null,
    val message: String? = null,
    val type: ErrorType,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
) {
    @Serializable
    enum class ErrorType {
        BROKEN_LINK,
        TEMPORARY,
    }
}

@Inject
@SingleIn(AppScope::class)
class UpdateErrorStore(
    private val context: Context,
    private val json: Json,
) {
    private val errorFile: File
        get() = File(context.filesDir, "update_errors.json")

    private val _errorsFlow = MutableStateFlow<List<UpdateError>>(loadErrors())
    val errorsFlow: StateFlow<List<UpdateError>> = _errorsFlow.asStateFlow()

    fun getErrors(): List<UpdateError> = _errorsFlow.value

    fun getBrokenLinkErrors(): List<UpdateError> {
        return _errorsFlow.value.filter { it.type == UpdateError.ErrorType.BROKEN_LINK }
    }

    fun getErrorMangaIds(): Set<Long> {
        return _errorsFlow.value.mapTo(HashSet()) { it.mangaId }
    }

    /**
     * Reemplaza todos los errores: la lista pasa a ser exactamente [errors].
     * Es el camino de la pasada completa de actualización (toda la biblioteca) — los
     * títulos que ya no fallan salen de la lista en vez de arrastrar su error viejo.
     * A los que vuelven a fallar se les conserva el retryCount acumulado.
     */
    @Synchronized
    fun setErrors(errors: List<UpdateError>) {
        val previous = _errorsFlow.value.associateBy { it.mangaId }
        val merged = errors.map { err ->
            val old = previous[err.mangaId]
            if (old != null && old.retryCount > err.retryCount) err.copy(retryCount = old.retryCount) else err
        }
        saveToFile(merged)
    }

    /**
     * Pasada parcial (por categoría o fuente): reemplaza los errores de los títulos
     * [processedIds] — los que no fallaron salen de la lista — y deja intactos los del
     * resto, que esta corrida no tocó. Sin [processedIds] no hace nada (no se sabe a quién
     * limpiar).
     */
    @Synchronized
    fun replaceForManga(newErrors: List<UpdateError>, processedIds: Set<Long>) {
        if (processedIds.isEmpty()) return
        val previous = _errorsFlow.value.associateBy { it.mangaId }
        val fresh = newErrors.map { err ->
            val old = previous[err.mangaId]
            if (old != null && old.retryCount > err.retryCount) err.copy(retryCount = old.retryCount) else err
        }.associateBy { it.mangaId }
        val updated = buildList {
            _errorsFlow.value.filterTo(this) { it.mangaId !in processedIds }
            addAll(fresh.values)
        }
        saveToFile(updated)
    }

    /**
     * Incrementar retryCount para un manga específico.
     */
    @Synchronized
    fun incrementRetryCount(mangaId: Long) {
        val updated = _errorsFlow.value.map { error ->
            if (error.mangaId == mangaId) {
                error.copy(retryCount = error.retryCount + 1)
            } else {
                error
            }
        }
        saveToFile(updated)
    }

    @Synchronized
    fun removeErrorsForManga(mangaId: Long) {
        val updated = _errorsFlow.value.filterNot { it.mangaId == mangaId }
        saveToFile(updated)
    }

    @Synchronized
    fun clear() {
        saveToFile(emptyList())
    }

    // Guardado atómico (feature del fork): escribe a un .tmp y hace rename; si el rename
    // falla, reintenta tras borrar el destino; como último recurso, escritura directa.
    private fun saveToFile(errors: List<UpdateError>) {
        try {
            val content = json.encodeToString(errors)
            val file = errorFile
            val tempFile = File(context.filesDir, "update_errors.json.tmp")
            tempFile.writeText(content)
            if (tempFile.renameTo(file) || run {
                    file.delete()
                    tempFile.renameTo(file)
                }
            ) {
                _errorsFlow.value = errors
            } else {
                file.writeText(content)
                _errorsFlow.value = errors
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save update errors" }
        }
    }

    private fun loadErrors(): List<UpdateError> {
        return try {
            val file = errorFile
            if (file.exists()) {
                val content = file.readText()
                json.decodeFromString<List<UpdateError>>(content)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to load update errors" }
            emptyList()
        }
    }
}
