package io.github.burootro.metamorph.features

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature
import java.util.concurrent.atomic.AtomicInteger

/**
 * استكشاف داخل عملية محرّك التشغيل،
 * لمعرفة أي دالة توقف الفيديو عند الخروج من التطبيق.
 */
class HeroProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "HeroProbe"
    override val key = "background_play"

    private val logged = AtomicInteger(0)

    override fun doHook() {

        val candidates = listOf(
            "com.facebook.video.heroplayer.service.HeroPlayerService",
            "com.facebook.video.heroplayer.service.HeroServicePlayerHandler",
            "com.facebook.video.heroplayer.ipc.VideoPlayRequest",
            "com.facebook.video.heroplayer.setting.HeroPlayerSetting",
            "com.facebook.video.heroplayer.service.HeroServiceClient"
        )

        val found = mutableListOf<Class<*>>()

        candidates.forEach { cn ->
            val cls = XposedHelpers.findClassIfExists(cn, classLoader)
            if (cls != null) {
                found.add(cls)
                log("✓ $cn")
            } else {
                log("✗ $cn")
            }
        }

        if (found.isEmpty()) {
            log("لم يُعثر على أي كلاس — المحرّك مشفّر بالكامل")
            return
        }

        // نراقب الدوال التي يُرجَّح أنها توقف التشغيل
        found.forEach { cls -> watch(cls) }

        log("جاهز — شغّل فيديو ثم اضغط Home")
    }

    private fun watch(cls: Class<*>) {

        val interesting = listOf("pause", "stop", "release", "setPlayWhenReady", "A00", "A01")

        cls.declaredMethods.forEach { method ->

            val match = interesting.any { method.name.equals(it, ignoreCase = true) }
            if (!match) return@forEach

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {

                        if (logged.get() >= 40) return
                        logged.incrementAndGet()

                        log("→ ${cls.simpleName}.${method.name}(${param.args.size})")
                    }
                })
            }
        }

        log("تمت مراقبة ${cls.simpleName}")
    }
}
