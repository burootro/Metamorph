package io.github.burootro.metamorph.features

import android.app.Application
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature

class TestHook(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "TestHook"
    override val key = "test_hook"

    override fun doHook() {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.thisObject as Application
                    Toast.makeText(
                        app,
                        "Metamorph شغال ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                    log("تم الحقن داخل فيسبوك بنجاح")
                }
            }
        )
    }
}
