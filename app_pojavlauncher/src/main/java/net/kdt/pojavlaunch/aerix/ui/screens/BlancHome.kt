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

    val rightColumn: @Composable androidx.compose.foundation.layout.ColumnScope.(Boolean) -> Unit = { fill ->
        val cm = if (fill) Modifier.weight(1f) else Modifier
        InfoCard(cm, fill, onClick = { navigate(LauncherSection.ACCOUNTS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AerixImageWell(image = account?.face, fallback = R.drawable.aerix_head_steve, size = 40.dp, radius = 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(account?.username ?: "No account", color = Blanc.ivory, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(account?.authType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Tap to add", color = Blanc.dim, fontSize = 11.sp)
                }
                Text("\u2304", color = Blanc.dim, fontSize = 16.sp)
            }
        }
        InfoCard(cm, fill, onClick = { navigate(LauncherSection.VERSIONS) }) {
            Text("Game Version", color = Blanc.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Blanc.hero), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.aerix_ic_videogame_asset_outlined), null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(selected?.let { mcOf(it.versionId) } ?: "None", color = Blanc.ivory, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("\u2304", color = Blanc.dim, fontSize = 16.sp)
            }
        }
        ProfilePicker(cm, fill, selected?.name, instances.map { it.name }) { index -> PojavInstanceBridge.select(instances[index]) }
    }

    val features: @Composable (Modifier) -> Unit = { mod ->
        Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature("Fast Launch", "Max FPS", R.drawable.aerix_ic_rocket_launch_filled, Modifier.weight(1f)) { play() }
            Feature("Mod Support", "One Click", R.drawable.aerix_ic_extension_outlined, Modifier.weight(1f)) {
                BlancNav.kind = AssetType.MOD
                net.kdt.pojavlaunch.aerix.ui.screens.AerixDownloadRequest.open(AssetType.MOD, navigate)
            }
            Feature("Multiplayer", "Play Together", R.drawable.aerix_ic_group_outlined, Modifier.weight(1f)) { navigate(LauncherSection.MULTIPLAYER) }
            Feature("Hosting", "Own Server", R.drawable.aerix_ic_terminal_outlined, Modifier.weight(1f)) { navigate(LauncherSection.HOST) }
        }
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 560.dp) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hero(selected, account, Modifier.weight(1f).fillMaxWidth(), play, editionSwitch) { navigate(LauncherSection.CREATE_INSTANCE) }
                        features(Modifier.fillMaxWidth().height(84.dp))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rightColumn(true)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hero(selected, account, Modifier.fillMaxWidth().height(230.dp), play, editionSwitch) { navigate(LauncherSection.CREATE_INSTANCE) }
                    rightColumn(false)
                    features(Modifier.fillMaxWidth().height(84.dp))
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
    onCreate: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2B1470), Color(0xFF140B33), Color(0xFF0C0720))))
            .border(BorderStroke(1.dp, Blanc.line), shape)
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(260.dp).background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color.Transparent))))
        CubeArt(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.55f))
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { editionSwitch() }
        if (account != null) {
            AerixSkinPreview3D(
                account = account,
                interactionEnabled = false,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(170.dp).padding(vertical = 6.dp)
            )
        }
        Column(Modifier.align(Alignment.CenterStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("READY TO PLAY", color = Blanc.rose, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text(
                selected?.name ?: "Create your first instance", color = Blanc.ivory, fontSize = 26.sp, fontWeight = FontWeight.Black,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(210.dp)
            )
            if (selected != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(loaderOf(selected.versionId)); Chip(mcOf(selected.versionId))
            }
            Spacer(Modifier.height(4.dp))
            val pill = RoundedCornerShape(50)
            Row(
                Modifier.height(50.dp).shadow(14.dp, pill, ambientColor = Blanc.violet, spotColor = Blanc.violet).clip(pill).background(Blanc.hero).border(BorderStroke(1.dp, Color(0x66FFFFFF)), pill)
                    .clickable(role = Role.Button) { if (selected != null) onPlay() else onCreate() }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(if (selected != null) R.drawable.aerix_ic_play_arrow_filled else R.drawable.aerix_ic_add), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selected != null) "Launch Game" else "Create", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
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
