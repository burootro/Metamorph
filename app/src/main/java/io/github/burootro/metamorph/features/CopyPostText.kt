package io.github.burootro.metamorph.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature
import io.github.burootro.metamorph.core.LithoUtils

class CopyPostText(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "CopyPostText"
    override val key = "copy_post_text"

    override fun doHook() {

        val lithoView = XposedHelpers.findClassIfExists(
            "com.facebook.litho.LithoView",
            classLoader
        ) ?: run {
            log("لم يتم العثور على LithoView")
            return
        }

        XposedHelpers.findAndHookMethod(
            lithoView,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    attachLongPress(view)
                }
            }
        )

        log("تم تركيب الهوك على LithoView")
    }

    private fun attachLongPress(view: View) {
        try {
            // نتفادى التركيب أكثر من مرة على نفس العنصر
            if (view.getTag(TAG_ID) == true) return
            view.setTag(TAG_ID, true)

            view.setOnLongClickListener { v ->
                val text = LithoUtils.extractText(v)

                if (text.isBlank()) {
                    return@setOnLongClickListener false
                }

                copyToClipboard(v.context, text)
                Toast.makeText(v.context, "تم نسخ النص", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (t: Throwable) {
            XposedBridge.log("[Metamorph][$name] ${t.message}")
        }
    }

    private fun copyToClipboard(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Metamorph", text))
    }

    companion object {
        // معرّف فريد للوسم حتى لا يتعارض مع وسوم فيسبوك
        private const val TAG_ID = 0x7F5A0001
    }
}
