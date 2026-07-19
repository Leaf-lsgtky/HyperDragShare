package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ShareUriTokenTest {
    private static final String TOKEN = "42da71e4-df3c-4de0-a6e5-526dedc34dc7";

    @Test
    public void parsesNewJpegPathAndLegacyTokenPath() {
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN + ".jpg"));
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN));
    }

    @Test
    public void createsJpegFileName() {
        assertEquals(TOKEN + ".jpg", ShareUriToken.fileName(TOKEN));
    }

    @Test
    public void rejectsInvalidPathSegments() {
        assertNull(ShareUriToken.parse("not-a-token.jpg"));
        assertNull(ShareUriToken.parse(null));
    }
}
