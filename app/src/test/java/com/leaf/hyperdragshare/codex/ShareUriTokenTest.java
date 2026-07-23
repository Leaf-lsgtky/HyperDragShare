package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ShareUriTokenTest {
    private static final String TOKEN = "42da71e4-df3c-4de0-a6e5-526dedc34dc7";

    @Test
    public void parsesPngJpegAndLegacyTokenPaths() {
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN + ".png"));
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN + ".jpg"));
        assertEquals(TOKEN, ShareUriToken.parse(TOKEN));
    }

    @Test
    public void createsPngFileNameAndRetainsLegacySuffixMetadata() {
        assertEquals(TOKEN + ".png", ShareUriToken.fileName(TOKEN));
        assertEquals(".png", ShareUriToken.suffix(TOKEN + ".png"));
        assertEquals(".jpg", ShareUriToken.suffix(TOKEN + ".jpg"));
        assertEquals(".jpg", ShareUriToken.suffix(TOKEN));
    }

    @Test
    public void rejectsInvalidPathSegments() {
        assertNull(ShareUriToken.parse("not-a-token.jpg"));
        assertNull(ShareUriToken.suffix("not-a-token.png"));
        assertNull(ShareUriToken.parse(null));
    }
}
