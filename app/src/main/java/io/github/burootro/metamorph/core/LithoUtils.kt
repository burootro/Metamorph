package io.github.burootro.metamorph.core

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XposedHelpers

object LithoUtils {

    /**
     * يستخرج كل النصوص المعروضة داخل LithoView.
     * Litho يرسم النص كـ Drawable وليس TextView،
     * لذلك نمر على العناصر المرسومة ونقرأ منها.
     */
    fun extractText(view: View): String {
        val out = StringBuilder()
        collect(view, out)
        return out.toString().trim()
    }

    private fun collect(view: View, out: StringBuilder) {

        // 1) النصوص المرسومة داخل LithoView / ComponentHost
        try {
            val count = XposedHelpers.callMethod(view, "getMountItemCount") as? Int ?: 0
            for (i in 0 until count) {
                val item = XposedHelpers.callMethod(view, "getMountItemAt", i) ?: continue
                val content = runCatching {
                    XposedHelpers.callMethod(item, "getContent")
                }.getOrNull() ?: continue

                readDrawableText(content)?.let { text ->
                    if (text.isNotBlank()) out.append(text).append("\n")
                }

                if (content is View) collect(content, out)
            }
        } catch (_: Throwable) {
        }

        // 2) الأبناء العاديون
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collect(view.getChildAt(i), out)
            }
        }
    }

    /** يقرأ النص من TextDrawable الخاص بـ Litho */
    private fun readDrawableText(obj: Any): String? {
        val name = obj.javaClass.name
        if (!name.contains("Text", ignoreCase = true)) return null

        // TextDrawable يوفر getText()
        runCatching {
            val text = XposedHelpers.callMethod(obj, "getText")
            if (text != null) return text.toString()
        }

        // بعض الإصدارات تخزنه في حقل مباشر
        for (field in listOf("text", "mText")) {
            runCatching {
                val value = XposedHelpers.getObjectField(obj, field)
                if (value != null) return value.toString()
            }
        }

        return null
    }
}
