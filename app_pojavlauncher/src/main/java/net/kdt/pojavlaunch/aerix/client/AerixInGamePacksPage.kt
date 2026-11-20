package net.kdt.pojavlaunch.aerix.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.client.AerixInGamePackService.PackHit
import net.kdt.pojavlaunch.aerix.client.AerixInGamePackService.PackType
import net.kdt.pojavlaunch.aerix.client.AerixInGamePackService.Source
import java.io.File

private val Crimson = Color(0xFFDC1E3A)
private val CrimsonGlow = Color(0xFFFF2E4C)
private val DarkBase = Color(0xFF141519)
private val DarkPanel = Color(0xFF1C1E24)
private val DarkTile = Color(0xFF252830)
private val TextMuted = Color(0xFF9CA3AF)
private val TextBright = Color(0xFFF3F4F6)

/**
 * Full in-game Packs browser (Resource Packs + Shader Packs).
 *
 * - Source tabs: Modrinth | CurseForge
 * - Type tabs: Resource Packs | Shader Packs
 * - Auto-filters by the running Minecraft version
 * - Download + live Apply (no game restart)
 */
@Composable
fun AerixInGamePacksPage(
    mcVersion: String,
    gameDir: File?,
    curseForgeApiKey: String? = null,
    onBack: () -> Unit = {}
) {
    var source by remember { mutableStateOf(Source.MODRINTH) }
    var packType by remember { mutableStateOf(PackType.RESOURCE_PACK) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PackHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun doSearch(q: String = query) {
        if (gameDir == null) {
            error = "No active game directory"
            return
        }
        loading = true
        error = null
        statusMsg = null
        scope.launch {
            val result = runCatching {
                AerixInGamePackService.search(
                    query = q,
                    mcVersion = mcVersion,
                    type = packType,
                    source = source,
                    curseForgeApiKey = curseForgeApiKey
                )
            }
            loading = false
            result.onSuccess {
                hits = it
                if (it.isEmpty()) error = "No compatible packs found for $mcVersion"
            }.onFailure {
                error = it.message ?: "Search failed"
                hits = emptyList()
            }
        }
    }

    // Auto-search when source / type changes
    LaunchedEffect(source, packType, mcVersion) {
        doSearch("")
    }

    fun downloadAndApply(hit: PackHit) {
        if (gameDir == null || downloadingId != null) return
        downloadingId = hit.id
        downloadProgress = 0f
        statusMsg = "Resolving best version for $mcVersion…"
        scope.launch {
            try {
                val version = withContext(Dispatchers.IO) {
                    AerixInGamePackService.resolveBestVersion(hit, mcVersion, curseForgeApiKey)
                }
                if (version == null) {
                    statusMsg = "No version found for $mcVersion"
                    downloadingId = null
                    return@launch
                }
                statusMsg = "Downloading ${version.fileName}…"
                val file = AerixInGamePackService.downloadPack(
                    version = version,
                    type = packType,
                    gameDir = gameDir,
                    onProgress = { downloadProgress = it }
                )
                statusMsg = "Applying (live reload)…"
                AerixInGamePackService.requestLiveApply(gameDir, packType, file.name)
                statusMsg = "Applied: ${file.name}  · resources reloading"
            } catch (t: Throwable) {
                statusMsg = "Failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                downloadingId = null
                downloadProgress = 0f
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBase)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Packs",
                color = Crimson,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier = Modifier.weight(1f))
            Text(
                text = "MC $mcVersion",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier = Modifier.height(10.dp))

        // Source tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SourceChip("Modrinth", source == Source.MODRINTH) {
                source = Source.MODRINTH
            }
            SourceChip("CurseForge", source == Source.CURSEFORGE) {
                source = Source.CURSEFORGE
            }
        }

        Spacer(Modifier = Modifier.height(8.dp))

        // Type tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypeChip("Resource Packs", packType == PackType.RESOURCE_PACK) {
                packType = PackType.RESOURCE_PACK
            }
            TypeChip("Shader Packs", packType == PackType.SHADER_PACK) {
                packType = PackType.SHADER_PACK
            }
        }

        Spacer(Modifier = Modifier.height(10.dp))

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkTile)
                .border(1.dp, DarkPanel, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextBright, fontSize = 14.sp),
                cursorBrush = SolidColor(Crimson),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search packs…", color = TextMuted, fontSize = 14.sp)
                    }
                    inner()
                }
            )
            Text(
                text = "Go",
                color = Crimson,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { doSearch() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier = Modifier.height(8.dp))

        // Status / progress
        AnimatedVisibility(visible = statusMsg != null || downloadingId != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                statusMsg?.let {
                    Text(it, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (downloadingId != null) {
                    Spacer(Modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Crimson,
                        trackColor = DarkTile
                    )
                }
                Spacer(Modifier = Modifier.height(6.dp))
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> {
                    Box(Modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Crimson, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                error != null && hits.isEmpty() -> {
                    Box(Modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error ?: "", color = TextMuted, fontSize = 13.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hits, key = { it.id }) { hit ->
                            PackCard(
                                hit = hit,
                                isDownloading = downloadingId == hit.id,
                                onApply = { downloadAndApply(hit) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Crimson.copy(alpha = 0.18f) else DarkTile
    val border = if (selected) Crimson else Color.Transparent
    val text = if (selected) CrimsonGlow else TextMuted
    Text(
        text = label,
        color = text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        Brush.horizontalGradient(listOf(Crimson.copy(alpha = 0.25f), DarkTile))
    } else {
        Brush.horizontalGradient(listOf(DarkTile, DarkTile))
    }
    val text = if (selected) TextBright else TextMuted
    Text(
        text = label,
        color = text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun PackCard(
    hit: PackHit,
    isDownloading: Boolean,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkPanel)
            .border(1.dp, DarkTile, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon placeholder (simple colored box)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (hit.type == PackType.SHADER_PACK) Color(0xFF1E3A5F) else Color(0xFF3A1E2E)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hit.type == PackType.SHADER_PACK) "S" else "R",
                color = TextBright,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title,
                color = TextBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hit.description.isNotBlank()) {
                Text(
                    text = hit.description,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = hit.source.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = Crimson.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
                if (hit.downloads > 0) {
                    Text(
                        text = formatDownloads(hit.downloads),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDownloading) DarkTile else Crimson)
                .clickable(enabled = !isDownloading, onClick = onApply)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    color = Crimson,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "Apply",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDownloads(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M dl"
    n >= 1_000 -> "${n / 1_000}K dl"
    else -> "$n dl"
}
