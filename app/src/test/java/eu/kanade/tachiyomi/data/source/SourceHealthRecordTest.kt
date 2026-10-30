package eu.kanade.tachiyomi.data.source

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceHealthRecordTest {
    @Test
    fun `health record keeps success and error timestamps`() {
        val record = SourceHealthRecord(
            sourceId = 9L,
            status = SourceHealthStatus.NETWORK_ERROR,
            checkedAt = 300L,
            lastSuccessAt = 100L,
            lastErrorAt = 300L,
        )
        SourceHealthRecord.deserialize(record.serialize()) shouldBe record
    }

    @Test
    fun `old records remain readable and corrupt records are ignored`() {
        SourceHealthRecord.deserialize("9|AVAILABLE|100") shouldBe
            SourceHealthRecord(9L, SourceHealthStatus.AVAILABLE, 100L, lastSuccessAt = 100L)
        SourceHealthRecord.deserialize("bad|SOURCE_ERROR|100") shouldBe null
    }
}
