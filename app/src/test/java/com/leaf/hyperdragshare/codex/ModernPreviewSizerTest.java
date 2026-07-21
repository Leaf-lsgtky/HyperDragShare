package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModernPreviewSizerTest {
    @Test
    public void textPreviewGrowsWithContentAndNeverExceedsOneThirdOfScreenWidth() {
        CapturedContent shortText = CapturedContent.text("短文字", "pkg", new Rect());
        CapturedContent longText = CapturedContent.text(repeat("现代拖拽预览", 40), "pkg", new Rect());

        int shortSide = ModernPreviewSizer.squareSidePx(shortText, 1200, 3f);
        int longSide = ModernPreviewSizer.squareSidePx(longText, 1200, 3f);

        assertTrue(longSide > shortSide);
        assertTrue(shortSide <= 400);
        assertTrue(longSide <= 400);
    }

    @Test
    public void imagePreviewUsesImageDimensionsAndRespectsNarrowScreens() {
        Bitmap smallBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Bitmap largeBitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888);
        CapturedContent smallImage = CapturedContent.image(smallBitmap, "pkg", new Rect(), false);
        CapturedContent largeImage = CapturedContent.image(largeBitmap, "pkg", new Rect(), false);

        int smallSide = ModernPreviewSizer.squareSidePx(smallImage, 1080, 3f);
        int largeSide = ModernPreviewSizer.squareSidePx(largeImage, 1080, 3f);
        int narrowSide = ModernPreviewSizer.squareSidePx(largeImage, 300, 1f);

        assertTrue(largeSide > smallSide);
        assertTrue(largeSide <= 360);
        assertEquals(100, narrowSide);
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
