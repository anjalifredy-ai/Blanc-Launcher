package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.hosting.HostClient
import net.kdt.pojavlaunch.aerix.hosting.HostStatus
import net.kdt.pojavlaunch.aerix.hosting.HostedServer
import net.kdt.pojavlaunch.aerix.hosting.Modrinth
import net.kdt.pojavlaunch.aerix.hosting.ServerCatalog
import net.kdt.pojavlaunch.aerix.hosting.ServerFiles
import net.kdt.pojavlaunch.aerix.hosting.ServerSoftware
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** Host your own Minecraft servers on this device: sleeps when empty, wakes when someone joins. */
@Composable
fun HostScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val store = remember { HostClient.store(context) }
    var servers by remember { mutableStateOf(emptyList<HostedServer>()) }
    var status by remember { mutableStateOf(HostStatus()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var logoRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val (list, current) = withContext(Dispatchers.IO) { store.list() to HostClient.status(context) }
            servers = list
            status = current
            delay(1200)
        }
    }

    val open = servers.firstOrNull { it.id == openId }
    if (openId != null && open != null) {
        HostDetail(open, status, logoRevision, onLogoChanged = { logoRevision++ }, onBack = { openId = null })
        return
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x447C5CFF), Color.Transparent), radius = 800f))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HOST", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Your servers", color = Blanc.ivory, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Row(
                    Modifier.height(42.dp).clip(RoundedCornerShape(50)).background(Blanc.hero).clickable { creating = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.aerix_ic_add), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("NEW SERVER", color = Color.White, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                "Sleeps when empty, wakes when a player joins. Friends join via a free tunnel address. Keep your phone on and connected.",
                color = Blanc.dim, fontSize = 11.sp
            )
            if (servers.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Blanc.hero).clickable { creating = true }.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No servers yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Paper, Purpur, Fabric, Quilt, Forge, NeoForge or Vanilla. Pick a version and the launcher downloads everything.", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    Text("CREATE YOUR FIRST SERVER  \u2192", color = Color.White, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    items(servers, key = { it.id }) { s ->
                        ServerRow(s, if (status.serverId == s.id) status else null, logoRevision) { openId = s.id }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateServerDialog(
            store = store,
            onDismiss = { creating = false },
            onCreated = { s ->
                creating = false
                HostClient.install(context, s.id)
                openId = s.id
            }
        )
    }
}

private fun stateLabel(server: HostedServer, status: HostStatus?): Pair<String, Color> = when {
    status == null || status.state == HostStatus.OFF ->
        if (server.installed) "Offline" to Color(0xFF8A99A6) else "Not installed" to Color(0xFFE0A84F)
    status.state == HostStatus.RUNNING -> "Online" to Color(0xFF4CD964)
    status.state == HostStatus.SLEEPING -> "Sleeping" to Color(0xFF6CB8FF)
    status.state == HostStatus.STARTING -> "Starting" to Color(0xFFE0A84F)
    status.state == HostStatus.STOPPING -> "Stopping" to Color(0xFFE0A84F)
    status.state == HostStatus.INSTALLING -> "Installing" to Color(0xFFE0A84F)
    else -> "Problem" to Color(0xFFFF6B6B)
}

@Composable
private fun LogoImage(id: String, revision: Int, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(id, revision) {
        runCatching { BitmapFactory.decodeFile(HostClient.store(context).logoFile(id).absolutePath) }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(AerixSurface.panelRaised), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.aerix_ic_terminal_outlined), null, tint = AerixSurface.textSecondary)
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(6.dp))
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ServerRow(server: HostedServer, status: HostStatus?, logoRevision: Int, onClick: () -> Unit) {
    val (label, color) = stateLabel(server, status)
    Surface(shape = RoundedCornerShape(22.dp), color = Blanc.card, border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f)), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoImage(server.id, logoRevision, 52)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(server.name, color = Blanc.ivory, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.software.displayName} ${server.mcVersion}", color = AerixSurface.textSecondary, fontSize = 12.sp)
                val detail = status?.message?.takeIf { it.isNotBlank() && status.state != HostStatus.RUNNING }
                if (detail != null) Text(detail, color = AerixSurface.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else if (status != null && status.players >= 0) Text("${status.players}/${status.maxPlayers} players", color = AerixSurface.textMuted, fontSize = 11.sp)
            }
            StatusChip(label, color)
        }
    }
}

// ------------------------------------------------------------------ create dialog

@Composable
private fun CreateServerDialog(store: net.kdt.pojavlaunch.aerix.hosting.ServerStore, onDismiss: () -> Unit, onCreated: (HostedServer) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("My Server") }
    var software by remember { mutableStateOf(ServerSoftware.PAPER) }
    var versions by remember { mutableStateOf<List<String>?>(null) }
    var versionError by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var eula by remember { mutableStateOf(false) }
    var softwareMenu by remember { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(software, retry) {
        versions = null; versionError = null; version = null
        val result = withContext(Dispatchers.IO) { runCatching { ServerCatalog.versions(software) } }
        versions = result.getOrNull()
        versionError = result.exceptionOrNull()?.message ?: if (result.isFailure) "Could not load versions" else null
        version = versions?.firstOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New server") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { softwareMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Software: ${software.displayName}") }
                    DropdownMenu(softwareMenu, { softwareMenu = false }) {
                        ServerSoftware.values().forEach { s ->
                            DropdownMenuItem(text = { Column { Text(s.displayName); Text(s.blurb, fontSize = 11.sp) } }, onClick = { software = s; softwareMenu = false })
                        }
                    }
                }
                Text(software.blurb, fontSize = 11.sp, color = AerixSurface.textSecondary)
                when {
                    versions != null -> Box {
                        OutlinedButton(onClick = { versionMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Version: ${version ?: "-"}") }
                        DropdownMenu(versionMenu, { versionMenu = false }) {
                            versions!!.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { version = v; versionMenu = false }) }
                        }
                    }
                    versionError != null -> Column {
                        Text(versionError!!, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                        TextButton(onClick = { retry++ }) { Text("Try again") }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("Loading versions...", fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(eula, { eula = it })
                    Text("I accept the Minecraft EULA (aka.ms/MinecraftEULA)", fontSize = 12.sp, modifier = Modifier.clickable { eula = !eula })
                }
                if (software.needsInstaller) {
                    Text("${software.displayName} runs an installer first. That takes a few minutes.", fontSize = 11.sp, color = AerixSurface.textMuted)
                }
            }
        },
        confirmButton = {
            Button(enabled = eula && version != null && name.isNotBlank(), onClick = {
                runCatching { store.create(name, software, version!!, true) }
                    .onSuccess(onCreated)
                    .onFailure { Toast.makeText(context, "Could not create: ${it.message}", Toast.LENGTH_LONG).show() }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --------------------------------------------------------------------- detail

@Composable
private fun HostDetail(initial: HostedServer, globalStatus: HostStatus, logoRevision: Int, onLogoChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { HostClient.store(context) }
    var server by remember(initial.id) { mutableStateOf(store.get(initial.id) ?: initial) }
    // The host process may have finished installing, so the stored copy is the truth for "installed".
    LaunchedEffect(globalStatus.state, globalStatus.serverId) {
        withContext(Dispatchers.IO) { store.get(initial.id) }?.let { server = server.also { s -> s.installed = it.installed } }
    }
    val mine = globalStatus.serverId == server.id
    val status = if (mine) globalStatus else HostStatus()
    val otherBusy = !mine && globalStatus.state != HostStatus.OFF && globalStatus.serverId.isNotEmpty()
    val state = status.state
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val (label, color) = stateLabel(server, status)

    val infoBlock: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoImage(server.id, logoRevision, 44)
            StatusChip(label, color)
            if (state == HostStatus.RUNNING && status.players >= 0) Text("${status.players}/${status.maxPlayers} players", color = AerixSurface.textSecondary, fontSize = 12.sp)
        }
        if (status.message.isNotBlank() && state != HostStatus.RUNNING) {
            Text(status.message, color = if (state == HostStatus.ERROR) Color(0xFFFF6B6B) else AerixSurface.textSecondary, fontSize = 12.sp)
        }
        if (otherBusy) Text("Another server is using the host right now. Stop it first.", color = Color(0xFFE0A84F), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !server.installed && state != HostStatus.INSTALLING ->
                    Button(enabled = !otherBusy, onClick = { HostClient.install(context, server.id) }) { Text("Install") }
                state == HostStatus.OFF || state == HostStatus.ERROR -> {
                    Button(enabled = !otherBusy && server.installed, onClick = { HostClient.startNow(context, server.id) }) { Text("Start") }
                    OutlinedButton(enabled = !otherBusy && server.installed, onClick = { HostClient.host(context, server.id) }) { Text("Host") }
                }
                state == HostStatus.SLEEPING -> {
                    Button(onClick = { HostClient.startNow(context, server.id) }) { Text("Wake now") }
                    OutlinedButton(onClick = { HostClient.stopHosting(context, server.id) }) { Text("Stop hosting") }
                }
                state == HostStatus.STARTING || state == HostStatus.RUNNING -> {
                    Button(onClick = { HostClient.stopServer(context, server.id) }) { Text("Stop server") }
                    OutlinedButton(onClick = { HostClient.stopHosting(context, server.id) }) { Text("Stop hosting") }
                }
                state == HostStatus.STOPPING -> Text("Stopping...", color = AerixSurface.textSecondary)
                else -> {}
            }
        }
        SettingSwitch(
            "Online mode",
            if (server.onlineMode) "ON: only paid Minecraft accounts can join." else "OFF: offline / cracked players can join.",
            server.onlineMode
        ) { v ->
            val latest = store.get(server.id) ?: server
            latest.onlineMode = v
            runCatching { store.save(latest) }
                .onSuccess {
                    server = latest
                    if (state == HostStatus.SLEEPING) HostClient.host(context, latest.id)
                    Toast.makeText(context, if (state == HostStatus.RUNNING || state == HostStatus.STARTING) "Saved. Restart the server to apply." else "Saved", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
        OutlinedButton(onClick = {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = "State: ${status.state}\nMessage: ${status.message}\n\n" + HostClient.consoleTail(context, server.id, 24 * 1024)
            cb.setPrimaryClip(ClipData.newPlainText("Server diagnostics", text))
            Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy diagnostics", fontSize = 12.sp) }
        Text(
            if (server.autoSleep) "Sleeps after ${server.idleMinutes} min with nobody online and wakes when someone joins. Change this in Settings."
            else "Auto sleep is off. Turn it on in Settings.",
            color = AerixSurface.textMuted, fontSize = 11.sp
        )
        AddressCard(server, status)
    }
    val tabHeader: @Composable () -> Unit = {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Console", fontSize = 12.sp, maxLines = 1) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Files", fontSize = 12.sp, maxLines = 1) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(if (Modrinth.usesPlugins(server.software)) "Plugins" else "Mods", fontSize = 12.sp, maxLines = 1) })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Settings", fontSize = 12.sp, maxLines = 1) })
        }
    }
    val tabBody: @Composable (Modifier) -> Unit = { mod ->
        if (tab == 0) {
            ConsoleTab(server, status, mod)
        } else if (tab == 1) {
            FilesTab(server, mod)
        } else if (tab == 2) {
            AddonsTab(server, mod)
        } else {
            SettingsTab(server, state, logoRevision, onLogoChanged, onSaved = { server = it }, onDelete = { confirmDelete = true }, modifier = mod)
        }
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x337C5CFF), Color.Transparent), radius = 800f))) {
    AerixPage(title = server.name, subtitle = "${server.software.displayName} ${server.mcVersion}", trailing = { AerixGhostButton("Back", R.drawable.aerix_ic_close, onBack) }) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The launcher is usually landscape: controls on the left, console/settings get the full height on the right.
            val boxWidth = maxWidth
            if (maxWidth > 520.dp && maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.width(boxWidth * 0.36f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        infoBlock()
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabHeader()
                        tabBody(Modifier.weight(1f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(0.45f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        infoBlock()
                    }
                    tabHeader()
                    tabBody(Modifier.weight(1f))
                }
            }
        }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${server.name}?") },
            text = { Text("The world and all files of this server are deleted from this device. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    if (state != HostStatus.OFF) HostClient.stopHosting(context, server.id)
                    runCatching { store.delete(server.id) }
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddressCard(server: HostedServer, status: HostStatus) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(14.dp), color = Blanc.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Join address", color = AerixSurface.textMuted, fontSize = 11.sp)
            val shared = status.publicAddress
            if (shared.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shared, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Minecraft server address", shared))
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
            } else {
                Text(
                    if (!server.tunnelEnabled) "Tunnel is off. Only this device can join: localhost:${server.frontPort}"
                    else if (status.state == HostStatus.OFF) "Press Start or Host to get a public address"
                    else status.tunnel.ifBlank { "Connecting the tunnel..." },
                    color = AerixSurface.textSecondary, fontSize = 12.sp
                )
            }
            if (status.tunnel.isNotBlank() && shared.isNotEmpty()) Text("Tunnel: ${status.tunnel}", color = AerixSurface.textMuted, fontSize = 10.sp)
            Text("On this device: localhost:${server.frontPort}", color = AerixSurface.textMuted, fontSize = 10.sp)
        }
    }
}

private fun colorFor(line: String): Color = when {
    line.startsWith(">") -> Color(0xFF7FD6FF)
    line.startsWith("[Aerix]") -> Color(0xFFB7A9FF)
    line.contains("ERROR", true) || line.contains("Exception") || line.contains("FATAL") -> Color(0xFFFF7B7B)
    line.contains("WARN", true) -> Color(0xFFE8C46A)
    line.contains("Done (") || line.contains("joined the game") -> Color(0xFF7DE58F)
    else -> Color(0xFFCFE3D6)
}

@Composable
private fun ConsoleTab(server: HostedServer, status: HostStatus, modifier: Modifier) {
    val context = LocalContext.current
    val state = status.state
    var raw by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(server.id) {
        while (true) {
            raw = withContext(Dispatchers.IO) { HostClient.consoleTail(context, server.id, 64 * 1024) }
            delay(800)
        }
    }
    val lines = remember(raw) { if (raw.isEmpty()) emptyList() else raw.trimEnd('\n').split('\n').takeLast(600) }
    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    val canSend = state == HostStatus.RUNNING || state == HostStatus.STARTING
    fun send(text: String) {
        val t = text.trim().removePrefix("/")
        if (t.isNotEmpty()) HostClient.command(context, server.id, t)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${lines.size} lines", color = AerixSurface.textMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text("Auto-scroll", fontSize = 11.sp) })
            TextButton(onClick = {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Server console", raw))
                Toast.makeText(context, "Console copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy", fontSize = 12.sp) }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF06070B), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        if (status.message.isNotBlank()) status.message else "The console shows the server's output once it starts.",
                        color = AerixSurface.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    items(lines.size) { i ->
                        val line = lines[i]
                        Text(line, color = colorFor(line), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("list", "tps", "save-all", "difficulty").forEach { c ->
                AssistChip(onClick = { if (c == "difficulty") input = "difficulty " else send(c) }, enabled = canSend, label = { Text(c, fontSize = 11.sp) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                input, { input = it }, enabled = canSend, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(if (canSend) "Type a command (op Steve, say hi, stop)" else "Start the server to send commands") },
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                    if (canSend && input.isNotBlank()) { send(input); input = "" }
                })
            )
            Button(enabled = canSend && input.isNotBlank(), onClick = { send(input); input = "" }) { Text("Send") }
        }
    }
}

@Composable
private fun FilesTab(server: HostedServer, modifier: Modifier) {
    val context = LocalContext.current
    val root = remember(server.id) { HostClient.store(context).dir(server.id) }
    val scope = rememberCoroutineScope()
    var dir by rememberSaveable(server.id) { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ServerFiles.Entry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    LaunchedEffect(dir, refresh) {
        val result = withContext(Dispatchers.IO) { runCatching { ServerFiles.list(root, dir) } }
        entries = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
    }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var name = "upload"
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
                    }
                    context.contentResolver.openInputStream(uri)!!.use { ServerFiles.importStream(root, dir, name, it) }
                    name
                }
            }
            result.onSuccess { toast("Added $it"); refresh++ }.onFailure { toast("Could not add the file: ${it.message}") }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("/" + dir, color = AerixSurface.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dir.isNotEmpty()) OutlinedButton(onClick = { dir = ServerFiles.parent(dir) }) { Text("Up", fontSize = 12.sp) }
            OutlinedButton(onClick = { picker.launch("*/*") }) { Text("Upload", fontSize = 12.sp) }
            OutlinedButton(onClick = { folderName = ""; newFolder = true }) { Text("New folder", fontSize = 12.sp) }
            OutlinedButton(onClick = { refresh++ }) { Text("Refresh", fontSize = 12.sp) }
        }
        if (error != null) Text(error!!, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        Surface(shape = RoundedCornerShape(12.dp), color = Blanc.card, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(14.dp)) { Text("Nothing here yet. Start the server once to create its files.", color = AerixSurface.textMuted, fontSize = 12.sp) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(entries, key = { it.name }) { e ->
                        val path = ServerFiles.join(dir, e.name)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                when {
                                    e.directory -> dir = path
                                    ServerFiles.isEditable(e.name, e.size) -> scope.launch {
                                        val r = withContext(Dispatchers.IO) { runCatching { ServerFiles.readText(root, path) } }
                                        r.onSuccess { editText = it; editing = path }.onFailure { toast("Could not open: ${it.message}") }
                                    }
                                    else -> toast("${e.name} cannot be edited here (${e.size / 1024} KB)")
                                }
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text((if (e.directory) "> " else "") + e.name, color = AerixSurface.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!e.directory) Text(if (e.size >= 1024 * 1024) "${e.size / (1024 * 1024)} MB" else "${e.size / 1024} KB", color = AerixSurface.textMuted, fontSize = 10.sp)
                            }
                            TextButton(onClick = { deleting = path }) { Text("Delete", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { path ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(path.substringAfterLast('/'), fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    editText, { editText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val r = runCatching { ServerFiles.writeText(root, path, editText) }
                    r.onSuccess { toast("Saved. Restart the server to apply."); editing = null; refresh++ }.onFailure { toast("Could not save: ${it.message}") }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { path ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${path.substringAfterLast('/')}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    runCatching { ServerFiles.delete(root, path) }.onFailure { toast("Could not delete: ${it.message}") }
                    deleting = null; refresh++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (newFolder) {
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("New folder") },
            text = { OutlinedTextField(folderName, { folderName = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                Button(onClick = {
                    runCatching { ServerFiles.mkdir(root, dir, folderName) }.onFailure { toast("Could not create: ${it.message}") }
                    newFolder = false; refresh++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddonsTab(server: HostedServer, modifier: Modifier) {
    val context = LocalContext.current
    val software = server.software
    val kind = if (Modrinth.usesPlugins(software)) "plugin" else "mod"
    if (!Modrinth.supported(software)) {
        Column(modifier.fillMaxWidth()) {
            Text("Vanilla servers cannot load plugins or mods. Create a Paper server for plugins, or Fabric / NeoForge / Forge / Quilt for mods.", color = AerixSurface.textSecondary, fontSize = 12.sp)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val contentDir = remember(server.id) { ServerFiles.contentDir(HostClient.store(context).dir(server.id), software) }
    var query by remember { mutableStateOf("") }
    var searchKey by remember { mutableIntStateOf(0) }
    var hits by remember { mutableStateOf<List<Modrinth.Hit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(emptyList<String>()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchKey) {
        hits = null; error = null
        val r = withContext(Dispatchers.IO) { runCatching { Modrinth.search(software, server.mcVersion, query) } }
        hits = r.getOrNull() ?: emptyList()
        error = r.exceptionOrNull()?.let { "Could not load the list: ${it.message}" }
    }
    LaunchedEffect(refresh) {
        installed = withContext(Dispatchers.IO) { contentDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.map { it.name }?.sorted() ?: emptyList() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Search ${kind}s for ${server.mcVersion}") },
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searchKey++ })
            )
            Button(onClick = { searchKey++ }) { Text("Search") }
        }
        if (installed.isNotEmpty()) {
            Text("Installed (${installed.size}) - restart the server to apply changes", color = AerixSurface.textMuted, fontSize = 11.sp)
            LazyColumn(Modifier.heightIn(max = 96.dp).fillMaxWidth()) {
                items(installed, key = { it }) { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = AerixSurface.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            runCatching { java.io.File(contentDir, name).delete() }
                            refresh++
                        }) { Text("Remove", fontSize = 11.sp) }
                    }
                }
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        if (hits == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Loading...", fontSize = 12.sp)
            }
        } else if (hits!!.isEmpty() && error == null) {
            Text("Nothing found for ${server.mcVersion}. Try another search.", color = AerixSurface.textSecondary, fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits ?: emptyList(), key = { it.id }) { hit ->
                Surface(shape = RoundedCornerShape(12.dp), color = Blanc.card, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(hit.title, color = AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${hit.author} - ${hit.downloads} downloads", color = AerixSurface.textMuted, fontSize = 10.sp)
                            Text(hit.description, color = AerixSurface.textSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Button(enabled = installingId == null, onClick = {
                            installingId = hit.id
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { Modrinth.install(contentDir, software, server.mcVersion, hit.id) } }
                                installingId = null
                                r.onSuccess {
                                    Toast.makeText(context, if (it.isEmpty()) "Already installed" else "Installed ${it.joinToString()}. Restart the server.", Toast.LENGTH_LONG).show()
                                    refresh++
                                }.onFailure { Toast.makeText(context, "Install failed: ${it.message}", Toast.LENGTH_LONG).show() }
                            }
                        }) { Text(if (installingId == hit.id) "..." else "Install", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    server: HostedServer, state: String, logoRevision: Int, onLogoChanged: () -> Unit,
    onSaved: (HostedServer) -> Unit, onDelete: () -> Unit, modifier: Modifier
) {
    val context = LocalContext.current
    val store = remember { HostClient.store(context) }
    var name by remember(server.id) { mutableStateOf(server.name) }
    var description by remember(server.id) { mutableStateOf(server.description) }
    var onlineMode by remember(server.id, server.onlineMode) { mutableStateOf(server.onlineMode) }
    var maxPlayers by remember(server.id) { mutableStateOf(server.maxPlayers.toString()) }
    var ram by remember(server.id) { mutableStateOf(server.ramMb.toString()) }
    var gamemode by remember(server.id) { mutableStateOf(server.gamemode) }
    var difficulty by remember(server.id) { mutableStateOf(server.difficulty) }
    var pvp by remember(server.id) { mutableStateOf(server.pvp) }
    var whitelist by remember(server.id) { mutableStateOf(server.whitelist) }
    var allowFlight by remember(server.id) { mutableStateOf(server.allowFlight) }
    var commandBlocks by remember(server.id) { mutableStateOf(server.commandBlocks) }
    var viewDistance by remember(server.id) { mutableStateOf(server.viewDistance.toString()) }
    var seed by remember(server.id) { mutableStateOf(server.seed) }
    var preset by remember(server.id) { mutableStateOf(server.performancePreset) }
    var autoSleep by remember(server.id) { mutableStateOf(server.autoSleep) }
    var idle by remember(server.id) { mutableStateOf(server.idleMinutes.toString()) }
    var tunnel by remember(server.id) { mutableStateOf(server.tunnelEnabled) }
    var relay by remember(server.id) { mutableStateOf(server.relayHost) }
    var relaySecret by remember(server.id) { mutableStateOf(server.relaySecret) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val source = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val icon = Bitmap.createScaledBitmap(square, 64, 64, true)
            val out = java.io.ByteArrayOutputStream()
            icon.compress(Bitmap.CompressFormat.PNG, 100, out)
            store.saveLogo(server.id, out.toByteArray())
        }.onSuccess { onLogoChanged(); Toast.makeText(context, "Logo saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Could not use that image", Toast.LENGTH_LONG).show() }
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoImage(server.id, logoRevision, 64)
            OutlinedButton(onClick = { logoPicker.launch("image/*") }) { Text("Change logo") }
        }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Server name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it.take(120) }, label = { Text("Description (shown in the server list)") }, modifier = Modifier.fillMaxWidth())
        SettingSwitch("Online mode (premium accounts only)", "Turn off to let cracked / offline accounts join. Anyone can then use any name.", onlineMode) { onlineMode = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Max players", maxPlayers, Modifier.weight(1f)) { maxPlayers = it }
            NumberField("RAM (MB)", ram, Modifier.weight(1f)) { ram = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("Gamemode", gamemode, listOf("survival", "creative", "adventure", "spectator"), Modifier.weight(1f)) { gamemode = it }
            ChoiceButton("Difficulty", difficulty, listOf("peaceful", "easy", "normal", "hard"), Modifier.weight(1f)) { difficulty = it }
        }
        SettingSwitch("PvP", null, pvp) { pvp = it }
        SettingSwitch("Whitelist", "Only players you add with /whitelist add can join.", whitelist) { whitelist = it }
        SettingSwitch("Allow flight", null, allowFlight) { allowFlight = it }
        SettingSwitch("Command blocks", null, commandBlocks) { commandBlocks = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("View distance", viewDistance, Modifier.weight(1f)) { viewDistance = it }
            OutlinedTextField(seed, { seed = it.take(40) }, label = { Text("World seed") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        SettingSwitch("Phone performance preset", "Lighter mob and entity settings for Paper (applied at every start). Turn off to edit those values yourself.", preset) { preset = it }
        SettingSwitch("Sleep when empty", "Stops the server when nobody is online. It starts again when a player joins.", autoSleep) { autoSleep = it }
        if (autoSleep) NumberField("Minutes empty before sleeping", idle, Modifier.fillMaxWidth()) { idle = it }
        SettingSwitch("Public tunnel", "Gives friends an address to join without port forwarding.", tunnel) { tunnel = it }
        if (tunnel) OutlinedTextField(relay, { relay = it.trim() }, label = { Text("Tunnel relay (bore server, e.g. your Mumbai VPS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (tunnel) OutlinedTextField(relaySecret, { relaySecret = it.trim() }, label = { Text("Relay secret (only for a private relay)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Changes to most settings apply the next time the server starts.", color = AerixSurface.textMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val updated = (store.get(server.id) ?: server).also {
                    it.name = name.trim().ifEmpty { "My Server" }
                    it.description = description
                    it.onlineMode = onlineMode
                    it.maxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 500) ?: it.maxPlayers
                    it.ramMb = ram.toIntOrNull()?.coerceIn(512, 16384) ?: it.ramMb
                    it.gamemode = gamemode
                    it.difficulty = difficulty
                    it.pvp = pvp
                    it.whitelist = whitelist
                    it.allowFlight = allowFlight
                    it.commandBlocks = commandBlocks
                    it.viewDistance = viewDistance.toIntOrNull()?.coerceIn(2, 32) ?: it.viewDistance
                    it.seed = seed
                    it.performancePreset = preset
                    it.autoSleep = autoSleep
                    it.idleMinutes = idle.toIntOrNull()?.coerceIn(1, 240) ?: it.idleMinutes
                    it.tunnelEnabled = tunnel
                    it.relayHost = relay.ifEmpty { "bore.pub" }
                    it.relaySecret = relaySecret
                }
                runCatching { store.save(updated) }
                    .onSuccess {
                        onSaved(updated)
                        // A sleeping host reloads the list entry text and tunnel straight away.
                        if (state == HostStatus.SLEEPING) HostClient.host(context, updated.id)
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() }
            }) { Text("Save settings") }
            OutlinedButton(onClick = onDelete) { Text("Delete server") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AerixSurface.textPrimary, fontSize = 13.sp)
            if (hint != null) Text(hint, color = AerixSurface.textMuted, fontSize = 11.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ChoiceButton(label: String, value: String, options: List<String>, modifier: Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value", maxLines = 1) }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}
