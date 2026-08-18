package com.yaserx.voicely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

private val Bg = Color(0xFF09090B)
private val Card = Color(0xFF151519)
private val Accent = Color(0xFFFFB800)

private data class Sound(val title: String, val emoji: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoicelyApp() }
    }
}

@Composable
private fun VoicelyApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sounds = remember {
        listOf(
            Sound("BRUH", "😂"), Sound("WHAT", "❓"), Sound("SUS", "🗿"),
            Sound("LOL", "🤣"), Sound("NO WAY", "💀"), Sound("AIRHORN", "📢"),
            Sound("CLAP", "👏"), Sound("WOW", "🔥")
        )
    }

    MaterialTheme {
        Scaffold(
            containerColor = Bg,
            topBar = { Header() },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0E0E11)) {
                    val items = listOf(
                        "Home" to Icons.Default.Home,
                        "Discover" to Icons.Default.Search,
                        "Boards" to Icons.Default.SurroundSound,
                        "Studio" to Icons.Default.Mic,
                        "Me" to Icons.Default.Settings
                    )
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(item.second, contentDescription = item.first) },
                            label = { Text(item.first) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                BoardHeader()
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(sounds) { sound -> SoundCard(sound) }
                    item { AddSoundCard() }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Bg).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("VOICELY", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Soundboard for gamers", color = Color.Gray, fontSize = 11.sp)
        }
        IconButton(onClick = {}) { Icon(Icons.Default.FavoriteBorder, "Favorites", tint = Color.White) }
    }
}

@Composable
private fun BoardHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Gaming", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("8 sounds", color = Color.Gray, fontSize = 12.sp)
        }
        IconButton(onClick = {}) { Icon(Icons.Default.Add, "Add sound", tint = Accent) }
    }
}

@Composable
private fun SoundCard(sound: Sound) {
    Box(
        modifier = Modifier
            .height(132.dp)
            .background(Card, RoundedCornerShape(20.dp))
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sound.emoji, fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Text(sound.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("TAP TO PLAY", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddSoundCard() {
    Box(
        modifier = Modifier.height(132.dp).background(Color(0xFF101014), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(6.dp))
            Text("ADD SOUND", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
