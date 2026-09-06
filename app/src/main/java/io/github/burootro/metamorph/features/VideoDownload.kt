package io.github.burootro.metamorph.features

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.BackgroundAudio
import io.github.burootro.metamorph.core.Downloader
import io.github.burootro.metamorph.core.Feature
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

class VideoDownload(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "VideoDownload"
    override val key = "video_download"

    override fun doHook() {
        hookParams()
        hookActivities()
    }

    // ---------- 1) التقاط رابط الفيديو ----------

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

            showButtons()
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

    // ---------- 2) تتبّع الشاشة الحالية ----------

    private fun hookActivities() {

        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as? Application ?: return
                    app.registerActivityLifecycleCallbacks(Tracker)
                }
            }
        )

        log("تم تركيب الهوك")
    }

    private object Tracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            Registry.activity = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    // ---------- 3) الأزرار العائمة ----------

    private fun showButtons() {

        val activity = Registry.activity?.get() ?: return

        activity.runOnUiThread {
            runCatching {

                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    ?: return@runCatching

                val existing = root.findViewWithTag<LinearLayout>(TAG)
                if (existing != null) {
                    existing.visibility = android.view.View.VISIBLE
                    return@runCatching
                }

                val ctx = activity

                val bar = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    tag = TAG
                }

                val download = buildButton(ctx, "⤓", "#E65B4A9E")
                download.setOnClickListener { onDownload(it.context) }

                val listen = buildButton(ctx, "♪", "#E62E7D5B")
                listen.setOnClickListener { onListen(it.context) }

                bar.addView(download)
                bar.addView(
                    listen,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(ctx, 10) }
                )

                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = dp(ctx, 16)
                    bottomMargin = dp(ctx, 100)
                }

                root.addView(bar, params)
            }
        }
    }

    private fun onDownload(ctx: Context) {

        val url = Registry.url

        if (url.isNullOrBlank()) {
            Toast.makeText(ctx, "لا يوجد فيديو", Toast.LENGTH_SHORT).show()
            return
        }

        Downloader.download(
            ctx = ctx,
            url = url,
            fileName = Downloader.videoFileName(Registry.videoId),
            title = "فيديو فيسبوك"
        )
    }

    /** التشغيل داخل عملية فيسبوك — لا قيود على الخدمات هنا */
    private fun onListen(ctx: Context) {

        val url = Registry.url

        if (url.isNullOrBlank()) {
            Toast.makeText(ctx, "لا يوجد فيديو", Toast.LENGTH_SHORT).show()
            return
        }

        BackgroundAudio.play(ctx, url, "فيديو فيسبوك")
    }

    private fun buildButton(ctx: Context, label: String, color: String): TextView {
        return TextView(ctx).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            val size = dp(ctx, 46)
            width = size
            height = size

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }

            elevation = dp(ctx, 10).toFloat()
            isClickable = true
        }
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    private object Registry {
        @Volatile var url: String? = null
        @Volatile var videoId: String? = null
        @Volatile var activity: WeakReference<Activity>? = null
    }

    companion object {
        private const val TAG = "metamorph_bar"
    }
}
