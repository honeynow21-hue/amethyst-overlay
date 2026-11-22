package org.amethystoverlay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// type is "local" (you choose the name, offline) or "microsoft" (name comes from Minecraft).
data class Account(
    val name: String,
    val type: String,
    val uuid: String? = null,
    val mcToken: String? = null,
    val msRefresh: String? = null,
    val mcExpiresAt: Long = 0L
) {
    val key: String get() = "$type:$name"
}

class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun list(): List<Account> {
        val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
        return List(arr.length()) { i ->
            val raw = arr.get(i)
            if (raw is JSONObject) fromJson(raw) else Account(raw.toString(), "local")
        }
    }

    fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("name", a.name)
                    .put("type", a.type)
                    .put("uuid", a.uuid ?: "")
                    .put("mcToken", a.mcToken ?: "")
                    .put("msRefresh", a.msRefresh ?: "")
                    .put("mcExpiresAt", a.mcExpiresAt)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun activeAccount(): Account? {
        val key = prefs.getString("active", null) ?: return null
        return list().firstOrNull { it.key == key }
    }

    fun setActive(account: Account?) {
        prefs.edit().putString("active", account?.key).apply()
    }

    fun savePendingLogin(verifier: String, state: String) {
        prefs.edit().putString("pending_verifier", verifier).putString("pending_state", state).apply()
    }

    fun pendingLogin(): Pair<String?, String?> =
        prefs.getString("pending_verifier", null) to prefs.getString("pending_state", null)

    fun clearPendingLogin() {
        prefs.edit().remove("pending_verifier").remove("pending_state").apply()
    }

    private fun fromJson(o: JSONObject): Account = Account(
        name = o.getString("name"),
        type = o.optString("type", "local"),
        uuid = o.optString("uuid").ifBlank { null },
        mcToken = o.optString("mcToken").ifBlank { null },
        msRefresh = o.optString("msRefresh").ifBlank { null },
        mcExpiresAt = o.optLong("mcExpiresAt", 0L)
    )
}
