package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.Display;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class BackgroundTouchBlockerTest {
    @Test
    public void rootServiceCommandUsesTheDynamicallyResolvedTransaction() {
        String command = BackgroundTouchBlocker.rootServiceCallCommand(123);

        assertTrue(command.contains("service call input 123"));
        assertFalse(command.contains("grep"));
    }

    @Test
    public void acceptsTheCurrentRomSuccessfulServiceCallParcel() {
        assertTrue(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\t00000000    '....')"));
        assertTrue(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: 00000000 00000000)"));
    }

    @Test
    public void rejectsServiceCallExceptionParcel() {
        assertFalse(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: ffffffff 00000021 00650052)"));
    }

    @Test
    public void usesRootCancellationWhenPortalInputApisAreDenied() {
        AtomicBoolean rootCalled = new AtomicBoolean();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(new Object()),
                (target, methodName, args) -> {
                    throw new SecurityException("Requires MONITOR_INPUT permission");
                },
                () -> {
                    rootCalled.set(true);
                    return true;
                });

        assertTrue(blocker.start());
        assertTrue(rootCalled.get());
        blocker.stop();
    }

    @Test
    public void stopDisposesInputMonitorAfterPilfering() {
        Object inputManager = new Object();
        Object monitor = new Object();
        AtomicBoolean disposed = new AtomicBoolean();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    if (target == inputManager && "cancelCurrentTouch".equals(methodName)) {
                        throw new SecurityException("Requires MONITOR_INPUT permission");
                    }
                    if (target == inputManager && "monitorGestureInput".equals(methodName)) {
                        return monitor;
                    }
                    if (target == monitor && "pilferPointers".equals(methodName)) {
                        return null;
                    }
                    if (target == monitor && "dispose".equals(methodName)) {
                        disposed.set(true);
                        return null;
                    }
                    throw new NoSuchMethodException(methodName);
                },
                () -> false);

        assertTrue(blocker.start());
        blocker.stop();

        assertTrue(disposed.get());
    }

    private static Context inputContext(Object inputManager) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public Display getDisplay() {
                throw new UnsupportedOperationException("Application context has no display");
            }

            @Override
            public Object getSystemService(String name) {
                return Context.INPUT_SERVICE.equals(name) ? inputManager : null;
            }
        };
    }
}
