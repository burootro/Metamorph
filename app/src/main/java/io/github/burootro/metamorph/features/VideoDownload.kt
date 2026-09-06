package io.github.burootro.metamorph.features

import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Downloader
import io.github.burootro.metamorph.core.Feature
import java.lang.reflect.Modifier

class VideoDownload(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "VideoDownload"
    override val key = "video_download"

    override fun doHook() {
        hookParams()
        hookVideoViews()
    }

    // ---------- 1) التقاط الرابط ----------

    private fun hookParams() {

        val paramsClass = XposedHelpers.findClassIfExists(
            "com.facebook.video.engine.api.VideoPlayerParams",
            classLoader
        ) ?: run {
            log("VideoPlayerParams غير موجود")
            return
        }

        paramsClass.declaredConstructors.forEach { ctor ->
            runCatching {
                XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        capture(param.thisObject)
                    }
                })
            }
        }
    }

    /** نبحث بالنوع لا بالاسم — أسماء الحقول تتغيّر كل تحديث */
    private fun capture(params: Any?) {
        if (params == null) return

        runCatching {
            var videoId: String? = null
            var dataSource: Any? = null

            params.javaClass.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers)) return@forEach
                field.isAccessible = true
                val value = field.get(params) ?: return@forEach

                when {
                    value.javaClass.simpleName == "VideoDataSource" -> dataSource = value

                    value is String && value.length in 8..40 && value.all { it.isDigit() } ->
                        if (videoId == null) videoId = value
                }
            }

            val source = dataSource ?: return@runCatching
            val uri = findUri(source) ?: return@runCatching

            Registry.url = uri.toString()
            Registry.videoId = videoId
            log("تم التقاط الفيديو ${videoId ?: ""}")
        }
    }

    /** يعيد أول Uri يشير إلى ملف فيديو */
    private fun findUri(source: Any): Uri? {
        source.javaClass.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach
            runCatching {
                field.isAccessible = true
                val value = field.get(source)
                if (value is Uri && value.toString().startsWith("http")) {
                    return value
                }
            }
        }
        return null
    }

    // ---------- 2) الضغط المطوّل على الفيديو ----------

    private fun hookVideoViews() {

        val listener = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                attachToParent(view)
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                TextureView::class.java, "onAttachedToWindow", listener
            )
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                SurfaceView::class.java, "onAttachedToWindow", listener
            )
        }

        log("تم تركيب الهوك على مشغّل الفيديو")
    }

    /** نركّب المستمع على الحاوية لا على السطح نفسه */
    private fun attachToParent(view: View) {
        runCatching {
            val target: View = (view.parent as? ViewGroup) ?: view
            if (target.getTag(TAG_ID) == true) return
            target.setTag(TAG_ID, true)

            target.setOnLongClickListener { v ->
                val url = Registry.url

                if (url.isNullOrBlank()) {
                    Toast.makeText(v.context, "لم يتم التقاط الفيديو بعد", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnLongClickListener true
                }

                val ok = Downloader.download(
                    ctx = v.context,
                    url = url,
                    fileName = Downloader.videoFileName(Registry.videoId),
                    title = "فيديو فيسبوك"
                )

                Toast.makeText(
                    v.context,
                    if (ok) "جارٍ التنزيل…" else "فشل التنزيل",
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }
    }

    private object Registry {
        @Volatile var url: String? = null
        @Volatile var videoId: String? = null
    }

    companion object {
        private const val TAG_ID = 0x7F5A0003
    }
}
