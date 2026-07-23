package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class LocalImageSaverTest {
    @Test
    public void timestampNameUsesSecondPrecisionAndPngExtension() {
        String name = LocalImageSaver.timestampName(0L);

        assertTrue(name.endsWith(".png"));
        assertEquals(19, name.length());
        assertTrue(name.matches("\\d{8}_\\d{6}\\.png"));
    }

    @Test
    public void pngEncodingPreservesTransparentPadding() throws IOException {
        Bitmap source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.TRANSPARENT);
        source.setPixel(0, 0, Color.RED);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LocalImageSaver.writePng(source, output);

        byte[] encoded = output.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        assertNotNull(decoded);
        assertEquals(0, Color.alpha(decoded.getPixel(2, 2)));
        assertEquals(Color.RED, decoded.getPixel(0, 0));
    }

    @Test
    public void pngEncodingKeepsOpaqueRightAndBottomEdgesAtDifferentDensity() throws IOException {
        Bitmap source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.rgb(17, 83, 149));
        source.setDensity(640);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LocalImageSaver.writePng(source, output);

        byte[] encoded = output.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        assertNotNull(decoded);
        assertEquals(4, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
        assertEquals(Color.rgb(17, 83, 149), decoded.getPixel(3, 2));
    }
}
