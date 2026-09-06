package io.github.burootro.metamorph.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.widget.Toast

/**
 * تشغيل صوت الفيديو في الخلفية بمشغّل مستقل،
 * يعمل حتى بعد إغلاق فيسبوك أو إطفاء الشاشة.
 */
class AudioService : Service() {

    private var player: MediaPlayer? = null
    private var title: String = "Metamorph"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                title = intent.getStringExtra(EXTRA_TITLE) ?: "فيديو فيسبوك"

                if (url.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startPlayback(url)
            }

            ACTION_TOGGLE -> togglePlayback()

            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startPlayback(url: String) {

        stopPlayback()

        startForeground(NOTIFICATION_ID, buildNotification(loading = true))

        player = MediaPlayer().apply {

            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )

            setOnPreparedListener {
                it.start()
                updateNotification()
            }

            setOnCompletionListener {
                stopPlayback()
                stopSelf()
            }

            setOnErrorListener { _, what, _ ->
                Toast.makeText(
                    this@AudioService,
                    "تعذّر التشغيل ($what)",
                    Toast.LENGTH_SHORT
                ).show()
                stopSelf()
                true
            }

            runCatching {
                setDataSource(
                    this@AudioService,
                    android.net.Uri.parse(url),
                    mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://www.facebook.com/"
                    )
                )
                prepareAsync()
            }.onFailure {
                stopSelf()
            }
        }
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.start()
        updateNotification()
    }

    private fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    // ---------- الإشعار ----------

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(loading = false))
    }

    private fun buildNotification(loading: Boolean): Notification {

        createChannel()

        val playing = player?.isPlaying == true

        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, AudioService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, AudioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(
                when {
                    loading -> "جارٍ التحضير…"
                    playing -> "قيد التشغيل"
                    else -> "متوقّف"
                }
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (playing) "إيقاف مؤقت" else "تشغيل",
                    toggle
                ).build()
            )
            .addAction(
                Notification.Action.Builder(null, "إغلاق", stop).build()
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "التشغيل في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val ACTION_PLAY = "io.github.burootro.metamorph.PLAY"
        const val ACTION_TOGGLE = "io.github.burootro.metamorph.TOGGLE"
        const val ACTION_STOP = "io.github.burootro.metamorph.STOP"

        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        private const val CHANNEL_ID = "metamorph_playback"
        private const val NOTIFICATION_ID = 4201

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
