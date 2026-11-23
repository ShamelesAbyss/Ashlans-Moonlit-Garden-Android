package com.shamelesabyss.moonlitgarden.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamelesabyss.moonlitgarden.R
import com.shamelesabyss.moonlitgarden.audio.MusicController
import com.shamelesabyss.moonlitgarden.audio.MusicUiState
import com.shamelesabyss.moonlitgarden.audio.SoundscapeController
import com.shamelesabyss.moonlitgarden.data.CropType
import com.shamelesabyss.moonlitgarden.data.GardenState
import com.shamelesabyss.moonlitgarden.domain.GameRules
import com.shamelesabyss.moonlitgarden.domain.GardenViewModel
import com.shamelesabyss.moonlitgarden.domain.Recipe
import kotlinx.coroutines.delay
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

private val pages = listOf("garden", "wood", "cabinet", "glade", "more")

@Composable
fun MoonlitApp(viewModel: GardenViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val music = remember { MusicController(context) }
    val soundscape = remember { SoundscapeController(context) }
    val garden = viewModel.garden
    var page by rememberSaveable { mutableStateOf("garden") }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(garden.settings.musicVolume) { music.setVolume(garden.settings.musicVolume) }
    LaunchedEffect(garden.settings.ambienceVolume, garden.settings.effectsVolume, garden.weather) {
        soundscape.setAmbience(garden.settings.ambienceVolume, garden.weather)
        soundscape.setEffects(garden.settings.effectsVolume)
    }
    LaunchedEffect(garden.settings.shuffle) { music.setShuffle(garden.settings.shuffle) }
    LaunchedEffect(garden.settings.repeat) {
        val wantOne = garden.settings.repeat == "one"
        if (music.uiState.repeatOne != wantOne) music.toggleRepeat()
    }
    DisposableEffect(lifecycleOwner, music) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.onAppStarted()
                    soundscape.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppStopped()
                    music.pauseForBackground()
                    soundscape.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppStopped()
            music.pauseForBackground()
            music.release()
            soundscape.release()
        }
    }
    BackHandler(enabled = page != "garden") { page = "garden" }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(viewModel.exportBackup().toByteArray(Charsets.UTF_8)) }
            }.onSuccess { viewModel.reportStatus("A JSON garden backup was exported to the location you chose.") }
                .onFailure { viewModel.reportStatus("The backup could not be written. Choose another location and try again.") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use(::readBoundedText) ?: error("The selected file could not be opened.") }
                .onSuccess { pendingImport = it }
                .onFailure { viewModel.reportStatus("The selected backup could not be read. The current garden is unchanged.") }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 780.dp
        Scaffold(
            containerColor = GardenPalette.Night,
            bottomBar = {
                NavigationBar(containerColor = GardenPalette.DeepViolet) {
                    listOf(
                        Triple("garden", "☾", "Garden"),
                        Triple("wood", "♧", "Woodland"),
                        Triple("cabinet", "✦", "Cabinet"),
                        Triple("glade", "✿", "Glade"),
                        Triple("more", "⋯", "More"),
                    ).forEach { (route, symbol, label) ->
                        val selected = if (route == "more") page !in pages.dropLast(1) else page == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                page = route
                                soundscape.playEffect("softspark")
                                when (route) {
                                    "garden" -> viewModel.selectArea("garden")
                                    "wood" -> viewModel.selectArea("wood")
                                    "glade" -> viewModel.selectArea("glade")
                                }
                            },
                            icon = { Text(symbol, fontSize = 20.sp) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                TopBar(garden, viewModel.loaded)
                if (viewModel.notice.isNotBlank()) {
                    Text(
                        viewModel.notice,
                        color = GardenPalette.MoonGold,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
                if (!viewModel.loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("A moment while the garden wakes…", color = GardenPalette.SoftText) }
                } else if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1.05f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ObjectiveCard(garden)
                            WorldCard(garden, viewModel, soundscape, music.uiState.isPlaying) { page = "jukebox" }
                            if (!garden.hasMilestone("welcome")) WelcomeCard(onBegin = viewModel::dismissWelcome)
                        }
                        Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PageContent(page, garden, viewModel, music, onNavigate = { page = it }, onExport = { exportLauncher.launch("ashlans-garden-${dateStamp()}.json") }, onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, onRestore = { showRestoreConfirm = true })
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ObjectiveCard(garden)
                        WorldCard(garden, viewModel, soundscape, music.uiState.isPlaying) { page = "jukebox" }
                        if (!garden.hasMilestone("welcome")) WelcomeCard(onBegin = viewModel::dismissWelcome)
                        PageContent(page, garden, viewModel, music, onNavigate = { page = it }, onExport = { exportLauncher.launch("ashlans-garden-${dateStamp()}.json") }, onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, onRestore = { showRestoreConfirm = true })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = GardenPalette.DeepViolet,
            title = { Text("Restore this garden backup?", color = GardenPalette.Rose) },
            text = { Text("Progress and settings will be replaced by the selected version-1 save. Every existing journal page on this device will be merged into it, and a private pre-import recovery copy will be saved locally first.", color = GardenPalette.SoftText) },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingImport
                    pendingImport = null
                    if (data != null) viewModel.importBackup(data)
                }) { Text("Restore and merge") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = GardenPalette.DeepViolet,
            title = { Text("Restore the previous save?", color = GardenPalette.Rose) },
            text = { Text("The saved pre-import garden will replace current progress. All current journal entries will be merged and kept.", color = GardenPalette.SoftText) },
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false; viewModel.restorePreImportBackup() }) { Text("Restore previous save") } },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopBar(garden: GardenState, loaded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Image(painterResource(R.drawable.official_garden_icon), contentDescription = "Official Ashlan’s Moonlit Garden icon", modifier = Modifier.size(46.dp).clip(CircleShape))
        Column(Modifier.weight(1f)) {
            Text("Ashlan’s Moonlit Garden", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("A warm cottage · a watchful familiar · a song kept close", color = GardenPalette.SoftText, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (loaded) "Evening ${garden.day}" else "Waking…", color = GardenPalette.MoonGold, fontWeight = FontWeight.SemiBold)
            Text("✦ ${garden.coins} mooncoins", color = GardenPalette.SoftText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ObjectiveCard(garden: GardenState) {
    val (title, detail) = GameRules.objective(garden)
    GardenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✧", color = GardenPalette.MoonGold, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("Garden notes", color = GardenPalette.SoftText, fontSize = 11.sp)
                Text(title, color = GardenPalette.Rose, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = GardenPalette.SoftText, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (garden.weather == "rain") "Soft rain" else "Clear skies", color = GardenPalette.MoonGold, fontSize = 12.sp)
                Text(timeLabel(garden.timeSeconds), color = GardenPalette.SoftText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WelcomeCard(onBegin: () -> Unit) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("A new beginning, made with love", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text("A cottage with warm windows. Nine little beds beneath the stars. Nettle has been waiting by the gate.", color = GardenPalette.SoftText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        GardenAction("Step into the garden", onClick = onBegin)
    }
}

@Composable
private fun WorldCard(
    garden: GardenState,
    viewModel: GardenViewModel,
    soundscape: SoundscapeController,
    musicPlaying: Boolean,
    onJukeboxTap: () -> Unit,
) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text(
            when (garden.area) { "wood" -> "The Hushwood"; "glade" -> "A quiet place beneath the branches"; else -> "The cottage garden" },
            color = GardenPalette.Rose,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        MoonlitWorldCanvas(
            garden = garden,
            modifier = Modifier.fillMaxWidth(),
            reduceMotion = garden.settings.reducedMotion,
            musicPlaying = musicPlaying,
            onBedTap = { soundscape.playEffect("plant"); viewModel.tend(it) },
            onForageTap = { soundscape.playEffect("harvest"); viewModel.gather(it) },
            onJukeboxTap = { soundscape.playEffect("softspark"); onJukeboxTap() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (garden.area) {
                "wood" -> "Tap a pale Ghostcap or violet Duskthorn to gather it. Each patch returns after you rest."
                "glade" -> "Flowers, lanterns, paper stars, and your own words belong here."
                else -> "Tap any of the nine beds to plant, water, or gather. The little jukebox waits by the path."
            },
            color = GardenPalette.SoftText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PageContent(
    page: String,
    garden: GardenState,
    viewModel: GardenViewModel,
    music: MusicController,
    onNavigate: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
) {
    when (page) {
        "garden" -> GardenControls(garden, viewModel, onNavigate)
        "wood" -> WoodlandControls(garden, viewModel)
        "cabinet" -> CabinetControls(garden, viewModel)
        "glade" -> GladeControls(garden, viewModel, onNavigate)
        "journal" -> JournalControls(garden, viewModel)
        "bag" -> BagControls(garden, viewModel)
        "shop" -> ShopControls(garden, viewModel)
        "jukebox" -> JukeboxControls(garden, viewModel, music)
        "settings" -> SettingsControls(garden, viewModel, onExport, onImport, onRestore)
        else -> MoreControls(onNavigate)
    }
}

@Composable
private fun GardenControls(garden: GardenState, viewModel: GardenViewModel, onNavigate: (String) -> Unit) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Tend the nine beds", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Plant → water → wait for the small bloom → gather. Nothing withers.", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CropType.entries.forEach { crop ->
                FilterChip(
                    selected = garden.selectedSeed == crop.key,
                    onClick = { viewModel.selectSeed(crop.key) },
                    label = { Text("${crop.title} · ${garden.seedCount(crop.key)}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..2) {
                    val index = row * 3 + col
                    GardenAction(bedLabel(garden, index), modifier = Modifier.weight(1f), onClick = { viewModel.tend(index) })
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GardenAction("Rest · next evening", onClick = viewModel::rest)
            GardenAction("Open satchel", onClick = { onNavigate("bag") })
        }
    }
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Nettle, keeper of small mysteries", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("“${viewModel.notice.takeIf { it.startsWith("Nettle:") || it.startsWith("I ") || it.startsWith("My ") || it.startsWith("The ") } ?: if (!garden.hasMilestone("harvest")) "A seed, a drink, a moment. We can manage that." else "If you need me, I will be somewhere inconveniently close."}”", color = GardenPalette.MoonGold, fontSize = 14.sp)
        Text("${garden.petCount} head scratches remembered · ${if (garden.hasMilestone("gift")) "A Moonmint friend" else "A small request: bring one Moonmint"}", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GardenAction("Talk", onClick = viewModel::talkToNettle)
            GardenAction("Scratch behind the ears", onClick = viewModel::scratchNettle)
            GardenAction("Give Moonmint", enabled = garden.count("moon") > 0, onClick = viewModel::giftMoonmint)
            GardenAction("Chase a spark", onClick = viewModel::playWithNettle)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GardenAction("The violet cabinet", modifier = Modifier.weight(1f), onClick = { onNavigate("cabinet") })
        GardenAction("Enchanted jukebox", modifier = Modifier.weight(1f), onClick = { onNavigate("jukebox") })
    }
}

@Composable
private fun WoodlandControls(garden: GardenState, viewModel: GardenViewModel) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Under the old trees", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Eight Ghostcap patches and four Duskthorn sprigs rest here. Gather each once per evening.", color = GardenPalette.SoftText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val start = row * 4
                for (offset in 0..3) {
                    val node = start + offset
                    val item = if (node < 8) "Ghostcap" else "Duskthorn"
                    val ready = node !in garden.foraged
                    GardenAction(if (ready) "Gather $item" else "$item · gathered", Modifier.weight(1f), enabled = ready) { viewModel.gather(node) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        GardenAction("Rest to begin a new evening", onClick = viewModel::rest)
    }
    GardenCard(Modifier.fillMaxWidth()) {
        Text("A quiet trail", color = GardenPalette.Rose, fontWeight = FontWeight.SemiBold)
        Text("Nettle says the mushrooms and she have an understanding. It mostly involves not sitting on them.", color = GardenPalette.SoftText, fontSize = 13.sp)
        Text("In your bag: ${garden.count("mushroom")} Ghostcaps · ${garden.count("dusk")} Duskthorn", color = GardenPalette.MoonGold, fontSize = 13.sp)
    }
}

@Composable
private fun CabinetControls(garden: GardenState, viewModel: GardenViewModel) {
    var category by rememberSaveable { mutableStateOf("potion") }
    val recipes = GameRules.recipes.filter { it.category == category }
    val selected = GameRules.recipes.first { it.id == viewModel.brewRecipeId }
    GardenCard(Modifier.fillMaxWidth()) {
        Text("The Violet Cabinet", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Prepare → stir → bottle. Ingredients are only consumed when the last step is complete.", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == "potion", onClick = { category = "potion"; viewModel.chooseRecipe("dew") }, label = { Text("✧ Potions") })
            FilterChip(selected = category == "poison", onClick = { category = "poison"; viewModel.chooseRecipe("blight") }, label = { Text("☾ Poisons") })
        }
        recipes.forEach { recipe -> RecipePickRow(recipe, selected.id == recipe.id, garden, onClick = { viewModel.chooseRecipe(recipe.id) }) }
        Spacer(Modifier.height(8.dp))
        Text(selected.title, color = GardenPalette.MoonGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(selected.effect, color = GardenPalette.SoftText, fontSize = 13.sp)
        Text("Ingredients · ${selected.ingredients.entries.joinToString(" + ") { (key, amount) -> "$amount ${itemName(key)} (${garden.count(key)})" }}", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Prepare", "Stir", "Bottle").forEachIndexed { index, step ->
                Text("${if (index < viewModel.brewStep) "✓" else index + 1} $step", color = if (index <= viewModel.brewStep) GardenPalette.MoonGold else GardenPalette.SoftText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        GardenAction(
            when (viewModel.brewStep) { 0 -> "Prepare ingredients"; 1 -> "Stir the moonwater"; else -> "Bottle the enchantment" },
            enabled = GameRules.canBrew(garden, selected),
            onClick = viewModel::advanceBrewing,
        )
        if (viewModel.brewStatus.isNotBlank()) Text(viewModel.brewStatus, color = GardenPalette.MoonGold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(6.dp))
        Text("${garden.discovered.size} / 6 recipes inked · ${GameRules.recipes.count { it.category == "poison" && it.id in garden.discovered }} / 3 violet bottles${if (garden.hasMilestone("apothecary")) " · ✧ Cabinet complete" else ""}", color = GardenPalette.SoftText, fontSize = 11.sp)
        if (!garden.hasMilestone("parcel")) {
            Spacer(Modifier.height(7.dp))
            GardenAction("Open Nettle’s ingredient parcel", onClick = viewModel::claimParcel)
        }
    }
}

@Composable
private fun RecipePickRow(recipe: Recipe, selected: Boolean, garden: GardenState, onClick: () -> Unit) {
    val tint = if (selected) GardenPalette.Lavender else GardenPalette.Border
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(13.dp)).border(1.dp, tint, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (recipe.category == "poison") "☾" else "✧", color = GardenPalette.MoonGold)
        Column(Modifier.weight(1f)) {
            Text(recipe.title, color = GardenPalette.SoftText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(if (recipe.id in garden.discovered) "In your grimoire · ${recipe.salePrice} coins" else "Undiscovered · ${recipe.salePrice} coins", color = GardenPalette.SoftText, fontSize = 10.sp)
        }
        GardenAction(if (selected) "Selected" else "Choose", onClick = onClick)
    }
}

@Composable
private fun GladeControls(garden: GardenState, viewModel: GardenViewModel, onNavigate: (String) -> Unit) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("For Ashlan, always loved", color = GardenPalette.Rose, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("A moon overhead. A quiet place whenever you want it. Place flowers, lanterns, or paper stars; Ashlan’s Bloom wakes the moonflowers.", color = GardenPalette.SoftText, fontSize = 13.sp)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GardenAction("Place lantern", onClick = { viewModel.placeOffering("light") })
            GardenAction("Lay moonflower", onClick = { viewModel.placeOffering("flower") })
            GardenAction("Hang paper star", onClick = { viewModel.placeOffering("star") })
            GardenAction("Open journal", onClick = { onNavigate("journal") })
        }
        Spacer(Modifier.height(8.dp))
        Text("${garden.offerings.size} little offerings remain in the glade", color = GardenPalette.MoonGold, fontSize = 12.sp)
        if (!garden.hasMilestone("bloom")) Text("Moonflowers glow here after Ashlan’s Bloom is grown and gathered.", color = GardenPalette.SoftText, fontSize = 12.sp)
    }
}

@Composable
private fun JournalControls(garden: GardenState, viewModel: GardenViewModel) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Pages beneath the moon", color = GardenPalette.Rose, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Your words stay on this device. The game never invents personal memories or messages from Ashlan.", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.journalDraft,
            onValueChange = viewModel::updateJournalDraft,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("A memory, a thought, a little detail") },
            placeholder = { Text("Write whatever you wish to keep…") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(6.dp))
        GardenAction("Keep this page", enabled = viewModel.journalDraft.isNotBlank(), onClick = viewModel::keepJournalPage)
        Spacer(Modifier.height(8.dp))
        if (garden.journal.isEmpty()) Text("There is no hurry to fill these pages.", color = GardenPalette.SoftText, fontSize = 13.sp)
        garden.journal.asReversed().forEach { entry ->
            GardenCard(Modifier.fillMaxWidth()) {
                Text(entry.date, color = GardenPalette.MoonGold, fontSize = 11.sp)
                Text(entry.text, color = GardenPalette.SoftText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BagControls(garden: GardenState, viewModel: GardenViewModel) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Your satchel", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("A few good things, gathered slowly.", color = GardenPalette.SoftText, fontSize = 12.sp)
        garden.inventory.forEach { (item, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${itemName(item)} × $amount", color = GardenPalette.SoftText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                if (item in setOf("dew", "glow", "star", "blight", "veil", "unhex")) {
                    GardenAction("Use bottle", enabled = amount > 0, onClick = { viewModel.useBottle(item) })
                }
            }
        }
    }
}

@Composable
private fun ShopControls(garden: GardenState, viewModel: GardenViewModel) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Bramble’s evening stall", color = GardenPalette.Rose, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("“Seeds, small wonders, and absolutely no cursed turnips.” · Purse: ${garden.coins} mooncoins", color = GardenPalette.SoftText, fontSize = 12.sp)
        CropType.entries.forEach { crop ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("3 ${crop.title} seeds · ${garden.seedCount(crop.key)} in pouch", color = GardenPalette.SoftText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                GardenAction("${mapOf("moon" to 4, "ember" to 8, "bloom" to 12)[crop.key]} coins", enabled = garden.coins >= (mapOf("moon" to 4, "ember" to 8, "bloom" to 12)[crop.key] ?: 0)) { viewModel.buySeeds(crop.key) }
            }
        }
        val price = 35 + garden.upgrades * 30
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Enchanted soil · ${garden.upgrades}/3 · faster crops + path lanterns", color = GardenPalette.SoftText, modifier = Modifier.weight(1f), fontSize = 13.sp)
            GardenAction(if (garden.upgrades >= 3) "Complete" else "$price coins", enabled = garden.upgrades < 3 && garden.coins >= price, onClick = viewModel::upgradeSoil)
        }
        GameRules.recipes.forEach { recipe ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${recipe.title} × ${garden.count(recipe.id)}", color = GardenPalette.SoftText, modifier = Modifier.weight(1f), fontSize = 12.sp)
                GardenAction("Sell · ${recipe.salePrice}", enabled = garden.count(recipe.id) > 0) { viewModel.sellBottle(recipe.id) }
            }
        }
    }
}

@Composable
private fun JukeboxControls(garden: GardenState, viewModel: GardenViewModel, music: MusicController) {
    val state = music.uiState
    LaunchedEffect(state.isPlaying, state.tracks) {
        while (state.isPlaying) {
            delay(500)
            music.updatePosition()
        }
    }
    GardenCard(Modifier.fillMaxWidth()) {
        Text("The Enchanted Jukebox", color = GardenPalette.Rose, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("A little carved cabinet for songs kept close.", color = GardenPalette.SoftText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.enchanted_jukebox), contentDescription = "Original pixel-art amethyst jukebox", modifier = Modifier.height(154.dp).width(86.dp))
            Column(Modifier.weight(1f)) {
                Text(state.selectedTrack?.title ?: "Jukebox at rest", color = GardenPalette.MoonGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(state.selectedTrack?.artist ?: "No public track is cleared yet", color = GardenPalette.SoftText, fontSize = 12.sp)
                Text(if (state.isPlaying) "The record turns softly" else "The little cabinet is still", color = GardenPalette.SoftText, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    GardenAction("|◀", enabled = state.tracks.isNotEmpty(), onClick = music::previous)
                    GardenAction(if (state.isPlaying) "Pause" else "Play", onClick = music::togglePlayback)
                    GardenAction("▶|", enabled = state.tracks.isNotEmpty(), onClick = music::next)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GardenAction(if (garden.settings.shuffle) "Shuffle · on" else "Shuffle · off", onClick = { val next = !garden.settings.shuffle; music.setShuffle(next); viewModel.setShuffle(next) })
            GardenAction(if (garden.settings.repeat == "one") "Repeat · one" else "Repeat · all", onClick = { val next = if (garden.settings.repeat == "one") "all" else "one"; music.toggleRepeat(); viewModel.setRepeat(next) })
        }
        state.tracks.forEachIndexed { index, track ->
            GardenAction("${if (index == state.selectedIndex) "♪" else "○"} ${track.title} · ${track.artist}", modifier = Modifier.fillMaxWidth(), onClick = { music.choose(index) })
        }
        if (state.tracks.isNotEmpty()) {
            Text("${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}", color = GardenPalette.SoftText, fontSize = 11.sp)
            Slider(
                value = if (state.durationMs > 0L) state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat()) / state.durationMs else 0f,
                onValueChange = { music.seek((it * state.durationMs).toLong()) },
                enabled = state.durationMs > 0L,
            )
        }
        Text("Soundtrack volume · ${(garden.settings.musicVolume * 100).toInt()}%", color = GardenPalette.SoftText, fontSize = 12.sp)
        Slider(value = garden.settings.musicVolume, onValueChange = { music.setVolume(it); viewModel.setMusicVolume(it) })
        Text(state.notice.ifBlank { "Playback is offline-only. Ambience and interaction sounds have separate controls." }, color = GardenPalette.MoonGold, fontSize = 12.sp)
        if (state.tracks.isEmpty()) Text("The original MP3 and its cover remain in the private reference project while distribution rights are checked. No replacement track is included.", color = GardenPalette.SoftText, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsControls(
    garden: GardenState,
    viewModel: GardenViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Make yourself at home", color = GardenPalette.Rose, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Everything is stored on this device. There are no accounts, ads, telemetry, cloud sync, or online dependencies.", color = GardenPalette.SoftText, fontSize = 12.sp)
        VolumeControl("Ambience", garden.settings.ambienceVolume, viewModel::setAmbienceVolume)
        VolumeControl("Interaction sounds", garden.settings.effectsVolume, viewModel::setEffectsVolume)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reduced motion", color = GardenPalette.SoftText, fontWeight = FontWeight.SemiBold)
                Text("Still fireflies, no drifting rain or bobbing details", color = GardenPalette.SoftText, fontSize = 11.sp)
            }
            Switch(checked = garden.settings.reducedMotion, onCheckedChange = viewModel::setReducedMotion)
        }
        OutlinedTextField(
            value = garden.settings.accentColor,
            onValueChange = viewModel::setAccentColor,
            label = { Text("Glade accent · #RRGGBB") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = garden.settings.favoriteFlower,
            onValueChange = viewModel::setFavoriteFlower,
            label = { Text("Favorite flower or little detail") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GardenAction("Export JSON backup", modifier = Modifier.weight(1f), onClick = onExport)
            GardenAction("Import JSON backup", modifier = Modifier.weight(1f), onClick = onImport)
        }
        GardenAction("Restore pre-import recovery copy", onClick = onRestore)
        Text("Imports validate version-1 saves, replace progress after your confirmation, and merge existing journal pages by date + exact text. A pre-import recovery copy stays local.", color = GardenPalette.SoftText, fontSize = 11.sp)
    }
}

@Composable
private fun VolumeControl(label: String, value: Float, onChange: (Float) -> Unit) {
    Text("$label · ${(value * 100).toInt()}%", color = GardenPalette.SoftText, fontSize = 12.sp)
    Slider(value = value, onValueChange = onChange)
}

@Composable
private fun MoreControls(onNavigate: (String) -> Unit) {
    GardenCard(Modifier.fillMaxWidth()) {
        Text("Your little garden", color = GardenPalette.Rose, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Choose a corner to visit.", color = GardenPalette.SoftText, fontSize = 13.sp)
        listOf(
            "bag" to "The satchel · ingredients and bottles",
            "shop" to "Bramble’s stall · seeds and upgrades",
            "jukebox" to "The Enchanted Jukebox · offline music",
            "journal" to "Pages beneath the moon · personal journal",
            "settings" to "Settings · sound, motion and backups",
        ).forEach { (route, label) -> GardenAction(label, modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(route) }) }
        Spacer(Modifier.height(8.dp))
        Text("Made for Ashlan, always loved. The memorial glade is here whenever you need it.", color = GardenPalette.MoonGold, fontSize = 12.sp)
    }
}

private fun bedLabel(garden: GardenState, index: Int): String {
    val bed = garden.beds.getOrNull(index) ?: return "Bed ${index + 1}\nPlant ${CropType.fromKey(garden.selectedSeed)?.title ?: "seed"}"
    val crop = CropType.fromKey(bed.type)
    return when {
        !bed.watered -> "Bed ${index + 1}\nWater ${crop?.title ?: "crop"}"
        (crop?.let { bed.age >= it.secondsToMature } == true) -> "Bed ${index + 1}\nHarvest ${crop?.title}"
        else -> "Bed ${index + 1}\n${crop?.title} · ${floor((crop?.secondsToMature ?: 0.0) - bed.age).toInt().coerceAtLeast(0)}s"
    }
}

private fun itemName(key: String): String = when (key) {
    "moon" -> "Moonmint"; "ember" -> "Emberbell"; "bloom" -> "Ashlan’s Bloom"
    "mushroom" -> "Ghostcap"; "dusk" -> "Duskthorn"
    else -> GameRules.recipes.firstOrNull { it.id == key }?.title ?: key
}

private fun timeLabel(seconds: Double): String {
    val hour = (seconds / GameRules.SECONDS_PER_EVENING * 24.0 + 16.0).toInt() % 24
    val minute = ((seconds % 7.5) / 7.5 * 60.0).toInt().coerceIn(0, 59)
    return "%02d:%02d".format(hour, minute)
}

private fun dateStamp(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun formatMs(value: Long): String = "%d:%02d".format(value / 60_000L, value / 1_000L % 60L)

private fun readBoundedText(input: InputStream): String {
    val bytes = input.readNBytes(4 * 1024 * 1024 + 1)
    require(bytes.size <= 4 * 1024 * 1024) { "Backup exceeds the 4 MB safety limit." }
    return bytes.toString(Charsets.UTF_8)
}
