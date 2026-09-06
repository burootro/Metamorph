package io.github.burootro.metamorph.core

import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

abstract class Feature(
    val classLoader: ClassLoader,
    val prefs: XSharedPreferences
) {

    /** اسم الميزة كما يظهر في السجل */
    abstract val name: String

    /** مفتاح الميزة في الإعدادات */
    abstract val key: String

    /** الهوك الفعلي */
    @Throws(Throwable::class)
    abstract fun doHook()

    fun isEnabled(): Boolean = prefs.getBoolean(key, false)

    fun log(msg: String) = XposedBridge.log("[Metamorph][$name] $msg")

    companion object {

        /** يشغّل كل ميزة بمعزل عن الأخرى */
        fun runAll(features: List<Feature>) {
            features.forEach { feature ->
                try {
                    if (feature.isEnabled()) {
                        feature.doHook()
                        feature.log("مفعّلة")
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[Metamorph][${feature.name}] فشلت: ${t.message}")
                    XposedBridge.log(t)
                }
            }
        }
    }
}
