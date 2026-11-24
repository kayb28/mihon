package eu.kanade.domain.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore

/**
 * JVM tests for the one-shot viewer-mode migration (fix D2).
 */
class WebGpuViewerModeMigrationTest {

    private val newPref: (PreferenceStore) -> tachiyomi.core.common.preference.Preference<BasePreferences.WebGpuViewerMode> =
        { store -> store.getEnum(WebGpuViewerModeMigration.NEW_KEY, BasePreferences.WebGpuViewerMode.STOCK) }

    @Test
    fun `first launch with legacy experimental flag true writes EXPERIMENTAL`() {
        val store = InMemoryPreferenceStore()
        val pref = newPref(store)
        WebGpuViewerModeMigration.migrateOnce(pref, legacyExperimental = true, legacyHq = false)
        assertEquals(BasePreferences.WebGpuViewerMode.EXPERIMENTAL, pref.get())
        assertTrue(pref.isSet())
    }

    @Test
    fun `first launch with legacy HQ flag true writes HQ`() {
        val store = InMemoryPreferenceStore()
        val pref = newPref(store)
        WebGpuViewerModeMigration.migrateOnce(pref, legacyExperimental = false, legacyHq = true)
        assertEquals(BasePreferences.WebGpuViewerMode.HQ, pref.get())
    }

    @Test
    fun `both legacy keys true resolves EXPERIMENTAL (experimental wins)`() {
        val store = InMemoryPreferenceStore()
        val pref = newPref(store)
        WebGpuViewerModeMigration.migrateOnce(pref, legacyExperimental = true, legacyHq = true)
        assertEquals(BasePreferences.WebGpuViewerMode.EXPERIMENTAL, pref.get())
    }

    @Test
    fun `both legacy keys false writes STOCK`() {
        val store = InMemoryPreferenceStore()
        val pref = newPref(store)
        WebGpuViewerModeMigration.migrateOnce(pref, legacyExperimental = false, legacyHq = false)
        assertEquals(BasePreferences.WebGpuViewerMode.STOCK, pref.get())
    }

    @Test
    fun `second launch does not recompute: legacy key change is ignored`() {
        // Launch 1: legacy HQ true → HQ written.
        val store1 = InMemoryPreferenceStore()
        val pref1 = newPref(store1)
        WebGpuViewerModeMigration.migrateOnce(pref1, legacyExperimental = false, legacyHq = true)
        assertEquals(BasePreferences.WebGpuViewerMode.HQ, pref1.get())

        // Launch 2: persisted store now holds HQ; legacy experimental flips to true but must
        // NOT upgrade the value — the unified key is the only source of truth.
        val store2 = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference<Any>(
                    WebGpuViewerModeMigration.NEW_KEY,
                    pref1.get(),
                    BasePreferences.WebGpuViewerMode.STOCK,
                ),
            ),
        )
        val pref2 = newPref(store2)
        WebGpuViewerModeMigration.migrateOnce(pref2, legacyExperimental = true, legacyHq = false)
        assertEquals(BasePreferences.WebGpuViewerMode.HQ, pref2.get(), "legacy keys must not govern after one-shot migration")
        assertFalse(pref2.get() == BasePreferences.WebGpuViewerMode.EXPERIMENTAL)
    }

    @Test
    fun `corrupt unified value (resolved to STOCK by deserializer) is never rewritten by legacy keys`() {
        // The real Android store persists the enum as a string; a corrupt value is resolved to
        // STOCK by getEnum's deserializer and isSet() is still true. Simulate that resolved
        // state with a fake preference; migration must leave it alone.
        var setCalls = 0
        val corrupt = object : tachiyomi.core.common.preference.Preference<BasePreferences.WebGpuViewerMode> {
            override fun key(): String = WebGpuViewerModeMigration.NEW_KEY
            override fun get(): BasePreferences.WebGpuViewerMode = BasePreferences.WebGpuViewerMode.STOCK
            override fun set(value: BasePreferences.WebGpuViewerMode) {
                setCalls++
            }
            override fun isSet(): Boolean = true
            override fun delete() = Unit
            override fun defaultValue(): BasePreferences.WebGpuViewerMode = BasePreferences.WebGpuViewerMode.STOCK
            override fun changes(): kotlinx.coroutines.flow.Flow<BasePreferences.WebGpuViewerMode> =
                kotlinx.coroutines.flow.flowOf(get())
            override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): kotlinx.coroutines.flow.StateFlow<BasePreferences.WebGpuViewerMode> =
                kotlinx.coroutines.flow.MutableStateFlow(get())
        }
        WebGpuViewerModeMigration.migrateOnce(corrupt, legacyExperimental = true, legacyHq = true)
        assertEquals(0, setCalls, "corrupt-but-set value must not be overwritten by migration")
        assertEquals(BasePreferences.WebGpuViewerMode.STOCK, corrupt.get())
    }
}
