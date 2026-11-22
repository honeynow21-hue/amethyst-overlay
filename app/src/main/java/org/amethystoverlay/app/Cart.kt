package org.amethystoverlay.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

data class CartItem(
    val mod: ModItem,
    val loader: String,
    val gameVersion: String
)

// Installs the best build of a project, plus its required dependencies (one level deep).
// Returns null on success, or a short reason for failure.
private fun installProject(
    dir: File,
    projectId: String,
    loader: String,
    gameVersion: String,
    withDeps: Boolean
): String? {
    val versions = ModrinthApi.getVersions(projectId, loader, gameVersion).getOrElse {
        return "Modrinth error: ${it.message}"
    }
    val best = ModrinthApi.pickBest(versions)
        ?: return "no $loader build for Minecraft $gameVersion"

    val dest = File(dir, best.fileName)
    if (!dest.exists()) {
        ModrinthApi.downloadTo(best.fileUrl, dest, best.sha512)
    }

    if (withDeps) {
        for (dep in best.requiredProjectIds) {
            val problem = installProject(dir, dep, loader, gameVersion, withDeps = false)
            if (problem != null) return "required dependency failed: $problem"
        }
    }
    return null
}

// Installs every cart item into the profile's mods folder, in parallel.
// Each result is the item plus null (success) or a failure reason.
suspend fun installCart(context: Context, items: List<CartItem>, profile: Profile): List<Pair<CartItem, String?>> =
    withContext(Dispatchers.IO) {
        coroutineScope {
            items.map { item ->
                async {
                    val problem: String? =
                        if (item.loader != profile.loader || item.gameVersion != profile.gameVersion) {
                            "loader or version doesn't match ${profile.label}"
                        } else {
                            try {
                                installProject(
                                    dir = profileModsDir(context, profile),
                                    projectId = item.mod.slug,
                                    loader = item.loader,
                                    gameVersion = item.gameVersion,
                                    withDeps = true
                                )
                            } catch (e: Exception) {
                                e.message ?: "download failed"
                            }
                        }
                    item to problem
                }
            }.map { it.await() }
        }
    }
