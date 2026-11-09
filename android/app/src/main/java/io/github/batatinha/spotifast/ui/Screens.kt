package io.github.batatinha.spotifast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.batatinha.spotifast.AppViewModel
import io.github.batatinha.spotifast.Collection
import io.github.batatinha.spotifast.Song
import io.github.batatinha.spotifast.Tab

const val REDIRECT_URI = "http://127.0.0.1:8989/login"

@Composable
fun SpotifastRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) {
        vm.error?.let {
            snackbar.showSnackbar(it)
            vm.error = null
        }
    }
    BackHandler(enabled = vm.showNowPlaying || vm.stack.isNotEmpty()) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize()) {
        when {
            !vm.status.webSignedIn -> SetupScreen(vm)
            vm.showNowPlaying -> NowPlayingScreen(vm)
            else -> MainScreen(vm, snackbar)
        }
        vm.busy?.let { BusyOverlay(it, onCancel = vm::cancelLogin) }
        if (!vm.status.webSignedIn || vm.showNowPlaying) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
      }
    }
}

@Composable
private fun BusyOverlay(message: String, onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC0F1114)).clickable(enabled = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

// ----- Sign-in -----

@Composable
private fun SetupScreen(vm: AppViewModel) {
    var clientId by rememberSaveable { mutableStateOf(vm.status.clientId ?: "") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val start = {
        // The keyboard would cover the waiting overlay and its Cancel button.
        focus.clearFocus()
        vm.signIn(clientId)
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Green),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.PlayArrow, null, tint = Background, modifier = Modifier.size(36.dp)) }
        Text("Spotifast", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Spotify, light and fast. Playback needs Spotify Premium.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Register Spotifast with Spotify", fontWeight = FontWeight.SemiBold)
                Text(
                    "In the Spotify developer dashboard (developer.spotify.com/dashboard), " +
                        "choose Create app, tick Web API, and add this redirect URI:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(REDIRECT_URI, color = Green, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(REDIRECT_URI)) }) { Text("Copy") }
                }
                Text(
                    "Already use Spotifast on a computer? The same Client ID works here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text("2. Paste the Client ID", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it.trim() },
            label = { Text("Client ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (clientId.length == 32) start() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { start() },
            enabled = clientId.length == 32,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Sign in with Spotify") }
    }
}

// ----- Main -----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(vm)
                NavigationBar(containerColor = Surface) {
                    NavigationBarItem(
                        selected = vm.tab == Tab.Library && vm.stack.isEmpty(),
                        onClick = { vm.stack.clear(); vm.tab = Tab.Library },
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.Search && vm.stack.isEmpty(),
                        onClick = { vm.stack.clear(); vm.tab = Tab.Search },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Search") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val open = vm.stack.lastOrNull()
            when {
                open != null -> CollectionScreen(vm, open)
                vm.tab == Tab.Library -> LibraryScreen(vm)
                else -> SearchScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Column {
        TopAppBar(
            title = { Text("Your Library", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.loadLibrary() })
                    DropdownMenuItem(
                        text = { Text("Play what's on my other device here") },
                        onClick = { menu = false; vm.transfer() },
                    )
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; vm.signOut() })
                }
            },
        )
        if (vm.libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            if (!vm.status.playbackSignedIn) {
                item { PlaybackSetupCard(vm) }
            }
            item { CollectionRow(vm.liked) { vm.open(vm.liked) } }
            items(vm.playlists, key = { "p" + it.uri }) { CollectionRow(it) { vm.open(it) } }
            if (vm.albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(vm.albums, key = { "a" + it.uri }) { CollectionRow(it) { vm.open(it) } }
            }
        }
    }
}

@Composable
private fun PlaybackSetupCard(vm: AppViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Play music on this phone", fontWeight = FontWeight.SemiBold)
            Text(
                "Approve playback once in the browser. This phone then shows up in " +
                    "Spotify Connect as Spotifast.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm::setUpPlayback) { Text("Set up playback here") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Cover(url: String?, size: Dp, round: Boolean = false, liked: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                if (liked) {
                    Brush.linearGradient(listOf(Color(0xFF4A2BD8), Color(0xFF9AC7C2)))
                } else {
                    Brush.linearGradient(listOf(Surface, Surface))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (liked) Icons.Default.Favorite else Icons.Default.MusicNote,
                null,
                tint = if (liked) Color.White else Muted,
            )
        }
    }
}

@Composable
private fun CollectionRow(collection: Collection, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            collection.imageUrl,
            56.dp,
            round = collection.kind == Collection.Kind.Artist,
            liked = collection.kind == Collection.Kind.Liked,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                collection.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, current: Boolean, onClick: () -> Unit, onQueue: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = song.playable, onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.imageUrl, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.name,
                color = when {
                    current -> Green
                    !song.playable -> Muted
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artists,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { menu = false; onQueue() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(vm: AppViewModel, collection: Collection) {
    val songs = vm.songs[collection.uri]
    val playing = vm.player.track?.uri
    Column {
        TopAppBar(
            title = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Cover(
                        collection.imageUrl,
                        180.dp,
                        round = collection.kind == Collection.Kind.Artist,
                        liked = collection.kind == Collection.Kind.Liked,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(collection.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(collection.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.play(collection, null) }, enabled = !songs.isNullOrEmpty()) {
                            Icon(Icons.Default.PlayArrow, null)
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { vm.play(collection, null, shuffle = true) },
                            enabled = !songs.isNullOrEmpty(),
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Text(" Shuffle")
                        }
                    }
                }
            }
            if (songs == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(songs, key = { it.uri + songs.indexOf(it) }) { song ->
                    SongRow(
                        song,
                        current = song.uri == playing,
                        onClick = { vm.play(collection, song) },
                        onQueue = { vm.queue(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(vm: AppViewModel) {
    val playing = vm.player.track?.uri
    Column(Modifier.statusBarsPadding()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::search,
            placeholder = { Text("What do you want to listen to?") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.search("") }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        val r = vm.results
        LazyColumn(Modifier.fillMaxSize()) {
            if (r.songs.isNotEmpty()) {
                item { SectionTitle("Songs") }
                items(r.songs, key = { "s" + it.uri }) { song ->
                    SongRow(song, song.uri == playing, onClick = { vm.play(null, song) }, onQueue = { vm.queue(song) })
                }
            }
            if (r.artists.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(r.artists, key = { "r" + it.uri }) { CollectionRow(it) { vm.open(it) } }
            }
            if (r.albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(r.albums, key = { "a" + it.uri }) { CollectionRow(it) { vm.open(it) } }
            }
            if (r.playlists.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                items(r.playlists, key = { "p" + it.uri }) { CollectionRow(it) { vm.open(it) } }
            }
        }
    }
}

// ----- Player -----

@Composable
private fun MiniPlayer(vm: AppViewModel) {
    val state = vm.player
    val track = state.track ?: return
    val position = rememberPosition(vm)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2F36))
            .clickable { vm.showNowPlaying = true },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track.artUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    track.artists,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = vm::toggle) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause")
                }
            }
            IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
        val progress = if (track.durationMs > 0) position.toFloat() / track.durationMs else 0f
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            trackColor = Color(0xFF4A5059),
        )
    }
}

@Composable
fun rememberPosition(vm: AppViewModel): Long {
    var now by remember { mutableStateOf(vm.player.positionNow()) }
    LaunchedEffect(vm.player) {
        while (true) {
            now = vm.player.positionNow()
            kotlinx.coroutines.delay(250)
        }
    }
    return now
}
