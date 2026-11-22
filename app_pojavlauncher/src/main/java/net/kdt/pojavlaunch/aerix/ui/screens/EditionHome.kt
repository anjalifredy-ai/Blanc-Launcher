package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.bedrock.BedrockDetector
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

enum class Edition(val label: String) { JAVA("Java Edition"), BEDROCK("Bedrock Edition") }

/** The chosen edition, remembered between launches. */
object EditionState {
    private const val PREFS = "aerix_edition"
    var current by mutableStateOf(Edition.JAVA)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("edition", "JAVA")
        }.getOrNull()
        current = if (saved == "BEDROCK") Edition.BEDROCK else Edition.JAVA
    }

    fun set(context: Context, edition: Edition) {
        current = edition
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("edition", edition.name).apply() }
    }
}

/** Home with a Java / Bedrock switch on top. Bedrock opens only when the player's own Play Store copy is installed. */
@Composable
fun EditionHome(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { EditionState.load(context) }
    var detection by remember { mutableStateOf(BedrockDetector.detect(context)) }
    var menu by remember { mutableStateOf(false) }
    val edition = EditionState.current
    // Re-check when the screen is shown again, e.g. after installing Minecraft from Google Play.
    LaunchedEffect(edition) { detection = BedrockDetector.detect(context) }
    // A copy that was removed while Bedrock is selected sends the user back to Java.
    LaunchedEffect(detection.installed, detection.fromPlayStore) {
        if (edition == Edition.BEDROCK && !detection.allowed()) EditionState.set(context, Edition.JAVA)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { detection = BedrockDetector.detect(context); menu = true }) {
                    Text(edition.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("v", fontSize = 11.sp)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Java Edition") }, onClick = { EditionState.set(context, Edition.JAVA); menu = false })
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Bedrock Edition")
                                if (!detection.allowed()) Text(
                                    if (!detection.installed) "Install Minecraft from Google Play first" else "Needs the Google Play version",
                                    fontSize = 10.sp, color = AerixSurface.textMuted
                                )
                            }
                        },
                        onClick = {
                            menu = false
                            if (detection.allowed()) EditionState.set(context, Edition.BEDROCK)
                            else Toast.makeText(
                                context,
                                if (!detection.installed) "Bedrock needs Minecraft installed from Google Play on this phone." else "Bedrock works only with the copy installed from Google Play.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (edition == Edition.BEDROCK && detection.allowed()) BedrockHome(detection) else HomeScreen(activity, navigate)
        }
    }
}

@Composable
private fun BedrockHome(detection: BedrockDetector.Result) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Minecraft Bedrock", color = AerixSurface.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(16.dp), color = AerixSurface.panel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your copy was found", color = Color(0xFF4CD964), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Version ${detection.versionName} (build ${detection.versionCode}), installed from Google Play", color = AerixSurface.textSecondary, fontSize = 12.sp)
            }
        }
        for ((title, text) in listOf(
            "Versions" to "Run more than one Bedrock version inside the launcher. Only versions you own and import yourself.",
            "Resource packs" to "Browse and install Bedrock resource packs.",
            "Shaders" to "Browse and install Bedrock shaders / render packs."
        )) {
            Surface(shape = RoundedCornerShape(16.dp), color = AerixSurface.panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = AerixSurface.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(text, color = AerixSurface.textSecondary, fontSize = 12.sp)
                    Text("Not built yet", color = AerixSurface.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
