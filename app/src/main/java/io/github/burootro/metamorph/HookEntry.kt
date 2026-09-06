package io.github.burootro.metamorph

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.burootro.metamorph.core.Feature
import io.github.burootro.metamorph.core.Prefs
import io.github.burootro.metamorph.features.HeroProbe
import io.github.burootro.metamorph.features.VideoDownload

class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {

        if (lpparam.packageName != TARGET) return

        val process = lpparam.processName
        val prefs = Prefs.xposed()
        val cl = lpparam.classLoader

        when {
            // العملية الرئيسية — الواجهة والتنزيل
            process == TARGET -> {
                XposedBridge.log("[Metamorph] العملية الرئيسية")
                Feature.runAll(listOf(VideoDownload(cl, prefs)))
            }

            // عملية محرّك التشغيل — هنا يُتخذ قرار الإيقاف
            process.endsWith(":videoplayer") -> {
                XposedBridge.log("[Metamorph] عملية المشغّل: $process")
                Feature.runAll(listOf(HeroProbe(cl, prefs)))
            }

            else -> return
        }
    }

    companion object {
        private const val TARGET = "com.facebook.katana"
    }
}
