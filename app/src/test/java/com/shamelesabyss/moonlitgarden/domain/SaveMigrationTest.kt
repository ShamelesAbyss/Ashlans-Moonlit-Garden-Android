package com.shamelesabyss.moonlitgarden.domain

import com.shamelesabyss.moonlitgarden.data.GardenState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveMigrationTest {
    @Test fun rejectsAnUnsupportedSaveVersion() {
        val json = JSONObject(GardenState.fresh().toLegacyJson()).put("version", 2).toString()
        assertThrows(IllegalArgumentException::class.java) { GardenState.fromLegacyJson(json) }
    }

    @Test fun rejectsSavesThatWouldLoseOneOfTheNineBeds() {
        val json = JSONObject(GardenState.fresh().toLegacyJson()).put("beds", JSONArray()).toString()
        assertThrows(IllegalArgumentException::class.java) { GardenState.fromLegacyJson(json) }
    }

    @Test fun mergeNeverDropsExistingJournalPages() {
        val current = GardenState.fresh().copy(journal = listOf(
            com.shamelesabyss.moonlitgarden.data.JournalEntry("kept page", "today"),
        ))
        val imported = GardenState.fresh().copy(journal = listOf(
            com.shamelesabyss.moonlitgarden.data.JournalEntry("older page", "yesterday"),
        ))
        val restored = imported.copy(journal = GardenState.mergeJournal(imported.journal, current.journal))
        assertEquals(listOf("older page", "kept page"), restored.journal.map { it.text })
    }
}
