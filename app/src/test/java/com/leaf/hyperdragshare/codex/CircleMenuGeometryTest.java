package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CircleMenuGeometryTest {
    @Test
    public void onlyLeftAndRightEdgesCanTrigger() {
        assertEquals(
                CircleMenuGeometry.EDGE_LEFT,
                CircleMenuGeometry.nearestEdge(20, 500, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_RIGHT,
                CircleMenuGeometry.nearestEdge(1060, 500, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 20, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 1980, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(20, 20, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 1000, 1080, 2000, 76));
    }

    @Test
    public void startAnglesMatchFourReferenceLayouts() {
        assertEquals(18, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 5));
        assertEquals(108, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_TOP, 5));
        assertEquals(198, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_RIGHT, 5));
        assertEquals(288, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 5));
        assertEquals(324, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 2));
        assertEquals(90, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 1));
        assertEquals(0, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 1));
    }

    @Test
    public void sideContainersAreSemicirclesClampedInsideInsets() {
        int[] left = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_LEFT,
                20,
                1080,
                2000,
                600,
                40,
                80);
        assertEquals(0, left[0]);
        assertEquals(40, left[1]);
        assertEquals(300, left[2]);
        assertEquals(640, left[3]);

        int[] right = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_RIGHT,
                1900,
                1080,
                2000,
                600,
                40,
                80);
        assertEquals(780, right[0]);
        assertEquals(1320, right[1]);
        assertEquals(1080, right[2]);
        assertEquals(1920, right[3]);
    }

    @Test
    public void itemCentersAdvanceAroundArc() {
        float[] first = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 0, 5);
        float[] second = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 1, 5);
        assertTrue(first[0] != second[0] || first[1] != second[1]);
        assertTrue(first[0] >= 0 && first[0] <= 300);
        assertTrue(first[1] >= 700 && first[1] <= 1300);
    }

    @Test
    public void centerItemUsesScreenEdgeAsCircleCenter() {
        float[] leftCenter = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 2, 5);
        assertEquals(220f, leftCenter[0], 0.01f);
        assertEquals(1000f, leftCenter[1], 0.01f);

        float[] rightCenter = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_RIGHT, 780, 700, 600, 220, 2, 5);
        assertEquals(860f, rightCenter[0], 0.01f);
        assertEquals(1000f, rightCenter[1], 0.01f);
    }

    @Test
    public void displayOrderIsNaturalFromTopToBottom() {
        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4},
                CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_LEFT, 5));
        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4},
                CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_RIGHT, 5));
        assertArrayEquals(
                new int[]{0, 1, 2},
                CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_LEFT, 3));

        float[] leftFirst = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 0, 5);
        float[] leftLast = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 4, 5);
        float[] rightFirst = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_RIGHT, 780, 700, 600, 220, 0, 5);
        float[] rightLast = CircleMenuGeometry.itemCenterValues(
                CircleMenuGeometry.EDGE_RIGHT, 780, 700, 600, 220, 4, 5);
        assertTrue(leftFirst[1] < leftLast[1]);
        assertTrue(rightFirst[1] < rightLast[1]);
    }
}
