package io.github.burootro.metamorph.features

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature

/**
 * يمنع فيسبوك من تنفيذ إجراءات الإيقاف عند الخروج،
 * مع إبقاء استدعاء النظام سليمًا حتى لا يتعطّل التطبيق.
 */
class BackgroundPlay(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "BackgroundPlay"
    override val key = "background_play"

    override fun doHook() {

        val delegate = XposedHelpers.findClassIfExists(
            "com.facebook.katana.activity.FbMainTabActivityDelegate",
            classLoader
        ) ?: run {
            log("FbMainTabActivityDelegate غير موجود")
            return
        }

        val replacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {

                // نمرّر الاستدعاء إلى النظام فقط
                runCatching {
                    XposedHelpers.callMethod(param.thisObject, "callSuperOnPause")
                }.onFailure {
                    log("فشل تمرير الاستدعاء: ${it.message}")

                    // في حال الفشل نترك السلوك الأصلي يعمل
                    XposedBridge.invokeOriginalMethod(
                        param.method, param.thisObject, param.args
                    )
                }

                return null
            }
        }

        var hooked = 0

        delegate.declaredMethods.forEach { method ->
            if (method.name == "onPause" && method.parameterTypes.isEmpty()) {
                runCatching {
                    XposedBridge.hookMethod(method, replacement)
                    hooked++
                }
            }
        }

        log(if (hooked > 0) "تم تعطيل الإيقاف التلقائي" else "لم يُعثر على onPause")
    }
}
