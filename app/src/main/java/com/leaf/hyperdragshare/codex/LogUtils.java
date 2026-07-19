package com.leaf.hyperdragshare.codex;

import android.util.Log;

/** Minimal logging adapter retained by the imported BigBang chip implementation. */
final class LogUtils {
    private LogUtils() {}

    static void d(String tag, String message) {
        Log.d(tag, message);
    }
}
