package io.github.burootro.metamorph.features

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature

/**
 * استكشاف — يرصد من يستدعي إيقاف الفيديو
 * ويطبع مسار الاستدعاء لنعرف أين نتدخّل.
 */
class BackgroundProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "BackgroundProbe"
    override val key = "background_probe"

    private var count = 0

    override fun doHook() {

        // مشغّل الفيديو الأساسي في فيسبوك
        val candidates = listOf(
            "com.facebook.video.engine.api.VideoPlayer",
            "com.facebook.exoplayer.ipc.VideoPlayerServiceApi",
            "com.facebook.video.heroplayer.ipc.VideoPlayRequest"
        )

        candidates.forEach { name ->
            val cls = XposedHelpers.findClassIfExists(name, classLoader)
            log(if (cls != null) "✓ موجود: $name" else "✗ غير موجود: $name")
        }

        // نراقب أي دالة اسمها pause في حزم الفيديو
        hookByName("com.facebook.video")

        log("جاهز — شغّل فيديو ثم اخرج من التطبيق")
    }

    private fun hookByName(prefix: String) {

        // ExoPlayer هو المحرّك الفعلي — نراقب setPlayWhenReady
        val exo = XposedHelpers.findClassIfExists(
            "com.google.android.exoplayer2.ExoPlayerImpl",
            classLoader
        ) ?: XposedHelpers.findClassIfExists(
            "com.facebook.video.heroplayer.service.HeroPlayerService",
            classLoader
        )

        if (exo == null) {
            log("لم يُعثر على محرّك التشغيل — سنعتمد على مسار آخر")
        } else {
            log("✓ محرّك التشغيل: ${exo.name}")
        }

        // الطريقة الأوسع: مراقبة دورة حياة الشاشة
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", classLoader, "onPause",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (count >= 1) return
                        count++

                        log("==== الخروج من التطبيق ====")
                        log("الشاشة: ${param.thisObject.javaClass.name}")
                        printStack()
                        log("==== انتهى ====")
                    }
                }
            )
        }.onFailure { log("فشل هوك onPause: ${it.message}") }
    }

    private fun printStack() {
        val stack = Throwable().stackTrace
        stack.take(30).forEach { frame ->
            val line = "${frame.className}.${frame.methodName}"
            if (!line.startsWith("de.robv") && !line.startsWith("io.github")) {
                log("  $line")
            }
        }
    }
}
