package com.shamelesabyss.moonlitgarden.domain

import com.shamelesabyss.moonlitgarden.data.CropBed
import com.shamelesabyss.moonlitgarden.data.CropType
import com.shamelesabyss.moonlitgarden.data.GardenState
import com.shamelesabyss.moonlitgarden.data.JournalEntry
import com.shamelesabyss.moonlitgarden.data.Offering

/** Six entirely fictional cabinet recipes from the original garden. */
data class Recipe(
    val id: String,
    val title: String,
    val ingredients: Map<String, Int>,
    val salePrice: Int,
    val category: String,
    val effect: String,
)

object GameRules {
    const val SECONDS_PER_EVENING = 180.0
    const val WOODLAND_NODE_COUNT = 12
    const val BREW_STEP_COUNT = 3

    val recipes = listOf(
        Recipe("dew", "Dewdrop tonic", mapOf("moon" to 1, "mushroom" to 1), 18, "potion", "Waters every planted bed."),
        Recipe("glow", "Lanternlight elixir", mapOf("ember" to 1, "mushroom" to 1), 28, "potion", "Gives the witch a warm, lasting halo."),
        Recipe("star", "Starlace cordial", mapOf("bloom" to 1, "moon" to 1), 45, "potion", "Places a lasting star in the memorial glade."),
        Recipe("blight", "Blightbane poison", mapOf("dusk" to 1, "mushroom" to 1), 24, "poison", "Adds up to 18 seconds to each watered crop."),
        Recipe("veil", "Mothveil venom", mapOf("dusk" to 1, "ember" to 1), 32, "poison", "Leaves a lasting violet mothlight around Nettle."),
        Recipe("unhex", "Thornhex tincture", mapOf("dusk" to 2, "moon" to 1), 40, "poison", "Unravels a make-believe hex into 30 mooncoins."),
    )

    fun canBrew(state: GardenState, recipe: Recipe): Boolean =
        recipe.ingredients.all { (item, required) -> state.count(item) >= required }

    fun tick(state: GardenState, rawSeconds: Double): GardenState {
        if (rawSeconds <= 0.0 || !rawSeconds.isFinite()) return state
        var day = state.day
        var time = state.timeSeconds + rawSeconds
        var weather = state.weather
        var foraged = state.foraged
        while (time >= SECONDS_PER_EVENING) {
            time -= SECONDS_PER_EVENING
            day += 1
            weather = if (day % 3 == 0) "rain" else "clear"
            foraged = emptyList()
        }
        val cropMultiplier = 1.0 + state.upgrades * 0.25
        val beds = state.beds.map { bed ->
            if (bed == null) null
            else {
                val watered = bed.watered || weather == "rain"
                val age = if (watered) (bed.age + rawSeconds * cropMultiplier).coerceAtMost(
                    CropType.fromKey(bed.type)?.secondsToMature ?: bed.age,
                ) else bed.age
                bed.copy(age = age, watered = watered)
            }
        }
        return state.copy(day = day, timeSeconds = time, weather = weather, foraged = foraged, beds = beds)
    }

    fun tend(state: GardenState, index: Int): Pair<GardenState, String> {
        if (index !in state.beds.indices) return state to "That bed is beyond the garden path."
        val bed = state.beds[index]
        if (bed == null) {
            if (state.seedCount(state.selectedSeed) <= 0) return state to "No seeds of that kind. Bramble sells more at the stall."
            val newSeeds = state.seeds.toMutableMap().apply { this[state.selectedSeed] = (this[state.selectedSeed] ?: 0) - 1 }
            val beds = state.beds.toMutableList().apply { this[index] = CropBed(state.selectedSeed) }
            return state.copy(seeds = newSeeds, beds = beds, milestones = state.milestones + ("plant" to true)) to
                "${CropType.fromKey(state.selectedSeed)?.title ?: "A seed"} planted. Tend again to water."
        }
        if (!bed.watered) {
            val beds = state.beds.toMutableList().apply { this[index] = bed.copy(watered = true) }
            return state.copy(beds = beds, milestones = state.milestones + ("water" to true)) to "A little water, a little patience."
        }
        val crop = CropType.fromKey(bed.type) ?: return state to "This crop cannot be recognized."
        if (bed.age < crop.secondsToMature) {
            return state to "${crop.title} · ${kotlin.math.ceil(crop.secondsToMature - bed.age).toInt()} seconds to bloom."
        }
        val inventory = state.inventory.toMutableMap().apply { this[crop.key] = (this[crop.key] ?: 0) + 1 }
        val milestones = state.milestones + mapOf("harvest" to true) + if (crop == CropType.BLOOM) mapOf("bloom" to true) else emptyMap()
        val beds = state.beds.toMutableList().apply { this[index] = null }
        val message = if (crop == CropType.BLOOM) "Ashlan’s Bloom opens. Moonflowers now glow in the glade."
        else "${crop.title} gathered. +2 mooncoins"
        return state.copy(inventory = inventory, beds = beds, coins = state.coins + 2, milestones = milestones) to message
    }

    fun gather(state: GardenState, nodeIndex: Int): Pair<GardenState, String> {
        if (nodeIndex !in 0 until WOODLAND_NODE_COUNT) return state to "There is nothing to gather here."
        if (nodeIndex in state.foraged) return state to "That little patch has already been gathered this evening."
        val item = if (nodeIndex < 8) "mushroom" else "dusk"
        val inventory = state.inventory.toMutableMap().apply { this[item] = (this[item] ?: 0) + 1 }
        return state.copy(inventory = inventory, foraged = state.foraged + nodeIndex) to
            (if (item == "mushroom") "Ghostcap, tucked safely into your bag." else "Duskthorn, tucked safely into your bag.")
    }

    fun rest(state: GardenState): Pair<GardenState, String> {
        val day = state.day + 1
        val weather = if (day % 3 == 0) "rain" else "clear"
        val beds = state.beds.map { bed ->
            if (bed == null) null else bed.copy(
                age = if (bed.watered) CropType.fromKey(bed.type)?.secondsToMature ?: bed.age else bed.age,
                watered = bed.watered || weather == "rain",
            )
        }
        return state.copy(day = day, timeSeconds = 0.0, weather = weather, foraged = emptyList(), beds = beds) to
            "A new evening. The garden kept your place."
    }

    fun claimParcel(state: GardenState): Pair<GardenState, String> {
        if (state.hasMilestone("parcel")) return state to "Nettle’s little parcel has already been opened."
        val inventory = state.inventory.toMutableMap()
        mapOf("moon" to 2, "ember" to 2, "dusk" to 3, "mushroom" to 2).forEach { (key, amount) ->
            inventory[key] = (inventory[key] ?: 0) + amount
        }
        return state.copy(inventory = inventory, milestones = state.milestones + ("parcel" to true)) to
            "Nettle’s parcel: Moonmint, Emberbell, Ghostcaps, and three Duskthorn sprigs."
    }

    fun brew(state: GardenState, recipeId: String): Pair<GardenState, String> {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return state to "That recipe is not in the grimoire."
        if (!canBrew(state, recipe)) return state to "Gather the ingredients before beginning the ritual."
        val inventory = state.inventory.toMutableMap()
        recipe.ingredients.forEach { (item, count) -> inventory[item] = (inventory[item] ?: 0) - count }
        inventory[recipe.id] = (inventory[recipe.id] ?: 0) + 1
        val discovered = if (recipe.id in state.discovered) state.discovered else state.discovered + recipe.id
        val milestones = state.milestones.toMutableMap().apply {
            this["brew"] = true
            if (recipe.category == "poison") this["poison"] = true
            if (recipes.filter { it.category == "poison" }.all { it.id in discovered }) this["apothecary"] = true
        }
        return state.copy(inventory = inventory, discovered = discovered, milestones = milestones) to "${recipe.title} bottled. Find it in your satchel."
    }

    fun useBottle(state: GardenState, bottleId: String): Pair<GardenState, String> {
        if (state.count(bottleId) <= 0) return state to "There is no bottle of that kind in your satchel."
        val inventory = state.inventory.toMutableMap().apply { this[bottleId] = (this[bottleId] ?: 0) - 1 }
        var next = state.copy(inventory = inventory)
        val message = when (bottleId) {
            "dew" -> {
                next = next.copy(
                    beds = next.beds.map { it?.copy(watered = true) },
                    milestones = next.milestones + ("water" to true),
                )
                "A silver cloud waters the whole garden."
            }
            "glow" -> {
                next = next.copy(milestones = next.milestones + ("halo" to true))
                "A lantern halo will follow you."
            }
            "star" -> {
                next = next.copy(offerings = next.offerings + Offering("star", 480.0, 270.0))
                "A lasting star joins the glade."
            }
            "blight" -> {
                val beds = next.beds.map { bed ->
                    if (bed != null && bed.watered) bed.copy(age = (bed.age + 18.0).coerceAtMost(CropType.fromKey(bed.type)?.secondsToMature ?: bed.age)) else bed
                }
                next = next.copy(beds = beds, milestones = next.milestones + ("blight" to true))
                "The make-believe blight melts into silver sparks. Watered crops gain 18 seconds."
            }
            "veil" -> {
                next = next.copy(milestones = next.milestones + ("veil" to true))
                "Violet mothlight drifts around your familiar. A lasting little enchantment."
            }
            "unhex" -> {
                next = next.copy(coins = next.coins + 30, milestones = next.milestones + ("unhex" to true))
                "An old woodland hex unravels into 30 mooncoins."
            }
            else -> return state to "That item is not a usable bottle."
        }
        return next to message
    }

    fun buySeeds(state: GardenState, type: String): Pair<GardenState, String> {
        val price = when (type) { "moon" -> 4; "ember" -> 8; "bloom" -> 12; else -> return state to "Those seeds are not sold here." }
        if (state.coins < price) return state to "There are not quite enough mooncoins."
        val seeds = state.seeds.toMutableMap().apply { this[type] = (this[type] ?: 0) + 3 }
        return state.copy(coins = state.coins - price, seeds = seeds) to "Three ${CropType.fromKey(type)?.title} seeds, wrapped in paper."
    }

    fun upgradeSoil(state: GardenState): Pair<GardenState, String> {
        if (state.upgrades >= 3) return state to "The enchanted soil is already fully tended."
        val price = 35 + state.upgrades * 30
        if (state.coins < price) return state to "You need $price mooncoins for this garden upgrade."
        return state.copy(coins = state.coins - price, upgrades = state.upgrades + 1) to
            "Enchanted soil settles into the beds. Crops grow faster and a lantern joins the path."
    }

    fun sellBottle(state: GardenState, recipeId: String): Pair<GardenState, String> {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return state to "That bottle cannot be sold here."
        if (state.count(recipeId) <= 0) return state to "There is no bottle to sell."
        val inventory = state.inventory.toMutableMap().apply { this[recipeId] = (this[recipeId] ?: 0) - 1 }
        return state.copy(inventory = inventory, coins = state.coins + recipe.salePrice) to "${recipe.title} sold for ${recipe.salePrice} mooncoins."
    }

    fun giftMoonmint(state: GardenState): Pair<GardenState, String> {
        if (state.count("moon") <= 0) return state to "Nettle’s request is for one Moonmint leaf."
        val inventory = state.inventory.toMutableMap().apply { this["moon"] = (this["moon"] ?: 0) - 1 }
        val first = !state.hasMilestone("gift")
        val seeds = state.seeds.toMutableMap()
        if (first) seeds["ember"] = (seeds["ember"] ?: 0) + 3
        val milestones = if (first) state.milestones + ("gift" to true) else state.milestones
        return state.copy(
            inventory = inventory,
            seeds = seeds,
            coins = state.coins + if (first) 15 else 0,
            petCount = state.petCount + 2,
            milestones = milestones,
        ) to if (first) "Nettle: For me? Oh. I shall treasure it. Then eat it. (+15 coins, 3 Emberbell seeds)"
        else "Nettle accepts your gift with extravagant dignity."
    }

    fun scratchNettle(state: GardenState): GardenState = state.copy(petCount = state.petCount + 1)
    fun talkToNettle(state: GardenState): GardenState = state.copy(talkCount = state.talkCount + 1)

    fun offer(state: GardenState, type: String): Pair<GardenState, String> {
        if (type !in setOf("light", "flower", "star")) return state to "Choose a lantern, flower, or paper star."
        val offerings = state.offerings + Offering(type, 380.0 + state.offerings.size * 23.0, 315.0 + (state.offerings.size % 4) * 12.0)
        return state.copy(offerings = offerings) to "A small offering, kept beneath the moon."
    }

    fun writeJournal(state: GardenState, text: String, date: String): Pair<GardenState, String> {
        if (text.isBlank()) return state to "There is no hurry to fill these pages."
        return state.copy(journal = state.journal + JournalEntry(text, date)) to "Your page is kept."
    }

    fun objective(state: GardenState): Pair<String, String> = when {
        !state.hasMilestone("plant") -> "A small beginning" to "Plant Moonmint in one of the nine garden beds."
        !state.hasMilestone("water") -> "A drink of moonlight" to "Tend the planted bed again to water it."
        !state.hasMilestone("harvest") -> "Something is growing" to "A watered Moonmint blooms in 22 seconds. Explore or visit Nettle."
        !state.hasMilestone("brew") -> "A little alchemy" to "Prepare Moonmint + Ghostcap into a Dewdrop tonic."
        !state.hasMilestone("gift") -> "Nettle’s small request" to "Bring Nettle a Moonmint from your satchel."
        !state.hasMilestone("poison") -> "The violet cabinet" to "Open Nettle’s parcel, then brew your first fictional poison."
        !state.hasMilestone("bloom") -> "A flower for the glade" to "Plant, water, and harvest Ashlan’s Bloom."
        else -> "Your own little ritual" to "Grow, brew, decorate, and return whenever you wish."
    }
}
