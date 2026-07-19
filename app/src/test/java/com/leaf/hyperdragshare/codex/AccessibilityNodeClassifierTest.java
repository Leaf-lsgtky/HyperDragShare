package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class AccessibilityNodeClassifierTest {
    @Test
    public void textAndImageFollowDocumentedPriority() {
        AccessibilityNodeSnapshot text = snapshot(new Rect(0, 0, 200, 100))
                .className("android.widget.TextView")
                .text("Hello")
                .build();
        AccessibilityNodeSnapshot image = snapshot(new Rect(20, 20, 120, 90))
                .className("android.widget.ImageView")
                .contentDescription("photo")
                .build();
        AccessibilityNodeClassifier.Buckets buckets = new AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(Arrays.asList(text, image));

        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                buckets, 30f, 30f);
        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.TEXT, selection.candidate.kind);
    }

    @Test
    public void strongImageUsesImageRegionEvenWithDescription() {
        AccessibilityNodeSnapshot image = snapshot(new Rect(0, 0, 100, 100))
                .className("android.widget.ImageView")
                .contentDescription("A scenic photo")
                .build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(image)),
                10f,
                10f);

        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection.candidate.kind);
    }

    @Test
    public void genericImageRoleWithoutTextIsAnImageRegion() {
        AccessibilityNodeSnapshot image = snapshot(new Rect(0, 0, 100, 100))
                .className("android.view.View")
                .contentDescription("image")
                .build();

        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(image)),
                10f,
                10f);

        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection.candidate.kind);
    }

    @Test
    public void smallEmptyLeafAndPasswordAreRejected() {
        AccessibilityNodeSnapshot tiny = snapshot(new Rect(0, 0, 20, 20))
                .className("android.view.View")
                .build();
        AccessibilityNodeSnapshot password = snapshot(new Rect(30, 0, 200, 80))
                .className("android.widget.EditText")
                .text("secret")
                .password(true)
                .build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(tiny, password)),
                10f,
                10f);

        assertNull(selection);
    }

    @Test
    public void sameTextParentIsReplacedBySpecificChild() {
        AccessibilityNodeSnapshot parent = snapshot(new Rect(0, 0, 200, 200))
                .className("android.widget.TextView")
                .text("same")
                .leaf(false)
                .depth(1)
                .traversalOrder(1)
                .build();
        AccessibilityNodeSnapshot child = snapshot(new Rect(20, 20, 100, 60))
                .className("android.widget.TextView")
                .text("same")
                .leaf(true)
                .depth(2)
                .traversalOrder(2)
                .build();
        AccessibilityNodeClassifier.Buckets buckets = new AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(Arrays.asList(parent, child));

        assertEquals(1, buckets.nativeText.size());
        assertEquals(new Rect(20, 20, 100, 60), buckets.nativeText.get(0).bounds);
    }

    private static AccessibilityNodeSnapshot.Builder snapshot(Rect bounds) {
        return new AccessibilityNodeSnapshot.Builder()
                .bounds(bounds)
                .visible(true)
                .leaf(true)
                .traversalOrder(1);
    }
}
