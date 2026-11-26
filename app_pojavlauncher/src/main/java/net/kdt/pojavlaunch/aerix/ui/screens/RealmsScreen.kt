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
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.multiplayer.MultiplayerService
import net.kdt.pojavlaunch.aerix.multiplayer.RoomCode
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import java.io.File
import java.net.ServerSocket

private class Realm(val name: String, val slots: Int)

private fun loadRealms(c: Context): List<Realm> =
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).getString("list", "").orEmpty()
        .split(";").mapNotNull { e -> e.split("|").takeIf { it.size == 2 }?.let { Realm(it[0], it[1].toIntOrNull() ?: 10) } }

private fun saveRealms(c: Context, l: List<Realm>) {
    c.getSharedPreferences("blanc_realms", Context.MODE_PRIVATE).edit()
        .putString("list", l.joinToString(";") { it.name + "|" + it.slots }).apply()
}

@Composable
fun RealmsScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    var mp by remember { mutableStateOf(MpState()) }
    var realms by remember { mutableStateOf(loadRealms(context)) }
    var name by remember { mutableStateOf("") }
    var slots by remember { mutableFloatStateOf(10f) }
    var address by remember { mutableStateOf("") }
    var active by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) { mp = withContext(Dispatchers.IO) { readMp(context) }; delay(1000) }
    }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_LONG).show()
    fun send(i: Intent) = ContextCompat.startForegroundService(context, i)

    fun open(r: Realm) {
        if (PojavInstanceBridge.selected == null) { toast("Create or pick an instance first"); return }
        active = r.name
        val log = File(Tools.DIR_GAME_HOME, "latestlog.txt").absolutePath
        send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_HOST)
            .putExtra(MultiplayerService.EXTRA_LOG, log).putExtra(MultiplayerService.EXTRA_MAX, r.slots))
        PojavLaunchBridge.play(activity)
    }
    fun create() {
        val n = name.trim()
        if (n.isEmpty()) { toast("Give your realm a name"); return }
        val r = Realm(n, slots.toInt())
        realms = listOf(r) + realms.filter { it.name != n }
        saveRealms(context, realms); name = ""
        open(r)
    }
    fun join() {
        if (RoomCode.decode(address) == null) { toast("Not a valid realm address. Example: myrealm-1A4S6.blanc"); return }
        val port = runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(25599)
        send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_JOIN)
            .putExtra(MultiplayerService.EXTRA_CODE, address).putExtra(MultiplayerService.EXTRA_LOCAL_PORT, port))
        PojavLaunchBridge.play(activity, "127.0.0.1:$port")
    }

    val live = mp.mode == "host" && mp.state == "ONLINE" && mp.code.isNotEmpty()
    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("BLANC REALMS", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text("Your own realm", color = Blanc.ivory, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Name it, pick slots (up to ${MultiplayerService.MAX_REALM_SLOTS}), share the address.", color = Blanc.dim, fontSize = 11.sp)
            }
            if (live) {
                val addr = RoomCode.address(active.ifEmpty { "realm" }, mp.code)
                Card(Modifier.fillMaxWidth(), "REALM ONLINE") {
                    Text(addr, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("${mp.players}/${mp.max} players", color = Blanc.dim, fontSize = 11.sp)
                    Pill("COPY ADDRESS", true) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Realm", addr)); toast("Copied")
                    }
                    Pill("CLOSE REALM", false) { send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_STOP)) }
                }
            }
            Card(Modifier.fillMaxWidth(), "CREATE REALM") {
                Text("Then open your world: Esc > Open to LAN > Start LAN World.", color = Blanc.dim, fontSize = 11.sp)
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Realm name") })
                Text("Player slots: ${slots.toInt()}", color = Blanc.ivory, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(slots, { slots = it }, valueRange = 2f..MultiplayerService.MAX_REALM_SLOTS.toFloat())
                if (mp.state == "ERROR") Text(mp.message, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                Pill("CREATE & OPEN", true) { create() }
            }
            if (realms.isNotEmpty()) Card(Modifier.fillMaxWidth(), "MY REALMS") {
                realms.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Blanc.cardHi).clickable { open(r) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Blanc.hero), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 11.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text(r.name, color = Blanc.ivory, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.slots} slots", color = Blanc.dim, fontSize = 11.sp)
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), "JOIN A REALM") {
                OutlinedTextField(address, { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("myrealm-1A4S6.blanc") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                Pill("JOIN", true) { join() }
            }
        }
    }
}
