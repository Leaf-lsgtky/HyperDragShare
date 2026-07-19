package com.example.dragshare;

import android.view.WindowManager;

/** Window type is a runtime capability, not a property of a share payload. */
final class OverlayWindowPolicy {
    final int windowType;
    final String sourceName;

    private OverlayWindowPolicy(int windowType, String sourceName) {
        this.windowType = windowType;
        this.sourceName = sourceName;
    }

    static OverlayWindowPolicy portal() {
        return new OverlayWindowPolicy(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                "portal");
    }

    static OverlayWindowPolicy accessibility() {
        return new OverlayWindowPolicy(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                "accessibility");
    }
}
