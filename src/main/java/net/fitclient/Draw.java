package net.fitclient;

import net.minecraft.client.gui.DrawContext;

/** Small 2D drawing helpers: rounded rectangles, outlines, the "F" mark. */
public final class Draw {
    private Draw() {}

    /** Filled rounded rectangle, drawn as horizontal scanlines. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int dy = 0; dy < h; dy++) {
            int inset = 0;
            if (dy < r) inset = corner(r, dy);
            else if (dy >= h - r) inset = corner(r, h - 1 - dy);
            c.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
        }
    }

    private static int corner(int r, int dy) {
        double d = r - dy - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
    }

    /** Rounded rectangle with a 1px border. */
    public static void roundBorder(DrawContext c, int x, int y, int w, int h, int r, int border, int fill) {
        round(c, x, y, w, h, r, border);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Slanted, bold "F" built from rectangles. size = height in pixels. */
    public static void fMark(DrawContext c, int x, int y, int size, int color) {
        int stem = Math.max(3, size / 3);
        int shear = size / 4;
        int topH = Math.max(3, size / 4);
        int midY = (int) (size * 0.46);
        int midH = Math.max(3, size / 5);
        int fullW = (int) (size * 0.78);
        int midW = (int) (size * 0.60);
        for (int row = 0; row < size; row++) {
            int off = (int) Math.round(shear * (1.0 - row / (double) size));
            int x0 = x + off;
            if (row < topH) {
                c.fill(x0, y + row, x0 + fullW, y + row + 1, color);
            } else if (row >= midY && row < midY + midH) {
                c.fill(x0, y + row, x0 + midW, y + row + 1, color);
            } else {
                c.fill(x0, y + row, x0 + stem, y + row + 1, color);
            }
        }
    }

    public static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
