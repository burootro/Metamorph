package io.github.burootro.metamorph.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import de.robv.android.xposed.XposedHelpers

object BitmapExtractor {

    /** يبحث عن أكبر صورة معروضة داخل العنصر ويعيدها */
    fun findLargest(view: View): Bitmap? {
        val found = mutableListOf<Bitmap>()
        collect(view, found)
        return found.maxByOrNull { it.width * it.height }
    }

    private fun collect(view: View, out: MutableList<Bitmap>) {

        // ImageView / DraweeView العادي
        if (view is ImageView) {
            view.drawable?.let { toBitmap(it)?.let(out::add) }
        }

        // Fresco DraweeHolder — يستخدمه فيسبوك بكثافة
        runCatching {
            val hierarchy = XposedHelpers.callMethod(view, "getHierarchy")
            val top = XposedHelpers.callMethod(hierarchy, "getTopLevelDrawable") as? Drawable
            top?.let { toBitmap(it)?.let(out::add) }
        }

        // العناصر المرسومة داخل LithoView
        runCatching {
            val count = XposedHelpers.callMethod(view, "getMountItemCount") as? Int ?: 0
            for (i in 0 until count) {
                val item = XposedHelpers.callMethod(view, "getMountItemAt", i) ?: continue
                val content = runCatching {
                    XposedHelpers.callMethod(item, "getContent")
                }.getOrNull() ?: continue

                when (content) {
                    is Drawable -> toBitmap(content)?.let(out::add)
                    is View -> collect(content, out)
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collect(view.getChildAt(i), out)
            }
        }
    }

    /** يحوّل أي Drawable إلى Bitmap */
    private fun toBitmap(drawable: Drawable): Bitmap? {

        if (drawable is BitmapDrawable) {
            return drawable.bitmap?.takeIf { !it.isRecycled }
        }

        // Fresco يغلّف الصورة داخل drawables متداخلة
        runCatching {
            val inner = XposedHelpers.callMethod(drawable, "getCurrent") as? Drawable
            if (inner != null && inner !== drawable) {
                toBitmap(inner)?.let { return it }
            }
        }

        runCatching {
            val underlying = XposedHelpers.callMethod(drawable, "getDrawable") as? Drawable
            if (underlying != null && underlying !== drawable) {
                toBitmap(underlying)?.let { return it }
            }
        }

        // الحل الأخير: نرسم الـ Drawable على Bitmap جديد
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w < 150 || h < 150) return null

        return runCatching {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
    }
}
