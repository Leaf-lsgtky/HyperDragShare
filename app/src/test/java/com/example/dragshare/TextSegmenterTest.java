package com.example.dragshare;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class TextSegmenterTest {
    @Test
    public void nativeRangesUseTheBigBangWordAndPunctuationFormat() {
        assertArrayEquals(
                new int[]{0, 1, 3, 4, -1, 2, 2},
                TextSegmenter.buildSegments("你好，世界", new int[]{0, 2, 3, 5}));
    }

    @Test
    public void whitespaceIsKeptBetweenWordsButNotMadeSelectable() {
        assertArrayEquals(
                new int[]{0, 4, 6, 7, -1},
                TextSegmenter.buildSegments("hello 世界", new int[]{0, 5, 6, 8}));
    }

    @Test
    public void emptyInputDoesNotProduceAnInvalidBigBangSegment() {
        assertNull(TextSegmenter.buildSegments("", new int[]{0, 1}));
    }
}
