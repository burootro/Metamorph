package io.github.burootro.metamorph.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import de.robv.android.xposed.XposedBridge

object Downloader {

    private const val FOLDER = "Metamorph"

    /**
     * ينزّل الملف عبر مدير التنزيل الخاص بالنظام.
     * لا يحتاج أذونات تخزين، ويُظهر إشعار تقدّم للمستخدم.
     */
    fun download(
        ctx: Context,
        url: String,
        fileName: String,
        title: String = fileName
    ): Boolean {

        return runCatching {

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription("Metamorph")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MOVIES,
                    "$FOLDER/$fileName"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // بعض روابط fbcdn ترفض الطلبات بدون هذه الترويسة
                addRequestHeader("User-Agent", USER_AGENT)
            }

            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            log("بدأ التنزيل: $fileName")
            true

        }.getOrElse {
            log("فشل التنزيل: ${it.message}")
            false
        }
    }

    fun videoFileName(videoId: String?): String {
        val id = videoId?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
        return "FB_$id.mp4"
    }

    private fun log(msg: String) = XposedBridge.log("[Metamorph][Downloader] $msg")

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
}
