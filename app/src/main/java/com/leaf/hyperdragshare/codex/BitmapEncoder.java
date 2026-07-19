package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;

final class BitmapEncoder {
    private static final int MAX_SIDE = 1600;
    private static final int MIN_SIDE = 32;
    private static final int[] QUALITIES = {92, 84, 76, 68, 58, 48, 38};

    private BitmapEncoder() {}

    static byte[] encodeJpeg(Bitmap source, int maximumBytes) {
        if (source == null || source.isRecycled()) {
            throw new IllegalArgumentException("Bitmap is unavailable");
        }

        Bitmap work = source;
        boolean ownsWork = false;
        try {
            if (source.getConfig() == Bitmap.Config.HARDWARE) {
                work = source.copy(Bitmap.Config.ARGB_8888, false);
                ownsWork = true;
            }

            int largestSide = Math.max(work.getWidth(), work.getHeight());
            if (largestSide > MAX_SIDE) {
                float scale = (float) MAX_SIDE / largestSide;
                Bitmap scaled = Bitmap.createScaledBitmap(
                        work,
                        Math.max(1, Math.round(work.getWidth() * scale)),
                        Math.max(1, Math.round(work.getHeight() * scale)),
                        true);
                if (ownsWork) {
                    work.recycle();
                }
                work = scaled;
                ownsWork = true;
            }

            if (work.hasAlpha()) {
                Bitmap flattened = Bitmap.createBitmap(
                        work.getWidth(), work.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(flattened);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(work, 0, 0, null);
                if (ownsWork) {
                    work.recycle();
                }
                work = flattened;
                ownsWork = true;
            }

            byte[] lastAttempt = new byte[0];
            while (true) {
                for (int quality : QUALITIES) {
                    lastAttempt = compress(work, quality);
                    if (lastAttempt.length > 0 && lastAttempt.length <= maximumBytes) {
                        return lastAttempt;
                    }
                }

                if (Math.min(work.getWidth(), work.getHeight()) <= MIN_SIDE) {
                    return lastAttempt;
                }
                Bitmap smaller = Bitmap.createScaledBitmap(
                        work,
                        Math.max(1, Math.round(work.getWidth() * 0.72f)),
                        Math.max(1, Math.round(work.getHeight() * 0.72f)),
                        true);
                if (ownsWork) {
                    work.recycle();
                }
                work = smaller;
                ownsWork = true;
            }
        } finally {
            if (ownsWork && work != null && !work.isRecycled()) {
                work.recycle();
            }
        }
    }

    private static byte[] compress(Bitmap bitmap, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(128 * 1024);
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            return new byte[0];
        }
        return output.toByteArray();
    }
}
