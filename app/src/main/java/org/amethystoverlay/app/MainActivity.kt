package org.amethystoverlay.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val BUILD_LABEL = "Build 0.2.0 (Phase 2)"

// Passes the Microsoft sign-in result from the browser redirect into the app.
object AuthBus {
    var pendingCode by mutableStateOf<String?>(null)
    var returnedState by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>(null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger(filesDir)
        handleAuthIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot(filesDir)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "amethystoverlay" || data.host != "auth") return

        val problem = data.getQueryParameter("error_description") ?: data.getQueryParameter("error")
        if (problem != null) {
            AuthBus.status = "Microsoft sign-in didn't finish: $problem"
            return
        }
        AuthBus.returnedState = data.getQueryParameter("state")
        AuthBus.pendingCode = data.getQueryParameter("code")
    }
}

enum class Screen { HOME, ACCOUNT, MODS, CART, MANAGE, SETTINGS, CRASH }

@Composable
fun AppRoot(filesDir: File) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val accountStore = remember { AccountStore(context) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var profile by remember { mutableStateOf(prefs.profile()) }
    var accountTick by remember { mutableIntStateOf(0) }
    val cart = remember { mutableStateListOf<CartItem>() }
    val crashFile = remember { File(filesDir, CRASH_FILE_NAME) }

    val selectProfile: (Profile) -> Unit = { picked ->
        profile = picked
        prefs.setProfile(picked.id)
    }

    // Runs when the browser sends the user back with a sign-in code.
    LaunchedEffect(AuthBus.pendingCode) {
        val code = AuthBus.pendingCode ?: return@LaunchedEffect
        AuthBus.pendingCode = null
        val (verifier, expectedState) = accountStore.pendingLogin()
        accountStore.clearPendingLogin()
        if (verifier == null || expectedState == null || expectedState != AuthBus.returnedState) {
            AuthBus.status = "That sign-in link didn't match this app's request. Please try again."
            return@LaunchedEffect
        }

        AuthBus.status = "Finishing Microsoft sign-in..."
        val result = withContext(Dispatchers.IO) { MicrosoftAuth.completeLogin(code, verifier) }
        result
            .onSuccess { s ->
                val others = accountStore.list().filterNot { it.type == "microsoft" && it.name == s.name }
                val account = Account(s.name, "microsoft", s.uuid, s.mcToken, s.msRefresh, s.mcExpiresAt)
                accountStore.save(others + account)
                accountStore.setActive(account)
                AuthBus.status = "Signed in as ${s.name}."
                screen = Screen.ACCOUNT
                accountTick++
            }
            .onFailure { AuthBus.status = it.message ?: "Sign-in failed." }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.CART) Screen.MODS else Screen.HOME
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBrush)) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                hasCrash = crashFile.exists(),
                profile = profile,
                activeAccount = accountStore.activeAccount(),
                onProfileSelect = selectProfile,
                onNavigate = { screen = it }
            )
            Screen.ACCOUNT -> key(accountTick) {
                AccountScreen(onBack = { screen = Screen.HOME })
            }
            Screen.MODS -> ModsScreen(
                cart = cart,
                profile = profile,
                onBack = { screen = Screen.HOME },
                onOpenCart = { screen = Screen.CART }
            )
            Screen.CART -> CartScreen(
                cart = cart,
                profile = profile,
                onBack = { screen = Screen.MODS }
            )
            Screen.MANAGE -> ManageModsScreen(
                profile = profile,
                onProfileSelect = selectProfile,
                onBack = { screen = Screen.HOME }
            )
            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
            Screen.CRASH -> CrashScreen(file = crashFile, onBack = { screen = Screen.HOME })
        }
    }
}
