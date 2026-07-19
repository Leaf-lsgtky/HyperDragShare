package com.example.dragshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class LongPressGestureDetectorTest {
    @Test
    public void timeoutStartsCaptureAndKeepsLatestPointerForDrag() {
        FakeCallback callback = new FakeCallback();
        FakeScheduler scheduler = new FakeScheduler();
        LongPressGestureDetector detector = new LongPressGestureDetector(
                500L, 10f, callback, () -> 0L, scheduler);

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 10f, 20f, 0L);
        scheduler.runPending();
        assertEquals(1, callback.longPressCount);
        assertTrue(detector.beginDragging(detector.currentGestureId()));
        detector.onPointerEvent(MotionEvent.ACTION_MOVE, 30f, 40f, 600L);
        detector.onPointerEvent(MotionEvent.ACTION_UP, 30f, 40f, 620L);

        assertEquals(2, callback.dragCount);
        assertEquals(LongPressGestureDetector.State.IDLE, detector.state());
    }

    @Test
    public void movementPastSlopCancelsBeforeTimeout() {
        FakeCallback callback = new FakeCallback();
        FakeScheduler scheduler = new FakeScheduler();
        LongPressGestureDetector detector = new LongPressGestureDetector(
                500L, 10f, callback, () -> 0L, scheduler);

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 0f, 0f, 0L);
        detector.onPointerEvent(MotionEvent.ACTION_MOVE, 11f, 0f, 10L);
        scheduler.runPending();

        assertEquals(0, callback.longPressCount);
        assertEquals(LongPressGestureDetector.State.IGNORED_UNTIL_UP, detector.state());
    }

    @Test
    public void captureCompletionAfterUpCannotStartDragging() {
        FakeCallback callback = new FakeCallback();
        FakeScheduler scheduler = new FakeScheduler();
        LongPressGestureDetector detector = new LongPressGestureDetector(
                500L, 10f, callback, () -> 0L, scheduler);

        detector.onPointerEvent(MotionEvent.ACTION_DOWN, 0f, 0f, 0L);
        scheduler.runPending();
        long gesture = detector.currentGestureId();
        detector.onPointerEvent(MotionEvent.ACTION_UP, 0f, 0f, 600L);

        assertFalse(detector.beginDragging(gesture));
        assertTrue(callback.cancelledCount > 0);
    }

    private static final class FakeCallback implements LongPressGestureDetector.Callback {
        int longPressCount;
        int dragCount;
        int cancelledCount;

        @Override public boolean isLongPressAllowed() { return true; }
        @Override public void onLongPress(long gestureId, float stableX, float stableY) {
            longPressCount++;
        }
        @Override public void onDrag(int action, float x, float y, long eventTime) { dragCount++; }
        @Override public void onGestureCancelled(long gestureId) { cancelledCount++; }
    }

    private static final class FakeScheduler implements LongPressGestureDetector.Scheduler {
        Runnable pending;
        @Override public void postDelayed(Runnable runnable, long delayMillis) { pending = runnable; }
        @Override public void removeCallbacks(Runnable runnable) {
            if (pending == runnable) pending = null;
        }
        void runPending() {
            Runnable runnable = pending;
            pending = null;
            if (runnable != null) runnable.run();
        }
    }
}
