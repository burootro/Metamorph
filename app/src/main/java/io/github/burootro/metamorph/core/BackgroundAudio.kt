package io.github.burootro.metamorph.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.XposedBridge

/**
 * مشغّل صوت يعمل داخل عملية فيسبوك نفسها،
 * فلا تنطبق عليه قيود تشغيل الخدمات بين التطبيقات.
 */
object BackgroundAudio {

    private var player: MediaPlayer? = null
    private var registered = false
    private var title = "فيديو فيسبوك"

    private val main = Handler(Looper.getMainLooper())

    fun play(ctx: Context, url: String, label: String) {

        title = label
        val app = ctx.applicationContext

        register(app)
        stop(app, silent = true)

        toast(app, "جارٍ التحضير…")

        player = MediaPlayer().apply {

            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )

            setOnPreparedListener {
                it.start()
                notify(app)
            }

            setOnCompletionListener { stop(app, silent = false) }

            setOnErrorListener { _, what, _ ->
                log("خطأ في التشغيل: $what")
                toast(app, "تعذّر التشغيل")
                stop(app, silent = true)
                true
            }

            runCatching {
                setDataSource(
                    app,
                    Uri.parse(url),
                    mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "https://www.facebook.com/"
                    )
                )
                prepareAsync()
            }.onFailure {
                log("فشل التحضير: ${it.message}")
                toast(app, "تعذّر التشغيل")
            }
        }
    }

    private fun toggle(ctx: Context) {
        val p = player ?: return
        runCatching {
            if (p.isPlaying) p.pause() else p.start()
            notify(ctx)
        }
    }

    fun stop(ctx: Context, silent: Boolean) {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null

        runCatching {
            manager(ctx).cancel(NOTIFICATION_ID)
        }

        if (!silent) toast(ctx, "تم إيقاف التشغيل")
    }

    // ---------- الإشعار وأزرار التحكّم ----------

    private fun register(ctx: Context) {
        if (registered) return
        registered = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_TOGGLE -> toggle(ctx)
                    ACTION_STOP -> stop(ctx, silent = false)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE)
            addAction(ACTION_STOP)
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                ctx.registerReceiver(receiver, filter)
            }
        }.onFailure { log("فشل تسجيل المستقبل: ${it.message}") }
    }

    private fun notify(ctx: Context) {
        runCatching {

            channel(ctx)

            val playing = player?.isPlaying == true

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(ctx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }

            val note = builder
                .setContentTitle(title)
                .setContentText(if (playing) "قيد التشغيل" else "متوقّف مؤقتًا")
                .setSubText("Metamorph")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(playing)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        if (playing) "إيقاف مؤقت" else "تشغيل",
                        intentFor(ctx, ACTION_TOGGLE, 1)
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        null, "إغلاق", intentFor(ctx, ACTION_STOP, 2)
                    ).build()
                )
                .build()

            manager(ctx).notify(NOTIFICATION_ID, note)

        }.onFailure { log("فشل الإشعار: ${it.message}") }
    }

    private fun intentFor(ctx: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(action).setPackage(ctx.packageName)
        return PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val m = manager(ctx)
        if (m.getNotificationChannel(CHANNEL_ID) != null) return

        m.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Metamorph — التشغيل في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun manager(ctx: Context) =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun toast(ctx: Context, msg: String) {
        main.post { runCatching { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } }
    }

    private fun log(msg: String) = XposedBridge.log("[Metamorph][Audio] $msg")

    private const val CHANNEL_ID = "metamorph_playback"
    private const val NOTIFICATION_ID = 4201

    private const val ACTION_TOGGLE = "io.github.burootro.metamorph.TOGGLE"
    private const val ACTION_STOP = "io.github.burootro.metamorph.STOP"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
}
