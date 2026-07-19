# Xposed entry points are loaded by class name from assets/xposed_init.
-keep class com.leaf.hyperdragshare.codex.MainHook { *; }

# cppjieba registers these methods by their Java class and method names in JNI_OnLoad.
-keep class com.leaf.hyperdragshare.codex.TextSegmenter {
    native <methods>;
}
