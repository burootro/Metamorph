package io.github.burootro.metamorph.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Downloader {

    private const val FOLDER = "Metamorph"
    private val main = Handler(Looper.getMainLooper())

    /**
     * ينزّل الملف داخل عملية فيسبوك نفسها،
     * لأن روابط fbcdn ترفض الطلبات الخارجية.
     */
    fun download(ctx: Context, url: String, fileName: String, title: String = fileName) {

        toast(ctx, "بدأ التنزيل…")

        Thread {
            val result = runCatching { fetchAndSave(ctx, url, fileName) }

            val message = when {
                result.isSuccess && result.getOrNull() == true ->
                    "تم الحفظ في Movies/$FOLDER"

                else -> {
                    log("فشل: ${result.exceptionOrNull()?.message}")
                    "فشل التنزيل"
                }
            }

            main.post { toast(ctx, message) }
        }.start()
    }

    private fun fetchAndSave(ctx: Context, url: String, fileName: String): Boolean {

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Referer", "https://www.facebook.com/")
            connect()
        }

        log("رمز الاستجابة: ${conn.responseCode}")

        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return false
        }

        val ok = conn.inputStream.use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(ctx, fileName) { out -> input.copyTo(out) }
            } else {
                saveViaFile(fileName) { out -> input.copyTo(out) }
            }
        }

        conn.disconnect()
        return ok
    }

    private fun saveViaMediaStore(
        ctx: Context,
        fileName: String,
        write: (java.io.OutputStream) -> Unit
    ): Boolean {

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$FOLDER"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false

        resolver.openOutputStream(uri)?.use(write) ?: return false

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return true
    }

    private fun saveViaFile(
        fileName: String,
        write: (java.io.OutputStream) -> Unit
    ): Boolean {

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            FOLDER
        )
        if (!dir.exists()) dir.mkdirs()

        FileOutputStream(File(dir, fileName)).use(write)
        return true
    }

    fun videoFileName(videoId: String?): String {
        val id = videoId?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        return "FB_$id.mp4"
    }

    private fun toast(ctx: Context, msg: String) {
        runCatching { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun log(msg: String) = XposedBridge.log("[Metamorph][Downloader] $msg")

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
}
