package io.github.burootro.metamorph.features

import android.view.View
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.BitmapExtractor
import io.github.burootro.metamorph.core.Feature
import io.github.burootro.metamorph.core.MediaSaver

class SaveImage(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "SaveImage"
    override val key = "save_image"

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
                    attach(view)
                }
            }
        )

        log("تم تركيب الهوك")
    }

    private fun attach(view: View) {
        try {
            if (view.getTag(TAG_ID) == true) return
            view.setTag(TAG_ID, true)

            view.setOnLongClickListener { v ->
                val bitmap = BitmapExtractor.findLargest(v)

                if (bitmap == null) {
                    return@setOnLongClickListener false
                }

                val ok = MediaSaver.saveImage(v.context, bitmap)

                Toast.makeText(
                    v.context,
                    if (ok) "تم حفظ الصورة في Metamorph" else "فشل الحفظ",
                    Toast.LENGTH_SHORT
                ).show()

                log("حفظ: ${bitmap.width}x${bitmap.height} — نجاح=$ok")
                ok
            }
        } catch (t: Throwable) {
            log("خطأ: ${t.message}")
        }
    }

    companion object {
        private const val TAG_ID = 0x7F5A0002
    }
}
