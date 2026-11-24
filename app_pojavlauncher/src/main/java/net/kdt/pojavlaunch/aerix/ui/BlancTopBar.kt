package net.kdt.pojavlaunch.aerix.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.guide.AerixGuideState
import net.kdt.pojavlaunch.aerix.guide.guideAnchor
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private data class BlancTab(val section: LauncherSection, val label: String, val icon: Int, val guideKey: String? = null)

/**
 * Blanc Launcher top navigation: brand on the left, scrollable pill tabs in the middle,
 * quick actions (More, Settings, Account) on the right. Replaces both side rails.
 */
@Composable
fun BlancTopBar(
    selectedSection: LauncherSection,
    onNavigate: (LauncherSection) -> Unit,
    onCreateInstance: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AerixThemeManager.currentAccent()
    val account = PojavAccountBridge.current
    val tabs = listOf(
        BlancTab(LauncherSection.HOME, "Home", R.drawable.aerix_ic_home_filled),
        BlancTab(LauncherSection.LIBRARY, "Library", R.drawable.aerix_ic_dashboard_filled, AerixGuideState.LIBRARY),
        BlancTab(LauncherSection.MULTIPLAYER, "Multiplayer", R.drawable.aerix_ic_sports_esports_outlined),
        BlancTab(LauncherSection.HOST, "Host", R.drawable.aerix_ic_terminal_outlined),
        BlancTab(LauncherSection.SERVERS, "Servers", R.drawable.aerix_ic_public),
        BlancTab(LauncherSection.DISCOVER, "Discover", R.drawable.aerix_ic_public),
        BlancTab(LauncherSection.MODPACKS, "Modpacks", R.drawable.aerix_ic_archive_filled, AerixGuideState.MODPACKS)
    )
    fun isSelected(tab: LauncherSection): Boolean = when (tab) {
        LauncherSection.LIBRARY -> selectedSection == LauncherSection.LIBRARY || selectedSection == LauncherSection.INSTANCE_EDITOR ||
            selectedSection == LauncherSection.CONTENT || selectedSection == LauncherSection.CREATE_INSTANCE ||
            selectedSection == LauncherSection.INSTANCE_SELECTOR
        LauncherSection.DISCOVER -> selectedSection == LauncherSection.DISCOVER || selectedSection == LauncherSection.DOWNLOADS
        else -> selectedSection == tab
    }
    val barShape = RoundedCornerShape(26.dp)
    var moreOpen by remember { mutableStateOf(false) }
    val accountSelected = selectedSection == LauncherSection.ACCOUNTS || selectedSection == LauncherSection.ADD_ACCOUNT || selectedSection == LauncherSection.OFFLINE_ACCOUNT

    // A slim floating pill, icons only, centred at the top.
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(barShape)
                .background(Color(0xE60C0D14))
                .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0x40FFFFFF), accent.copy(alpha = 0.35f), Color(0x18FFFFFF)))), barShape)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button) { onNavigate(LauncherSection.HOME) }
                    .semantics { contentDescription = "Blanc Home" },
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(R.drawable.ic_aerix_mark), contentDescription = "Blanc", tint = Color.Unspecified, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.width(1.dp).height(18.dp).background(Color(0x30FFFFFF)))
            tabs.forEach { tab ->
                TopTab(tab.label, tab.icon, isSelected(tab.section), accent, tab.guideKey) { onNavigate(tab.section) }
            }
            Box(Modifier.width(1.dp).height(18.dp).background(Color(0x30FFFFFF)))
            IconAction(R.drawable.aerix_ic_add, "Create instance", false, accent, onCreateInstance, AerixGuideState.CREATE)
            Box {
                IconAction(R.drawable.aerix_ic_build_outlined, "More", moreOpen, accent, { moreOpen = true })
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    val items = listOf(
                        "Aerix Client" to LauncherSection.AERIX_CLIENT,
                        "Quick tools" to LauncherSection.QUICK_TOOLS,
                        "Instances" to LauncherSection.INSTANCE_SELECTOR,
                        "Files" to LauncherSection.FILES,
                        "Logs" to LauncherSection.LOGS,
                        "New releases" to LauncherSection.OTHER_RELEASES,
                        "Team" to LauncherSection.CONTRIBUTION
                    )
                    items.forEach { (label, target) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { moreOpen = false; onNavigate(target) })
                    }
                    DropdownMenuItem(text = { Text("Profile") }, onClick = { moreOpen = false; onOpenProfile() })
                }
            }
            IconAction(
                R.drawable.aerix_ic_settings_filled, "Settings",
                selectedSection == LauncherSection.SETTINGS || selectedSection == LauncherSection.ABOUT || selectedSection == LauncherSection.LOGS,
                accent, { onNavigate(LauncherSection.SETTINGS) }, AerixGuideState.SETTINGS
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(if (accountSelected) 2.dp else 1.dp, if (accountSelected) accent else AerixSurface.borderSoft), CircleShape)
                    .clickable(role = Role.Button) { onNavigate(LauncherSection.ACCOUNTS) }
                    .semantics { contentDescription = account?.username ?: "Accounts" },
                contentAlignment = Alignment.Center
            ) {
                AerixImageWell(image = account?.face, fallback = R.drawable.aerix_head_steve, size = 26.dp, radius = 13.dp)
            }
        }
    }
}

@Composable
private fun TopTab(label: String, icon: Int, selected: Boolean, accent: Color, guideKey: String?, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent, tween(160), label = "tabBg")
    val fg by animateColorAsState(if (selected) accent else AerixSurface.textSecondary, tween(160), label = "tabFg")
    Box(
        modifier = Modifier
            .then(if (guideKey != null) Modifier.guideAnchor(guideKey) else Modifier)
            .size(width = 40.dp, height = 34.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painterResource(icon), contentDescription = label, tint = fg, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun IconAction(icon: Int, label: String, selected: Boolean, accent: Color, onClick: () -> Unit, guideKey: String? = null) {
    Box(
        modifier = Modifier
            .then(if (guideKey != null) Modifier.guideAnchor(guideKey) else Modifier)
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painterResource(icon), contentDescription = label, tint = if (selected) accent else AerixSurface.textSecondary, modifier = Modifier.size(19.dp))
    }
}
