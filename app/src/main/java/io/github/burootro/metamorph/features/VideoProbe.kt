package io.github.burootro.metamorph.features

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature
import java.lang.reflect.Modifier

/**
 * مرحلة استكشاف فقط — لا تحمّل شيئًا.
 * تبحث عن كائن معاملات الفيديو وتسجّل حقوله في السجل
 * حتى نعرف أين يخزّن فيسبوك رابط الفيديو.
 */
class VideoProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "VideoProbe"
    override val key = "video_probe"

    override fun doHook() {

        val target = XposedHelpers.findClassIfExists(
            "com.facebook.video.common.playerorigin.PlayerOrigin",
            classLoader
        )

        if (target == null) {
            log("PlayerOrigin غير موجود — سنعتمد على VideoPlayerParams")
        }

        // الكلاس الأساسي لمعاملات المشغّل — اسمه غير مشفّر عادةً
        val paramsClass = XposedHelpers.findClassIfExists(
            "com.facebook.video.engine.api.VideoPlayerParams",
            classLoader
        ) ?: run {
            log("VideoPlayerParams غير موجود — جرّب المسار البديل")
            return
        }

        log("تم العثور على VideoPlayerParams")

        // نلتقط أي إنشاء لهذا الكائن
        paramsClass.declaredConstructors.forEach { ctor ->
            runCatching {
                XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        dumpFields(param.thisObject)
                    }
                })
            }
        }

        log("تم تركيب ${paramsClass.declaredConstructors.size} هوك")
    }

    private var dumped = false

    /** يطبع حقول الكائن مرة واحدة فقط لتفادي إغراق السجل */
    private fun dumpFields(obj: Any?) {
        if (obj == null || dumped) return
        dumped = true

        log("---- حقول ${obj.javaClass.name} ----")

        obj.javaClass.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach

            runCatching {
                field.isAccessible = true
                val value = field.get(obj) ?: return@runCatching
                val text = value.toString()

                if (text.length in 1..300) {
                    log("${field.name} (${field.type.simpleName}) = $text")
                }
            }
        }

        log("---- انتهى ----")
    }
}
