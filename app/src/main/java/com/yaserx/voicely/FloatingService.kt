package com.yaserx.voicely

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Floating in-game controller.
 *
 * This service only controls Voicely's own audio playback. Android does not
 * expose a public API for injecting this audio into another app's microphone.
 */
class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<String, Int>()
    private val sounds = mutableListOf<OverlaySound>()

    private data class OverlaySound(val id: String, val title: String, val uri: String)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        pool = SoundPool.Builder().setMaxStreams(8).build()
        loadSavedSounds()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null && Settings.canDrawOverlays(this)) showBubble()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.yaserx.voicely.R.drawable.ic_stat_voicely)
            .setContentTitle("Voicely Game Mode")
            .setContentText("Floating soundboard is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Voicely Game Mode",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Keeps the Voicely gaming controller active" }
            )
        }
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 160
        }
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return

        val view = TextView(this).apply {
            text = "🎛"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(255, 184, 0))
            setPadding(8, 8, 8, 8)
            elevation = 20f
            setOnClickListener { togglePanel() }
            setOnTouchListener(DragTouchListener(this@FloatingService))
        }

        bubble = view
        windowManager.addView(view, overlayParams(dp(54), dp(54)))
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(17, 19, 25))
            elevation = 24f
        }

        val title = TextView(this).apply {
            text = "VOICELY  •  GAME MODE"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        root.addView(title)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        sounds.take(6).forEach { sound ->
            val button = TextView(this).apply {
                text = sound.title.take(9)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 10f
                setBackgroundColor(Color.rgb(35, 39, 48))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { play(sound) }
            }
            row.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        root.addView(row)

        val close = TextView(this).apply {
            text = "CLOSE"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 184, 0))
            textSize = 10f
            setPadding(0, dp(8), 0, dp(2))
            setOnClickListener { removePanel() }
        }
        root.addView(close)

        panel = root
        windowManager.addView(root, overlayParams(dp(300), dp(105)))
    }

    private fun removePanel() {
        panel?.let {
            runCatching { windowManager.removeView(it) }
        }
        panel = null
    }

    private fun loadSavedSounds() {
        val prefs = getSharedPreferences("voicely", Context.MODE_PRIVATE)
        val raw = prefs.getString("sounds", "") ?: return
        raw.split("\\n").forEach { row ->
            val p = row.split("|", limit = 4)
            if (p.size == 4 && p[0].isNotBlank() && p[2].isNotBlank()) {
                sounds += OverlaySound(p[0], p[1], p[2])
            }
        }
    }

    private fun play(sound: OverlaySound) {
        val existing = loaded[sound.id]
        if (existing != null) {
            pool?.play(existing, 1f, 1f, 1, 0, 1f)
            return
        }

        runCatching {
            val afd = contentResolver.openAssetFileDescriptor(Uri.parse(sound.uri), "r") ?: return
            val id = pool?.load(afd.fileDescriptor, afd.startOffset, afd.length, 1) ?: 0
            afd.close()
            if (id != 0) {
                loaded[sound.id] = id
                pool?.setOnLoadCompleteListener { p, sampleId, status ->
                    if (status == 0 && sampleId == id) p.play(sampleId, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        pool?.release()
        pool = null
        loaded.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class DragTouchListener(private val service: FloatingService) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = v.layoutParams as? WindowManager.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = startX - dx
                    params.y = startY + dy
                    service.windowManager.updateViewLayout(v, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) v.performClick()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "voicely_game_mode"
        private const val NOTIFICATION_ID = 4101
    }
}
