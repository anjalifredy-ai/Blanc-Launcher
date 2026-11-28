package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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

internal class MpState(val mode: String = "", val state: String = "OFF", val code: String = "", val players: Int = 0, val max: Int = 10, val message: String = "")

internal fun readMp(context: Context): MpState {
    val f = MultiplayerService.stateFile(context)
    if (!f.isFile) return MpState()
    val m = f.readLines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
    return MpState(m["mode"] ?: "", m["state"] ?: "OFF", m["code"] ?: "", m["players"]?.toIntOrNull() ?: 0, m["max"]?.toIntOrNull() ?: 10, m["message"] ?: "")
}

private fun recents(context: Context): List<String> =
    context.getSharedPreferences("blanc_mp", Context.MODE_PRIVATE).getString("recent", "")!!.split(";").filter { it.isNotBlank() }

private fun rememberWorld(context: Context, code: String) {
    val list = (listOf(code) + recents(context).filter { it != code }).take(6)
    context.getSharedPreferences("blanc_mp", Context.MODE_PRIVATE).edit().putString("recent", list.joinToString(";")).apply()
}

@Composable
fun MultiplayerScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    var mp by remember { mutableStateOf(MpState()) }
    var recent by remember { mutableStateOf(recents(context)) }
    var codeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            mp = withContext(Dispatchers.IO) { readMp(context) }
            delay(1000)
        }
    }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected

    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_LONG).show()
    fun send(intent: Intent) = ContextCompat.startForegroundService(context, intent)

    fun host() {
        if (selected == null) { toast("Create or pick an instance first"); return }
        val log = File(Tools.DIR_GAME_HOME, "latestlog.txt").absolutePath
        send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_HOST).putExtra(MultiplayerService.EXTRA_LOG, log))
        PojavLaunchBridge.play(activity)
    }

    fun join(code: String) {
        if (RoomCode.decode(code) == null) { toast("That code is not valid. Example: 1A4S6."); return }
        val port = runCatching { ServerSocket(0).use { it.localPort } }.getOrDefault(25599)
        send(
            Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_JOIN)
                .putExtra(MultiplayerService.EXTRA_CODE, code).putExtra(MultiplayerService.EXTRA_LOCAL_PORT, port)
        )
        rememberWorld(context, code); recent = recents(context)
        PojavLaunchBridge.play(activity, "127.0.0.1:$port")
    }

    fun stop() { send(Intent(context, MultiplayerService::class.java).setAction(MultiplayerService.ACTION_STOP)) }

    Box(Modifier.fillMaxSize().background(Blanc.ink).background(Brush.radialGradient(listOf(Color(0x448B5CF6), Color.Transparent), radius = 900f))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 640.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("MULTIPLAYER", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Play together", color = Blanc.ivory, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Works with any Minecraft Java version. Up to ${mp.max} players.", color = Blanc.dim, fontSize = 11.sp)
                }
                if (mp.mode == "host" && mp.state == "ONLINE" && mp.code.isNotEmpty()) LiveCodeCard(mp, ::stop) {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("World code", mp.code)); toast("Code copied")
                }
                val hostCard: @Composable (Modifier) -> Unit = { mod ->
                    Card(mod, "HOST A WORLD") {
                        Text("Pick the instance, then tap Start. Open your world and choose Esc > Open to LAN > Start LAN World. A short code like 1A4S6 appears in the notification.", color = Blanc.dim, fontSize = 11.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            instances.take(12).forEach { inst ->
                                val on = inst == selected
                                Text(
                                    inst.name, color = if (on) Color.White else Blanc.ivory, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clip(CircleShape).background(if (on) Blanc.violet else Blanc.cardHi).clickable { PojavInstanceBridge.select(inst) }.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                        if (mp.mode == "host" && mp.state != "OFF" && mp.code.isEmpty()) Text(mp.message, color = Blanc.amber, fontSize = 11.sp)
                        if (mp.state == "ONLINE" && mp.message.startsWith("Last failed")) Text(mp.message, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                        if (mp.state == "ERROR") Text(mp.message, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                        Pill("START & SHARE", true) { host() }
                    }
                }
                val joinCard: @Composable (Modifier) -> Unit = { mod ->
                    Card(mod, "JOIN A WORLD") {
                        OutlinedTextField(
                            codeText, { codeText = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("word-word-word-word-word") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                        )
                        Pill("JOIN", true) { join(codeText) }
                        if (recent.isNotEmpty()) {
                            Text("RECENT WORLDS", color = Blanc.dim, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                            recent.forEach { c ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Blanc.cardHi).clickable { join(c) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Blanc.hero), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 11.sp) }
                                    Spacer(Modifier.width(10.dp))
                                    Text(c, color = Blanc.ivory, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    hostCard(Modifier.weight(1f)); joinCard(Modifier.weight(1f))
                } else {
                    hostCard(Modifier.fillMaxWidth()); joinCard(Modifier.fillMaxWidth())
                }
                Card(Modifier.fillMaxWidth(), "MORE") {
                    Text("Voice chat: coming soon. For more than ${mp.max} players, create a dedicated server.", color = Blanc.dim, fontSize = 11.sp)
                    Pill("CREATE A SERVER", false) { navigate(LauncherSection.HOST) }
                }
            }
        }
    }
}

@Composable
internal fun Card(modifier: Modifier, title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape).background(Blanc.card).border(BorderStroke(1.dp, Blanc.line), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Blanc.ivory, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
        content()
    }
}

@Composable
internal fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(shape)
            .then(if (primary) Modifier.background(Blanc.hero) else Modifier.background(Color(0x1FFFFFFF)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black) }
}

@Composable
internal fun LiveCodeCard(mp: MpState, onStop: () -> Unit, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Blanc.hero).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("YOUR WORLD IS OPEN  ·  ${mp.players}/${mp.max} ONLINE", color = Color(0xCCFFFFFF), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(mp.code, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COPY", color = Color(0xFF2A1670), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onCopy).padding(horizontal = 22.dp, vertical = 11.dp))
            Text("STOP", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onStop).padding(horizontal = 22.dp, vertical = 11.dp))
        }
    }
}
