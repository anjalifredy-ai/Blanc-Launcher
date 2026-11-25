package net.kdt.pojavlaunch.aerix.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.bridge.AerixSkinPreset
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import java.io.File
import java.io.FileOutputStream

private val tones = intArrayOf(0xFFF5CBA7, 0xFFE8B98C, 0xFFD39B6F, 0xFFB57A52, 0xFF8D5A3B, 0xFF6B4226, 0xFFFFE0C8, 0xFFA8D5A2).map { it.toInt() }
private val hairs = intArrayOf(0xFF1B1B1B, 0xFF4A2F1B, 0xFF8B5A2B, 0xFFE0B44C, 0xFFB5381E, 0xFFEDEDED, 0xFF5B2EE0, 0xFF2AA3D6, 0xFFE0559C, 0xFF3DA35D).map { it.toInt() }
private val shirts = intArrayOf(
    0xFF8B5CF6, 0xFFE0455C, 0xFF2E86DE, 0xFF27AE60, 0xFFF39C12, 0xFF1F1F2E,
    0xFFECF0F1, 0xFFE056A0, 0xFF16A085, 0xFF7F8C8D, 0xFFD35400, 0xFF34495E
).map { it.toInt() }
private val pants = intArrayOf(0xFF2C3E50, 0xFF1B1B1B, 0xFF4A5A8A, 0xFF6D4C41, 0xFF5B2EE0, 0xFF7F8C8D).map { it.toInt() }
private val accents = intArrayOf(0xFFFFFFFF, 0xFF111111, 0xFFFFD54F, 0xFF80DEEA).map { it.toInt() }

/** Skin number [n] of the built-in library: tones x hair x shirt x pants x accent = 23,040 looks. */
private fun skinFor(n: Int): Bitmap {
    var k = n
    val tone = tones[k % tones.size]; k /= tones.size
    val hair = hairs[k % hairs.size]; k /= hairs.size
    val shirt = shirts[k % shirts.size]; k /= shirts.size
    val pant = pants[k % pants.size]; k /= pants.size
    val accent = accents[k % accents.size]
    return PojavAccountBridge.generateSkinBitmap(tone, hair, 0xFF1B2A41.toInt(), shirt, accent, shirt, pant, 0xFF222222.toInt())
}

private const val LIBRARY_SIZE = 23040

/** Wardrobe: browse a huge built-in skin library, look up any player's skin by name, equip with one tap. */
@Composable
fun BlancWardrobe(activity: FragmentActivity) {
    val context = LocalContext.current
    val account = PojavAccountBridge.current
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var slim by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val saved = remember { PojavAccountBridge.loadSavedSkinPresets(context) }
    val tabs = listOf("Library", "Saved", "Find player")

    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
    fun equipGenerated(n: Int) {
        val acc = account ?: run { toast("Add an account first"); return }
        busy = true
        Thread {
            val file = File(PojavAccountBridge.skinsDirectory(), "blanc-$n.png")
            val bmp = skinFor(n)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val preview = PojavAccountBridge.renderSkinBodyPreview(bmp).asImageBitmap()
            activity.runOnUiThread {
                PojavAccountBridge.equipSkinPreset(context, acc, AerixSkinPreset("blanc-$n", "Blanc #$n", file, preview)) { ok ->
                    busy = false; toast(if (ok) "Skin equipped" else "Could not equip skin")
                }
            }
        }.start()
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 640.dp
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("WARDROBE", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Text("Skins", color = Blanc.ivory, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("${"%,d".format(LIBRARY_SIZE)} looks in the library. Tap one, then Equip.", color = Blanc.dim, fontSize = 11.sp)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEachIndexed { i, label ->
                            val on = i == tab
                            Text(
                                label, color = if (on) Color.White else Blanc.dim, fontSize = 12.sp, fontWeight = if (on) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier.clip(CircleShape)
                                    .then(if (on) Modifier.background(Blanc.hero) else Modifier.background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), CircleShape))
                                    .clickable { tab = i }.padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                    when (tab) {
                        0 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("More skins", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).background(Blanc.cardHi).clickable { offset = (offset + 48) % LIBRARY_SIZE }.padding(horizontal = 14.dp, vertical = 7.dp))
                                Text("Random", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).background(Blanc.cardHi).clickable { offset = (0 until LIBRARY_SIZE).random() }.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(92.dp), modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items((0 until 96).map { (offset + it * 211) % LIBRARY_SIZE }, key = { it }) { n ->
                                    val preview = remember(n) { PojavAccountBridge.renderSkinBodyPreview(skinFor(n)).asImageBitmap() }
                                    val on = selected == n
                                    val shape = RoundedCornerShape(16.dp)
                                    Box(
                                        Modifier.height(128.dp).clip(shape).background(if (on) Blanc.cardHi else Blanc.card)
                                            .border(BorderStroke(if (on) 1.5.dp else 1.dp, if (on) Blanc.violet else Blanc.line), shape)
                                            .clickable { selected = n }.padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                                }
                            }
                        }
                        1 -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(92.dp), modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(saved, key = { it.id }) { preset ->
                                val shape = RoundedCornerShape(16.dp)
                                Column(
                                    Modifier.height(140.dp).clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape)
                                        .clickable {
                                            val acc = account
                                            if (acc == null) toast("Add an account first")
                                            else PojavAccountBridge.equipSkinPreset(context, acc, preset) { ok -> toast(if (ok) "Skin equipped" else "Could not equip skin") }
                                        }.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    preset.preview?.let { Image(it, null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit) }
                                    Text(preset.name, color = Blanc.ivory, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Type any Minecraft player's name to copy their skin.", color = Blanc.dim, fontSize = 12.sp)
                            OutlinedTextField(lookup, { lookup = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Player name") })
                            Text(
                                if (busy) "Working..." else "GET SKIN", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.clip(CircleShape).background(Blanc.hero).clickable(enabled = !busy && lookup.isNotBlank()) {
                                    val acc = account
                                    if (acc == null) { toast("Add an account first"); return@clickable }
                                    val name = lookup.trim()
                                    busy = true
                                    PojavAccountBridge.equipOnlineSkin(
                                        context, acc, name,
                                        listOf("https://minotar.net/skin/$name.png", "https://crafatar.com/skins/$name"), slim
                                    ) { ok -> busy = false; toast(if (ok) "Skin of $name equipped" else "Could not find that player") }
                                }.padding(horizontal = 26.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
                if (wide) {
                    val shape = RoundedCornerShape(24.dp)
                    Column(
                        Modifier.width(220.dp).fillMaxHeight().clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val preview = remember(selected) { PojavAccountBridge.renderSkinBodyPreview(skinFor(selected)).asImageBitmap() }
                        Text("Blanc #$selected", color = Blanc.ivory, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Image(preview, null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Wide" to false, "Slim" to true).forEach { (label, value) ->
                                Text(
                                    label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape).background(if (slim == value) Blanc.violet else Blanc.cardHi).clickable {
                                        slim = value
                                        account?.let { PojavAccountBridge.setSlimModel(context, it, value) }
                                    }.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Text(
                            if (busy) "..." else "EQUIP", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Blanc.hero).clickable(enabled = !busy) { equipGenerated(selected) }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            if (!wide && tab == 0) {
                Text(
                    if (busy) "..." else "EQUIP #$selected", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(CircleShape).background(Blanc.hero)
                        .clickable(enabled = !busy) { equipGenerated(selected) }.padding(horizontal = 28.dp, vertical = 12.dp)
                )
            }
        }
    }
}
