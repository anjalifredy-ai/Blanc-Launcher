package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.GameDownloadTarget
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Blanc palette: ink black, ivory text, one electric violet-to-rose gradient. */
private object Blanc {
    val ink = Color(0xFF08080D)
    val card = Color(0xFF12131C)
    val cardHi = Color(0xFF1B1C29)
    val line = Color(0x1FFFFFFF)
    val ivory = Color(0xFFF6F3EC)
    val dim = Color(0xFF9A9BB0)
    val violet = Color(0xFF7C5CFF)
    val rose = Color(0xFFFF6FA8)
    val amber = Color(0xFFFFB86B)
    val hero = Brush.linearGradient(listOf(Color(0xFF3B2BA8), Color(0xFF7C3AED), Color(0xFFDB4F95)))
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
fun BlancHome(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    LaunchedEffect(Unit) { PojavAccountBridge.ensureLoaded() }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val account = PojavAccountBridge.current
    val play: () -> Unit = { PojavLaunchBridge.play(activity) }
    val create: () -> Unit = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blanc.ink)
            .background(Brush.radialGradient(listOf(Color(0x557C5CFF), Color.Transparent), radius = 900f))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 640.dp
            if (wide) {
                Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Hero(selected, account?.username, Modifier.weight(1f).fillMaxWidth(), play, create) {
                            selected?.let { PojavInstanceBridge.edit(activity, it) }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        QuickGrid(activity, navigate, create)
                        Recent(instances, selected, activity)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Hero(selected, account?.username, Modifier.fillMaxWidth().height(230.dp), play, create) {
                        selected?.let { PojavInstanceBridge.edit(activity, it) }
                    }
                    QuickGrid(activity, navigate, create)
                    Recent(instances, selected, activity)
                }
            }
        }
    }
}

@Composable
private fun Hero(
    selected: AerixInstance?, user: String?, modifier: Modifier,
    onPlay: () -> Unit, onCreate: () -> Unit, onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.clip(shape).background(Blanc.hero).border(BorderStroke(1.dp, Color(0x33FFFFFF)), shape)) {
        // soft light blobs
        Box(Modifier.align(Alignment.TopEnd).size(200.dp).background(Brush.radialGradient(listOf(Color(0x44FFFFFF), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WELCOME BACK", color = Color(0xB3FFFFFF), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text(user ?: "Player", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selected != null) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(role = Role.Button, onClick = onEdit),
                        contentAlignment = Alignment.Center
                    ) { Icon(painterResource(R.drawable.aerix_ic_settings_filled), "Instance settings", tint = Color.White, modifier = Modifier.size(19.dp)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AerixImageWell(image = selected?.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 64.dp, radius = 18.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(selected?.name ?: "No instance yet", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    if (selected != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(loaderOf(selected.versionId)); Chip(mcOf(selected.versionId))
                    } else Text("Create your first instance to start playing", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Color.White)
                    .clickable(role = Role.Button) { if (selected != null) onPlay() else onCreate() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(if (selected != null) R.drawable.aerix_ic_play_arrow_filled else R.drawable.aerix_ic_add), null, tint = Color(0xFF2A1670), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selected != null) "PLAY" else "CREATE", color = Color(0xFF2A1670), fontSize = 15.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private class Tile(val label: String, val icon: Int, val tint: Color, val onClick: () -> Unit)

@Composable
private fun QuickGrid(activity: FragmentActivity, navigate: (LauncherSection) -> Unit, create: () -> Unit) {
    val tiles = listOf(
        Tile("New", R.drawable.aerix_ic_add, Blanc.violet, create),
        Tile("Library", R.drawable.aerix_ic_dashboard_filled, Blanc.rose) { navigate(LauncherSection.LIBRARY) },
        Tile("Host", R.drawable.aerix_ic_terminal_outlined, Blanc.amber) { navigate(LauncherSection.HOST) },
        Tile("Discover", R.drawable.aerix_ic_public, Color(0xFF5CE1E6)) { navigate(LauncherSection.DISCOVER) },
        Tile("Files", R.drawable.aerix_ic_folder_outlined, Color(0xFF8BE28B)) { navigate(LauncherSection.FILES) },
        Tile("Controls", R.drawable.aerix_ic_build_outlined, Color(0xFFFF8A65)) { PojavNavigator.openCustomControls(activity) },
        Tile("Logs", R.drawable.aerix_ic_description_outlined, Blanc.dim) { navigate(LauncherSection.LOGS) },
        Tile("Tools", R.drawable.aerix_ic_build_outlined, Color(0xFFB39DFF)) { navigate(LauncherSection.QUICK_TOOLS) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("QUICK ACCESS")
        tiles.chunked(4).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { t -> TileView(t, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(76.dp).clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape)
            .clickable(role = Role.Button, onClick = t.onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(t.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(t.icon), t.label, tint = t.tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(t.label, color = Blanc.ivory, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Blanc.dim, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun Recent(instances: List<AerixInstance>, selected: AerixInstance?, activity: FragmentActivity) {
    if (instances.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("YOUR INSTANCES")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            instances.take(12).forEach { inst ->
                val active = inst == selected
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.width(150.dp).clip(shape)
                        .background(if (active) Blanc.cardHi else Blanc.card)
                        .border(BorderStroke(if (active) 1.5.dp else 1.dp, if (active) Blanc.violet else Blanc.line), shape)
                        .clickable(role = Role.Button) { PojavInstanceBridge.select(inst) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AerixImageWell(image = inst.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 38.dp, radius = 12.dp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Blanc.violet)
                                .clickable(role = Role.Button) {
                                    PojavInstanceBridge.select(inst)
                                    PojavLaunchBridge.play(activity)
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(painterResource(R.drawable.aerix_ic_play_arrow_filled), "Play ${inst.name}", tint = Color.White, modifier = Modifier.size(17.dp)) }
                    }
                    Text(inst.name, color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${loaderOf(inst.versionId)} · ${mcOf(inst.versionId)}", color = Blanc.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
