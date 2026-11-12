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

private val BROKEN_LINK_HTTP_CODES = setOf(404, 410)
private const val BROKEN_LINK_CONFIRMATION_THRESHOLD = 3

@Serializable
data class UpdateError(
    val mangaId: Long,
    val sourceId: Long,
    val httpCode: Int? = null,
    val message: String? = null,
    val type: ErrorType,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val brokenLinkStreak: Int = 0,
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
        val merged = errors.map { err -> mergeWithPrevious(err, previous[err.mangaId]) }
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
        val fresh = newErrors
            .map { err -> mergeWithPrevious(err, previous[err.mangaId]) }
            .associateBy { it.mangaId }
        val updated = buildList {
            _errorsFlow.value.filterTo(this) { it.mangaId !in processedIds }
            addAll(fresh.values)
        }
        saveToFile(updated)
    }

    /**
     * Añade errores tal cual, sin merge con lo anterior: para conservar errores previos de
     * títulos que la pasada no llegó a intentar (cola cortada por caída del origen o
     * presupuesto de la pasada en calma agotado). No debe recibir ids ya presentes.
     */
    @Synchronized
    fun retainErrors(errors: List<UpdateError>) {
        if (errors.isEmpty()) return
        val updated = buildList {
            addAll(_errorsFlow.value)
            addAll(errors)
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

    private fun mergeWithPrevious(err: UpdateError, old: UpdateError?): UpdateError {
        val isBrokenHttpCode = err.httpCode?.let { it in BROKEN_LINK_HTTP_CODES } == true
        val streak = when {
            old == null && isBrokenHttpCode -> 1
            old == null -> 0
            isBrokenHttpCode && old.httpCode?.let { it in BROKEN_LINK_HTTP_CODES } == true ->
                old.brokenLinkStreak + 1
            isBrokenHttpCode -> 1
            else -> 0
        }
        val type = when {
            err.type == UpdateError.ErrorType.BROKEN_LINK -> UpdateError.ErrorType.BROKEN_LINK
            old?.brokenLinkStreak?.let { it >= BROKEN_LINK_CONFIRMATION_THRESHOLD } == true ->
                UpdateError.ErrorType.BROKEN_LINK
            streak >= BROKEN_LINK_CONFIRMATION_THRESHOLD -> UpdateError.ErrorType.BROKEN_LINK
            else -> UpdateError.ErrorType.TEMPORARY
        }
        return err.copy(
            type = type,
            retryCount = maxOf(err.retryCount, old?.retryCount ?: 0),
            brokenLinkStreak = streak,
        )
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
