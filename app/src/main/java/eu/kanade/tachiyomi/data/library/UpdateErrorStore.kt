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

    @Synchronized
    fun setErrors(errors: List<UpdateError>) {
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

    @Synchronized
    fun removeErrorsForManga(mangaId: Long) {
        val updated = _errorsFlow.value.filterNot { it.mangaId == mangaId }
        setErrors(updated)
    }

    @Synchronized
    fun clear() {
        setErrors(emptyList())
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
