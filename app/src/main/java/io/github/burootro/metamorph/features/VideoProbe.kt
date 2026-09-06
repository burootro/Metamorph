package io.github.burootro.metamorph.features

import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature
import java.lang.reflect.Modifier

/**
 * مرحلة استكشاف — تبحث عن رابط الفيديو داخل كائن المعاملات
 * وتنزل مستوى واحد داخل الحقول الكائنية.
 */
class VideoProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "VideoProbe"
    override val key = "video_probe"

    private var dumped = false

    override fun doHook() {

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
                        dump(param.thisObject)
                    }
                })
            }
        }

        log("تم تركيب ${paramsClass.declaredConstructors.size} هوك")
    }

    private fun dump(obj: Any?) {
        if (obj == null || dumped) return
        dumped = true

        log("==== بداية الفحص ====")
        scan(obj, depth = 0, path = "root")
        log("==== نهاية الفحص ====")
    }

    /** يمر على الحقول وينزل مستوى واحد داخل الكائنات */
    private fun scan(obj: Any, depth: Int, path: String) {

        obj.javaClass.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach

            runCatching {
                field.isAccessible = true
                val value = field.get(obj) ?: return@runCatching

                val here = "$path.${field.name}"

                when {
                    // ما نبحث عنه بالضبط
                    value is Uri -> log("★ URI $here = $value")

                    value is String && value.contains("http") ->
                        log("★ رابط $here = ${value.take(600)}")

                    value is String && value.length in 1..120 ->
                        log("$here = $value")

                    // ننزل مستوى واحد داخل الكائنات غير البدائية
                    depth < 2 && isWorthScanning(value) -> {
                        log("↓ دخول $here (${value.javaClass.simpleName})")
                        scan(value, depth + 1, here)
                    }
                }
            }
        }
    }

    private fun isWorthScanning(value: Any): Boolean {
        val cls = value.javaClass
        if (cls.isPrimitive || cls.isArray) return false

        val n = cls.name
        return !n.startsWith("java.") &&
                !n.startsWith("kotlin.") &&
                !n.startsWith("android.os.") &&
                value !is Number &&
                value !is Boolean &&
                value !is CharSequence &&
                value !is Enum<*> &&
                value !is Collection<*> &&
                value !is Map<*, *>
    }
}
