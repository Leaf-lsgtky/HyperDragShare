package com.leaf.hyperdragshare.codex;

import android.view.WindowManager;

/** Window type is a runtime capability, not a property of a share payload. */
final class OverlayWindowPolicy {
    final int windowType;
    final String sourceName;

    private OverlayWindowPolicy(int windowType, String sourceName) {
        this.windowType = windowType;
        this.sourceName = sourceName;
    }

    @SuppressWarnings("deprecation")
    static OverlayWindowPolicy portal() {
        // Taplus is granted INTERNAL_SYSTEM_WINDOW. On the verified HyperOS device, the
        // same OPAQUE alpha=1 probe remains solid only on this window type, not TYPE_PHONE.
        return new OverlayWindowPolicy(
                WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                "portal");
    }

    static OverlayWindowPolicy accessibility() {
        return new OverlayWindowPolicy(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                "accessibility");
    }
}
