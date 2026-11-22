package org.amethystoverlay.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("< Back", color = AccentAlt) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Dropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NavTile(title: String, subtitle: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
fun HomeScreen(
    hasCrash: Boolean,
    profile: Profile,
    activeAccount: Account?,
    onProfileSelect: (Profile) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Amethyst Overlay",
            style = TextStyle(brush = HeaderBrush, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        )
        Text(BUILD_LABEL, fontSize = 12.sp, color = Muted)
        Text(
            if (activeAccount == null) {
                "No account selected"
            } else {
                "Playing as ${activeAccount.name} (${if (activeAccount.type == "microsoft") "Microsoft" else "local"})"
            },
            fontSize = 13.sp,
            color = AccentAlt
        )

        if (hasCrash) {
            Button(
                onClick = { onNavigate(Screen.CRASH) },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("A crash was recorded. Tap to view and copy.")
            }
        }

        Dropdown("Profile", PROFILES.map { it.label }, profile.label) { label ->
            PROFILES.firstOrNull { it.label == label }?.let(onProfileSelect)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PlayBrush)
                .clickable {
                    message = if (activeAccount == null) {
                        "Pick an account first (Account screen)."
                    } else {
                        "Launching Minecraft is the next step. It would use ${activeAccount.name} and ${profile.label}."
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("PLAY", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        message?.let { Text(it, color = Muted, fontSize = 13.sp) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavTile("Mods", "Browse Modrinth", Accent, Modifier.weight(1f)) { onNavigate(Screen.MODS) }
            NavTile("Manage Mods", "Installed per profile", Color(0xFF0097A7), Modifier.weight(1f)) {
                onNavigate(Screen.MANAGE)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavTile("Account", "Microsoft or local", Color(0xFFEF6C00), Modifier.weight(1f)) {
                onNavigate(Screen.ACCOUNT)
            }
            NavTile("Settings", "Performance preview", Color(0xFFD81B60), Modifier.weight(1f)) {
                onNavigate(Screen.SETTINGS)
            }
        }
    }
}

@Composable
fun ModsScreen(
    cart: MutableList<CartItem>,
    profile: Profile,
    onBack: () -> Unit,
    onOpenCart: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var typed by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var offset by remember { mutableIntStateOf(0) }
    var nonce by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<Result<List<ModItem>>?>(null) }

    val categories = listOf("All", "optimization", "adventure", "technology", "magic", "utility")

    LaunchedEffect(submitted, category, offset, nonce) {
        loading = true
        result = withContext(Dispatchers.IO) {
            ModrinthApi.search(
                query = submitted,
                category = if (category == "All") null else category,
                offset = offset
            )
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Mods", onBack)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Search mods") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                submitted = typed
                offset = 0
                nonce++
            }) { Text("Search") }
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Dropdown("Category", categories, category) {
                    category = it
                    offset = 0
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onOpenCart,
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) { Text("Cart (${cart.size})") }
        }
        Text("Adds target: ${profile.label}", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            result?.isFailure == true -> Text(
                "Couldn't load mods: ${result?.exceptionOrNull()?.message}",
                color = ErrorRed
            )
            else -> {
                val list = result?.getOrNull().orEmpty()
                if (list.isEmpty()) {
                    Text("No results.", color = Muted)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list) { mod ->
                        ModCard(
                            mod = mod,
                            inCart = cart.any { it.mod.slug == mod.slug },
                            onAdd = {
                                cart.add(CartItem(mod = mod, loader = profile.loader, gameVersion = profile.gameVersion))
                            },
                            onView = { uriHandler.openUri("https://modrinth.com/mod/${mod.slug}") }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { offset = (offset - 20).coerceAtLeast(0) },
                enabled = offset > 0 && !loading
            ) { Text("Previous") }
            Text("Page ${offset / 20 + 1}", modifier = Modifier.padding(top = 12.dp), color = Muted)
            OutlinedButton(
                onClick = { offset += 20 },
                enabled = !loading && (result?.getOrNull()?.size ?: 0) == 20
            ) { Text("Next") }
        }
    }
}

@Composable
fun ModCard(mod: ModItem, inCart: Boolean, onAdd: () -> Unit, onView: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(mod.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            Text("${mod.downloads} downloads", fontSize = 12.sp, color = AccentAlt)
            Spacer(Modifier.height(6.dp))
            Text(
                mod.description,
                fontSize = 13.sp,
                color = Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAdd,
                    enabled = !inCart,
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text(if (inCart) "In cart" else "Add to cart")
                }
                OutlinedButton(onClick = onView) { Text("View on Modrinth") }
            }
        }
    }
}

@Composable
fun CartScreen(cart: MutableList<CartItem>, profile: Profile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(profile) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Cart", onBack)

        if (cart.isEmpty()) {
            Text("Your cart is empty. Add mods from the Mods screen.", color = Muted)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = cart.size, key = { cart[it].mod.slug }) { index ->
                    CartRow(
                        item = cart[index],
                        onChange = { cart[index] = it },
                        onRemove = { cart.removeAt(index) }
                    )
                }
            }
            status?.let { Text(it, color = Muted, fontSize = 13.sp) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showDialog = true },
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text(
                    if (downloading) "DOWNLOADING..." else "DOWNLOAD TO PROFILE",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Download to profile") },
            text = {
                Column {
                    Text(
                        "Each mod gets the newest stable build for this profile, plus its required dependencies.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Dropdown("Profile", PROFILES.map { it.label }, target.label) { label ->
                        PROFILES.firstOrNull { it.label == label }?.let { target = it }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    val chosen = target
                    val toInstall = cart.toList()
                    scope.launch {
                        downloading = true
                        status = "Downloading ${toInstall.size} mod(s) to ${chosen.label}..."
                        val results = installCart(context, toInstall, chosen)
                        val okSlugs = results.filter { it.second == null }.map { it.first.mod.slug }
                        cart.removeAll { it.mod.slug in okSlugs }
                        val errors = results.mapNotNull { (item, problem) ->
                            problem?.let { "${item.mod.title}: $it" }
                        }
                        status = if (errors.isEmpty()) {
                            "Installed to ${chosen.label}. Check Manage Mods."
                        } else {
                            "Some failed:\n" + errors.joinToString("\n")
                        }
                        downloading = false
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CartRow(item: CartItem, onChange: (CartItem) -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBgAlt)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.mod.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRemove) { Text("Remove", color = ErrorRed) }
            }
            Dropdown("Loader", listOf("fabric", "forge"), item.loader) {
                onChange(item.copy(loader = it))
            }
            Dropdown("Minecraft", listOf("1.21.11", "1.20.1"), item.gameVersion) {
                onChange(item.copy(gameVersion = it))
            }
            Text(
                "The best matching build is picked automatically when you download.",
                fontSize = 12.sp,
                color = Muted
            )
        }
    }
}

@Composable
fun ManageModsScreen(profile: Profile, onProfileSelect: (Profile) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember(profile.id) { mutableStateOf(listInstalledMods(context, profile)) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Manage Mods", onBack)

        Dropdown("Profile", PROFILES.map { it.label }, profile.label) { label ->
            PROFILES.firstOrNull { it.label == label }?.let(onProfileSelect)
        }
        Spacer(Modifier.height(8.dp))
        Text("${files.size} mod(s) installed in ${profile.label}", color = Muted, fontSize = 13.sp)
        message?.let { Text(it, color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))

        if (files.isEmpty()) {
            Text("No mods installed for this profile yet.", color = Muted)
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.nameWithoutExtension,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                file.name,
                                fontSize = 11.sp,
                                color = Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = {
                            message = if (file.delete()) "Removed ${file.name}" else "Couldn't remove ${file.name}"
                            files = listInstalledMods(context, profile)
                        }) { Text("Remove", color = ErrorRed) }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember { AccountStore(context) }
    var accounts by remember { mutableStateOf(store.list()) }
    var active by remember { mutableStateOf(store.activeAccount()) }
    var localName by remember { mutableStateOf("") }
    var renamingKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        accounts = store.list()
        active = store.activeAccount()
    }

    fun saveLocal() {
        val name = localName.trim()
        val renaming = renamingKey
        error = null
        when {
            name.isEmpty() -> error = "Type a username first."
            accounts.any {
                it.type == "local" && it.name.equals(name, ignoreCase = true) && it.key != renaming
            } -> error = "That local username already exists."
            renaming != null -> {
                val updated = accounts.map { if (it.key == renaming) it.copy(name = name) else it }
                store.save(updated)
                if (active?.key == renaming) {
                    store.setActive(updated.first { it.key == "local:$name" })
                }
                renamingKey = null
            }
            else -> store.save(accounts + Account(name, "local"))
        }
        if (error == null) {
            localName = ""
            reload()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Accounts", onBack) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBgAlt)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Microsoft account", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Sign in with the Microsoft account that owns Minecraft. Your name comes from Minecraft and can't be changed here.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    if (!MicrosoftAuth.isConfigured()) {
                        Text(
                            "Sign-in isn't set up yet. The app needs an Azure client ID (see setup steps).",
                            fontSize = 12.sp,
                            color = ErrorRed
                        )
                    }
                    AuthBus.status?.let { Text(it, fontSize = 12.sp, color = AccentAlt) }
                    Button(
                        onClick = {
                            val verifier = MicrosoftAuth.newVerifier()
                            val state = MicrosoftAuth.newState()
                            store.savePendingLogin(verifier, state)
                            AuthBus.status = "Opening Microsoft sign-in in your browser..."
                            uriHandler.openUri(MicrosoftAuth.authorizeUrl(verifier, state))
                        },
                        enabled = MicrosoftAuth.isConfigured(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text("Sign in with Microsoft") }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBgAlt)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (renamingKey == null) "Local account" else "Rename local account",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Offline name you choose. Works only on servers that allow offline play.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    OutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveLocal() }) {
                            Text(if (renamingKey == null) "Create local account" else "Save new name")
                        }
                        if (renamingKey != null) {
                            OutlinedButton(onClick = {
                                renamingKey = null
                                localName = ""
                                error = null
                            }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        item {
            Text("Your accounts", fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (accounts.isEmpty()) {
            item { Text("No accounts yet.", color = Muted) }
        }

        items(accounts) { acc ->
            val isActive = active?.key == acc.key
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${acc.name}${if (isActive) "  (active)" else ""}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (acc.type == "microsoft") "MICROSOFT" else "LOCAL (offline)",
                        fontSize = 11.sp,
                        color = if (acc.type == "microsoft") Green else AccentAlt
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (acc.type == "local") {
                            OutlinedButton(onClick = {
                                renamingKey = acc.key
                                localName = acc.name
                                error = null
                            }) { Text("Rename") }
                            Button(onClick = {
                                store.setActive(acc)
                                reload()
                            }) { Text("Set active and play") }
                        } else {
                            Button(onClick = {
                                store.setActive(acc)
                                reload()
                            }) { Text("Set active") }
                        }
                    }
                    TextButton(onClick = {
                        store.save(accounts.filter { it.key != acc.key })
                        if (isActive) store.setActive(null)
                        if (renamingKey == acc.key) {
                            renamingKey = null
                            localName = ""
                        }
                        reload()
                    }) { Text("Remove", color = ErrorRed) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Settings", onBack)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBgAlt)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Performance preview", fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "These switches are a preview only. They don't change anything in the game yet.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PERF_TOGGLES) { (key, label) ->
                var on by remember(key) { mutableStateOf(prefs.perf(key)) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = on,
                            onCheckedChange = {
                                on = it
                                prefs.setPerf(key, it)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CrashScreen(file: File, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = remember { if (file.exists()) file.readText() else "No crash recorded." }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Last crash log", onBack)
        Row {
            Button(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                file.delete()
                onBack()
            }) { Text("Clear") }
        }
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
