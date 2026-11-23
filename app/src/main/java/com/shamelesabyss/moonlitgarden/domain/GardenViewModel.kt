package com.shamelesabyss.moonlitgarden.domain

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shamelesabyss.moonlitgarden.data.GardenRepository
import com.shamelesabyss.moonlitgarden.data.GardenState
import com.shamelesabyss.moonlitgarden.data.JournalEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GardenRepository(application)
    var garden by mutableStateOf(GardenState.fresh())
        private set
    var loaded by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set
    var journalDraft by mutableStateOf("")
        private set
    var brewRecipeId by mutableStateOf("dew")
        private set
    var brewStep by mutableStateOf(0)
        private set
    var brewStatus by mutableStateOf("")
        private set

    private var tickerJob: Job? = null
    private var draftJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch {
            try {
                garden = repository.load() ?: GardenState.fresh()
                journalDraft = repository.loadJournalDraft()
            } catch (_: Exception) {
                notice = "The local garden save could not be read. Your original private reference has not been changed."
            } finally {
                loaded = true
                startTickerIfReady()
            }
        }
    }

    fun reportStatus(message: String) {
        notice = message
    }

    fun dismissWelcome() {
        garden = garden.copy(milestones = garden.milestones + ("welcome" to true))
        notice = "Nettle: First, a Moonmint seed. Then a little water. I shall supervise."
        persist()
    }

    fun onAppStarted() {
        started = true
        startTickerIfReady()
    }

    fun onAppStopped() {
        started = false
        tickerJob?.cancel()
        tickerJob = null
        persist()
    }

    private fun startTickerIfReady() {
        if (!started || !loaded || tickerJob != null) return
        tickerJob = viewModelScope.launch {
            var secondsSinceSave = 0
            while (isActive) {
                delay(1_000)
                garden = GameRules.tick(garden, 1.0)
                secondsSinceSave += 1
                if (secondsSinceSave >= 10) {
                    secondsSinceSave = 0
                    persist()
                }
            }
        }
    }

    private fun persist() {
        if (!loaded) return
        val snapshot = garden.copy(savedAt = System.currentTimeMillis())
        garden = snapshot
        viewModelScope.launch {
            runCatching { repository.save(snapshot) }
                .onFailure { notice = "Local save unavailable. Export a JSON backup before leaving this device." }
        }
    }

    private fun apply(result: Pair<GardenState, String>) {
        garden = result.first
        notice = result.second
        persist()
    }

    fun selectArea(area: String) {
        if (area !in setOf("garden", "wood", "glade")) return
        garden = garden.copy(area = area)
        notice = when (area) {
            "wood" -> "Ghostcaps and Duskthorn grow beneath the old trees. Gather each little patch once per evening."
            "glade" -> "The glade is here whenever you want it. For Ashlan, always loved."
            else -> "Warm windows, familiar paths. The garden kept your place."
        }
        persist()
    }

    fun selectSeed(type: String) {
        if (type in GardenState.SEED_KEYS) {
            garden = garden.copy(selectedSeed = type)
            notice = "${garden.seeds[type] ?: 0} ${com.shamelesabyss.moonlitgarden.data.CropType.fromKey(type)?.title} seeds ready."
        }
    }

    fun tend(index: Int) = apply(GameRules.tend(garden, index))
    fun gather(index: Int) = apply(GameRules.gather(garden, index))
    fun rest() = apply(GameRules.rest(garden))
    fun claimParcel() = apply(GameRules.claimParcel(garden))
    fun useBottle(id: String) = apply(GameRules.useBottle(garden, id))
    fun buySeeds(id: String) = apply(GameRules.buySeeds(garden, id))
    fun upgradeSoil() = apply(GameRules.upgradeSoil(garden))
    fun sellBottle(id: String) = apply(GameRules.sellBottle(garden, id))
    fun giftMoonmint() = apply(GameRules.giftMoonmint(garden))

    fun scratchNettle() {
        garden = GameRules.scratchNettle(garden)
        notice = "Nettle leans into your hand. A tiny, thunderous purr."
        persist()
    }

    fun talkToNettle() {
        garden = GameRules.talkToNettle(garden)
        notice = nettleLine()
        persist()
    }

    fun playWithNettle() {
        notice = "Nettle pounces! The spark wins, narrowly."
    }

    private fun nettleLine(): String {
        val lines = listOf(
            "I have inspected the moon. Still round enough.",
            "My hat is not too large. My ears are simply ambitious.",
            "The mushrooms and I have an understanding. I do not sit on them.",
            "If you need me, I will be somewhere inconveniently close.",
            "I am practicing being mysterious. Please look suitably impressed.",
            "This garden is an excellent place to do a small amount of nothing.",
            "I have labelled the poison shelf. The labels say: definitely not tea.",
            "Duskthorn, Ghostcap, three careful stirs. I am a cat of considerable scholarship.",
            "The moths approve of that violet bottle. I asked.",
        )
        return when {
            garden.hasMilestone("poison") && garden.talkCount % 3 == 0 -> "A proper little poison cabinet! Only curses need be nervous."
            !garden.hasMilestone("harvest") -> "A seed, a drink, a moment. We can manage that."
            !garden.hasMilestone("gift") -> "A Moonmint leaf would make an excellent snack. For research."
            garden.hasMilestone("bloom") && garden.talkCount % 3 == 0 -> "I saw the Bloom open. Even my whiskers went quiet."
            else -> lines[(garden.talkCount - 1).mod(lines.size)]
        }
    }

    fun placeOffering(type: String) = apply(GameRules.offer(garden, type))

    fun setReducedMotion(enabled: Boolean) = updateSettings { it.copy(reducedMotion = enabled) }
    fun setMusicVolume(value: Float) = updateSettings { it.copy(musicVolume = value.coerceIn(0f, 1f)) }
    fun setAmbienceVolume(value: Float) = updateSettings { it.copy(ambienceVolume = value.coerceIn(0f, 1f)) }
    fun setEffectsVolume(value: Float) = updateSettings { it.copy(effectsVolume = value.coerceIn(0f, 1f)) }
    fun setAccentColor(value: String) {
        if (value.matches(Regex("#[0-9a-fA-F]{6}"))) updateSettings { it.copy(accentColor = value) }
    }
    fun setFavoriteFlower(value: String) = updateSettings { it.copy(favoriteFlower = value.take(100)) }
    fun setShuffle(value: Boolean) = updateSettings { it.copy(shuffle = value) }
    fun setRepeat(value: String) = updateSettings { it.copy(repeat = if (value == "one") "one" else "all") }

    private fun updateSettings(transform: (com.shamelesabyss.moonlitgarden.data.GardenSettings) -> com.shamelesabyss.moonlitgarden.data.GardenSettings) {
        garden = garden.copy(settings = transform(garden.settings))
        persist()
    }

    fun chooseRecipe(id: String) {
        if (GameRules.recipes.none { it.id == id }) return
        brewRecipeId = id
        brewStep = 0
        brewStatus = ""
    }

    fun advanceBrewing() {
        val recipe = GameRules.recipes.firstOrNull { it.id == brewRecipeId } ?: return
        if (!GameRules.canBrew(garden, recipe)) {
            brewStatus = "Gather the ingredients before beginning the ritual."
            return
        }
        if (brewStep < GameRules.BREW_STEP_COUNT - 1) {
            brewStep += 1
            brewStatus = if (brewStep == 1) "The ingredients shimmer in the mortar."
            else "Three slow stirs. A little magic begins to gather."
            return
        }
        brewStep = 0
        val result = GameRules.brew(garden, recipe.id)
        garden = result.first
        brewStatus = result.second
        notice = result.second
        persist()
    }

    fun updateJournalDraft(text: String) {
        journalDraft = text.take(20_000)
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.saveJournalDraft(journalDraft) }
        }
    }

    fun keepJournalPage() {
        val text = journalDraft
        if (text.isBlank()) {
            notice = "There is no hurry to fill these pages."
            return
        }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        garden = GameRules.writeJournal(garden, text, now).first
        journalDraft = ""
        viewModelScope.launch { repository.saveJournalDraft("") }
        notice = "Your page is kept."
        persist()
    }

    fun exportBackup(): String = garden.copy(savedAt = System.currentTimeMillis()).toLegacyJson()

    fun importBackup(json: String): Boolean {
        return try {
            val imported = GardenState.fromLegacyJson(json)
            val merged = imported.copy(journal = GardenState.mergeJournal(imported.journal, garden.journal))
            viewModelScope.launch {
                repository.savePreImport(garden)
                repository.save(merged)
                garden = merged
                notice = "Backup restored. Every existing journal page was kept and merged."
            }
            true
        } catch (error: Exception) {
            notice = "That backup could not be read. Your garden has not changed. ${error.message.orEmpty()}"
            false
        }
    }

    fun restorePreImportBackup() {
        viewModelScope.launch {
            try {
                val json = repository.preImportBackup() ?: run {
                    notice = "There is no pre-import recovery copy on this device."
                    return@launch
                }
                val previous = GardenState.fromLegacyJson(json)
                val restored = previous.copy(journal = GardenState.mergeJournal(previous.journal, garden.journal))
                repository.savePreImport(garden)
                repository.save(restored)
                garden = restored
                notice = "The previous garden save was restored. All current journal pages were kept."
            } catch (_: Exception) {
                notice = "The pre-import recovery copy could not be read. Your current garden is unchanged."
            }
        }
    }

    fun saveCurrentNow() = persist()
}
