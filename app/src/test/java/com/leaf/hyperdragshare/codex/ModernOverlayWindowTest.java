package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModernOverlayWindowTest {
    @Test
    public void transparentWindowBackgroundUsesTheModernLocalCornerRadius() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(16f * context.getResources().getDisplayMetrics().density,
                ModernOverlayWindow.localBackgroundCornerRadiusPx(context),
                0.001f);
    }

    @Test
    public void opaqueFallbackIsUsedWhenCrossWindowBlurIsUnavailable() {
        assertTrue(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, true));
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(0, true));
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, false));
    }

    @Test
    public void portalUsesTheVerifiedOpaqueWindowTypeForPublicBackdropBlur() {
        assertEquals(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                OverlayWindowPolicy.portal().windowType);
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                OverlayWindowPolicy.accessibility().windowType);
    }
}
