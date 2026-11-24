package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AssetType
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.ui.screens.AerixDownloadRequest
import net.kdt.pojavlaunch.aerix.ui.screens.Blanc

/** Which content tab Discover was opened on, so the sidebar can highlight Mods / Packs / Shaders separately. */
object BlancNav {
    var kind by mutableStateOf(AssetType.MOD)
}

private class Entry(val label: String, val icon: Int, val selected: (LauncherSection) -> Boolean, val go: ((LauncherSection) -> Unit) -> Unit)

/**
 * Blanc sidebar: logo block, labelled entries, status footer. Collapses to icons only on narrow screens.
 */
@Composable
fun BlancSideBar(
    section: LauncherSection,
    compact: Boolean,
    onNavigate: (LauncherSection) -> Unit,
    modifier: Modifier = Modifier
) {
    fun discover(kind: AssetType): (LauncherSection) -> Boolean = { it == LauncherSection.DISCOVER && BlancNav.kind == kind }
    val entries = listOf(
        Entry("Home", R.drawable.aerix_ic_home_filled, { it == LauncherSection.HOME }) { it(LauncherSection.HOME) },
        Entry("Versions", R.drawable.aerix_ic_dashboard_filled, {
            it == LauncherSection.LIBRARY || it == LauncherSection.CREATE_INSTANCE || it == LauncherSection.INSTANCE_EDITOR ||
                it == LauncherSection.INSTANCE_SELECTOR || it == LauncherSection.CONTENT
        }) { it(LauncherSection.LIBRARY) },
        Entry("Mods", R.drawable.aerix_ic_extension_outlined, discover(AssetType.MOD)) { nav -> BlancNav.kind = AssetType.MOD; AerixDownloadRequest.open(AssetType.MOD, nav) },
        Entry("Resource Packs", R.drawable.aerix_ic_format_paint_outlined, discover(AssetType.RESOURCE_PACK)) { nav -> BlancNav.kind = AssetType.RESOURCE_PACK; AerixDownloadRequest.open(AssetType.RESOURCE_PACK, nav) },
        Entry("Shaders", R.drawable.aerix_ic_lightbulb, discover(AssetType.SHADER_PACK)) { nav -> BlancNav.kind = AssetType.SHADER_PACK; AerixDownloadRequest.open(AssetType.SHADER_PACK, nav) },
        Entry("Multiplayer", R.drawable.aerix_ic_group_outlined, { it == LauncherSection.MULTIPLAYER }) { it(LauncherSection.MULTIPLAYER) },
        Entry("Hosting", R.drawable.aerix_ic_terminal_outlined, { it == LauncherSection.HOST }) { it(LauncherSection.HOST) },
        Entry("Servers", R.drawable.aerix_ic_public, { it == LauncherSection.SERVERS }) { it(LauncherSection.SERVERS) },
        Entry("Accounts", R.drawable.aerix_ic_person_outlined, {
            it == LauncherSection.ACCOUNTS || it == LauncherSection.ADD_ACCOUNT || it == LauncherSection.OFFLINE_ACCOUNT
        }) { it(LauncherSection.ACCOUNTS) },
        Entry("Settings", R.drawable.aerix_ic_settings_filled, {
            it == LauncherSection.SETTINGS || it == LauncherSection.ABOUT || it == LauncherSection.LOGS
        }) { it(LauncherSection.SETTINGS) }
    )
    val width: Dp = if (compact) 58.dp else 158.dp
    val shape = RoundedCornerShape(22.dp)
    val ready = PojavInstanceBridge.selected != null

    Column(
        modifier = modifier
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
            .width(width)
            .fillMaxHeight()
            .clip(shape)
            .background(Blanc.card)
            .border(BorderStroke(1.dp, Blanc.line), shape)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        // Brand
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Blanc.hero),
                contentAlignment = Alignment.Center
            ) { Text("B", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("BLANC", color = Blanc.ivory, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("LAUNCHER", color = Blanc.violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            entries.forEach { e ->
                val on = e.selected(section)
                val rowShape = RoundedCornerShape(13.dp)
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(rowShape)
                        .then(if (on) Modifier.background(Blanc.hero).border(BorderStroke(1.dp, Color(0x55FFFFFF)), rowShape) else Modifier)
                        .clickable(role = Role.Tab) { e.go(onNavigate) }
                        .semantics { contentDescription = e.label }
                        .padding(horizontal = if (compact) 0.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
                ) {
                    Icon(painterResource(e.icon), e.label, tint = if (on) Color.White else Blanc.dim, modifier = Modifier.size(19.dp))
                    if (!compact) {
                        Spacer(Modifier.width(10.dp))
                        Text(e.label, color = if (on) Color.White else Blanc.ivory, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        // Status footer
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) Color(0xFF3DDC84) else Color(0xFFFFB86B)))
            if (!compact) {
                Spacer(Modifier.width(7.dp))
                Text(if (ready) "Ready to launch!" else "Pick a version", color = Blanc.dim, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
