package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.ui.BlancNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Blanc palette: deep purple-black surfaces, one violet accent. */
internal object Blanc {
    val ink = Color(0xFF0A0614)
    val card = Color(0xFF140E27)
    val cardHi = Color(0xFF1D1538)
    val line = Color(0x338B5CF6)
    val ivory = Color(0xFFF4F0FF)
    val dim = Color(0xFFA79DC4)
    val violet = Color(0xFF8B5CF6)
    val rose = Color(0xFFC084FC)
    val amber = Color(0xFF7C9CFF)
    val hero = Brush.linearGradient(listOf(Color(0xFF5B2EE0), Color(0xFF8B5CF6), Color(0xFFB57BFF)))
}

private fun loaderOf(versionId: String): String {
    val v = versionId.lowercase()
    return when {
        "neoforge" in v -> "NeoForge"
        "forge" in v -> "Forge"
        "fabric" in v -> "Fabric"
        "quilt" in v -> "Quilt"
        "optifine" in v -> "OptiFine"
        else -> "Vanilla"
    }
}

private fun mcOf(versionId: String): String = GameDownloadTarget.minecraftVersionIn(versionId) ?: versionId

@Composable
fun BlancHome(activity: FragmentActivity, navigate: (LauncherSection) -> Unit, editionSwitch: @Composable () -> Unit = {}) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val account = PojavAccountBridge.current
    val play: () -> Unit = { PojavLaunchBridge.play(activity) }

    val features: @Composable (Modifier) -> Unit = { mod ->
        Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewsTile("COSMETICS", "1000+ looks", listOf(Color(0xFF7B2FF7), Color(0xFFC084FC)), Modifier.weight(1f)) { navigate(LauncherSection.WARDROBE) }
            NewsTile("REALMS", "Join anytime", listOf(Color(0xFF2B5BE0), Color(0xFF7C9CFF)), Modifier.weight(1f)) { navigate(LauncherSection.REALMS) }
            NewsTile("HOSTING", "Own server", listOf(Color(0xFF4B1FA8), Color(0xFF8B5CF6)), Modifier.weight(1f)) { navigate(LauncherSection.HOST) }
            Column(
                Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Blanc.card)
                    .border(BorderStroke(1.dp, Blanc.line), RoundedCornerShape(18.dp)).clickable { navigate(LauncherSection.MULTIPLAYER) }.padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("FRIENDS", color = Blanc.rose, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Play together", color = Blanc.ivory, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text("Join with a 5-char code", color = Blanc.dim, fontSize = 10.sp)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pickInstance: (net.kdt.pojavlaunch.aerix.bridge.AerixInstance) -> Unit = { PojavInstanceBridge.select(it) }
            val addProfile = { navigate(LauncherSection.CREATE_INSTANCE) }
            if (maxWidth >= 560.dp) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hero(selected, account, Modifier.weight(1f).fillMaxWidth(), play, editionSwitch, { navigate(LauncherSection.VERSIONS) }, addProfile)
                        ProfileSlots(instances, selected, Modifier.fillMaxWidth().height(38.dp), pickInstance, addProfile)
                        features(Modifier.fillMaxWidth().height(80.dp))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCard(Modifier, false, onClick = { navigate(LauncherSection.ACCOUNTS) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AerixImageWell(image = account?.face, fallback = R.drawable.aerix_head_steve, size = 34.dp, radius = 10.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(account?.username ?: "No account", color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(account?.authType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Tap to add", color = Blanc.dim, fontSize = 10.sp)
                                }
                                Text("\u2304", color = Blanc.dim, fontSize = 16.sp)
                            }
                        }
                        PromoCard("Blanc Realms", "Play with friends anytime", "JOIN", listOf(Color(0xFF3B1FA8), Color(0xFF6D3FF0)), Modifier.height(54.dp)) { navigate(LauncherSection.REALMS) }
                        PromoCard("Wardrobe", "1000+ skins and looks", "OPEN", listOf(Color(0xFF1F7A4A), Color(0xFF2FA865)), Modifier.height(54.dp)) { navigate(LauncherSection.WARDROBE) }
                        Text("POPULAR SERVERS", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp, start = 2.dp))
                        POPULAR.forEach { ServerRow(it, Modifier.weight(1f)) { navigate(LauncherSection.SERVERS) } }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hero(selected, account, Modifier.fillMaxWidth().height(300.dp), play, editionSwitch, { navigate(LauncherSection.VERSIONS) }, addProfile)
                    ProfileSlots(instances, selected, Modifier.fillMaxWidth().height(40.dp), pickInstance, addProfile)
                    features(Modifier.fillMaxWidth().height(84.dp))
                    PromoCard("Blanc Realms", "Play with friends anytime", "JOIN", listOf(Color(0xFF3B1FA8), Color(0xFF6D3FF0)), Modifier.height(56.dp)) { navigate(LauncherSection.REALMS) }
                    POPULAR.forEach { ServerRow(it, Modifier.height(48.dp)) { navigate(LauncherSection.SERVERS) } }
                }
            }
        }
    }
}

@Composable
private fun Hero(
    selected: net.kdt.pojavlaunch.aerix.bridge.AerixInstance?,
    account: net.kdt.pojavlaunch.aerix.bridge.AerixAccount?,
    modifier: Modifier,
    onPlay: () -> Unit,
    editionSwitch: @Composable () -> Unit,
    onVersions: () -> Unit,
    onCreate: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2B1470), Color(0xFF140B33), Color(0xFF0C0720))))
            .border(BorderStroke(1.dp, Blanc.line), shape)
    ) {
        Box(Modifier.align(Alignment.Center).size(320.dp).background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color.Transparent))))
        CubeArt(Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { editionSwitch() }
        if (account != null) {
            AerixSkinPreview3D(
                account = account,
                interactionEnabled = false,
                modifier = Modifier.align(Alignment.Center).fillMaxHeight().width(170.dp).padding(top = 34.dp, bottom = 48.dp)
            )
            Text(
                account.username, color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xAA0A0614))
                    .border(BorderStroke(1.dp, Blanc.line), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        if (selected != null) Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(loaderOf(selected.versionId)); Chip(mcOf(selected.versionId))
        }
        val pill = RoundedCornerShape(16.dp)
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).height(52.dp).widthIn(min = 230.dp, max = 330.dp)
                .shadow(16.dp, pill, ambientColor = Blanc.violet, spotColor = Blanc.violet).clip(pill).background(Blanc.hero)
                .border(BorderStroke(1.dp, Color(0x66FFFFFF)), pill),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { if (selected != null) onPlay() else onCreate() },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(if (selected != null) "LAUNCH GAME" else "CREATE INSTANCE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                if (selected != null) Text(mcOf(selected.versionId) + " - " + loaderOf(selected.versionId), color = Color(0xDDFFFFFF), fontSize = 10.sp, maxLines = 1)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x44FFFFFF)))
            Box(Modifier.width(44.dp).fillMaxHeight().clickable(role = Role.Button, onClick = onVersions), contentAlignment = Alignment.Center) {
                Text("\u2304", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ProfileSlots(instances: List<net.kdt.pojavlaunch.aerix.bridge.AerixInstance>, selected: net.kdt.pojavlaunch.aerix.bridge.AerixInstance?, modifier: Modifier, onPick: (net.kdt.pojavlaunch.aerix.bridge.AerixInstance) -> Unit, onAdd: () -> Unit) {
    val shown = instances.take(3)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { inst ->
            val on = inst.id == selected?.id
            val sh = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(sh).background(if (on) Blanc.cardHi else Blanc.card)
                    .border(BorderStroke(1.dp, if (on) Blanc.violet else Blanc.line), sh).clickable { onPick(inst) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(inst.name, color = Blanc.ivory, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        repeat(3 - shown.size) {
            val sh = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(sh).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), sh).clickable(onClick = onAdd),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) { Text("+  Add a Profile", color = Blanc.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private class PopularServer(val name: String, val address: String, val colors: List<Color>)
private val POPULAR = listOf(
    PopularServer("Hypixel", "mc.hypixel.net", listOf(Color(0xFFB7791F), Color(0xFF7A4B0E))),
    PopularServer("Mineplex", "mineplex.com", listOf(Color(0xFFD9822B), Color(0xFF8A4A12))),
    PopularServer("MCC Island", "play.mccisland.net", listOf(Color(0xFF3B8E5A), Color(0xFF1F5A38))),
    PopularServer("DonutSMP", "donutsmp.net", listOf(Color(0xFF2B7BE0), Color(0xFF1B4C96)))
)

@Composable
private fun PromoCard(title: String, sub: String, action: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(colors)).border(BorderStroke(1.dp, Color(0x44FFFFFF)), shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(sub, color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
        Text(
            action, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x66000000)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ServerRow(s: PopularServer, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(s.colors)), contentAlignment = Alignment.Center) {
            Text(s.name.take(1), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(s.address, color = Blanc.dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("\u25CF", color = Color(0xFF4ADE80), fontSize = 10.sp)
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Blanc.ivory, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(Color(0x338B5CF6)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, fill: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier).clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
private fun ProfilePicker(modifier: Modifier, fill: Boolean, current: String?, names: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        InfoCard(Modifier, fill, onClick = { if (names.isNotEmpty()) open = true }) {
            Text("Profile", color = Blanc.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current ?: "No profiles", color = Blanc.ivory, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("\u2304", color = Blanc.dim, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

@Composable
private fun Feature(title: String, subtitle: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, tint = Blanc.violet, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = Blanc.ivory, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = Blanc.dim, fontSize = 10.sp, maxLines = 1)
    }
}

/** Original isometric block art for the hero banner (drawn, no image assets). */
@Composable
internal fun CubeArt(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        fun cube(cx: Float, cy: Float, r: Float, a: Float) {
            val k = r * 0.87f
            val h = r * 0.5f
            val top = androidx.compose.ui.graphics.Path().apply { moveTo(cx, cy - r); lineTo(cx + k, cy - h); lineTo(cx, cy); lineTo(cx - k, cy - h); close() }
            val left = androidx.compose.ui.graphics.Path().apply { moveTo(cx - k, cy - h); lineTo(cx, cy); lineTo(cx, cy + r); lineTo(cx - k, cy + h); close() }
            val right = androidx.compose.ui.graphics.Path().apply { moveTo(cx + k, cy - h); lineTo(cx, cy); lineTo(cx, cy + r); lineTo(cx + k, cy + h); close() }
            drawPath(left, Color(0xFF4C2BB8).copy(alpha = a))
            drawPath(right, Color(0xFF2E1778).copy(alpha = a))
            drawPath(top, Color(0xFFA98BFF).copy(alpha = a))
        }
        val w = size.width
        val hh = size.height
        cube(w * 0.50f, hh * 0.28f, hh * 0.17f, 0.85f)
        cube(w * 0.78f, hh * 0.42f, hh * 0.24f, 0.95f)
        cube(w * 0.32f, hh * 0.60f, hh * 0.15f, 0.6f)
        cube(w * 0.60f, hh * 0.78f, hh * 0.20f, 0.8f)
        cube(w * 0.90f, hh * 0.82f, hh * 0.12f, 0.55f)
    }
}

@Composable
private fun NewsTile(title: String, sub: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxHeight().clip(shape).background(Brush.linearGradient(colors)).border(BorderStroke(1.dp, Color(0x44FFFFFF)), shape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        CubeArt(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(sub, color = Color(0xCCFFFFFF), fontSize = 10.sp)
        }
    }
}
