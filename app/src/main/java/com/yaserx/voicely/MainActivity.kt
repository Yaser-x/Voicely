package com.yaserx.voicely

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID

private val Bg = Color(0xFF08090C)
private val Panel = Color(0xFF111319)
private val Card = Color(0xFF171A21)
private val Accent = Color(0xFFFFB800)
private val Muted = Color(0xFF8B909B)

private data class SoundItem(val id: String, val title: String, val uri: String, val favorite: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioEngine.init(this)
        setContent { VoicelyApp() }
    }

    override fun onDestroy() {
        AudioEngine.release()
        super.onDestroy()
    }
}

private object Store {
    private lateinit var prefs: SharedPreferences
    fun init(context: Context) { prefs = context.getSharedPreferences("voicely", Context.MODE_PRIVATE) }
    fun sounds(): List<SoundItem> {
        val raw = prefs.getString("sounds", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\\n").mapNotNull { row ->
            val p = row.split("|", limit = 4)
            if (p.size == 4) SoundItem(p[0], p[1], p[2], p[3] == "1") else null
        }
    }
    fun save(list: List<SoundItem>) { prefs.edit().putString("sounds", list.joinToString("\\n") { "${it.id}|${it.title.replace("|", " ")}|${it.uri}|${if (it.favorite) 1 else 0}" }).apply() }
}

private object AudioEngine {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private val pending = mutableSetOf<String>()
    fun init(context: Context) { Store.init(context); pool = SoundPool.Builder().setMaxStreams(8).build() }
    fun load(context: Context, item: SoundItem) {
        if (ids.containsKey(item.id) || pending.contains(item.id)) return
        pending += item.id
        try {
            val afd = context.contentResolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")
            if (afd != null) {
                val soundId = pool?.load(afd.fileDescriptor, afd.startOffset, afd.length, 1) ?: 0
                ids[item.id] = soundId
                afd.close()
            }
        } catch (_: Exception) { }
        pending -= item.id
    }
    fun play(context: Context, item: SoundItem) {
        load(context, item)
        val id = ids[item.id] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }
    fun unload(id: String) { ids.remove(id)?.let { pool?.unload(it) } }
    fun release() { pool?.release(); pool = null; ids.clear() }
}

@Composable
private fun VoicelyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as Activity
    var tab by remember { mutableIntStateOf(0) }
    var sounds by remember { mutableStateOf(Store.sounds()) }
    var query by remember { mutableStateOf("") }
    var gameMode by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.replace('_', ' ')?.ifBlank { "New Sound" } ?: "New Sound"
        val item = SoundItem(UUID.randomUUID().toString(), title, uri.toString())
        sounds = sounds + item
        Store.save(sounds)
        AudioEngine.load(context, item)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        Scaffold(
            containerColor = Bg,
            topBar = { Header(onGameMode = {
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    context.startService(Intent(context, FloatingService::class.java))
                    gameMode = true
                }
            }) },
            bottomBar = { BottomNav(tab) { tab = it } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(sounds, query, { query = it }, { picker.launch(arrayOf("audio/*")) }, { item -> AudioEngine.play(context, item) }, { item ->
                        sounds = sounds.map { if (it.id == item.id) it.copy(favorite = !it.favorite) else it }; Store.save(sounds)
                    })
                    1 -> DiscoverScreen(sounds, { picker.launch(arrayOf("audio/*")) })
                    2 -> BoardsScreen(sounds, { picker.launch(arrayOf("audio/*")) }, { item -> AudioEngine.play(context, item) })
                    3 -> StudioScreen(recording, {
                        if (!recording) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val file = java.io.File(context.cacheDir, "voicely_${System.currentTimeMillis()}.m4a")
                                recorder = MediaRecorder(context).apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }
                                recording = true
                            }
                        } else {
                            try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }; recorder = null; recording = false
                        }
                    })
                    4 -> MeScreen(gameMode, { gameMode = false; context.stopService(Intent(context, FloatingService::class.java)) })
                }
            }
        }
    }
}

@Composable private fun Header(onGameMode: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("VOICELY", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("Gaming soundboard", color = Muted, fontSize = 11.sp) }
        FilledTonalButton(onClick = onGameMode, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Color.Black), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Icon(Icons.Default.SportsEsports, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("GAME", fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun BottomNav(tab: Int, select: (Int) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Discover" to Icons.Default.Search, "Boards" to Icons.Default.GridView, "Studio" to Icons.Default.Mic, "Me" to Icons.Default.Settings)
    NavigationBar(containerColor = Color(0xFF0D0F13)) { items.forEachIndexed { i, item -> NavigationBarItem(selected = tab == i, onClick = { select(i) }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }) } }
}

@Composable private fun HomeScreen(sounds: List<SoundItem>, query: String, onQuery: (String) -> Unit, add: () -> Unit, play: (SoundItem) -> Unit, fav: (SoundItem) -> Unit) {
    val filtered = sounds.filter { it.title.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search your sounds") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Gaming", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("${sounds.size} sounds", color = Muted, fontSize = 12.sp) }; IconButton(onClick = add) { Icon(Icons.Default.AddCircle, "Add sound", tint = Accent) } }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) EmptyState(add) else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered) { SoundCard(it, play, fav) } }
    }
}

@Composable private fun SoundCard(item: SoundItem, play: (SoundItem) -> Unit, fav: (SoundItem) -> Unit) {
    Box(Modifier.height(145.dp).background(Card, RoundedCornerShape(20.dp)).clickable { play(item) }.padding(12.dp)) {
        IconButton(onClick = { fav(item) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) { Icon(if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (item.favorite) Accent else Muted, modifier = Modifier.size(18.dp)) }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("🔊", fontSize = 34.sp); Spacer(Modifier.height(7.dp)); Text(item.title.take(18), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("TAP TO PLAY", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun EmptyState(add: () -> Unit) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("🎛️", fontSize = 54.sp); Spacer(Modifier.height(12.dp)); Text("Your soundboard is empty", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Import your first meme, reaction or game sound.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(18.dp)); Button(onClick = add, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ADD SOUND", fontWeight = FontWeight.Bold) } } }

@Composable private fun DiscoverScreen(sounds: List<SoundItem>, add: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Discover", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Build your own library from audio you have permission to use.", color = Muted, fontSize = 12.sp) }; item { DiscoverCard("🔥 Trending", "Your imported sounds, sorted by recent use", Icons.Default.Whatshot) }; item { DiscoverCard("😂 Memes", "Keep your funniest clips in one board", Icons.Default.EmojiEmotions) }; item { DiscoverCard("🎮 Gaming", "Quick reactions for game sessions", Icons.Default.SportsEsports) }; item { Button(onClick = add, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Upload, null); Spacer(Modifier.width(8.dp)); Text("IMPORT AUDIO", fontWeight = FontWeight.Bold) } } } }

@Composable private fun DiscoverCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) } } }

@Composable private fun BoardsScreen(sounds: List<SoundItem>, add: () -> Unit, play: (SoundItem) -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Soundboards", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Organize sounds for different games and moods.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(18.dp)); BoardRow("🎮 Gaming", sounds.size, sounds, play); BoardRow("😂 Memes", sounds.size, sounds, play); BoardRow("💀 Reactions", sounds.size, sounds, play); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ADD SOUND TO CURRENT BOARD") } } }

@Composable private fun BoardRow(name: String, count: Int, sounds: List<SoundItem>, play: (SoundItem) -> Unit) { Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("$count", color = Muted, fontSize = 12.sp) }; if (sounds.isNotEmpty()) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { sounds.take(3).forEach { item -> AssistChip(onClick = { play(item) }, label = { Text(item.title.take(10)) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }) } } } } }

@Composable private fun StudioScreen(recording: Boolean, toggle: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Studio", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Record your voice for personal clips.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(50.dp)); Box(Modifier.size(180.dp).background(if (recording) Accent.copy(alpha = .18f) else Card, RoundedCornerShape(90.dp)), contentAlignment = Alignment.Center) { IconButton(onClick = toggle, modifier = Modifier.size(110.dp).background(if (recording) Color.Red else Accent, RoundedCornerShape(55.dp))) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(42.dp)) } }; Spacer(Modifier.height(20.dp)); Text(if (recording) "RECORDING..." else "TAP TO RECORD", color = if (recording) Color.Red else Accent, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); Text("Recordings are stored locally in the app cache until you export or add them to a board.", color = Muted, fontSize = 11.sp) } }

@Composable private fun MeScreen(gameMode: Boolean, stopGame: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); SettingRow("🎮 Game Mode", if (gameMode) "Overlay active" else "Ready", if (gameMode) stopGame else null); SettingRow("🎤 Microphone", "Used only for Studio recording", null); SettingRow("🔌 USB Audio", "Hardware mixer support", null); SettingRow("⚡ Performance", "Low-latency playback engine", null); Spacer(Modifier.height(20.dp)); Text("Voicely 1.0 • Built for gaming", color = Muted, fontSize = 11.sp) } }

@Composable private fun SettingRow(title: String, subtitle: String, action: (() -> Unit)?) { Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) }; if (action != null) TextButton(onClick = action) { Text("STOP", color = Accent) } } Spacer(Modifier.height(10.dp)) }

class FloatingService : android.app.Service() {
    private var wm: WindowManager? = null
    private var view: android.view.View? = null
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(101, notification()); showBubble() }
    private fun createChannel() { val nm = getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel("voicely_game", "Voicely Game Mode", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification() = NotificationCompat.Builder(this, "voicely_game").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Voicely Game Mode").setContentText("Floating soundboard is active").setOngoing(true).build()
    private fun showBubble() { if (!Settings.canDrawOverlays(this)) return; wm = getSystemService(WINDOW_SERVICE) as WindowManager; val b = android.widget.TextView(this).apply { text = "🎛"; textSize = 25f; gravity = Gravity.CENTER; setBackgroundColor(0xEE171A21.toInt()); setOnClickListener { openApp() } }; view = b; val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE; val p = WindowManager.LayoutParams(64, 64, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 12; y = 0 }; wm?.addView(b, p) }
    private fun openApp() { val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i) }
    override fun onDestroy() { view?.let { wm?.removeView(it) }; view = null; super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
