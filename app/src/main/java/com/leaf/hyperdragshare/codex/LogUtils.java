package com.leaf.hyperdragshare.codex;

/** Minimal logging adapter retained by the imported BigBang chip implementation. */
final class LogUtils {
    private LogUtils() {}

    static void d(String tag, String message) {
        DragShareLog.d(tag, message);
    }
}
