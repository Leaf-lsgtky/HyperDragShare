package com.leaf.hyperdragshare.codex;

/** DragShare captures one text block at a time, so BigBang's adjacent-paragraph pull is inert. */
final class TextSessionCoordinator {
    static final TextSessionCoordinator INSTANCE = new TextSessionCoordinator();

    private TextSessionCoordinator() {}

    String peekAdjacentText(String direction) {
        return null;
    }
}
