package eu.kanade.tachiyomi.data.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Clock

/** A summary of the most recent observed operation, not a promise of current availability. */
enum class SourceHealthStatus {
    AVAILABLE,
    NETWORK_ERROR,
    NEEDS_VERIFICATION,
    SOURCE_ERROR,
}

data class SourceHealthRecord(
    val sourceId: Long,
    val status: SourceHealthStatus,
    val checkedAt: Long,
    val lastSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
) {
    fun serialize(): String = "$sourceId|${status.name}|$checkedAt|${lastSuccessAt ?: -1}|${lastErrorAt ?: -1}"

    companion object {
        fun deserialize(value: String): SourceHealthRecord? {
            val parts = value.split('|', limit = 5)
            if (parts.size !in setOf(3, 5)) return null
            val sourceId = parts[0].toLongOrNull() ?: return null
            val checkedAt = parts[2].toLongOrNull() ?: return null
            val status = runCatching { SourceHealthStatus.valueOf(parts[1]) }.getOrNull() ?: return null
            val lastSuccessAt = parts.getOrNull(3)?.toLongOrNull()?.takeIf { it >= 0 }
                ?: checkedAt.takeIf { status == SourceHealthStatus.AVAILABLE }
            val lastErrorAt = parts.getOrNull(4)?.toLongOrNull()?.takeIf { it >= 0 }
                ?: checkedAt.takeIf { status != SourceHealthStatus.AVAILABLE }
            return SourceHealthRecord(sourceId, status, checkedAt, lastSuccessAt, lastErrorAt)
        }
    }
}

@Inject
@SingleIn(AppScope::class)
class SourceHealthStore(preferenceStore: PreferenceStore) {
    val records: Preference<Set<SourceHealthRecord>> = preferenceStore.getObjectSetFromStringSet(
        key = "source_health_last_observed",
        defaultValue = emptySet(),
        serializer = SourceHealthRecord::serialize,
        deserializer = SourceHealthRecord::deserialize,
    )

    private val mutex = Mutex()

    fun latest(sourceId: Long): SourceHealthRecord? = records.get().firstOrNull { it.sourceId == sourceId }

    suspend fun recordSuccess(sourceId: Long) {
        record(sourceId, SourceHealthStatus.AVAILABLE)
    }

    suspend fun recordFailure(sourceId: Long, error: Throwable) {
        record(sourceId, statusFor(error))
    }

    suspend fun recordNeedsVerification(sourceId: Long) {
        record(sourceId, SourceHealthStatus.NEEDS_VERIFICATION)
    }

    private suspend fun record(sourceId: Long, status: SourceHealthStatus) {
        mutex.withLock {
            records.getAndSet { current ->
                val previous = current.firstOrNull { it.sourceId == sourceId }
                val now = Clock.System.now().toEpochMilliseconds()
                val record = SourceHealthRecord(
                    sourceId = sourceId,
                    status = status,
                    checkedAt = now,
                    lastSuccessAt = if (status == SourceHealthStatus.AVAILABLE) now else previous?.lastSuccessAt,
                    lastErrorAt = if (status == SourceHealthStatus.AVAILABLE) previous?.lastErrorAt else now,
                )
                (current.filterNot { it.sourceId == sourceId } + record).toSet()
            }
        }
    }

    private fun statusFor(error: Throwable): SourceHealthStatus {
        val causeChain = generateSequence(error) { it.cause }.toList()
        val httpCode = causeChain.filterIsInstance<HttpException>().firstOrNull()?.code
        return when {
            causeChain.any { it is SourceNotInstalledException } -> SourceHealthStatus.NEEDS_VERIFICATION
            httpCode != null && httpCode in setOf(401, 403, 429, 451) -> SourceHealthStatus.NEEDS_VERIFICATION
            causeChain.any { it is UnknownHostException || it is SocketTimeoutException || it is IOException } ->
                SourceHealthStatus.NETWORK_ERROR
            else -> SourceHealthStatus.SOURCE_ERROR
        }
    }
}
