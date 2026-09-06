package io.github.burootro.metamorph.core

import android.content.Context
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.io.File

object DexKitHelper {

    private var bridge: DexKitBridge? = null

    init {
        runCatching { System.loadLibrary("dexkit") }
    }

    /**
     * يفتح DexKit على ملف APK الخاص بفيسبوك.
     * عملية ثقيلة — تُستدعى مرة واحدة فقط.
     */
    @Synchronized
    fun open(apkPath: String): DexKitBridge? {
        bridge?.let { return it }

        return runCatching {
            val b = DexKitBridge.create(apkPath)
            bridge = b
            log("تم الفتح: $apkPath")
            b
        }.getOrElse {
            log("فشل الفتح: ${it.message}")
            null
        }
    }

    /** يُغلق DexKit ويحرر الذاكرة بعد انتهاء البحث */
    @Synchronized
    fun close() {
        runCatching { bridge?.close() }
        bridge = null
    }

    /** ملف الكاش الذي نخزن فيه نتائج البحث */
    fun cacheFile(ctx: Context, versionName: String): File {
        val dir = File(ctx.cacheDir, "metamorph")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "sig_$versionName.json")
    }

    fun log(msg: String) = XposedBridge.log("[Metamorph][DexKit] $msg")
}
