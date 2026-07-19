package com.example.dragshare;

import android.app.Application;

/** Starts the optional tokenizer warm-up for every module-app process. */
public final class DragShareApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TextSegmenter.preloadIfEnabled(this);
    }
}
