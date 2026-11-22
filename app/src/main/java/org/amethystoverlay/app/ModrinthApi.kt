package org.amethystoverlay.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class ModItem(
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Int,
    val iconUrl: String?
)

data class ModVersion(
    val id: String,
    val versionNumber: String,
    val versionType: String,
    val datePublished: String,
    val fileUrl: String,
    val fileName: String,
    val sha512: String?,
    val requiredProjectIds: List<String>
)

object ModrinthApi {
    private const val SEARCH = "https://api.modrinth.com/v2/search"
    private const val USER_AGENT = "AmethystOverlay/0.2.0 (honeynow21-hue/amethyst-overlay)"

    private fun open(url: String, readTimeoutMs: Int = 15000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 15000
            readTimeout = readTimeoutMs
        }

    private fun getText(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun search(query: String, category: String?, offset: Int, limit: Int = 20): Result<List<ModItem>> =
        runCatching {
            val facets = JSONArray()
            facets.put(JSONArray().put("project_type:mod"))
            if (category != null) {
                facets.put(JSONArray().put("categories:$category"))
            }
            val url = SEARCH +
                "?query=" + URLEncoder.encode(query, "UTF-8") +
                "&limit=$limit&offset=$offset" +
                "&facets=" + URLEncoder.encode(facets.toString(), "UTF-8")

            val hits = JSONObject(getText(url)).getJSONArray("hits")
            List(hits.length()) { i ->
                val h = hits.getJSONObject(i)
                ModItem(
                    slug = h.getString("slug"),
                    title = h.getString("title"),
                    description = h.optString("description"),
                    downloads = h.optInt("downloads"),
                    iconUrl = h.optString("icon_url").ifBlank { null }
                )
            }
        }

    // Returns every build of a project for one loader and one Minecraft version.
    fun getVersions(projectIdOrSlug: String, loader: String, gameVersion: String): Result<List<ModVersion>> =
        runCatching {
            val loaders = URLEncoder.encode(JSONArray().put(loader).toString(), "UTF-8")
            val gameVers = URLEncoder.encode(JSONArray().put(gameVersion).toString(), "UTF-8")
            val url = "https://api.modrinth.com/v2/project/$projectIdOrSlug/version" +
                "?loaders=$loaders&game_versions=$gameVers"

            val arr = JSONArray(getText(url))
            val list = mutableListOf<ModVersion>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                val files = v.getJSONArray("files")
                if (files.length() == 0) continue

                var file = files.getJSONObject(0)
                for (j in 0 until files.length()) {
                    val f = files.getJSONObject(j)
                    if (f.optBoolean("primary")) {
                        file = f
                        break
                    }
                }

                val required = mutableListOf<String>()
                val deps = v.optJSONArray("dependencies")
                if (deps != null) {
                    for (d in 0 until deps.length()) {
                        val dep = deps.getJSONObject(d)
                        if (dep.optString("dependency_type") == "required" && !dep.isNull("project_id")) {
                            required.add(dep.getString("project_id"))
                        }
                    }
                }

                list.add(
                    ModVersion(
                        id = v.getString("id"),
                        versionNumber = v.getString("version_number"),
                        versionType = v.optString("version_type", "release"),
                        datePublished = v.optString("date_published"),
                        fileUrl = file.getString("url"),
                        fileName = file.getString("filename"),
                        sha512 = file.optJSONObject("hashes")?.optString("sha512")?.ifBlank { null },
                        requiredProjectIds = required
                    )
                )
            }
            list
        }

    // Best build: stable releases first, then betas, then alphas; newest first within each.
    fun pickBest(versions: List<ModVersion>): ModVersion? {
        fun rank(type: String): Int = when (type) {
            "release" -> 0
            "beta" -> 1
            else -> 2
        }
        return versions
            .sortedWith(compareBy<ModVersion> { rank(it.versionType) }.thenByDescending { it.datePublished })
            .firstOrNull()
    }

    // Downloads a file and checks its SHA-512 hash. Deletes the file if anything goes wrong.
    fun downloadTo(url: String, dest: File, expectedSha512: String?) {
        val conn = open(url, 60000)
        try {
            val digest = MessageDigest.getInstance("SHA-512")
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (expectedSha512 != null && !actual.equals(expectedSha512, ignoreCase = true)) {
                throw IllegalStateException("file check failed for ${dest.name}")
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
