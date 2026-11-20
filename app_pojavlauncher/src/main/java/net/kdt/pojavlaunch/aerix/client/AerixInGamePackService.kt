package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * In-game Resource Pack + Shader Pack browser.
 *
 * Sources:
 *  - Modrinth  (project_type = resourcepack | shader)
 *  - CurseForge (classId 12 = Resource Packs, 6552 = Shaders)
 *
 * Always filters by the running Minecraft version so only compatible packs appear.
 * Download lands directly into the instance resourcepacks/ or shaderpacks/ folder.
 * After download the caller writes a live-apply command so the Fabric client mod
 * can trigger MinecraftClient.reloadResources() without restarting the game.
 */
object AerixInGamePackService {

    enum class PackType { RESOURCE_PACK, SHADER_PACK }
    enum class Source { MODRINTH, CURSEFORGE }

    data class PackHit(
        val id: String,
        val title: String,
        val description: String,
        val iconUrl: String?,
        val source: Source,
        val type: PackType,
        val downloads: Long = 0L,
        val author: String = ""
    )

    data class PackVersion(
        val versionId: String,
        val name: String,
        val mcVersions: List<String>,
        val downloadUrl: String,
        val fileName: String,
        val sizeBytes: Long = 0L,
        val sha1: String? = null
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MODRINTH = "https://api.modrinth.com/v2"
    private const val CURSEFORGE = "https://api.curseforge.com/v1"
    private const val UA = "Aerix-Launcher/2.3.2 (in-game-pack-browser)"

    // CurseForge class IDs
    private const val CF_RESOURCE_PACK = 12
    private const val CF_SHADER = 6552

    /** Search both sources. [mcVersion] is required and always applied. */
    suspend fun search(
        query: String,
        mcVersion: String,
        type: PackType,
        source: Source,
        offset: Int = 0,
        limit: Int = 30,
        curseForgeApiKey: String? = null
    ): List<PackHit> = withContext(Dispatchers.IO) {
        when (source) {
            Source.MODRINTH -> searchModrinth(query, mcVersion, type, offset, limit)
            Source.CURSEFORGE -> searchCurseForge(query, mcVersion, type, offset, limit, curseForgeApiKey)
        }
    }

    private fun searchModrinth(
        query: String,
        mcVersion: String,
        type: PackType,
        offset: Int,
        limit: Int
    ): List<PackHit> {
        val projectType = if (type == PackType.RESOURCE_PACK) "resourcepack" else "shader"
        val facets = "[[\"project_type:$projectType\"],[\"versions:$mcVersion\"]]"
        val url = "$MODRINTH/search?query=${java.net.URLEncoder.encode(query, "UTF-8")}&facets=$facets&limit=$limit&offset=$offset&index=relevance"
        val body = get(url) ?: return emptyList()
        val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
        val out = ArrayList<PackHit>(hits.length())
        for (i in 0 until hits.length()) {
            val h = hits.optJSONObject(i) ?: continue
            out += PackHit(
                id = h.optString("project_id"),
                title = h.optString("title"),
                description = h.optString("description").take(180),
                iconUrl = h.optString("icon_url").takeIf { it.isNotBlank() },
                source = Source.MODRINTH,
                type = type,
                downloads = h.optLong("downloads", 0L),
                author = h.optString("author")
            )
        }
        return out
    }

    private fun searchCurseForge(
        query: String,
        mcVersion: String,
        type: PackType,
        offset: Int,
        limit: Int,
        apiKey: String?
    ): List<PackHit> {
        if (apiKey.isNullOrBlank() || apiKey == "DUMMY") return emptyList()
        val classId = if (type == PackType.RESOURCE_PACK) CF_RESOURCE_PACK else CF_SHADER
        val url = "$CURSEFORGE/mods/search?gameId=432&classId=$classId&gameVersion=$mcVersion&searchFilter=${java.net.URLEncoder.encode(query, "UTF-8")}&pageSize=$limit&index=$offset&sortField=2&sortOrder=desc"
        val body = get(url, mapOf("x-api-key" to apiKey)) ?: return emptyList()
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<PackHit>(data.length())
        for (i in 0 until data.length()) {
            val m = data.optJSONObject(i) ?: continue
            val logo = m.optJSONObject("logo")?.optString("thumbnailUrl")
            out += PackHit(
                id = m.optInt("id").toString(),
                title = m.optString("name"),
                description = m.optString("summary").take(180),
                iconUrl = logo?.takeIf { it.isNotBlank() },
                source = Source.CURSEFORGE,
                type = type,
                downloads = m.optLong("downloadCount", 0L),
                author = m.optJSONArray("authors")?.optJSONObject(0)?.optString("name") ?: ""
            )
        }
        return out
    }

    /** Returns the newest version that supports [mcVersion], or null. */
    suspend fun resolveBestVersion(
        hit: PackHit,
        mcVersion: String,
        curseForgeApiKey: String? = null
    ): PackVersion? = withContext(Dispatchers.IO) {
        when (hit.source) {
            Source.MODRINTH -> resolveModrinthVersion(hit.id, mcVersion)
            Source.CURSEFORGE -> resolveCurseForgeVersion(hit.id, mcVersion, curseForgeApiKey)
        }
    }

    private fun resolveModrinthVersion(projectId: String, mcVersion: String): PackVersion? {
        val body = get("$MODRINTH/project/$projectId/version?game_versions=[\"$mcVersion\"]") ?: return null
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        // Prefer the first (newest) that lists our version
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            val games = v.optJSONArray("game_versions") ?: continue
            var matches = false
            for (g in 0 until games.length()) {
                if (games.optString(g) == mcVersion) { matches = true; break }
            }
            if (!matches) continue
            val files = v.optJSONArray("files") ?: continue
            val primary = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .firstOrNull { it.optBoolean("primary", false) } ?: files.optJSONObject(0) ?: continue
            val url = primary.optString("url")
            if (url.isBlank()) continue
            val hashes = primary.optJSONObject("hashes")
            return PackVersion(
                versionId = v.optString("id"),
                name = v.optString("name"),
                mcVersions = listOf(mcVersion),
                downloadUrl = url,
                fileName = primary.optString("filename").ifBlank { "${projectId}.zip" },
                sizeBytes = primary.optLong("size", 0L),
                sha1 = hashes?.optString("sha1")
            )
        }
        return null
    }

    private fun resolveCurseForgeVersion(modId: String, mcVersion: String, apiKey: String?): PackVersion? {
        if (apiKey.isNullOrBlank() || apiKey == "DUMMY") return null
        val body = get("$CURSEFORGE/mods/$modId/files?gameVersion=$mcVersion&pageSize=20", mapOf("x-api-key" to apiKey)) ?: return null
        val data = JSONObject(body).optJSONArray("data") ?: return null
        for (i in 0 until data.length()) {
            val f = data.optJSONObject(i) ?: continue
            val downloadUrl = f.optString("downloadUrl").takeIf { it.isNotBlank() }
                ?: "https://www.curseforge.com/api/v1/mods/$modId/files/${f.optInt("id")}/download"
            return PackVersion(
                versionId = f.optInt("id").toString(),
                name = f.optString("displayName").ifBlank { f.optString("fileName") },
                mcVersions = listOf(mcVersion),
                downloadUrl = downloadUrl,
                fileName = f.optString("fileName").ifBlank { "$modId.zip" },
                sizeBytes = f.optLong("fileLength", 0L)
            )
        }
        return null
    }

    /**
     * Downloads the pack into the correct folder of the running instance.
     * Returns the final File on success.
     */
    suspend fun downloadPack(
        version: PackVersion,
        type: PackType,
        gameDir: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dirName = if (type == PackType.RESOURCE_PACK) "resourcepacks" else "shaderpacks"
        val dir = File(gameDir, dirName).apply { mkdirs() }
        val target = uniqueFile(dir, version.fileName)
        val tmp = File(dir, target.name + ".part")

        val req = Request.Builder().url(version.downloadUrl).header("User-Agent", UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: version.sizeBytes
            var written = 0L
            body.byteStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        onProgress(1f)
        target
    }

    /**
     * Writes a command that the Aerix Client Fabric mod will pick up on the next tick
     * and use to call MinecraftClient.reloadResources() (or enable a shader).
     */
    fun requestLiveApply(gameDir: File, type: PackType, fileName: String) {
        val cfg = File(gameDir, "config").apply { mkdirs() }
        val cmd = File(cfg, "aerix_pack_cmd.properties")
        val props = java.util.Properties()
        props.setProperty("action", if (type == PackType.RESOURCE_PACK) "reload_resources" else "set_shader")
        props.setProperty("file", fileName)
        props.setProperty("ts", System.currentTimeMillis().toString())
        cmd.outputStream().use { props.store(it, "Aerix in-game pack apply command") }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            headers.forEach { (k, v) -> b.header(k, v) }
            http.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
