package tachiyomi.domain.source.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceContentFilterEntryTest {
    @Test
    fun `content filters are keyed by source and stable filter id`() {
        val entry = SourceContentFilterEntry(42L, "exclude-adult")
        entry.serialize() shouldBe "42|exclude-adult"
        SourceContentFilterEntry.deserialize(entry.serialize()) shouldBe entry
        SourceContentFilterEntry.deserialize("bad|exclude-adult") shouldBe null
        SourceContentFilterEntry.deserialize("42|") shouldBe null
    }
}
