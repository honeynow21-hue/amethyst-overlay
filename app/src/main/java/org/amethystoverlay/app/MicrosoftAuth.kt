package org.amethystoverlay.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class MsSession(
    val name: String,
    val uuid: String,
    val mcToken: String,
    val msRefresh: String,
    val mcExpiresAt: Long
)

// Microsoft sign-in (OAuth2 with PKCE) through the browser, then Xbox Live -> XSTS -> Minecraft.
object MicrosoftAuth {
    // Your Azure app's client ID. Until you set it, sign-in stays disabled in the app.
    const val CLIENT_ID = "REPLACE_WITH_AZURE_CLIENT_ID"
    const val REDIRECT_URI = "amethystoverlay://auth"

    private const val SCOPE = "XboxLive.signin offline_access"
    private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
    private const val VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val STATE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val rng = SecureRandom()

    fun isConfigured(): Boolean = CLIENT_ID != "REPLACE_WITH_AZURE_CLIENT_ID"

    fun newVerifier(): String = (1..64).map { VERIFIER_CHARS[rng.nextInt(VERIFIER_CHARS.length)] }.joinToString("")

    fun newState(): String = (1..24).map { STATE_CHARS[rng.nextInt(STATE_CHARS.length)] }.joinToString("")

    fun authorizeUrl(verifier: String, state: String): String =
        "https://login.live.com/oauth20_authorize.srf" +
            "?client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=" + enc(REDIRECT_URI) +
            "&scope=" + enc(SCOPE) +
            "&code_challenge=" + challenge(verifier) +
            "&code_challenge_method=S256" +
            "&state=" + enc(state)

    // Turns the code from the browser redirect into a Minecraft session.
    fun completeLogin(code: String, verifier: String): Result<MsSession> = runCatching {
        val form = "client_id=$CLIENT_ID" +
            "&grant_type=authorization_code" +
            "&code=" + enc(code) +
            "&redirect_uri=" + enc(REDIRECT_URI) +
            "&code_verifier=" + verifier +
            "&scope=" + enc(SCOPE)
        val (status, body) = send(TOKEN_URL, "POST", emptyMap(), form, "application/x-www-form-urlencoded")
        check(status == 200) { "Microsoft sign-in failed: ${oauthError(body)}" }
        val tokens = JSONObject(body)
        minecraftChain(tokens.getString("access_token"), tokens.getString("refresh_token"))
    }

    // Gets a fresh Minecraft session from a saved Microsoft refresh token.
    fun refresh(msRefresh: String): Result<MsSession> = runCatching {
        val form = "client_id=$CLIENT_ID" +
            "&grant_type=refresh_token" +
            "&refresh_token=" + enc(msRefresh) +
            "&redirect_uri=" + enc(REDIRECT_URI) +
            "&scope=" + enc(SCOPE)
        val (status, body) = send(TOKEN_URL, "POST", emptyMap(), form, "application/x-www-form-urlencoded")
        check(status == 200) { "Microsoft session expired. Sign in again." }
        val tokens = JSONObject(body)
        minecraftChain(tokens.getString("access_token"), tokens.getString("refresh_token"))
    }

    private fun minecraftChain(msAccess: String, msRefresh: String): MsSession {
        val xblBody = JSONObject()
            .put(
                "Properties",
                JSONObject()
                    .put("AuthMethod", "RPS")
                    .put("SiteName", "user.auth.xboxlive.com")
                    .put("RpsTicket", "d=$msAccess")
            )
            .put("RelyingParty", "http://auth.xboxlive.com")
            .put("TokenType", "JWT")
            .toString()
        val (xblStatus, xblText) = send(
            "https://user.auth.xboxlive.com/user/authenticate", "POST", emptyMap(), xblBody, "application/json"
        )
        check(xblStatus == 200) { "Xbox Live sign-in failed (code $xblStatus)." }
        val xblToken = JSONObject(xblText).getString("Token")

        val xstsBody = JSONObject()
            .put(
                "Properties",
                JSONObject()
                    .put("SandboxId", "RETAIL")
                    .put("UserTokens", JSONArray().put(xblToken))
            )
            .put("RelyingParty", "rp://api.minecraftservices.com/")
            .put("TokenType", "JWT")
            .toString()
        val (xstsStatus, xstsText) = send(
            "https://xsts.auth.xboxlive.com/xsts/authorize", "POST", emptyMap(), xstsBody, "application/json"
        )
        if (xstsStatus != 200) throw IllegalStateException(xstsError(xstsText))
        val xsts = JSONObject(xstsText)
        val uhs = xsts.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")
        val xstsToken = xsts.getString("Token")

        val mcBody = JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken").toString()
        val (mcStatus, mcText) = send(
            "https://api.minecraftservices.com/authentication/login_with_xbox", "POST", emptyMap(), mcBody, "application/json"
        )
        check(mcStatus == 200) {
            if (mcStatus == 403) {
                "Minecraft refused this sign-in (403). The Azure app may need Mojang approval."
            } else {
                "Minecraft login failed (code $mcStatus)."
            }
        }
        val mc = JSONObject(mcText)
        val mcToken = mc.getString("access_token")
        val expiresAt = System.currentTimeMillis() + mc.optLong("expires_in", 86400L) * 1000L

        val (profileStatus, profileText) = send(
            "https://api.minecraftservices.com/minecraft/profile", "GET",
            mapOf("Authorization" to "Bearer $mcToken"), null, null
        )
        if (profileStatus == 404) {
            throw IllegalStateException("This Microsoft account doesn't own Minecraft Java Edition.")
        }
        check(profileStatus == 200) { "Couldn't read the Minecraft profile (code $profileStatus)." }
        val profile = JSONObject(profileText)
        return MsSession(
            name = profile.getString("name"),
            uuid = profile.getString("id"),
            mcToken = mcToken,
            msRefresh = msRefresh,
            mcExpiresAt = expiresAt
        )
    }

    private fun send(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        contentType: String?
    ): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Accept", "application/json")
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun oauthError(text: String): String =
        runCatching { JSONObject(text).optString("error_description", text) }.getOrDefault(text)

    private fun xstsError(text: String): String {
        val xerr = runCatching { JSONObject(text).optLong("XErr") }.getOrDefault(0L)
        return when (xerr) {
            2148916233L -> "This Microsoft account has no Xbox profile. Sign in at minecraft.net first."
            2148916238L -> "This is a child account. It must be added to a family before it can sign in."
            else -> "Xbox sign-in was rejected (code $xerr)."
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
