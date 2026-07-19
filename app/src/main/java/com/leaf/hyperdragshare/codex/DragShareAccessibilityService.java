package com.leaf.hyperdragshare.codex;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Accessibility-mode runtime owner. Events only update window state; capture starts after a long press. */
public final class DragShareAccessibilityService extends AccessibilityService {
    private static final String TAG = "DragShare/Accessibility";
    private static final int MAX_NODE_COUNT = 4_000;
    private static final int MAX_NODE_DEPTH = 64;
    private static final long NODE_BUDGET_MILLIS = 120L;
    private static final long ROOT_RETRY_DELAY_MILLIS = 5_000L;
    private static final int MAX_ROOT_RESTARTS = 3;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable rootReadyReporter = new Runnable() {
        @Override
        public void run() {
            RootTouchSource source = rootTouchSource;
            boolean ready = runtimeStarted && source != null && source.isReady();
            AccessibilityRuntimeStatus.setRootInputReady(ready);
            if (ready != lastReportedRootReady) {
                lastReportedRootReady = ready;
                trace("root ready=" + ready);
            }
            if (!runtimeStarted) {
                return;
            }
            if (ready) {
                rootNotReadySince = 0L;
                rootRestartCount = 0;
            } else {
                long now = SystemClock.uptimeMillis();
                if (rootNotReadySince == 0L) {
                    rootNotReadySince = now;
                } else if (now - rootNotReadySince >= ROOT_RETRY_DELAY_MILLIS
                        && rootRestartCount < MAX_ROOT_RESTARTS
                        && source != null) {
                    rootRestartCount++;
                    rootNotReadySince = now;
                    DragShareLog.w(TAG, "restarting unavailable Root input attempt="
                            + rootRestartCount);
                    source.stop();
                    mainHandler.postDelayed(() -> {
                        if (runtimeStarted && rootTouchSource == source && !source.isReady()) {
                            source.start();
                        }
                    }, 250L);
                }
            }
            mainHandler.postDelayed(this, ready ? 1_000L : 400L);
        }
    };

    private ContentObserver settingsObserver;
    private BroadcastReceiver screenReceiver;
    private volatile boolean screenInteractive = true;
    private volatile String foregroundPackage;
    private volatile int windowGeneration;
    private long rootNotReadySince;
    private int rootRestartCount;
    private boolean lastReportedRootReady;
    private boolean runtimeStarted;
    private int runtimeLongPressTimeoutMillis = Integer.MIN_VALUE;
    private int runtimeRecognitionSensitivityPercent = Integer.MIN_VALUE;
    private RootTouchSource rootTouchSource;
    private DragShareController controller;
    private AccessibilityContentCaptureSource captureSource;
    private ExecutorService classifierExecutor;
    private AccessibilityScreenshotter screenshotter;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityTrace.reset(this);
        AccessibilityRuntimeStatus.setConnected(true);
        screenInteractive = isScreenInteractive();
        trace("service connected mode="
                + DragShareSettings.readLocal(this).contentCaptureMode
                + " interactive=" + screenInteractive
                + " locked=" + isDeviceLocked()
                + " sdk=" + Build.VERSION.SDK_INT);
        registerSettingsObserver();
        registerScreenReceiver();
        applyMode();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        foregroundPackage = packageName == null ? null : packageName.toString();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            windowGeneration++;
        }
    }

    @Override
    public void onInterrupt() {
        AccessibilityContentCaptureSource source = captureSource;
        if (source != null) {
            source.cancel();
        }
        AccessibilityScreenshotter activeScreenshotter = screenshotter;
        if (activeScreenshotter != null) {
            activeScreenshotter.cancelAll();
        }
    }

    @Override
    public void onDestroy() {
        unregisterSettingsObserver();
        unregisterScreenReceiver();
        stopRuntime();
        AccessibilityRuntimeStatus.setConnected(false);
        super.onDestroy();
    }

    boolean isAccessibilityCaptureEnabled() {
        DragShareSettings settings = DragShareSettings.readLocal(this);
        return isAccessibilityRuntimeEnabled(settings)
                && settings.isAccessibilityRecognitionEnabledForOrientation(
                getResources().getConfiguration().orientation);
    }

    private boolean isAccessibilityRuntimeEnabled() {
        return isAccessibilityRuntimeEnabled(DragShareSettings.readLocal(this));
    }

    private boolean isAccessibilityRuntimeEnabled(DragShareSettings settings) {
        return screenInteractive
                && !isDeviceLocked()
                && settings.isAccessibilityCaptureMode();
    }

    AccessibilityCandidateSelector.Selection selectCandidateAt(float x, float y, long gestureId) {
        if (!isAccessibilityCaptureEnabled()) {
            trace("gesture=" + gestureId + " selection skipped disabled");
            return null;
        }
        DragShareSettings settings = DragShareSettings.readLocal(this);
        List<WindowRoot> roots = rootsAtPoint(x, y, settings);
        trace("gesture=" + gestureId + " roots=" + roots.size()
                + " point=" + Math.round(x) + "," + Math.round(y));
        for (WindowRoot windowRoot : roots) {
            try {
                List<AccessibilityNodeSnapshot> snapshots = snapshotTree(
                        windowRoot.root,
                        windowRoot.layer);
                int[] screen = screenSize();
                AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(
                        getResources().getDisplayMetrics().density,
                        screen[0],
                        screen[1]);
                AccessibilityNodeClassifier.Buckets buckets = classifier.classify(snapshots);
                AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                        buckets,
                        x,
                        y);
                DragShareLog.i(TAG, "gesture=" + gestureId
                        + " nodes=" + snapshots.size()
                        + " candidates=" + buckets.candidateCount());
                if (selection != null) {
                    if (AccessibilityBlacklist.isBlocked(
                            this,
                            settings,
                            selection.candidate.sourcePackage)) {
                        trace("gesture=" + gestureId + " candidate skipped blacklist");
                        continue;
                    }
                    trace("gesture=" + gestureId + " selected="
                            + selection.candidate.kind
                            + " bounds=" + selection.candidate.bounds.flattenToString());
                    return selection;
                }
            } catch (Throwable error) {
                DragShareLog.w(TAG, "gesture=" + gestureId + " window snapshot failed", error);
            } finally {
                recycleNode(windowRoot.root);
            }
        }
        trace("gesture=" + gestureId + " no candidate");
        return null;
    }

    private void applyMode() {
        if (isAccessibilityRuntimeEnabled()) {
            startRuntime();
        } else {
            stopRuntime();
        }
    }

    private void startRuntime() {
        if (runtimeStarted) {
            return;
        }
        DragShareSettings settings = DragShareSettings.readLocal(this);
        runtimeStarted = true;
        runtimeLongPressTimeoutMillis = settings.accessibilityLongPressTimeoutMillis;
        runtimeRecognitionSensitivityPercent = settings.accessibilityRecognitionSensitivityPercent;
        rootNotReadySince = 0L;
        rootRestartCount = 0;
        lastReportedRootReady = false;
        classifierExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "drag-share-accessibility-classifier");
            thread.setDaemon(true);
            return thread;
        });
        controller = new DragShareController(
                this,
                OverlayWindowPolicy.accessibility());
        screenshotter = new AccessibilityScreenshotter(this, classifierExecutor);
        rootTouchSource = new RootTouchSource(
                getApplicationContext(),
                (action, x, y, eventTime) -> {
                    if (action == android.view.MotionEvent.ACTION_DOWN
                            || action == android.view.MotionEvent.ACTION_UP
                            || action == android.view.MotionEvent.ACTION_CANCEL) {
                        trace("root action=" + android.view.MotionEvent.actionToString(action)
                                + " point=" + Math.round(x) + "," + Math.round(y));
                    }
                    AccessibilityContentCaptureSource source = captureSource;
                    if (source != null) {
                        source.onPointerEvent(action, x, y, eventTime);
                    }
                });
        captureSource = new AccessibilityContentCaptureSource(
                this,
                controller,
                rootTouchSource,
                classifierExecutor,
                screenshotter);
        rootTouchSource.start();
        mainHandler.removeCallbacks(rootReadyReporter);
        mainHandler.post(rootReadyReporter);
        DragShareLog.i(TAG, "accessibility runtime started");
        trace("runtime started");
    }

    private void stopRuntime() {
        if (!runtimeStarted && controller == null && rootTouchSource == null) {
            AccessibilityRuntimeStatus.setRootInputReady(false);
            return;
        }
        runtimeStarted = false;
        runtimeLongPressTimeoutMillis = Integer.MIN_VALUE;
        runtimeRecognitionSensitivityPercent = Integer.MIN_VALUE;
        rootNotReadySince = 0L;
        rootRestartCount = 0;
        lastReportedRootReady = false;
        mainHandler.removeCallbacks(rootReadyReporter);
        AccessibilityRuntimeStatus.setRootInputReady(false);
        AccessibilityContentCaptureSource source = captureSource;
        captureSource = null;
        if (source != null) {
            source.cancel();
        }
        AccessibilityScreenshotter activeScreenshotter = screenshotter;
        screenshotter = null;
        if (activeScreenshotter != null) {
            activeScreenshotter.close();
        }
        RootTouchSource input = rootTouchSource;
        rootTouchSource = null;
        if (input != null) {
            input.stop();
        }
        ExecutorService executor = classifierExecutor;
        classifierExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        DragShareController activeController = controller;
        controller = null;
        if (activeController != null) {
            activeController.destroy();
        }
        DragShareLog.i(TAG, "accessibility runtime stopped");
        trace("runtime stopped");
    }

    private List<WindowRoot> rootsAtPoint(
            float x,
            float y,
            DragShareSettings settings) {
        List<WindowRoot> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        AccessibilityNodeInfo activeRoot = null;
        try {
            activeRoot = getRootInActiveWindow();
            addRootIfUsable(result, seen, activeRoot, Integer.MAX_VALUE, settings);
        } catch (Throwable ignored) {
            recycleNode(activeRoot);
        }
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (Throwable ignored) {
            windows = null;
        }
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = null;
                try {
                    if (window == null || window.getType()
                            == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                        continue;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && window.getDisplayId() != Display.DEFAULT_DISPLAY) {
                        continue;
                    }
                    Rect bounds = new Rect();
                    window.getBoundsInScreen(bounds);
                    if (!contains(bounds, x, y)) {
                        continue;
                    }
                    root = window.getRoot();
                    int layer = window.getLayer();
                    if (window.isActive()) layer += 1_000_000;
                    if (window.isFocused()) layer += 100_000;
                    addRootIfUsable(result, seen, root, layer, settings);
                    root = null;
                } catch (Throwable ignored) {
                    // One broken window must not cancel the full capture.
                } finally {
                    recycleNode(root);
                    recycleWindow(window);
                }
            }
        }
        sortWindowRoots(result);
        return result;
    }

    private void addRootIfUsable(
            List<WindowRoot> roots,
            Set<String> seen,
            AccessibilityNodeInfo root,
            int layer,
            DragShareSettings settings) {
        if (root == null) {
            return;
        }
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        String packageName = asString(root.getPackageName());
        // Window bounds were already checked when available. Some ROMs expose an
        // empty or inset root bounds while its descendants still have valid bounds.
        if (getPackageName().equals(packageName)) {
            recycleNode(root);
            return;
        }
        if (AccessibilityBlacklist.isBlocked(this, settings, packageName)) {
            recycleNode(root);
            return;
        }
        String key = packageName + ":" + bounds.flattenToString();
        if (!seen.add(key)) {
            recycleNode(root);
            return;
        }
        roots.add(new WindowRoot(root, layer));
    }

    private static void sortWindowRoots(List<WindowRoot> roots) {
        roots.sort(new Comparator<WindowRoot>() {
            @Override
            public int compare(WindowRoot first, WindowRoot second) {
                return Integer.compare(second.layer, first.layer);
            }
        });
    }

    private List<AccessibilityNodeSnapshot> snapshotTree(
            AccessibilityNodeInfo root,
            int layer) {
        List<AccessibilityNodeSnapshot> snapshots = new ArrayList<>();
        TraversalBudget budget = new TraversalBudget();
        traverse(root, 0, false, layer, snapshots, budget);
        if (budget.exhausted) {
            DragShareLog.w(TAG, "node traversal budget exhausted nodes=" + budget.nodeCount);
        }
        return snapshots;
    }

    private void traverse(
            AccessibilityNodeInfo node,
            int depth,
            boolean inheritedWebView,
            int layer,
            List<AccessibilityNodeSnapshot> snapshots,
            TraversalBudget budget) {
        if (node == null || budget.exhausted || depth > MAX_NODE_DEPTH) {
            return;
        }
        if (++budget.nodeCount > MAX_NODE_COUNT
                || SystemClock.uptimeMillis() - budget.startedAt > NODE_BUDGET_MILLIS) {
            budget.exhausted = true;
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String className = asString(node.getClassName());
        boolean insideWebView = inheritedWebView || "android.webkit.WebView".equals(className);
        int childCount = node.getChildCount();
        snapshots.add(new AccessibilityNodeSnapshot.Builder()
                .bounds(bounds)
                .packageName(asString(node.getPackageName()))
                .className(className)
                .viewId(node.getViewIdResourceName())
                .text(asString(node.getText()))
                .contentDescription(asString(node.getContentDescription()))
                .visible(node.isVisibleToUser())
                .editable(node.isEditable())
                .password(node.isPassword())
                .clickable(node.isClickable())
                .longClickable(node.isLongClickable())
                .important(node.isImportantForAccessibility())
                .leaf(childCount == 0)
                .insideWebView(insideWebView)
                .depth(depth)
                .windowLayer(layer)
                .traversalOrder(budget.nodeCount)
                .build());
        for (int index = 0; index < childCount && !budget.exhausted; index++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(index);
                traverse(child, depth + 1, insideWebView, layer, snapshots, budget);
            } finally {
                recycleNode(child);
            }
        }
    }

    private int[] screenSize() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private void registerSettingsObserver() {
        unregisterSettingsObserver();
        settingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                DragShareSettings settings = DragShareSettings.readLocal(
                        DragShareAccessibilityService.this);
                if (runtimeStarted && (settings.accessibilityLongPressTimeoutMillis
                        != runtimeLongPressTimeoutMillis
                        || settings.accessibilityRecognitionSensitivityPercent
                        != runtimeRecognitionSensitivityPercent)) {
                    stopRuntime();
                }
                applyMode();
            }
        };
        getContentResolver().registerContentObserver(
                DragShareSettings.settingsUri(),
                false,
                settingsObserver);
    }

    private void unregisterSettingsObserver() {
        if (settingsObserver == null) {
            return;
        }
        try {
            getContentResolver().unregisterContentObserver(settingsObserver);
        } catch (Throwable ignored) {
            // Service teardown can race the resolver.
        }
        settingsObserver = null;
    }

    private void registerScreenReceiver() {
        if (screenReceiver != null) {
            return;
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenInteractive = false;
                    stopRuntime();
                } else if (Intent.ACTION_USER_PRESENT.equals(action)
                        || Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenInteractive = isScreenInteractive();
                    applyMode();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterScreenReceiver() {
        if (screenReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(screenReceiver);
        } catch (Throwable ignored) {
            // Already unregistered during platform shutdown.
        }
        screenReceiver = null;
    }

    private boolean isScreenInteractive() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return manager == null || manager.isInteractive();
    }

    private boolean isDeviceLocked() {
        KeyguardManager manager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }

    void trace(String message) {
        AccessibilityTrace.record(this, message);
    }

    private static boolean contains(Rect bounds, float x, float y) {
        return bounds != null && x >= bounds.left && x <= bounds.right
                && y >= bounds.top && y <= bounds.bottom;
    }

    private static String asString(CharSequence value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("deprecation")
    private static void recycleNode(AccessibilityNodeInfo node) {
        if (node != null) {
            node.recycle();
        }
    }

    @SuppressWarnings("deprecation")
    private static void recycleWindow(AccessibilityWindowInfo window) {
        if (window != null) {
            window.recycle();
        }
    }

    private static final class WindowRoot {
        final AccessibilityNodeInfo root;
        final int layer;

        WindowRoot(AccessibilityNodeInfo root, int layer) {
            this.root = root;
            this.layer = layer;
        }
    }

    private static final class TraversalBudget {
        final long startedAt = SystemClock.uptimeMillis();
        int nodeCount;
        boolean exhausted;
    }
}
