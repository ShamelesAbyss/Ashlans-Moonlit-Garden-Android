package com.shamelesabyss.moonlitgarden.domain

import com.shamelesabyss.moonlitgarden.data.CropBed
import com.shamelesabyss.moonlitgarden.data.GardenState
import com.shamelesabyss.moonlitgarden.data.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test fun freshSaveRoundTripsInTheOriginalVersionOneShape() {
        val state = GardenState.fresh()
        val restored = GardenState.fromLegacyJson(state.toLegacyJson())
        assertEquals(state.day, restored.day)
        assertEquals(9, restored.beds.size)
        assertEquals(5, restored.seedCount("moon"))
        assertEquals(2, restored.count("mushroom"))
    }

    @Test fun moonmintNeedsTwoTendsThenGrowsForTwentyTwoActiveSeconds() {
        val (planted, _) = GameRules.tend(GardenState.fresh(), 0)
        val (watered, _) = GameRules.tend(planted, 0)
        assertEquals(4, watered.seedCount("moon"))
        val growing = GameRules.tick(watered, 21.0)
        assertTrue((growing.beds[0]?.age ?: 0.0) < 22.0)
        val ripe = GameRules.tick(growing, 1.0)
        val (harvested, _) = GameRules.tend(ripe, 0)
        assertEquals(1, harvested.count("moon"))
        assertEquals(22, harvested.coins)
        assertEquals(null, harvested.beds[0])
    }

    @Test fun rainWatersCropsAndAThirdEveningBeginsWithRain() {
        val state = GardenState(day = 2, beds = listOf(CropBed("moon")) + List(8) { null })
        val rainy = GameRules.tick(state, 180.0)
        assertEquals(3, rainy.day)
        assertEquals("rain", rainy.weather)
        assertTrue(rainy.beds[0]?.watered == true)
    }

    @Test fun woodlandHasEightGhostcapsAndFourDuskthornNodes() {
        var state = GardenState.fresh()
        repeat(8) { index -> state = GameRules.gather(state, index).first }
        repeat(4) { index -> state = GameRules.gather(state, index + 8).first }
        assertEquals(10, state.count("mushroom"))
        assertEquals(4, state.count("dusk"))
        assertFalse(GameRules.gather(state, 0).second.contains("Ghostcap"))
    }

    @Test fun cabinetConsumesIngredientsOnlyWhenBottledAndUnlocksAllPoisons() {
        var state = GardenState.fresh().copy(inventory = GardenState.fresh().inventory + mapOf("moon" to 3, "ember" to 3, "dusk" to 4))
        GameRules.recipes.filter { it.category == "poison" }.forEach { recipe ->
            state = GameRules.brew(state, recipe.id).first
        }
        assertEquals(3, state.discovered.count { id -> GameRules.recipes.first { it.id == id }.category == "poison" })
        assertTrue(state.hasMilestone("poison"))
        assertTrue(state.hasMilestone("apothecary"))
    }

    @Test fun upgradeCostsThirtyFiveThenSixtyFiveAndSpeedsCropsByTwentyFivePercent() {
        val (first, _) = GameRules.upgradeSoil(GardenState.fresh().copy(coins = 35))
        assertEquals(1, first.upgrades)
        assertEquals(0, first.coins)
        assertEquals(1.25, GameRules.tick(first.copy(beds = listOf(CropBed("moon", watered = true)) + List(8) { null }), 1.0).beds[0]!!.age, 0.0001)
        val (second, _) = GameRules.upgradeSoil(first.copy(coins = 65))
        assertEquals(2, second.upgrades)
        assertEquals(0, second.coins)
    }

    @Test fun importJournalKeepsCurrentEntriesAndDeduplicatesAgainstImported() {
        val duplicate = JournalEntry("a thought", "today")
        val kept = JournalEntry("another thought", "yesterday")
        val merged = GardenState.mergeJournal(listOf(duplicate), listOf(duplicate, kept))
        assertEquals(listOf(duplicate, kept), merged)
    }

    @Test fun restMaturesWateredPlantsAndRefreshesForage() {
        val state = GardenState(day = 1, foraged = listOf(1, 9), beds = listOf(CropBed("ember", age = 1.0, watered = true)) + List(8) { null })
        val (next, _) = GameRules.rest(state)
        assertEquals(2, next.day)
        assertTrue(next.foraged.isEmpty())
        assertEquals(32.0, next.beds[0]!!.age, 0.0001)
    }
}
