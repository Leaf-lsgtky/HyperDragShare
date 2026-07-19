package com.example.dragshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LocalImageSaverTest {
    @Test
    public void timestampNameUsesSecondPrecisionAndJpegExtension() {
        String name = LocalImageSaver.timestampName(0L);

        assertTrue(name.endsWith(".jpg"));
        assertEquals(19, name.length());
        assertTrue(name.matches("\\d{8}_\\d{6}\\.jpg"));
    }
}
