# نقطة دخول الموديول — يناديها LSPosed بالاسم
-keep class io.github.burootro.metamorph.HookEntry { *; }

# كل الميزات والأدوات التي تُستدعى عبر الانعكاس
-keep class io.github.burootro.metamorph.features.** { *; }
-keep class io.github.burootro.metamorph.core.** { *; }

# واجهات Xposed
-keep class de.robv.android.xposed.** { *; }
-keepclassmembers class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public *;
}

# المستقبِلات التي نسجّلها داخل عملية فيسبوك
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}

-dontwarn de.robv.android.xposed.**
