package org.amethystoverlay.app

import android.content.Context
import java.io.File

data class Profile(
    val id: String,
    val label: String,
    val gameVersion: String,
    val loader: String
)

val PROFILES = listOf(
    Profile("1.21.11-fabric", "1.21.11 Fabric", "1.21.11", "fabric"),
    Profile("1.20.1-forge", "1.20.1 Forge", "1.20.1", "forge")
)

val PERF_TOGGLES = listOf(
    "maxfps" to "Maximize FPS (smart culling)",
    "ram_cap" to "4GB RAM safe cap",
    "metal" to "Metal render boost",
    "particles" to "Particle and weather minimizer",
    "throttle" to "Entity and animation throttler",
    "fps60" to "Thermal stabilizer (60 FPS lock)"
)

fun profileModsDir(context: Context, profile: Profile): File {
    val dir = File(context.filesDir, "instances/${profile.id}/mods")
    dir.mkdirs()
    return dir
}

fun listInstalledMods(context: Context, profile: Profile): List<File> =
    profileModsDir(context, profile)
        .listFiles { f -> f.isFile && f.name.endsWith(".jar") }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun profile(): Profile {
        val id = prefs.getString("profile", null)
        return PROFILES.firstOrNull { it.id == id } ?: PROFILES[0]
    }

    fun setProfile(id: String) {
        prefs.edit().putString("profile", id).apply()
    }

    fun perf(key: String): Boolean = prefs.getBoolean("perf_$key", false)

    fun setPerf(key: String, value: Boolean) {
        prefs.edit().putBoolean("perf_$key", value).apply()
    }
}
