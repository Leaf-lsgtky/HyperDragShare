package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

final class ShareTarget {
    final ComponentName component;
    final CharSequence label;
    final Drawable icon;
    private final String key;
    private final String packageName;
    private final boolean builtIn;

    ShareTarget(ComponentName component, CharSequence label, Drawable icon) {
        this(
                component,
                label,
                icon,
                component == null ? null : component.flattenToString(),
                component == null ? "" : component.getPackageName(),
                false);
    }

    private ShareTarget(
            ComponentName component,
            CharSequence label,
            Drawable icon,
            String key,
            String packageName,
            boolean builtIn) {
        this.component = component;
        this.label = label;
        this.icon = icon;
        this.key = key;
        this.packageName = packageName;
        this.builtIn = builtIn;
    }

    static ShareTarget saveToLocal(Drawable icon) {
        return new ShareTarget(
                null,
                "保存到本地",
                icon,
                DragShareSettings.TARGET_SAVE_LOCAL,
                "builtin",
                true);
    }

    static ShareTarget textSegmentation(Drawable icon) {
        return new ShareTarget(
                null,
                "文本分词",
                icon,
                DragShareSettings.TARGET_TEXT_SEGMENTATION,
                "builtin",
                true);
    }

    String key() {
        return key;
    }

    String packageName() {
        return packageName;
    }

    boolean isBuiltIn() {
        return builtIn;
    }

    boolean isSaveToLocal() {
        return builtIn && DragShareSettings.TARGET_SAVE_LOCAL.equals(key);
    }

    boolean isTextSegmentation() {
        return builtIn && DragShareSettings.TARGET_TEXT_SEGMENTATION.equals(key);
    }
}
