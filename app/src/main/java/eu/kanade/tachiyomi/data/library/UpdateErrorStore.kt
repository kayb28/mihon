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

    // Carga perezosa: evita I/O de disco en el hilo que construye el singleton
    private val _errorsFlow: MutableStateFlow<List<UpdateError>> by lazy { MutableStateFlow(loadErrors()) }
    val errorsFlow: StateFlow<List<UpdateError>>
        get() = _errorsFlow.asStateFlow()

    fun getErrors(): List<UpdateError> = _errorsFlow.value

    fun getBrokenLinkErrors(): List<UpdateError> {
        return _errorsFlow.value.filter { it.type == UpdateError.ErrorType.BROKEN_LINK }
    }

    fun getErrorMangaIds(): Set<Long> {
        return _errorsFlow.value.mapTo(HashSet()) { it.mangaId }
    }

    /**
     * Reemplaza todos los errores (comportamiento original).
     */
    @Synchronized
    fun setErrors(errors: List<UpdateError>) {
        saveToFile(errors)
    }

    /**
     * Merge de errores nuevos con los existentes.
     * Los errores de la misma manga se actualizan (no duplican).
     *
     * Un error HTTP 404/410 solo se promueve a [UpdateError.ErrorType.BROKEN_LINK] tras
     * [BROKEN_LINK_CONFIRMATION_THRESHOLD] corridas consecutivas de actualización fallidas
     * con ese código: muchas fuentes devuelven 404 en rate-limits o bloqueos temporales.
     * [UpdateError.ErrorType.BROKEN_LINK] confirmado es "pegajoso": no se degrada a TEMPORARY
     * hasta que la manga se actualice con éxito ([removeErrorsForManga]).
     */
    @Synchronized
    fun addErrors(newErrors: List<UpdateError>) {
        if (newErrors.isEmpty()) return
        val existing = _errorsFlow.value
        val merged = (existing + newErrors)
            .groupBy { it.mangaId }
            .map { (_, errorsForManga) ->
                val newest = errorsForManga.maxByOrNull { it.timestamp }!!
                val previous = existing.firstOrNull { it.mangaId == newest.mangaId }
                val isHttpBrokenCode = isBrokenHttpCode(newest.httpCode)
                val streak = when {
                    !isHttpBrokenCode -> 0
                    previous != null && isBrokenHttpCode(previous.httpCode) -> previous.brokenLinkStreak + 1
                    else -> 1
                }
                val type = when {
                    newest.type == UpdateError.ErrorType.BROKEN_LINK -> UpdateError.ErrorType.BROKEN_LINK
                    previous?.type == UpdateError.ErrorType.BROKEN_LINK -> UpdateError.ErrorType.BROKEN_LINK
                    streak >= BROKEN_LINK_CONFIRMATION_THRESHOLD -> UpdateError.ErrorType.BROKEN_LINK
                    else -> UpdateError.ErrorType.TEMPORARY
                }
                newest.copy(
                    type = type,
                    brokenLinkStreak = streak,
                    retryCount = maxOf(newest.retryCount, previous?.retryCount ?: 0),
                )
            }
        saveToFile(merged)
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

    private fun saveToFile(errors: List<UpdateError>) {
        try {
            val content = json.encodeToString(errors)
            errorFile.writeText(content)
            _errorsFlow.value = errors
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

    private fun isBrokenHttpCode(code: Int?): Boolean = code != null && code in BROKEN_LINK_HTTP_CODES

    private companion object {
        val BROKEN_LINK_HTTP_CODES = setOf(404, 410)
        const val BROKEN_LINK_CONFIRMATION_THRESHOLD = 3
    }
}
