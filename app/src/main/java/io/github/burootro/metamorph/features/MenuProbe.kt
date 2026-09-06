package io.github.burootro.metamorph.features

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature

/**
 * استكشاف — يرصد ظهور قائمة المشاركة ويطبع بنيتها،
 * لنعرف أين نضيف زر التنزيل.
 */
class MenuProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "MenuProbe"
    override val key = "menu_probe"

    private var reported = 0

    override fun doHook() {

        // كل القوائم المنبثقة في فيسبوك تمر عبر Dialog
        XposedHelpers.findAndHookMethod(
            Dialog::class.java, "show",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val dialog = param.thisObject as? Dialog ?: return
                    dialog.window?.decorView?.post {
                        inspect(dialog)
                    }
                }
            }
        )

        log("تم تركيب الهوك على Dialog")
    }

    private fun inspect(dialog: Dialog) {
        if (reported >= 2) return

        runCatching {
            val root = dialog.window?.decorView ?: return
            val labels = mutableListOf<String>()
            collectLabels(root, labels)

            // نتجاهل الحوارات التي لا تشبه قائمة المشاركة
            if (labels.isEmpty()) return

            reported++

            log("==== حوار: ${dialog.javaClass.name} ====")
            log("النصوص: ${labels.take(25).joinToString(" | ")}")
            log("---- الشجرة ----")
            dumpTree(root, 0)
            log("==== انتهى ====")
        }
    }

    private fun collectLabels(view: View, out: MutableList<String>) {
        if (view is TextView) {
            val t = view.text?.toString()?.trim()
            if (!t.isNullOrBlank() && t.length < 40) out.add(t)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectLabels(view.getChildAt(i), out)
        }
    }

    private fun dumpTree(view: View, depth: Int) {
        if (depth > 8) return

        val pad = "  ".repeat(depth)
        val extra = when (view) {
            is TextView -> " \"${view.text?.toString()?.take(25)}\""
            is ViewGroup -> " [${view.childCount}]"
            else -> ""
        }

        log("$pad${view.javaClass.simpleName}$extra")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) dumpTree(view.getChildAt(i), depth + 1)
        }
    }
}
