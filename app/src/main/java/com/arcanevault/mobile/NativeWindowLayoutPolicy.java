package com.arcanevault.mobile;

/** Content-window layout decisions shared by native screens. Widths are dp. */
final class NativeWindowLayoutPolicy {
    enum Size { COMPACT, MEDIUM, EXPANDED }
    private NativeWindowLayoutPolicy() {}

    static Size size(int windowWidthDp) {
        if (windowWidthDp >= 840) return Size.EXPANDED;
        if (windowWidthDp >= 600) return Size.MEDIUM;
        return Size.COMPACT;
    }

    static boolean twoPaneCommander(int widthDp) {
        return size(widthDp) != Size.COMPACT;
    }

    /** Keep artwork legible: avoid forcing two columns into narrow split-screen windows. */
    static int catalogColumns(int contentWidthDp) {
        if (contentWidthDp < 300) return 1;
        if (contentWidthDp < 520) return 2;
        if (contentWidthDp < 760) return 3;
        if (contentWidthDp < 1020) return 4;
        if (contentWidthDp < 1280) return 5;
        return 6;
    }

    static int minimumArtworkWidthDp(int contentWidthDp) {
        return Math.max(100, contentWidthDp / catalogColumns(contentWidthDp) - 8);
    }
}
