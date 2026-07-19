package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

/**
 * Best-effort cancellation of the foreground app's current touch stream.
 *
 * <p>The portal process normally observes the gesture without owning it. On
 * system builds that expose the hidden InputManager cancellation/monitor APIs
 * to the portal UID, the current pointer stream is cancelled or pilfered so
 * the original window cannot continue a scroll. The feature is optional
 * because these are hidden, permission-gated APIs.</p>
 */
final class BackgroundTouchBlocker {
    private static final String TAG = "DragShare/InputBlocker";
    private static final String MONITOR_NAME = "DragShare background lock";

    private final Context context;
    private final MethodInvoker methodInvoker;
    private Object inputMonitor;

    BackgroundTouchBlocker(Context context) {
        this(context, new ReflectiveMethodInvoker());
    }

    BackgroundTouchBlocker(Context context, MethodInvoker methodInvoker) {
        this.context = context == null ? null : context.getApplicationContext();
        this.methodInvoker = methodInvoker == null ? new ReflectiveMethodInvoker() : methodInvoker;
    }

    synchronized boolean start() {
        if (inputMonitor != null) {
            return true;
        }
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            log("gesture monitor unavailable on this Android version");
            return false;
        }

        Object monitor = null;
        try {
            Object inputManager = context.getSystemService(Context.INPUT_SERVICE);
            if (inputManager == null) {
                log("InputManager service is null");
                return false;
            }

            // Android 14 exposes a simpler hidden operation that cancels the
            // current targets without requiring an input channel to be kept
            // alive. Prefer it when the ROM makes it available.
            try {
                methodInvoker.invoke(inputManager, "cancelCurrentTouch");
                log("foreground touch stream cancelled");
                return true;
            } catch (Throwable unavailable) {
                log("cancelCurrentTouch unavailable; trying gesture monitor");
            }

            int displayId = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.getDisplay() != null) {
                displayId = context.getDisplay().getDisplayId();
            }

            monitor = methodInvoker.invoke(
                    inputManager,
                    "monitorGestureInput",
                    MONITOR_NAME,
                    displayId);
            if (monitor == null) {
                log("monitorGestureInput returned null");
                return false;
            }

            // This sends ACTION_CANCEL to the previous touch target and makes
            // the monitor the target for the remainder of the pointer stream.
            methodInvoker.invoke(monitor, "pilferPointers");
            inputMonitor = monitor;
            log("foreground touch stream pilfered");
            return true;
        } catch (Throwable error) {
            if (monitor != null) {
                try {
                    methodInvoker.invoke(monitor, "dispose");
                } catch (Throwable ignored) {
                    // Keep the original failure as the diagnostic signal.
                }
            }
            log("unable to pilfer foreground touch stream", error);
            return false;
        }
    }

    synchronized void stop() {
        Object monitor = inputMonitor;
        inputMonitor = null;
        if (monitor == null) {
            return;
        }
        try {
            methodInvoker.invoke(monitor, "dispose");
        } catch (Throwable error) {
            log("unable to dispose gesture monitor", error);
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }

    interface MethodInvoker {
        Object invoke(Object target, String methodName, Object... args) throws Throwable;
    }

    private static final class ReflectiveMethodInvoker implements MethodInvoker {
        @Override
        public Object invoke(Object target, String methodName, Object... args) throws Throwable {
            if (target == null) {
                throw new NullPointerException("target");
            }
            Method method = findMethod(target.getClass(), methodName, args);
            method.setAccessible(true);
            return method.invoke(target, args);
        }

        private static Method findMethod(Class<?> type, String name, Object[] args)
                throws NoSuchMethodException {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (name.equals(method.getName()) && matches(method.getParameterTypes(), args)) {
                        return method;
                    }
                }
            }
            throw new NoSuchMethodException(type.getName() + "." + name);
        }

        private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
            if (parameterTypes.length != args.length) {
                return false;
            }
            for (int index = 0; index < parameterTypes.length; index++) {
                Object value = args[index];
                Class<?> parameter = parameterTypes[index];
                if (value == null) {
                    if (parameter.isPrimitive()) {
                        return false;
                    }
                    continue;
                }
                Class<?> valueType = value.getClass();
                if (parameter.isPrimitive()) {
                    if (!primitiveWrapper(parameter).isAssignableFrom(valueType)) {
                        return false;
                    }
                } else if (!parameter.isAssignableFrom(valueType)) {
                    return false;
                }
            }
            return true;
        }

        private static Class<?> primitiveWrapper(Class<?> primitive) {
            if (primitive == boolean.class) return Boolean.class;
            if (primitive == byte.class) return Byte.class;
            if (primitive == char.class) return Character.class;
            if (primitive == short.class) return Short.class;
            if (primitive == int.class) return Integer.class;
            if (primitive == long.class) return Long.class;
            if (primitive == float.class) return Float.class;
            if (primitive == double.class) return Double.class;
            return Void.class;
        }
    }
}
