package com.yaserx.voicely

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

private val Bg = Color(0xFF08090C)
private val Panel = Color(0xFF111319)
private val Card = Color(0xFF171A21)
private val Accent = Color(0xFFFFB800)
private val Muted = Color(0xFF8B909B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundLibrary.init(this)
        AudioEngine.init()
        setContent { VoicelyApp() }
    }

    override fun onDestroy() {
        AudioEngine.release()
        super.onDestroy()
    }
}

@Composable
private fun VoicelyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var sounds by remember { mutableStateOf(SoundLibrary.all()) }
    var query by remember { mutableStateOf("") }
    var gameMode by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun addImportedSound(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val title = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.ifBlank { "New Sound" }
            ?: "New Sound"
        val sound = SoundItem(UUID.randomUUID().toString(), title, uri.toString())
        sounds = SoundLibrary.add(sound)
        AudioEngine.preload(context, sound)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::addImportedSound)
    }

    fun startRecording() {
        if (recording) return
        val directory = File(context.getExternalFilesDir("Music"), "recordings").apply { mkdirs() }
        val file = File(directory, "voicely_${System.currentTimeMillis()}.m4a")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(128000)
            newRecorder.setAudioSamplingRate(44100)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            recordingFile = file
            recording = true
        }.onFailure { newRecorder.release(); recording = false }
    }

    fun stopRecording() {
        val active = recorder ?: return
        runCatching { active.stop() }
        active.release()
        recorder = null
        recording = false
        recordingFile?.let { file ->
            if (file.exists() && file.length() > 0L) {
                val sound = SoundItem(
                    UUID.randomUUID().toString(),
                    file.nameWithoutExtension.replace('_', ' '),
                    Uri.fromFile(file).toString(),
                    false,
                    "Recordings"
                )
                sounds = SoundLibrary.add(sound)
                AudioEngine.preload(context, sound)
            }
        }
        recordingFile = null
    }

    fun toggleGameMode() {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            return
        }
        val intent = Intent(context, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        gameMode = true
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        Scaffold(
            containerColor = Bg,
            topBar = { Header(onGameMode = ::toggleGameMode) },
            bottomBar = { BottomNav(tab) { tab = it } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(
                        sounds = sounds,
                        query = query,
                        onQuery = { query = it },
                        add = { picker.launch(arrayOf("audio/*")) },
                        play = { AudioEngine.play(context, it) },
                        fav = { item -> sounds = SoundLibrary.update(item.copy(favorite = !item.favorite)) }
                    )
                    1 -> DiscoverScreen(sounds) { picker.launch(arrayOf("audio/*")) }
                    2 -> BoardsScreen(sounds, { picker.launch(arrayOf("audio/*")) }) { AudioEngine.play(context, it) }
                    3 -> StudioScreen(recording) {
                        if (recording) stopRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    else -> SettingsScreen(
                        gameMode = gameMode,
                        onStartGame = ::toggleGameMode,
                        onStopGame = {
                            context.stopService(Intent(context, FloatingService::class.java))
                            gameMode = false
                        },
                        onOverlay = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onGameMode: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Bg).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("VOICELY", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Gaming soundboard", color = Muted, fontSize = 11.sp)
        }
        FilledTonalButton(
            onClick = onGameMode,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.SportsEsports, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("GAME", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomNav(tab: Int, select: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Discover" to Icons.Default.Search,
        "Boards" to Icons.Default.GridView,
        "Studio" to Icons.Default.Mic,
        "Me" to Icons.Default.Settings
    )
    NavigationBar(containerColor = Color(0xFF0D0F13)) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { select(index) },
                icon = { Icon(item.second, item.first) },
                label = { Text(item.first) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    sounds: List<SoundItem>,
    query: String,
    onQuery: (String) -> Unit,
    add: () -> Unit,
    play: (SoundItem) -> Unit,
    fav: (SoundItem) -> Unit
) {
    val filtered = sounds.filter { it.title.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your sounds") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Soundboard", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${sounds.size} sounds", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = add) { Icon(Icons.Default.AddCircle, "Add sound", tint = Accent) }
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) EmptyState(add)
        else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { SoundCard(it, play, fav) }
        }
    }
}

@Composable
private fun SoundCard(item: SoundItem, play: (SoundItem) -> Unit, fav: (SoundItem) -> Unit) {
    Box(
        Modifier.height(150.dp).background(Card, RoundedCornerShape(20.dp)).clickable { play(item) }.padding(12.dp)
    ) {
        IconButton(onClick = { fav(item) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Icon(
                if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                null,
                tint = if (item.favorite) Accent else Muted,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔊", fontSize = 34.sp)
            Spacer(Modifier.height(7.dp))
            Text(item.title.take(18), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(item.board.uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("TAP TO PLAY", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(add: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎛️", fontSize = 54.sp)
        Spacer(Modifier.height(12.dp))
        Text("Your soundboard is empty", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Import your first meme, reaction or game sound.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = add, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("ADD SOUND", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiscoverScreen(sounds: List<SoundItem>, add: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Discover", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your local sound library, organized for fast access.", color = Muted, fontSize = 12.sp)
        }
        item { DiscoverCard("🔥 Recent", "${sounds.size} sounds in your library", Icons.Default.History) }
        item { DiscoverCard("😂 Memes", "Quick reaction clips", Icons.Default.EmojiEmotions) }
        item { DiscoverCard("🎮 Gaming", "Fast sounds for game sessions", Icons.Default.SportsEsports) }
        item { DiscoverCard("❤️ Favorites", "${sounds.count { it.favorite }} saved sounds", Icons.Default.Favorite) }
        item {
            Button(
                onClick = add,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("IMPORT AUDIO", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DiscoverCard(title: String, subtitle: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BoardsScreen(sounds: List<SoundItem>, add: () -> Unit, play: (SoundItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Soundboards", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Switch between gaming, memes and recordings.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        listOf("Gaming", "Memes", "Reactions", "Recordings").forEach { board ->
            val boardSounds = sounds.filter { it.board == board }
            BoardRow(board, boardSounds, play)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("IMPORT SOUND")
        }
    }
}

@Composable
private fun BoardRow(name: String, sounds: List<SoundItem>, play: (SoundItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${sounds.size}", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(7.dp))
        if (sounds.isEmpty()) {
            Text("No sounds yet", color = Muted, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sounds.take(4).forEach { item ->
                    AssistChip(
                        onClick = { play(item) },
                        label = { Text(item.title.take(9)) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioScreen(recording: Boolean, toggle: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Studio", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Record voice clips and keep them local.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(50.dp))
        Box(
            Modifier.size(180.dp).background(if (recording) Accent.copy(alpha = .18f) else Card, RoundedCornerShape(90.dp)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = toggle,
                modifier = Modifier.size(110.dp).background(if (recording) Color.Red else Accent, RoundedCornerShape(55.dp))
            ) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (recording) "RECORDING..." else "TAP TO RECORD", color = if (recording) Color.Red else Accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Your recordings are stored in Voicely's app-specific music folder.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsScreen(
    gameMode: Boolean,
    onStartGame: () -> Unit,
    onStopGame: () -> Unit,
    onOverlay: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Control permissions and Game Mode.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        SettingRow("🎮 Game Mode", if (gameMode) "Overlay active" else "Ready") {
            if (gameMode) onStopGame() else onStartGame()
        }
        SettingRow("🫧 Overlay permission", "Required for floating controls", onOverlay)
        SettingRow("🎤 Microphone", "Used by Studio when you record", null)
        SettingRow("🔌 USB audio", "Optional hardware-assisted routing", null)
        Spacer(Modifier.height(24.dp))
        Text("Voicely", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Sound. React. Dominate.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Local-first soundboard. No account required for the core features.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        if (action != null) TextButton(onClick = action) { Text("OPEN", color = Accent, fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(10.dp))
}
