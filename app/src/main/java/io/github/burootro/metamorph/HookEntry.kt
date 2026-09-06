package io.github.burootro.metamorph

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.burootro.metamorph.core.Feature
import io.github.burootro.metamorph.core.Prefs
import io.github.burootro.metamorph.features.BackgroundProbe
import io.github.burootro.metamorph.features.VideoDownload

class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {

        if (lpparam.packageName != TARGET) return

        // فيسبوك يشغّل عدة عمليات — نتعامل مع الرئيسية فقط
        if (lpparam.processName != TARGET) return

        XposedBridge.log("[Metamorph] تم التحميل داخل $TARGET")

        val prefs = Prefs.xposed()
        val cl = lpparam.classLoader

        Feature.runAll(
            listOf(
                VideoDownload(cl, prefs),
                BackgroundProbe(cl, prefs)
            )
        )
    }

    companion object {
        private const val TARGET = "com.facebook.katana"
    }
}
