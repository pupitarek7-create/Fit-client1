package net.fitclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Drawing helpers. Everything is drawn in "design units" where one unit is a whole number of
 * physical pixels (see ClickGui), so edges stay sharp, and corners are anti-aliased.
 */
public final class Ui {
    private Ui() {}

    public static final Style UI    = font("ui");
    public static final Style SMALL = font("small");
    public static final Style BIG   = font("big");

    private static Style font(String name) {
        return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("fitclient", name)));
    }

    // ---------------------------------------------------------------- text
    public static void text(GuiGraphics g, String s, int x, int y, int color, Style style) {
        Font f = Minecraft.getInstance().font;
        g.drawString(f, Component.literal(s).withStyle(style), x, y, color, false);
    }

    public static int width(String s, Style style) {
        return Minecraft.getInstance().font.width(Component.literal(s).withStyle(style));
    }

    /** Text with its vertical middle on cy. */
    public static void textMid(GuiGraphics g, String s, int x, int cy, int color, Style style) {
        text(g, s, x, cy - lineH(style) / 2, color, style);
    }

    public static void textRight(GuiGraphics g, String s, int rx, int cy, int color, Style style) {
        textMid(g, s, rx - width(s, style), cy, color, style);
    }

    public static int lineH(Style style) {
        return style == BIG ? 24 : style == SMALL ? 12 : 15;
    }

    // ---------------------------------------------------------------- shapes
    /** Filled rounded rectangle with anti-aliased corners. Few fill calls: one per corner row + a few AA pixels. */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + r, x + w, y + h - r, color);              // middle band
        int a0 = color >>> 24, rgb = color & 0x00FFFFFF;
        int[][] cov = coverTable(r);
        for (int cy = 0; cy < r; cy++) {
            int[] row = cov[cy];
            int first = r, full = r;
            for (int cx = 0; cx < r; cx++) { if (first == r && row[cx] > 0) first = cx; if (row[cx] == 16) { full = cx; break; } }
            for (int cx = first; cx < full; cx++) {
                int c = (rgb) | (((a0 * row[cx]) / 16) << 24);
                g.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, c);
                g.fill(x + w - 1 - cx, y + cy, x + w - cx, y + cy + 1, c);
                g.fill(x + cx, y + h - 1 - cy, x + cx + 1, y + h - cy, c);
                g.fill(x + w - 1 - cx, y + h - 1 - cy, x + w - cx, y + h - cy, c);
            }
            g.fill(x + full, y + cy, x + w - full, y + cy + 1, color);
            g.fill(x + full, y + h - 1 - cy, x + w - full, y + h - cy, color);
        }
    }

    /** Cheap rounded cell (3 fills, 1px corner cut) for dense grids. */
    public static void cell(GuiGraphics g, int x, int y, int s, int color) {
        g.fill(x + 1, y, x + s - 1, y + s, color);
        g.fill(x, y + 1, x + 1, y + s - 1, color);
        g.fill(x + s - 1, y + 1, x + s, y + s - 1, color);
    }

    private static final int[][][] COVER_CACHE = new int[96][][];

    private static int[][] coverTable(int r) {
        if (r >= COVER_CACHE.length) return buildCover(r);
        if (COVER_CACHE[r] == null) COVER_CACHE[r] = buildCover(r);
        return COVER_CACHE[r];
    }

    private static int[][] buildCover(int r) {
        int[][] t = new int[r][r];
        for (int j = 0; j < r; j++) for (int i = 0; i < r; i++) t[j][i] = computeCoverage(r, i, j);
        return t;
    }

    private static int computeCoverage(int r, int cx, int cy) {
        int inside = 0;
        for (int sy = 0; sy < 4; sy++) {
            for (int sx = 0; sx < 4; sx++) {
                double px = cx + (sx + 0.5) / 4.0 - r, py = cy + (sy + 0.5) / 4.0 - r;
                if (px * px + py * py <= (double) r * r) inside++;
            }
        }
        return inside;
    }

    /** Rounded rectangle with a 1-unit border (drawn as outer shape + inner shape). */
    public static void roundBorder(GuiGraphics g, int x, int y, int w, int h, int r, int border, int fill) {
        round(g, x, y, w, h, r, border);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Soft glow: a few expanding translucent rounded rects behind something. */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int rgb, int strength) {
        for (int i = 4; i >= 1; i--) {
            int a = Math.max(1, strength * (5 - i) / 18);
            round(g, x - i * 3, y - i * 3, w + i * 6, h + i * 6, r + i * 3, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** Slanted, bold "F" built from rectangles. size = height. */
    public static void fMark(GuiGraphics g, int x, int y, int size, int color) {
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
            if (row < topH) g.fill(x0, y + row, x0 + fullW, y + row + 1, color);
            else if (row >= midY && row < midY + midH) g.fill(x0, y + row, x0 + midW, y + row + 1, color);
            else g.fill(x0, y + row, x0 + stem, y + row + 1, color);
        }
    }

    public static int alpha(int color, int a) { return (a << 24) | (color & 0x00FFFFFF); }

    public static int lerp(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
