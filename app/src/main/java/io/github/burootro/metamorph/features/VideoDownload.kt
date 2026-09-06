package io.github.burootro.metamorph.features

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
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
            log("✓ التقاط ${videoId ?: "?"}")
        }
    }

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

    // ---------- 2) زر التنزيل العائم ----------

    private fun hookVideoViews() {

        val listener = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                view.post { placeButton(view) }
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

        log("تم تركيب الهوك")
    }

    /**
     * نصعد في شجرة الآباء حتى نجد أول حاوية تدعم التراكب،
     * لأن فيديوهات الفيد تُرسم داخل LithoView لا FrameLayout.
     */
    private fun placeButton(surface: View) {
        runCatching {

            var parent = surface.parent
            var level = 0

            while (parent is ViewGroup && level < 5) {

                if (canOverlay(parent)) {
                    attachButton(parent)
                    return
                }

                parent = parent.parent
                level++
            }

            log("لم يُعثر على حاوية مناسبة")
        }
    }

    /** الحاويات التي ترسم أبناءها فوق بعضها */
    private fun canOverlay(group: ViewGroup): Boolean {
        val n = group.javaClass.name
        return group is FrameLayout ||
            n.endsWith("RelativeLayout") ||
            n.contains("LithoView") ||
            n.contains("ComponentHost") ||
            n.contains("VideoPlayer") ||
            n.contains("RichVideo")
    }

    private fun attachButton(container: ViewGroup) {

        if (container.getTag(TAG_ID) == true) return
        container.setTag(TAG_ID, true)

        val ctx = container.context
        val button = buildButton(ctx)

        val margin = dp(ctx, 12)

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(margin, margin, margin, margin)
        }

        button.setOnClickListener { v ->
            val url = Registry.url

            if (url.isNullOrBlank()) {
                Toast.makeText(v.context, "شغّل الفيديو أولًا", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
        }

        runCatching {
            container.addView(button, params)
            container.clipChildren = false
        }
    }

    private fun buildButton(ctx: Context): TextView {
        return TextView(ctx).apply {
            text = "⤓"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            val size = dp(ctx, 36)
            layoutParams = ViewGroup.LayoutParams(size, size)
            width = size
            height = size

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B3000000"))
            }

            elevation = dp(ctx, 8).toFloat()
            isClickable = true
            alpha = 0.9f
        }
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    private object Registry {
        @Volatile var url: String? = null
        @Volatile var videoId: String? = null
    }

    companion object {
        private const val TAG_ID = 0x7F5A0003
    }
}
