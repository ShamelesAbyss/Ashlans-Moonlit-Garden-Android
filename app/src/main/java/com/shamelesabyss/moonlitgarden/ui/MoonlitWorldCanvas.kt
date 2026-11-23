package com.shamelesabyss.moonlitgarden.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shamelesabyss.moonlitgarden.data.CropType
import com.shamelesabyss.moonlitgarden.data.GardenState
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun MoonlitWorldCanvas(
    garden: GardenState,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = garden.settings.reducedMotion,
    musicPlaying: Boolean = false,
    onBedTap: (Int) -> Unit,
    onForageTap: (Int) -> Unit,
    onJukeboxTap: () -> Unit,
) {
    val glow = if (reduceMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "moonlit-sky")
        val animatedGlow by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(3_200), RepeatMode.Reverse),
            label = "firefly-glow",
        )
        animatedGlow
    }
    Canvas(
        modifier = modifier
            .aspectRatio(1.62f)
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Pixel-art ${garden.area} beneath a crescent moon. Tap a garden bed, woodland gathering spot, or garden jukebox." }
            .pointerInput(garden.area, garden.beds, garden.foraged, onJukeboxTap) {
                detectTapGestures { point ->
                    val x = point.x / size.width * 960f
                    val y = point.y / size.height * 600f
                    when (garden.area) {
                        "garden" -> {
                            if (x in 575f..670f && y in 315f..430f) onJukeboxTap()
                            else {
                                val col = ((x - 330f) / 55f).toInt()
                                val row = ((y - 330f) / 48f).toInt()
                                if (col in 0..2 && row in 0..2 && x in 306f..515f && y in 305f..485f) onBedTap(row * 3 + col)
                            }
                        }
                        "wood" -> {
                            var closest = -1
                            var distance = Float.MAX_VALUE
                            for (i in 0 until 12) {
                                val nodeX = if (i < 8) 230f + (i % 4) * 150f else 195f + (i - 8) * 175f
                                val nodeY = if (i < 8) 250f + (i / 4) * 155f else 350f
                                val d = hypot(nodeX - x, nodeY - y)
                                if (d < distance) { distance = d; closest = i }
                            }
                            if (distance < 45f && closest >= 0) onForageTap(closest)
                        }
                    }
                }
            },
    ) {
        val sx = size.width / 960f
        val sy = size.height / 600f
        withTransform({ scale(sx, sy, Offset.Zero) }) {
            drawMoonlitScene(garden, glow, musicPlaying)
        }
    }
}

private fun DrawScope.drawMoonlitScene(garden: GardenState, glow: Float, musicPlaying: Boolean) {
    val sky = when (garden.area) {
        "wood" -> Color(0xFF18243A)
        "glade" -> Color(0xFF211B3B)
        else -> Color(0xFF18243B)
    }
    drawRect(brush = Brush.verticalGradient(listOf(sky, Color(0xFF283D4A), Color(0xFF304B43))))
    fun pixel(x: Float, y: Float, w: Float, h: Float, color: Color) {
        drawRect(color, Offset(x, y), Size(w, h))
    }
    fun tree(x: Float, y: Float, scale: Float = 1f) {
        pixel(x - 7 * scale, y + 6 * scale, 14 * scale, 58 * scale, Color(0xFF473649))
        pixel(x - 3 * scale, y + 15 * scale, 6 * scale, 43 * scale, Color(0xFF705165))
        listOf(Triple(-28f, -40f, 68f), Triple(10f, -24f, 70f), Triple(-14f, 0f, 84f)).forEach { (dx, dy, width) ->
            pixel(x + dx * scale, y + dy * scale, width * scale, 25 * scale, Color(0xFF1E3943))
            pixel(x + (dx + 5) * scale, y + (dy + 4) * scale, (width - 10) * scale, 8 * scale, Color(0xFF31524D))
        }
    }
    fun flower(x: Float, y: Float, color: Color) {
        pixel(x - 1f, y, 3f, 13f, Color(0xFF719D83))
        pixel(x - 5f, y - 5f, 11f, 6f, color)
        pixel(x - 3f, y - 8f, 7f, 10f, color)
        pixel(x - 1f, y - 3f, 3f, 3f, Color(0xFFFFE6AA))
    }
    fun lantern(x: Float, y: Float) {
        drawCircle(Color(0x38F6CE92), 28f, Offset(x, y))
        pixel(x - 6f, y - 7f, 12f, 20f, Color(0xFF9D795F))
        pixel(x - 3f, y - 4f, 6f, 12f, Color(0xFFFFDFA0))
        pixel(x - 8f, y - 10f, 16f, 4f, Color(0xFF493B52))
    }

    // Sparse star field and a luminous crescent: all geometry stays crisp at phone and foldable sizes.
    for (i in 0 until 72) {
        val x = ((i * 173 + 31) % 960).toFloat()
        val y = ((i * 67) % 145).toFloat()
        val alpha = if (garden.settings.reducedMotion) 0.45f else (0.35f + glow * ((i % 5) + 2) / 10f).coerceIn(0.3f, 1f)
        pixel(x, y, if (i % 6 == 0) 3f else 2f, 2f, Color(0xFFE9DFF8).copy(alpha = alpha))
    }
    drawCircle(Color(0x22E8D7B4), 74f, Offset(775f, 72f))
    drawCircle(Color(0xFFE9DAB8), 29f, Offset(775f, 68f))
    drawCircle(sky, 25f, Offset(787f, 58f))

    // Distant hills, pond path and mossy ground.
    pixel(0f, 162f, 960f, 440f, Color(0xFF2D4846))
    pixel(0f, 188f, 320f, 54f, Color(0xFF253F45))
    pixel(588f, 170f, 372f, 62f, Color(0xFF223B43))
    pixel(0f, 447f, 960f, 46f, Color(0xFF65585A))
    pixel(0f, 493f, 960f, 107f, Color(0xFF1B2934))
    for (i in 0 until 96) {
        val x = ((i * 59 + 9) % 960).toFloat()
        val y = (202 + (i * 41) % 390).toFloat()
        pixel(x, y, 3f, 2f, if (i % 4 == 0) Color(0xFF789778) else Color(0xFF3D5C51))
    }

    when (garden.area) {
        "garden" -> {
            tree(95f, 310f, 1.25f); tree(560f, 306f, 0.82f); tree(658f, 321f, 0.78f); tree(895f, 310f, 1.0f)
            // Cottage with a warm, layered roof, glowing windows and right-hand apothecary door.
            pixel(146f, 142f, 320f, 121f, Color(0xFF563E59))
            pixel(158f, 156f, 295f, 100f, Color(0xFF967380))
            for (i in 0..5) pixel(158f, 164f + i * 15f, 295f, 3f, Color(0xFF725469))
            pixel(132f, 137f, 348f, 18f, Color(0xFF51425C))
            pixel(163f, 117f, 285f, 20f, Color(0xFF6B5270))
            pixel(192f, 98f, 224f, 19f, Color(0xFF806183))
            pixel(220f, 89f, 166f, 9f, Color(0xFFAA879C))
            pixel(383f, 67f, 24f, 41f, Color(0xFF56485B))
            pixel(379f, 64f, 31f, 7f, Color(0xFF91748A))
            listOf(182f, 303f).forEach { x ->
                drawCircle(Color(0x45F4C179), 47f, Offset(x + 20f, 198f))
                pixel(x, 174f, 42f, 42f, Color(0xFF634E60))
                pixel(x + 5f, 179f, 32f, 32f, Color(0xFFF1C68D))
                pixel(x + 19f, 178f, 4f, 35f, Color(0xFF805E68))
                pixel(x + 4f, 193f, 34f, 4f, Color(0xFF805E68))
            }
            pixel(236f, 197f, 43f, 67f, Color(0xFF553F5C))
            pixel(243f, 202f, 29f, 55f, Color(0xFF9C7787))
            pixel(263f, 229f, 4f, 4f, Color(0xFFFFE3AA))
            pixel(392f, 202f, 33f, 59f, Color(0xFF4A3D55))
            pixel(398f, 209f, 21f, 47f, Color(0xFF886886))
            pixel(404f, 220f, 8f, 2f, Color(0xFFE6D2AD))

            // Nine rich beds, each directly tappable.
            for (i in 0 until 9) {
                val x = 330f + (i % 3) * 55f
                val y = 330f + (i / 3) * 48f
                val bed = garden.beds.getOrNull(i)
                pixel(x - 21f, y - 15f, 43f, 32f, Color(0xFF9A7068))
                pixel(x - 18f, y - 12f, 37f, 26f, if (bed?.watered == true) Color(0xFF484151) else Color(0xFF68515A))
                for (line in 0..3) pixel(x - 14f, y - 8f + line * 6f, 28f, 1.5f, Color(0xFF81616A))
                if (bed != null) {
                    val crop = CropType.fromKey(bed.type)
                    val ratio = crop?.let { (bed.age / it.secondsToMature).coerceIn(0.0, 1.0).toFloat() } ?: 0f
                    val leaf = crop?.let { Color(it.color) } ?: Color(0xFF99C6AC)
                    pixel(x - 2f, y - 3f - ratio * 14f, 4f, 11f + ratio * 14f, Color(0xFF83B391))
                    pixel(x - 10f, y - 5f, 10f, 4f, Color(0xFFA5CFAD))
                    pixel(x + 2f, y - 12f, 10f, 4f, Color(0xFF95C49D))
                    if (ratio >= 1f) flower(x, y - 22f, leaf) else pixel(x - 3f, y - 8f - ratio * 13f, 7f, 7f, leaf)
                    if (!bed.watered) pixel(x + 13f, y - 16f, 3f, 4f, Color(0xFF8AC4D2))
                }
            }
            // Bramble's market and the amethyst jukebox.
            pixel(724f, 244f, 125f, 55f, Color(0xFF725363))
            pixel(717f, 215f, 138f, 17f, Color(0xFFC0A087))
            for (i in 0..4) pixel(722f + i * 27f, 232f, 25f, 14f, if (i % 2 == 0) Color(0xFFE2C3A3) else Color(0xFFA48AA2))
            pixel(589f, 327f, 56f, 98f, Color(0xFF332743))
            pixel(596f, 334f, 42f, 84f, Color(0xFF5B426D))
            pixel(601f, 343f, 32f, 4f, Color(0xFFC49CF1))
            pixel(604f, 350f, 26f, 20f, Color(0xFF251A37))
            pixel(610f, 356f, 14f, 9f, Color(0xFFB99ADB))
            pixel(603f, 378f, 28f, 27f, Color(0xFF382743))
            pixel(607f, 382f, 20f, 17f, Color(0xFF60487A))
            drawCircle(Color(0x557D5AC1).copy(alpha = if (musicPlaying) glow else 0.18f), 46f, Offset(617f, 374f))
            if (musicPlaying) {
                val note = Color(0xFFE6C3EE).copy(alpha = 0.45f + 0.5f * glow)
                pixel(571f, 327f - 15f * glow, 5f, 5f, note)
                pixel(576f, 320f - 15f * glow, 2f, 10f, note)
                pixel(653f, 337f + 12f * glow, 5f, 5f, note)
                pixel(658f, 329f + 12f * glow, 2f, 11f, note)
            }
            for (i in 0 until garden.upgrades) lantern(540f + i * 61f, 435f)
            pixel(36f, 425f, 125f, 25f, Color(0xFF293F3D))
            flower(70f, 435f, Color(0xFFDEBCEB)); flower(110f, 440f, Color(0xFFFFE0A8))
            pixel(816f, 423f, 118f, 27f, Color(0xFF293F3D))
            flower(848f, 435f, Color(0xFFDEBCEB)); flower(892f, 440f, Color(0xFFFFE0A8))
            lantern(120f, 449f); lantern(850f, 449f)
            for (i in 0 until 15) flower(165f + i * 29f, 532f + (i % 3) * 8f, if (i % 3 == 0) Color(0xFFDFB6E5) else Color(0xFF99B5A8))
            pixel(40f, 461f, 140f, 2f, Color(0xFFEDD4B2))
            pixel(795f, 461f, 120f, 2f, Color(0xFFEDD4B2))
        }
        "wood" -> {
            for (i in 0 until 15) tree(75f + (i * 137 % 820), 205f + (i * 67 % 200), 0.8f + (i % 3) * 0.15f)
            for (i in 0 until 12) {
                if (i in garden.foraged) continue
                val x = if (i < 8) 230f + (i % 4) * 150f else 195f + (i - 8) * 175f
                val y = if (i < 8) 250f + (i / 4) * 155f else 350f
                if (i < 8) {
                    pixel(x - 3f, y - 6f, 6f, 12f, Color(0xFFD6C7B7))
                    pixel(x - 12f, y - 13f, 24f, 9f, Color(0xFFB8C8D1))
                    pixel(x - 8f, y - 19f, 16f, 7f, Color(0xFFE4E0DD))
                    drawCircle(Color(0x348DD5DE), 26f, Offset(x, y - 5f))
                } else {
                    flower(x, y, Color(0xFFB99AD6))
                    pixel(x - 7f, y + 7f, 15f, 4f, Color(0xFF826B9A))
                }
            }
            pixel(386f, 149f, 205f, 4f, Color(0xFFB9A8D4))
            pixel(422f, 173f, 135f, 2f, Color(0xFFD5CBE0))
        }
        else -> {
            for (i in 0 until 12) tree(44f + (i * 173 % 870), 205f + (i % 3) * 82f, 1.05f)
            drawCircle(Color(0x22C9B5F0), 178f, Offset(480f, 295f))
            pixel(429f, 224f, 100f, 80f, Color(0xFF888295))
            pixel(438f, 209f, 82f, 18f, Color(0xFFAAA0B1))
            pixel(438f, 234f, 82f, 3f, Color(0xFFD0BFD0))
            for (i in 0 until 12) {
                val x = 340f + (i * 57 % 300)
                flower(x, 367f + (i % 3) * 14f, if (garden.hasMilestone("bloom")) Color(0xFFDFB6E5) else Color(0xFF87938B))
            }
            garden.offerings.forEachIndexed { index, offering ->
                val x = 340f + (index % 5) * 61f
                val y = 300f + (index % 3) * 31f
                when (offering.type) {
                    "light" -> lantern(x, y)
                    "flower" -> flower(x, y, Color(0xFFDFB6E5))
                    else -> {
                        pixel(x - 5f, y - 7f, 10f, 10f, Color(0xFFF3D9A4))
                        drawCircle(Color(0x40F3D9A4), 22f, Offset(x, y - 2f))
                    }
                }
            }
            pixel(411f, 308f, 137f, 3f, Color(0xFFFFE8CF))
        }
    }

    // Nettle and a little witch silhouette are drawn in every place so familiar faces never feel far away.
    val characterX = if (garden.area == "glade") 685f else 496f
    val characterY = if (garden.area == "wood") 438f else 443f
    pixel(characterX - 11f, characterY - 11f, 23f, 18f, Color(0xFF262436))
    pixel(characterX - 10f, characterY - 18f, 6f, 10f, Color(0xFF302A43))
    pixel(characterX + 4f, characterY - 18f, 6f, 10f, Color(0xFF302A43))
    pixel(characterX - 6f, characterY - 8f, 3f, 3f, Color(0xFFF1CF8B))
    pixel(characterX + 4f, characterY - 8f, 3f, 3f, Color(0xFFF1CF8B))
    pixel(characterX + 9f, characterY - 4f, 12f, 4f, Color(0xFF302A43))
    pixel(528f, 420f, 22f, 18f, Color(0xFF92759B))
    pixel(526f, 408f, 25f, 15f, Color(0xFFB192B4))
    pixel(523f, 401f, 32f, 5f, Color(0xFF79608E))
    pixel(529f, 392f, 19f, 10f, Color(0xFF89709D))
    pixel(532f, 388f, 13f, 5f, Color(0xFFA086AF))
    pixel(541f, 414f, 3f, 3f, Color(0xFFF2D692))

    // Fireflies become still, rather than disappearing, when reduced motion is enabled.
    for (i in 0 until 13) {
        val x = ((i * 173 + 25) % 940 + 10).toFloat()
        val baseY = (180 + (i * 97) % 420).toFloat()
        val drift = if (garden.settings.reducedMotion) 0f else sin(glow * 3f + i) * 8f
        drawCircle(Color(0x32D6E78E), 8f, Offset(x + drift, baseY))
        pixel(x + drift - 1f, baseY - 1f, 3f, 3f, Color(0xFFE0E7A7))
    }
    if (garden.weather == "rain" && !garden.settings.reducedMotion) {
        for (i in 0 until 48) {
            val x = ((i * 83) % 960).toFloat()
            val y = (164 + (i * 47) % 405).toFloat()
            pixel(x, y, 2f, 10f, Color(0x667EAACB))
        }
    }
}
