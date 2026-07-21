package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class GestureMathTest {
    @Test
    public void menuTriggersAndActivationMovementRespectDirection() {
        assertFalse(GestureMath.shouldShowMenu(799, 800));
        assertTrue(GestureMath.shouldShowMenu(800, 800));
        assertTrue(GestureMath.shouldShowMenu(1200, 800));

        assertFalse(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 500f, 815f, 16f));
        assertTrue(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 500f, 816f, 16f));
        assertFalse(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                500f, 800f, 540f, 800f, 16f));
        assertTrue(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_TOP,
                500f, 800f, 500f, 784f, 16f));
        assertTrue(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_LEFT,
                500f, 800f, 484f, 800f, 16f));
        assertTrue(GestureMath.hasMovedTowardMenu(
                DragShareSettings.SIMPLE_MENU_POSITION_RIGHT,
                500f, 800f, 516f, 800f, 16f));
    }

    @Test
    public void edgeZonesScrollInExpectedDirection() {
        assertEquals(-1, GestureMath.edgeScrollDirection(0, 1080, 56));
        assertEquals(-1, GestureMath.edgeScrollDirection(56, 1080, 56));
        assertEquals(0, GestureMath.edgeScrollDirection(540, 1080, 56));
        assertEquals(1, GestureMath.edgeScrollDirection(1024, 1080, 56));
        assertEquals(1, GestureMath.edgeScrollDirection(1080, 1080, 56));
    }

    @Test
    public void clampKeepsCoordinatesWithinViewport() {
        assertEquals(8, GestureMath.clamp(-10, 8, 100));
        assertEquals(40, GestureMath.clamp(40, 8, 100));
        assertEquals(100, GestureMath.clamp(120, 8, 100));
    }

    @Test
    public void previewTracksPointerAndClampsAtScreenEdges() {
        assertEquals(8, GestureMath.previewLeft(20, 100, 1080, 8));
        assertEquals(490, GestureMath.previewLeft(540, 100, 1080, 8));
        assertEquals(972, GestureMath.previewLeft(1070, 100, 1080, 8));

        assertEquals(40, GestureMath.previewTop(20, 120, 20, 40, 1800));
        assertEquals(860, GestureMath.previewTop(1000, 120, 20, 40, 1800));
        assertEquals(1800, GestureMath.previewTop(2100, 120, 20, 40, 1800));
    }

    @Test
    public void portalGlowProgressTracksDownwardTravel() {
        assertEquals(0f, GestureMath.dragPullProgress(300, 400, 900), 0.001f);
        assertEquals(0.5f, GestureMath.dragPullProgress(650, 400, 900), 0.001f);
        assertEquals(1f, GestureMath.dragPullProgress(1000, 400, 900), 0.001f);
    }

    @Test
    public void portalItemsScaleByDistanceFromPointer() {
        assertEquals(1.23f, GestureMath.portalItemScale(500, 500, 80), 0.001f);
        assertEquals(1.10f, GestureMath.portalItemScale(580, 500, 80), 0.001f);
        assertEquals(1f, GestureMath.portalItemScale(700, 500, 80), 0.001f);
    }

    @Test
    public void nearHandMenuUsesTheLowerSideOfTheDevice() {
        assertTrue(GestureMath.nearHandMenuOnRight(0.2f));
        assertFalse(GestureMath.nearHandMenuOnRight(-0.2f));
    }

    @Test
    public void rawTouchRangeMapsToPhysicalPixels() {
        assertArrayEquals(
                new float[]{1219f, 2655f},
                GestureMath.mapRawPoint(
                        121999, 265599, 121999, 265599, 1220, 2656, 0),
                0.01f);
        assertArrayEquals(
                new float[]{610f, 1328f},
                GestureMath.mapRawPoint(
                        61050, 132850, 121999, 265599, 1220, 2656, 0),
                1f);
    }

    @Test
    public void rawTouchRangeAccountsForRotation() {
        assertArrayEquals(
                new float[]{2655f, 0f},
                GestureMath.mapRawPoint(
                        121999, 265599, 121999, 265599, 2656, 1220, 1),
                0.01f);
        assertArrayEquals(
                new float[]{0f, 1219f},
                GestureMath.mapRawPoint(
                        121999, 265599, 121999, 265599, 2656, 1220, 3),
                0.01f);
    }
}
