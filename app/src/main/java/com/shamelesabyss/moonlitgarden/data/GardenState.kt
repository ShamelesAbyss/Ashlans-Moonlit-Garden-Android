package com.shamelesabyss.moonlitgarden.data

import org.json.JSONArray
import org.json.JSONObject

/** The original three garden crops, with growth time measured in active garden seconds. */
enum class CropType(val key: String, val title: String, val secondsToMature: Double, val color: Long) {
    MOON("moon", "Moonmint", 22.0, 0xFFA4DFC5),
    EMBER("ember", "Emberbell", 32.0, 0xFFF7B485),
    BLOOM("bloom", "Ashlan’s Bloom", 42.0, 0xFFE6C3EE);

    companion object {
        fun fromKey(key: String): CropType? = entries.firstOrNull { it.key == key }
    }
}

data class CropBed(val type: String, val age: Double = 0.0, val watered: Boolean = false)
data class JournalEntry(val text: String, val date: String)
data class Offering(val type: String, val x: Double, val y: Double)

data class GardenSettings(
    val musicVolume: Float = 0.25f,
    val ambienceVolume: Float = 0.35f,
    val effectsVolume: Float = 0.45f,
    val reducedMotion: Boolean = false,
    val accentColor: String = "#dfb6e5",
    val favoriteFlower: String = "moonflowers",
    val jukeboxTrack: String = "moonlit-witchs-garden",
    val shuffle: Boolean = false,
    val repeat: String = "all",
)

data class GardenState(
    val day: Int = 1,
    val timeSeconds: Double = 0.0,
    val coins: Int = 20,
    val x: Double = 440.0,
    val y: Double = 360.0,
    val area: String = "garden",
    val selectedSeed: String = "moon",
    val inventory: Map<String, Int> = freshInventory(),
    val seeds: Map<String, Int> = mapOf("moon" to 5, "ember" to 0, "bloom" to 1),
    val beds: List<CropBed?> = List(9) { null },
    val journal: List<JournalEntry> = emptyList(),
    val offerings: List<Offering> = emptyList(),
    val milestones: Map<String, Boolean> = emptyMap(),
    val discovered: List<String> = emptyList(),
    val upgrades: Int = 0,
    val petCount: Int = 0,
    val weather: String = "clear",
    val settings: GardenSettings = GardenSettings(),
    val talkCount: Int = 0,
    val foraged: List<Int> = emptyList(),
    val savedAt: Long = 0L,
) {
    fun hasMilestone(key: String): Boolean = milestones[key] == true
    fun count(item: String): Int = inventory[item] ?: 0
    fun seedCount(type: String): Int = seeds[type] ?: 0

    fun toLegacyJson(): String {
        val root = JSONObject()
            .put("version", 1)
            .put("day", day)
            .put("time", timeSeconds)
            .put("coins", coins)
            .put("x", x)
            .put("y", y)
            .put("area", area)
            .put("seed", selectedSeed)
            .put("inv", integerMap(inventory))
            .put("seeds", integerMap(seeds))
            .put("beds", JSONArray().also { array ->
                beds.forEach { bed ->
                    if (bed == null) array.put(JSONObject.NULL)
                    else array.put(JSONObject().put("type", bed.type).put("age", bed.age).put("water", bed.watered))
                }
            })
            .put("journal", JSONArray().also { array ->
                journal.forEach { array.put(JSONObject().put("text", it.text).put("date", it.date)) }
            })
            .put("lights", JSONArray().also { array ->
                offerings.forEach { array.put(JSONObject().put("type", it.type).put("x", it.x).put("y", it.y)) }
            })
            .put("milestones", JSONObject().also { obj -> milestones.forEach { (key, value) -> obj.put(key, value) } })
            .put("discovered", JSONArray(discovered))
            .put("upgrades", upgrades)
            .put("pet", petCount)
            .put("weather", weather)
            .put("settings", JSONObject()
                .put("music", settings.musicVolume.toDouble())
                .put("ambient", settings.ambienceVolume.toDouble())
                .put("effects", settings.effectsVolume.toDouble())
                .put("reduced", settings.reducedMotion)
                .put("color", settings.accentColor)
                .put("flower", settings.favoriteFlower)
                .put("jukeboxTrack", settings.jukeboxTrack)
                .put("jukeboxShuffle", settings.shuffle)
                .put("jukeboxRepeat", settings.repeat))
            .put("talk", talkCount)
            .put("foraged", JSONArray(foraged))
            .put("savedAt", savedAt)
        return root.toString(2)
    }

    companion object {
        const val LEGACY_VERSION = 1
        val INVENTORY_KEYS = listOf("moon", "ember", "bloom", "mushroom", "dew", "glow", "star", "dusk", "blight", "veil", "unhex")
        val SEED_KEYS = listOf("moon", "ember", "bloom")

        fun fresh(): GardenState = GardenState()

        fun fromLegacyJson(source: String): GardenState {
            val root = JSONObject(source)
            require(root.requiredInt("version") == LEGACY_VERSION) { "Only version-1 garden backups are supported." }
            val bedArray = root.optJSONArray("beds") ?: error("The garden beds are missing.")
            require(bedArray.length() == 9) { "A garden backup must contain all nine beds." }
            val invObject = root.optJSONObject("inv") ?: error("The inventory is missing.")
            val seedObject = root.optJSONObject("seeds") ?: error("The seed pouch is missing.")
            val settingsObject = root.optJSONObject("settings") ?: error("Garden settings are missing.")
            val journalArray = root.optJSONArray("journal") ?: error("The memory journal is missing.")
            val lightsArray = root.optJSONArray("lights") ?: error("The memorial offerings are missing.")
            val milestoneObject = root.optJSONObject("milestones") ?: error("Garden milestones are missing.")
            val discoveredArray = root.optJSONArray("discovered") ?: error("The recipe discoveries are missing.")
            val forageArray = root.optJSONArray("foraged") ?: error("Woodland forage progress is missing.")

            val beds = (0 until bedArray.length()).map { index ->
                val value = bedArray.get(index)
                if (value == JSONObject.NULL) null else {
                    val bed = value as? JSONObject ?: error("Bed $index is malformed.")
                    val type = bed.requiredString("type")
                    require(CropType.fromKey(type) != null) { "Bed $index contains an unknown crop." }
                    val age = bed.requiredNumber("age")
                    require(age >= 0.0) { "Crop age cannot be negative." }
                    val watered = bed.get("water") as? Boolean ?: error("Bed $index has invalid water state.")
                    CropBed(type, age, watered)
                }
            }

            val journal = (0 until journalArray.length()).map { index ->
                val entry = journalArray.getJSONObject(index)
                JournalEntry(entry.requiredString("text"), entry.optString("date", ""))
            }
            val offerings = (0 until lightsArray.length()).map { index ->
                val item = lightsArray.getJSONObject(index)
                val type = item.requiredString("type")
                require(type in setOf("light", "flower", "star")) { "An offering has an unknown type." }
                Offering(type, item.requiredNumber("x"), item.requiredNumber("y"))
            }
            val milestones = buildMap {
                val keys = milestoneObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, milestoneObject.get(key) as? Boolean ?: error("Milestone '$key' is invalid."))
                }
            }
            val discovered = stringArray(discoveredArray)
            val foraged = (0 until forageArray.length()).map { index ->
                val value = forageArray.get(index)
                val number = value as? Number ?: error("Woodland node index is invalid.")
                val indexValue = number.toInt()
                require(indexValue in 0..11 && number.toDouble() == indexValue.toDouble()) { "Woodland node index is out of range." }
                indexValue
            }

            val area = root.optString("area", "garden")
            require(area in setOf("garden", "wood", "glade")) { "The saved location is unknown." }
            val weather = root.optString("weather", "clear")
            require(weather in setOf("clear", "rain")) { "The saved weather is unknown." }
            val selectedSeed = root.optString("seed", "moon")
            require(CropType.fromKey(selectedSeed) != null) { "The selected seed is unknown." }
            val repeat = settingsObject.optString("jukeboxRepeat", "all").let { if (it == "one") "one" else "all" }
            val settings = GardenSettings(
                musicVolume = settingsObject.optionalVolume("music", 0.25f),
                ambienceVolume = settingsObject.optionalVolume("ambient", 0.35f),
                effectsVolume = settingsObject.optionalVolume("effects", 0.45f),
                reducedMotion = settingsObject.optionalBoolean("reduced", false),
                accentColor = settingsObject.optString("color", "#dfb6e5").also { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) },
                favoriteFlower = settingsObject.optString("flower", "moonflowers").take(100),
                jukeboxTrack = settingsObject.optString("jukeboxTrack", "moonlit-witchs-garden"),
                shuffle = settingsObject.optionalBoolean("jukeboxShuffle", false),
                repeat = repeat,
            )

            return GardenState(
                day = root.requiredNonnegativeInt("day"),
                timeSeconds = root.requiredNonnegativeNumber("time"),
                coins = root.requiredNonnegativeInt("coins"),
                x = root.requiredNonnegativeNumber("x"),
                y = root.requiredNonnegativeNumber("y"),
                area = area,
                selectedSeed = selectedSeed,
                inventory = nonnegativeMap(invObject, INVENTORY_KEYS, freshInventory()),
                seeds = nonnegativeMap(seedObject, SEED_KEYS, mapOf("moon" to 5, "ember" to 0, "bloom" to 1)),
                beds = beds,
                journal = journal,
                offerings = offerings,
                milestones = milestones,
                discovered = discovered,
                upgrades = root.requiredNonnegativeInt("upgrades").also { require(it <= 3) },
                petCount = root.requiredNonnegativeInt("pet"),
                weather = weather,
                settings = settings,
                talkCount = root.requiredNonnegativeInt("talk"),
                foraged = foraged,
                savedAt = root.optLong("savedAt", 0L).coerceAtLeast(0L),
            )
        }

        fun mergeJournal(imported: List<JournalEntry>, current: List<JournalEntry>): List<JournalEntry> {
            val seen = imported.mapTo(mutableSetOf()) { "${it.date}|${it.text}" }
            return imported + current.filter { seen.add("${it.date}|${it.text}") }
        }

        private fun freshInventory() = INVENTORY_KEYS.associateWith { if (it == "mushroom") 2 else 0 }

        private fun integerMap(values: Map<String, Int>) = JSONObject().also { obj ->
            values.forEach { (key, value) -> obj.put(key, value) }
        }

        private fun nonnegativeMap(source: JSONObject, keys: List<String>, defaults: Map<String, Int>): Map<String, Int> =
            keys.associateWith { key ->
                if (!source.has(key)) defaults[key] ?: 0 else {
                    val number = source.get(key) as? Number ?: error("Count '$key' is not numeric.")
                    val value = number.toDouble()
                    require(value.isFinite() && value >= 0.0 && value <= Int.MAX_VALUE && value % 1.0 == 0.0) { "Count '$key' is invalid." }
                    value.toInt()
                }
            }

        private fun stringArray(array: JSONArray): List<String> = (0 until array.length()).map { index ->
            array.get(index) as? String ?: error("A saved list item is not text.")
        }
    }
}

private fun JSONObject.requiredString(key: String): String = get(key) as? String ?: error("'$key' must be text.")
private fun JSONObject.requiredNumber(key: String): Double {
    val number = get(key) as? Number ?: error("'$key' must be numeric.")
    return number.toDouble().also { require(it.isFinite()) { "'$key' must be finite." } }
}
private fun JSONObject.requiredNonnegativeNumber(key: String): Double = requiredNumber(key).also {
    require(it >= 0.0) { "'$key' cannot be negative." }
}
private fun JSONObject.requiredInt(key: String): Int {
    val number = requiredNumber(key)
    require(number % 1.0 == 0.0 && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
    return number.toInt()
}
private fun JSONObject.requiredNonnegativeInt(key: String): Int = requiredInt(key).also { require(it >= 0) { "'$key' cannot be negative." } }
private fun JSONObject.optionalVolume(key: String, fallback: Float): Float {
    if (!has(key)) return fallback
    val number = get(key) as? Number ?: error("Volume '$key' is invalid.")
    return number.toFloat().also { require(it.isFinite() && it in 0f..1f) { "Volume '$key' must be between zero and one." } }
}
private fun JSONObject.optionalBoolean(key: String, fallback: Boolean): Boolean {
    if (!has(key)) return fallback
    return get(key) as? Boolean ?: error("Setting '$key' is not a boolean.")
}
