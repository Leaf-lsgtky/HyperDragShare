package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** Small bounded trace used on ROMs that suppress third-party logcat output. */
final class AccessibilityTrace {
    private static final String FILE_NAME = "accessibility-trace.log";
    private static final long MAX_FILE_BYTES = 128L * 1024L;
    private static final Object LOCK = new Object();

    private AccessibilityTrace() {}

    static void reset(Context context) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            File file = traceFile(context);
            if (file.exists() && !file.delete()) {
                DragShareLog.w("DragShare/Accessibility", "unable to reset accessibility trace");
            }
        }
    }

    static void record(Context context, String message) {
        DragShareLog.i("DragShare/Accessibility", message);
        if (context == null || message == null) {
            return;
        }
        synchronized (LOCK) {
            File file = traceFile(context);
            if (file.length() >= MAX_FILE_BYTES && !file.delete()) {
                return;
            }
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file, true),
                    StandardCharsets.UTF_8)) {
                writer.write(Long.toString(System.currentTimeMillis()));
                writer.write(" uptime=");
                writer.write(Long.toString(SystemClock.uptimeMillis()));
                writer.write(' ');
                writer.write(message.replace('\n', ' '));
                writer.write('\n');
            } catch (Throwable error) {
                DragShareLog.w("DragShare/Accessibility", "unable to write accessibility trace", error);
            }
        }
    }

    private static File traceFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
