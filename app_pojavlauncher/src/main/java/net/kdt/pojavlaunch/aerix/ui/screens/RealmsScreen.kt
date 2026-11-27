package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.hosting.HostClient
import net.kdt.pojavlaunch.aerix.hosting.HostStatus
import net.kdt.pojavlaunch.aerix.hosting.ServerCatalog
import net.kdt.pojavlaunch.aerix.hosting.ServerSoftware
import net.kdt.pojavlaunch.aerix.multiplayer.MultiplayerService
import net.kdt.pojavlaunch.aerix.multiplayer.RoomCode
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.net.ServerSocket

private class Realm(val name: String, val slots: Int, val serverId: String, val version: String)

private fun loadRealms(c: Context): List<Realm> =
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).getString("list2", "").orEmpty()
        .split(";").mapNotNull { e -> e.split("|").takeIf { it.size == 4 }?.let { Realm(it[0], it[1].toIntOrNull() ?: 10, it[2], it[3]) } }

private fun saveRealms(c: Context, l: List<Realm>) {
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).edit()
        .putString("list2", l.joinToString(";") { it.name + "|" + it.slots + "|" + it.serverId + "|" + it.version }).apply()
}

private fun joinedRealms(c: Context): List<String> =
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).getString("joined", "").orEmpty().split(";").filter { it.isNotBlank() }

private fun rememberJoined(c: Context, a: String) {
    val l = (listOf(a) + joinedRealms(c).filter { it != a }).take(8)
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).edit().putString("joined", l.joinToString(";")).apply()
}

@Composable
fun RealmsScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf(HostStatus()) }
    var realms by remember { mutableStateOf(loadRealms(context)) }
    var joined by remember { mutableStateOf(joinedRealms(context)) }
    var name by remember { mutableStateOf("") }
    var slots by remember { mutableFloatStateOf(10f) }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { status = withContext(Dispatchers.IO) { HostClient.status(context) }; delay(1500) }
    }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_LONG).show()
    fun send(i: Intent) = ContextCompat.startForegroundService(context, i)
    val store = remember { HostClient.store(context) }

    fun turnOn(r: Realm) {
        scope.launch {
            val srv = withContext(Dispatchers.IO) { store.get(r.serverId) }
            if (srv == null) { toast("This realm's server was removed. Create it again."); return@launch }
            if (srv.installed) HostClient.host(context, srv.id) else { toast("Setting up the realm. First time takes a few minutes."); HostClient.install(context, srv.id) }
        }
    }
    fun create() {
        val n = name.trim()
        if (n.isEmpty()) { toast("Give your realm a name"); return }
        if (busy) return
        busy = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val all = ServerCatalog.versions(ServerSoftware.VANILLA)
                    val mine = PojavInstanceBridge.selected?.versionId
                    val v = if (mine != null && mine in all) mine else all.first()
                    val srv = store.create(n, ServerSoftware.VANILLA, v, true)
                    srv.maxPlayers = slots.toInt(); srv.onlineMode = false; srv.autoSleep = false; srv.tunnelEnabled = true
                    store.save(srv)
                    Realm(n, slots.toInt(), srv.id, v)
                }
            }
            busy = false
            res.onSuccess { r ->
                realms = listOf(r) + realms.filter { it.name != n }
                saveRealms(context, realms); name = ""
                HostClient.install(context, r.serverId)
                toast("Creating realm on Minecraft ${r.version}. Players need the same version.")
            }.onFailure { toast("Could not create: ${it.message}") }
        }
    }
    fun join(addr: String) {
        if (RoomCode.decode(addr) == null) { toast("Not a valid realm address. Example: myrealm-1A4S6.blanc"); return }
        val port = runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(25599)
        send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_JOIN)
            .putExtra(MultiplayerService.EXTRA_CODE, addr).putExtra(MultiplayerService.EXTRA_LOCAL_PORT, port))
        rememberJoined(context, addr); joined = joinedRealms(context)
        PojavLaunchBridge.play(activity, "127.0.0.1:$port")
    }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("BLANC REALMS", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text("Your own realm", color = Blanc.ivory, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("A real server on your phone. No LAN step: friends join and land straight in the world.", color = Blanc.dim, fontSize = 11.sp)
            }
            Card(Modifier.fillMaxWidth(), "CREATE REALM") {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Realm name") })
                Text("Player slots: ${slots.toInt()}", color = Blanc.ivory, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(slots, { slots = it }, valueRange = 2f..MultiplayerService.MAX_REALM_SLOTS.toFloat())
                Text("Uses the version of your selected instance. Offline accounts can join.", color = Blanc.dim, fontSize = 11.sp)
                Pill(if (busy) "CREATING..." else "CREATE REALM", true) { create() }
            }
            realms.forEach { r ->
                val mine = status.serverId == r.serverId
                val st = if (mine) status.state else HostStatus.OFF
                val port = if (mine) status.publicAddress.substringAfterLast(':', "").toIntOrNull() else null
                val addr = port?.let { RoomCode.address(r.name, RoomCode.encode(0, it, 0)) }
                Card(Modifier.fillMaxWidth(), r.name.uppercase()) {
                    Text("Minecraft ${r.version} · ${r.slots} slots · " + when (st) {
                        HostStatus.RUNNING, HostStatus.SLEEPING -> if (mine && status.players >= 0) "online ${status.players}/${r.slots}" else "online"
                        HostStatus.OFF -> "off"
                        else -> st.lowercase()
                    }, color = Blanc.dim, fontSize = 11.sp)
                    if (mine && st != HostStatus.OFF && addr == null) Text(status.message.ifBlank { "Getting your address..." }, color = Blanc.amber, fontSize = 11.sp)
                    if (addr != null) {
                        Text(addr, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Pill("COPY ADDRESS", false) {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Realm", addr)); toast("Copied")
                        }
                        Pill("PLAY IN MY REALM", true) { join(addr) }
                    }
                    if (st == HostStatus.OFF || !mine) Pill("TURN ON", true) { turnOn(r) }
                    else if (st == HostStatus.ERROR) Pill("RETRY", true) { turnOn(r) }
                    else Pill("TURN OFF", false) { HostClient.stopHosting(context, r.serverId) }
                    Pill("DELETE", false) {
                        scope.launch(Dispatchers.IO) { runCatching { HostClient.stopHosting(context, r.serverId); store.delete(r.serverId) } }
                        realms = realms.filter { it.serverId != r.serverId }; saveRealms(context, realms)
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), "JOIN A REALM") {
                OutlinedTextField(address, { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("myrealm-1A4S6.blanc") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                Pill("JOIN REALM", true) { join(address) }
                joined.forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Blanc.cardHi).clickable { join(a) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Blanc.hero), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 11.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(a, color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("JOIN", color = Blanc.violet, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
