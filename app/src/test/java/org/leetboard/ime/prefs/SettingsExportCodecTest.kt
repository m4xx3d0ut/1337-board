package org.leetboard.ime.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExportCodecTest {
    private val snapshot = SettingsExportSnapshot(
        preferences = linkedMapOf(
            "layout_id" to "qwerty4",
            "gesture_typing_enabled" to true,
            "edge_key_width_scale" to 0.75f,
            "hidden_optional_keys" to listOf("mic", "tab"),
            "glide_corrections" to "te\tthe\t1\t0\t0\nwrd\tword\t3\t1\t99",
        ),
        importedGlideWords = listOf("termux", "joplin"),
        glideUserLanguageModel = "w\ttest\t2\t42\nb\tgood\tmorning\t3\t99\n",
    )

    @Test
    fun jsonExportRoundTripsSettingsSnapshot() {
        val decoded = decodeSettingsExportSnapshot(snapshot.encode(SettingsExportFormat.JSON))

        assertEquals("qwerty4", decoded.preferences["layout_id"])
        assertEquals(true, decoded.preferences["gesture_typing_enabled"])
        assertEquals(0.75f, (decoded.preferences["edge_key_width_scale"] as Number).toFloat(), 0.001f)
        assertEquals(listOf("mic", "tab"), decoded.preferences["hidden_optional_keys"])
        assertTrue((decoded.preferences["glide_corrections"] as String).contains("wrd\tword"))
        assertEquals(listOf("termux", "joplin"), decoded.importedGlideWords)
        assertEquals(snapshot.glideUserLanguageModel, decoded.glideUserLanguageModel)
    }

    @Test
    fun yamlExportRoundTripsSettingsSnapshot() {
        val decoded = decodeSettingsExportSnapshot(snapshot.encode(SettingsExportFormat.YAML))

        assertEquals("qwerty4", decoded.preferences["layout_id"])
        assertEquals(true, decoded.preferences["gesture_typing_enabled"])
        assertEquals(0.75f, (decoded.preferences["edge_key_width_scale"] as Number).toFloat(), 0.001f)
        assertEquals(listOf("mic", "tab"), decoded.preferences["hidden_optional_keys"])
        assertTrue((decoded.preferences["glide_corrections"] as String).contains("te\tthe"))
        assertEquals(listOf("termux", "joplin"), decoded.importedGlideWords)
        assertEquals(snapshot.glideUserLanguageModel, decoded.glideUserLanguageModel)
    }
}
