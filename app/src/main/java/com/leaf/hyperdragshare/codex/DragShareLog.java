package com.leaf.hyperdragshare.codex;

import android.util.Log;

/** Logging boundary for code that can run outside the LSPosed process. */
final class DragShareLog {
    private DragShareLog() {}

    static void d(String tag, String message) {
        Log.d(tag, message);
    }

    static void i(String tag, String message) {
        Log.i(tag, message);
    }

    static void w(String tag, String message) {
        Log.w(tag, message);
    }

    static void w(String tag, String message, Throwable error) {
        Log.w(tag, message, error);
    }

    static void e(String tag, String message, Throwable error) {
        Log.e(tag, message, error);
    }
}
