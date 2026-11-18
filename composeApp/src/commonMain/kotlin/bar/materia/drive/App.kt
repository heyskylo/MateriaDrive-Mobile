package bar.materia.drive

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.launch

private val api = Api()

@Composable
fun App() {
    // Thumbnails load through the same HTTP client, so they carry the session cookie.
    setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory(api.http)) }.build() }
    MateriaTheme {
        var account by remember { mutableStateOf<Account?>(null) }
        var checked by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { account = runCatching { api.session() }.getOrNull(); checked = true }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(targetState = Pair(checked, account), label = "root") { (ok, acc) ->
                when {
                    !ok -> Box(Modifier.fillMaxSize(), Alignment.Center) { ContainedLoadingIndicator(Modifier.size(72.dp)) }
                    acc == null -> SignIn { account = it }
                    else -> Main(acc) { account = null }
                }
            }
        }
    }
}

// ---------- Sign in ----------
@Composable
private fun SignIn(onSignedIn: (Account) -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun go() = scope.launch {
        busy = true; error = null
        runCatching { api.signIn(login.trim(), password) }.onSuccess(onSignedIn).onFailure { error = it.message }
        busy = false
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(44.dp)).background(Brand.Lavender).padding(28.dp)) {
            Text("Your stuff.\nYours.", color = Brand.Ink, fontSize = 38.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp, modifier = Modifier.align(Alignment.BottomStart))
            Box(Modifier.align(Alignment.TopEnd).size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Brand.Lime), Alignment.Center) {
                Icon(Icons.Filled.Lock, null, tint = Brand.Ink, modifier = Modifier.size(36.dp))
            }
        }
        Text("Sign in to Materia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Your Materia account works for Drive, Photos and Mail.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(login, { login = it }, label = { Text("Email or username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { go() }, enabled = !busy && login.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(64.dp), shapes = ButtonDefaults.shapes()) {
            if (busy) LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Sign in", fontSize = 18.sp)
        }
    }
}

// ---------- Main ----------
private enum class Tab(val label: String, val icon: ImageVector) {
    Drive("Drive", Icons.Filled.Folder), Photos("Photos", Icons.Filled.PhotoLibrary), Starred("Starred", Icons.Filled.Star), Account("Account", Icons.Filled.AccountCircle)
}

@Composable
private fun Main(account: Account, onSignedOut: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.Drive) }
    var viewing by remember { mutableStateOf<DriveFile?>(null) }
    Scaffold(bottomBar = {
        NavigationBar {
            Tab.entries.forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) }) }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Drive -> DriveScreen(view = "drive", title = "My Drive", onOpen = { viewing = it })
                Tab.Starred -> DriveScreen(view = "starred", title = "Starred", onOpen = { viewing = it })
                Tab.Photos -> PhotosScreen { viewing = it }
                Tab.Account -> AccountScreen(account, onSignedOut)
            }
        }
    }
    viewing?.let { ImageViewer(it) { viewing = null } }
}

// ---------- Drive ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DriveScreen(view: String, title: String, onOpen: (DriveFile) -> Unit) {
    val stack = remember { mutableStateListOf<DriveFile>() }
    var items by remember { mutableStateOf<List<DriveFile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fabOpen by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val folder = stack.lastOrNull()

    LaunchedEffect(folder, reload) {
        items = null
        runCatching { api.list(if (folder != null) "drive" else view, folder?.id) }.onSuccess { items = it; error = null }.onFailure { error = it.message; items = emptyList() }
    }
    fun act(msg: String, op: suspend () -> Unit) = scope.launch {
        runCatching { op() }.onSuccess { snackbar.showSnackbar(msg); reload++ }.onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(folder?.name ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black) },
                navigationIcon = { if (folder != null) IconButton(onClick = { stack.removeAt(stack.lastIndex) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (view == "drive") FloatingActionButtonMenu(
                expanded = fabOpen,
                button = {
                    ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }) {
                        Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Add, "New")
                    }
                },
            ) {
                FloatingActionButtonMenuItem(onClick = { fabOpen = false; newFolder = true }, icon = { Icon(Icons.Filled.CreateNewFolder, null) }, text = { Text("New folder") })
                FloatingActionButtonMenuItem(onClick = { fabOpen = false; reload++ }, icon = { Icon(Icons.Filled.Refresh, null) }, text = { Text("Refresh") })
            }
        },
    ) { pad ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            list.isEmpty() -> EmptyState(Modifier.padding(pad), if (view == "starred") Icons.Outlined.StarOutline else Icons.Outlined.Inbox,
                error?.let { "Couldn't load" } ?: if (view == "starred") "No starred files" else "Nothing here yet", error ?: "Upload from the web app for now.")
            else -> LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (folders, files) = list.partition { it.isFolder }
                if (folders.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Folders") }
                items(folders, key = { it.id }) { f -> FileMenu(f, { m, op -> act(m, op) }) { press -> FolderTile(f, press) { stack.add(f) } } }
                if (files.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Files") }
                items(files, key = { it.id }) { f -> FileMenu(f, { m, op -> act(m, op) }) { press -> FileTile(f, press) { onOpen(f) } } }
            }
        }
    }
    if (newFolder) {
        var name by remember { mutableStateOf("Untitled folder") }
        AlertDialog(onDismissRequest = { newFolder = false }, title = { Text("New folder") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { newFolder = false; act("Folder created") { api.createFolder(name, folder?.id) } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancel") } })
    }
}

/** Long-press menu (star / trash) around a tile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileMenu(f: DriveFile, act: (String, suspend () -> Unit) -> Unit, tile: @Composable (onLongPress: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        tile { open = true }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(if (f.starred) "Remove star" else "Star") }, leadingIcon = { Icon(if (f.starred) Icons.Outlined.StarOutline else Icons.Filled.Star, null) },
                onClick = { open = false; act(if (f.starred) "Unstarred" else "Starred") { api.batch(if (f.starred) "unstar" else "star", listOf(f.id)) } })
            if (f.canManage) DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { open = false; act("Moved to trash") { api.batch("trash", listOf(f.id)) } })
        }
    }
}

@Composable private fun SectionLabel(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(f: DriveFile, onLongPress: () -> Unit = {}, onClick: () -> Unit) {
    val tint = f.color?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (f.role != null && f.role != "owner") Icons.Filled.FolderShared else Icons.Filled.Folder, null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (f.starred) Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(f: DriveFile, onLongPress: () -> Unit = {}, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), Alignment.Center) {
            if (f.hasThumb && !f.encrypted) AsyncImage(f.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(iconFor(f), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (f.encrypted) Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
        Text(f.size.bytes(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun iconFor(f: DriveFile): ImageVector = when {
    f.encrypted -> Icons.Filled.Lock
    f.isImage -> Icons.Filled.Image
    f.isVideo -> Icons.Filled.Movie
    f.mime?.startsWith("audio/") == true -> Icons.Filled.MusicNote
    f.mime == "application/pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.Description
}

@Composable
private fun EmptyState(modifier: Modifier, icon: ImageVector, title: String, message: String) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Photos ----------
@Composable
private fun PhotosScreen(onOpen: (DriveFile) -> Unit) {
    var items by remember { mutableStateOf<List<DriveFile>?>(null) }
    LaunchedEffect(Unit) { items = runCatching { api.list("photos") }.getOrDefault(emptyList()) }
    Column {
        Text("Photos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp, 24.dp, 20.dp, 12.dp))
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            list.isEmpty() -> EmptyState(Modifier, Icons.Filled.PhotoLibrary, "No photos yet", "Upload from the web app for now.")
            else -> LazyVerticalGrid(GridCells.Adaptive(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(list, key = { it.id }) { f ->
                    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainer).combinedClickableCompat { onOpen(f) }, Alignment.Center) {
                        if (f.hasThumb && !f.encrypted) AsyncImage(f.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(iconFor(f), null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

// ---------- Viewer ----------
@Composable
private fun ImageViewer(f: DriveFile, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 6f); offset += pan }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                f.encrypted -> Message(Icons.Filled.Lock, "End-to-end encrypted", "Open this file on the web, where your key is unlocked.")
                f.isImage -> AsyncImage(f.contentUrl, f.name, Modifier.fillMaxSize().transformable(state)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
                else -> Message(iconFor(f), f.name, "${f.size.bytes()} · previews for this type are on the web app (drive.materia.bar) for now.")
            }
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.safeDrawingPadding().padding(12.dp)) { Icon(Icons.Filled.Close, "Close") }
        }
    }
}

@Composable
private fun BoxScope.Message(icon: ImageVector, title: String, body: String) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Brand.Lavender, modifier = Modifier.size(48.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(body, color = Color.White.copy(alpha = 0.7f))
    }
}

// ---------- Account ----------
@Composable
private fun AccountScreen(account: Account, onSignedOut: () -> Unit) {
    var me by remember { mutableStateOf<Me?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { me = runCatching { api.me() }.getOrNull() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(40.dp)).background(Brand.Lavender).padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Brand.Lime), Alignment.Center) {
                Text(account.displayName.take(1).uppercase(), color = Brand.Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Text(account.displayName, color = Brand.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("@${account.username} · ${account.email}", color = Brand.Ink.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge)
        }
        me?.let { m ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LinearWavyProgressIndicator(progress = { (m.usedBytes.toFloat() / m.quotaBytes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${m.usedBytes.bytes()} of ${m.quotaBytes.bytes()} used", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { scope.launch { api.signOut(); onSignedOut() } }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out")
        }
    }
}
