package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class EvdevTouchParserTest {
    @Test
    public void emitsStableDownMoveAndUp() {
        List<Integer> actions = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y) -> actions.add(action));

        pointerDown(parser, 0, 42, 10, 20);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, 12);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);

        assertEquals(Arrays.asList(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP), actions);
    }

    @Test
    public void secondPointerCancelsInsteadOfFinishingShare() {
        List<Integer> actions = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y) -> actions.add(action));

        pointerDown(parser, 0, 42, 10, 20);
        pointerDown(parser, 1, 43, 30, 40);

        assertEquals(Arrays.asList(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_CANCEL), actions);
    }

    private static void pointerDown(
            EvdevTouchParser parser,
            int slot,
            int trackingId,
            int x,
            int y) {
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_SLOT, slot);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, trackingId);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, x);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_Y, y);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
    }
}
