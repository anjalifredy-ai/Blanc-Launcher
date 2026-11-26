package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixLoader
import net.kdt.pojavlaunch.aerix.bridge.AerixVersionType
import net.kdt.pojavlaunch.aerix.bridge.PojavCreateInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Versions page: Official / Snapshots / Old / Modded tabs and a one-tap Install list. */
@Composable
fun BlancVersions(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    val revision = PojavCreateInstanceBridge.versionTableRevision
    val versions = remember(revision) { PojavCreateInstanceBridge.gameVersions() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val installed = remember(versions, PojavInstanceBridge.instances) {
        versions.filter { it.type == AerixVersionType.INSTALLED }.map { it.id }.toSet()
    }
    val tabs = listOf("Official", "Snapshots", "Old Versions", "Modded", "Installed")
    val rows = remember(versions, tab) {
        when (tab) {
            0 -> versions.filter { it.type == AerixVersionType.RELEASE }
            1 -> versions.filter { it.type == AerixVersionType.SNAPSHOT }
            2 -> versions.filter { it.type == AerixVersionType.OLD_BETA || it.type == AerixVersionType.OLD_ALPHA }
            else -> emptyList()
        }
    }
    val installing = PojavCreateInstanceBridge.installing

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showArt = maxWidth >= 720.dp
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Blanc.hero), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.aerix_ic_dashboard_filled), null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Versions", color = Blanc.ivory, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("Choose a Minecraft version to play", color = Blanc.dim, fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEachIndexed { i, label ->
                            val on = i == tab
                            Text(
                                label, color = if (on) Color.White else Blanc.dim, fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Black else FontWeight.Medium, maxLines = 1,
                                modifier = Modifier.clip(CircleShape)
                                    .then(if (on) Modifier.background(Blanc.hero) else Modifier.background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), CircleShape))
                                    .clickable {
                                        when (i) {
                                            3 -> navigate(LauncherSection.CREATE_INSTANCE)
                                            4 -> navigate(LauncherSection.LIBRARY)
                                            else -> tab = i
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            if (versions.isEmpty()) "Loading the version list... connect to the internet if this stays empty." else "Nothing here.",
                            color = Blanc.dim, fontSize = 12.sp, modifier = Modifier.padding(8.dp)
                        )
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(rows, key = { it.id }) { v ->
                            val first = tab == 0 && v.id == rows.first().id
                            val have = v.id in installed
                            val shape = RoundedCornerShape(18.dp)
                            Row(
                                Modifier.fillMaxWidth().clip(shape).background(if (first) Blanc.cardHi else Blanc.card)
                                    .border(BorderStroke(if (first) 1.5.dp else 1.dp, if (first) Blanc.violet else Blanc.line), shape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(painterResource(R.drawable.aerix_loader_vanilla), null, Modifier.size(38.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.id, color = Blanc.ivory, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        if (first) "Latest Release" else when (v.type) {
                                            AerixVersionType.SNAPSHOT -> "Snapshot"
                                            AerixVersionType.OLD_BETA -> "Old Beta"
                                            AerixVersionType.OLD_ALPHA -> "Old Alpha"
                                            else -> "Release"
                                        },
                                        color = if (first) Color(0xFF3DDC84) else Blanc.dim, fontSize = 10.sp
                                    )
                                }
                                val pill = RoundedCornerShape(50)
                                Row(
                                    Modifier.height(36.dp).clip(pill)
                                        .then(if (have) Modifier.background(Color(0x338B5CF6)) else Modifier.background(Blanc.hero))
                                        .clickable(enabled = !installing) {
                                            if (have) {
                                                navigate(LauncherSection.LIBRARY)
                                            } else {
                                                Toast.makeText(context, "Installing ${v.id}...", Toast.LENGTH_SHORT).show()
                                                PojavCreateInstanceBridge.install(
                                                    context = activity, loader = AerixLoader.VANILLA, gameVersion = v.id,
                                                    loaderVersion = null, name = v.id, icon = null
                                                ) { PojavInstanceBridge.reload() }
                                            }
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!have) {
                                        Icon(painterResource(R.drawable.aerix_ic_download), null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(if (have) "Installed" else "Install", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                if (showArt) {
                    val shape = RoundedCornerShape(24.dp)
                    Box(
                        Modifier.width(210.dp).fillMaxHeight().clip(shape)
                            .background(Brush.linearGradient(listOf(Color(0xFF2B1470), Color(0xFF140B33), Color(0xFF0C0720))))
                            .border(BorderStroke(1.dp, Blanc.line), shape)
                    ) {
                        CubeArt(Modifier.fillMaxSize())
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text("Play the", color = Blanc.ivory, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("Latest Version!", color = Blanc.rose, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
