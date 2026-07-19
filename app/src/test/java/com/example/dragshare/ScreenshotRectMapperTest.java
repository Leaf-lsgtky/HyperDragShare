package com.example.dragshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ScreenshotRectMapperTest {
    @Test
    public void expandsAndMapsSameSizeDisplay() {
        assertEquals(
                new Rect(9, 19, 31, 41),
                ScreenshotRectMapper.mapAndExpand(
                        new Rect(10, 20, 30, 40), 100, 100, 100, 100, 1));
    }

    @Test
    public void mapsUniformScaleAndClamps() {
        assertEquals(
                new Rect(0, 0, 40, 40),
                ScreenshotRectMapper.mapAndExpand(
                        new Rect(-10, -10, 20, 20), 100, 100, 200, 200, 0));
    }

    @Test
    public void rejectsSwappedOrOutsideDimensions() {
        assertNull(ScreenshotRectMapper.mapAndExpand(
                new Rect(1, 1, 10, 10), 100, 200, 200, 100, 0));
        assertNull(ScreenshotRectMapper.mapAndExpand(
                new Rect(300, 300, 400, 400), 100, 100, 100, 100, 0));
    }
}
