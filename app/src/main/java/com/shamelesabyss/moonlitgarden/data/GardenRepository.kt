package com.shamelesabyss.moonlitgarden.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gardenDataStore by preferencesDataStore(name = "moonlit_garden")

class GardenRepository(private val context: Context) {
    private val saveKey = stringPreferencesKey("garden_v1_json")
    private val preImportKey = stringPreferencesKey("pre_import_recovery_json")
    private val journalDraftKey = stringPreferencesKey("journal_draft")

    suspend fun load(): GardenState? = context.gardenDataStore.data.map { preferences ->
        preferences[saveKey]?.let(GardenState::fromLegacyJson)
    }.first()

    suspend fun save(state: GardenState) {
        context.gardenDataStore.edit { it[saveKey] = state.copy(savedAt = System.currentTimeMillis()).toLegacyJson() }
    }

    suspend fun savePreImport(state: GardenState) {
        context.gardenDataStore.edit { it[preImportKey] = state.toLegacyJson() }
    }

    suspend fun preImportBackup(): String? = context.gardenDataStore.data.map { it[preImportKey] }.first()

    suspend fun saveJournalDraft(text: String) {
        context.gardenDataStore.edit { preferences ->
            if (text.isEmpty()) preferences.remove(journalDraftKey) else preferences[journalDraftKey] = text
        }
    }

    suspend fun loadJournalDraft(): String =
        context.gardenDataStore.data.map { it[journalDraftKey].orEmpty() }.first()
}
