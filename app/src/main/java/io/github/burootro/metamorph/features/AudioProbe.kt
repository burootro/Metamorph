package io.github.burootro.metamorph.features

import android.media.AudioManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.burootro.metamorph.core.Feature

/**
 * استكشاف — يرصد من يطلب التركيز الصوتي ومن يتخلّى عنه،
 * ومن يستقبل إشعار فقدانه عند الخروج من التطبيق.
 */
class AudioProbe(
    classLoader: ClassLoader,
    prefs: XSharedPreferences
) : Feature(classLoader, prefs) {

    override val name = "AudioProbe"
    override val key = "audio_probe"

    private var listenerLogged = false
    private var focusChangeLogged = 0

    override fun doHook() {

        hookRequest()
        hookAbandon()
        hookFocusChange()

        log("جاهز — شغّل فيديو ثم اضغط Home")
    }

    // من يطلب التركيز الصوتي؟ نلتقط المستمع المسؤول
    private fun hookRequest() {

        AudioManager::class.java.declaredMethods
            .filter { it.name == "requestAudioFocus" }
            .forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (listenerLogged) return
                            listenerLogged = true

                            log("---- طلب التركيز الصوتي ----")
                            param.args.forEachIndexed { i, arg ->
                                log("  وسيط $i: ${arg?.javaClass?.name} = $arg")
                            }
                            log("  المستدعي:")
                            printStack(12)
                            log("---- انتهى ----")
                        }
                    })
                }
            }
    }

    private fun hookAbandon() {

        AudioManager::class.java.declaredMethods
            .filter { it.name.startsWith("abandonAudioFocus") }
            .forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            log("★ التخلّي عن التركيز الصوتي — ${method.name}")
                            printStack(15)
                        }
                    })
                }
            }
    }

    // من يستقبل إشعار فقدان التركيز؟ هذا هو من يوقف الفيديو
    private fun hookFocusChange() {

        runCatching {
            val iface = XposedHelpers.findClass(
                "android.media.AudioManager\$OnAudioFocusChangeListener",
                classLoader
            )

            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("android.media.AudioManager", classLoader),
                "dispatchAudioFocusChange",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (focusChangeLogged >= 3) return
                        focusChangeLogged++

                        log("★ تغيّر التركيز الصوتي: ${param.args.getOrNull(0)}")
                        log("  المستمع: ${param.args.getOrNull(1)?.javaClass?.name}")
                    }
                }
            )

            log("✓ تم تركيب هوك تغيّر التركيز (${iface.simpleName})")

        }.onFailure {
            log("✗ فشل هوك تغيّر التركيز: ${it.message}")
        }
    }

    private fun printStack(limit: Int) {
        Throwable().stackTrace
            .asSequence()
            .map { "${it.className}.${it.methodName}" }
            .filterNot { it.startsWith("de.robv") || it.startsWith("io.github") }
            .filterNot { it.startsWith("android.media.AudioManager") }
            .take(limit)
            .forEach { log("    $it") }
    }
}
