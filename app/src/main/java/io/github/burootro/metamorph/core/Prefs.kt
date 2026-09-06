package io.github.burootro.metamorph.core

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

object Prefs {

    const val PKG = "io.github.burootro.metamorph"
    const val FILE = "settings"

    /** تُستخدم داخل عملية فيسبوك (قراءة فقط) */
    fun xposed(): XSharedPreferences {
        val prefs = XSharedPreferences(PKG, FILE)
        prefs.makeWorldReadable()
        prefs.reload()
        return prefs
    }

    /** تُستخدم داخل تطبيق الإعدادات (قراءة وكتابة) */
    @Suppress("DEPRECATION", "WorldReadableFiles")
    fun app(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_WORLD_READABLE)
}
