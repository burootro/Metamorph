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
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
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
            Registry.time = System.currentTimeMillis()

            showButton()
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

    // ---------- 3) الزر العائم ----------

    private fun showButton() {

        val activity = Registry.activity?.get() ?: return

        activity.runOnUiThread {
            runCatching {

                val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runCatching

                // إن كان الزر موجودًا بالفعل نكتفي بإظهاره
                val existing = root.findViewWithTag<TextView>(TAG)
                if (existing != null) {
                    existing.visibility = android.view.View.VISIBLE
                    return@runCatching
                }

                val ctx = activity
                val button = buildButton(ctx)
                button.tag = TAG

                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = dp(ctx, 16)
                    bottomMargin = dp(ctx, 100)
                }

                button.setOnClickListener { v -> onClick(v.context) }

                root.addView(button, params)
            }
        }
    }

    private fun onClick(ctx: Context) {

        val url = Registry.url

        if (url.isNullOrBlank()) {
            Toast.makeText(ctx, "لا يوجد فيديو", Toast.LENGTH_SHORT).show()
            return
        }

        val ok = Downloader.download(
            ctx = ctx,
            url = url,
            fileName = Downloader.videoFileName(Registry.videoId),
            title = "فيديو فيسبوك"
        )

        Toast.makeText(
            ctx,
            if (ok) "جارٍ التنزيل…" else "فشل التنزيل",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildButton(ctx: Context): TextView {
        return TextView(ctx).apply {
            text = "⤓"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            val size = dp(ctx, 46)
            width = size
            height = size

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E65B4A9E"))
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
        @Volatile var time: Long = 0
        @Volatile var activity: WeakReference<Activity>? = null
    }

    companion object {
        private const val TAG = "metamorph_dl"
    }
}
