package net.fitclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fit Client menu. Everything is drawn with DrawContext, no native libraries.
 *
 * NOTE: input method signatures changed in 1.21.9 (Click / KeyInput / CharInput objects).
 * They are all in the "INPUT" section at the bottom, so if your mappings differ that is the only part to touch.
 */
public class ClickGui extends Screen {
    // palette
    static final int BG        = 0xF0100C16;
    static final int BORDER    = 0xFF2B2334;
    static final int CARD      = 0xFF1A1521;
    static final int CARD_HOT  = 0xFF221B2B;
    static final int TEXT      = 0xFFF3ECF7;
    static final int MUTED     = 0xFF7E7290;
    static final int ACCENT    = 0xFFFF8EC8;
    static final int ACCENT_BG = 0xFF3A1E30;
    static final int TRACK_OFF = 0xFF2F2739;

    static final String[] TABS = {"MODULES", "SUS CHUNKS", "SETTINGS"};
    static final int W = 560, H = 340;

    private final List<Module> modules;
    private int tab = 0;
    private String search = "";
    private boolean searchFocus = false;

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui(List<Module> modules) {
        super(Text.literal("Fit Client"));
        this.modules = modules;
    }

    @Override public boolean shouldPause() { return false; }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
        c.fill(0, 0, width, height, 0x88000000); // dim the world

        int px = (width - W) / 2, py = (height - H) / 2;
        Draw.roundBorder(c, px, py, W, H, 10, BORDER, BG);

        header(c, px, py);
        tabs(c, px, py, mx, my);

        int cx = px + 16, cy = py + 84, cw = W - 32, ch = H - 100;
        switch (tab) {
            case 0 -> modulesTab(c, cx, cy, cw, ch, mx, my, delta);
            case 1 -> susTab(c, cx, cy, cw, ch, mx, my);
            default -> settingsTab(c, cx, cy, cw, ch, mx, my);
        }
    }

    private void header(DrawContext c, int px, int py) {
        Draw.fMark(c, px + 18, py + 14, 22, ACCENT);
        c.drawText(textRenderer, "FIT CLIENT", px + 52, py + 18, TEXT, false);
        c.drawText(textRenderer, "v1.0", px + 52, py + 29, MUTED, false);
        c.fill(px + 1, py + 50, px + W - 1, py + 51, BORDER);
    }

    private void tabs(DrawContext c, int px, int py, int mx, int my) {
        int x = px + 16, y = py + 58;
        for (int i = 0; i < TABS.length; i++) {
            int tw = textRenderer.getWidth(TABS[i]) + 22;
            boolean on = i == tab;
            boolean hover = mx >= x && mx < x + tw && my >= y && my < y + 20;
            Draw.roundBorder(c, x, y, tw, 20, 6, on ? ACCENT : BORDER, on ? ACCENT_BG : (hover ? CARD_HOT : CARD));
            c.drawText(textRenderer, TABS[i], x + 11, y + 6, on ? ACCENT : MUTED, false);
            final int idx = i;
            hits.add(new Hit(x, y, tw, 20, () -> tab = idx));
            x += tw + 6;
        }
    }

    // ------------------------------------------------------------------ modules tab
    private void modulesTab(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        // search box
        Draw.roundBorder(c, x, y, 190, 20, 6, searchFocus ? ACCENT : BORDER, CARD);
        String shown = search.isEmpty() && !searchFocus ? "SEARCH MODULES" : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        c.drawText(textRenderer, shown, x + 8, y + 6, search.isEmpty() ? MUTED : TEXT, false);
        hits.add(new Hit(x, y, 190, 20, () -> searchFocus = true));

        int colW = (w - 10) / 2, cardH = 34, gap = 8;
        int i = 0;
        for (Module m : modules) {
            if (!search.isEmpty() && !m.name.toLowerCase().contains(search.toLowerCase())) continue;
            int cx = x + (i % 2) * (colW + 10);
            int cy = y + 30 + (i / 2) * (cardH + gap);
            card(c, m, cx, cy, colW, cardH, mx, my, delta);
            i++;
        }
        if (i == 0) c.drawText(textRenderer, "NO MODULES FOUND", x, y + 40, MUTED, false);
    }

    private void card(DrawContext c, Module m, int x, int y, int w, int h, int mx, int my, float delta) {
        m.anim += ((m.enabled ? 1f : 0f) - m.anim) * Math.min(1f, delta * 0.5f);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Draw.roundBorder(c, x, y, w, h, 7, m.enabled ? Draw.lerp(BORDER, ACCENT, m.anim * 0.6f) : BORDER, hover ? CARD_HOT : CARD);
        if (m.enabled) c.fill(x + 1, y + 8, x + 3, y + h - 8, ACCENT); // accent bar

        c.drawText(textRenderer, m.name.toUpperCase(), x + 12, y + 7, m.enabled ? ACCENT : TEXT, false);
        String desc = textRenderer.trimToWidth(m.description.toUpperCase(), w - 110);
        c.drawText(textRenderer, desc, x + 12, y + 20, MUTED, false);

        // key chip
        int tx = x + w - 40;
        if (!m.keyLabel.isEmpty()) {
            int kw = textRenderer.getWidth(m.keyLabel) + 8;
            Draw.roundBorder(c, tx - kw - 8, y + 11, kw, 12, 4, BORDER, 0xFF14101A);
            c.drawText(textRenderer, m.keyLabel, tx - kw - 4, y + 13, MUTED, false);
        }
        // toggle pill
        Draw.round(c, tx, y + 10, 28, 14, 7, Draw.lerp(TRACK_OFF, ACCENT, m.anim));
        int knobX = tx + 2 + (int) (14 * m.anim);
        Draw.round(c, knobX, y + 12, 10, 10, 5, 0xFFFFFFFF);

        hits.add(new Hit(x, y, w, h, m::toggle));
    }

    // ------------------------------------------------------------------ sus chunks tab
    private void susTab(DrawContext c, int x, int y, int w, int h, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // left: status + chunk map
        int cell = 7, r = Math.min(SusChunks.radius, 14), side = r * 2 + 1, mapPx = side * cell;
        Draw.roundBorder(c, x, y, mapPx + 12, mapPx + 12, 8, BORDER, CARD);
        if (!SusChunks.MODULE.enabled) {
            c.drawText(textRenderer, "MODULE OFF", x + 12, y + 12, MUTED, false);
            c.drawText(textRenderer, "ENABLE IT ON THE", x + 12, y + 26, MUTED, false);
            c.drawText(textRenderer, "MODULES TAB", x + 12, y + 38, MUTED, false);
        } else if (mc.player != null) {
            ChunkPos me = mc.player.getChunkPos();
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    SusChunks.Result res = SusChunks.resultAt(me.x + dx, me.z + dz);
                    int col = 0xFF211B2A;
                    if (res != null) {
                        if (res.score() >= 12) col = ACCENT;
                        else if (res.score() >= 6) col = 0xFFB4608F;
                        else if (res.score() > 0) col = 0xFF5B3A50;
                        else col = 0xFF2A2234;
                    }
                    int gx = x + 6 + (dx + r) * cell, gy = y + 6 + (dz + r) * cell;
                    c.fill(gx, gy, gx + cell - 1, gy + cell - 1, col);
                }
            }
            int pxm = x + 6 + r * cell, pym = y + 6 + r * cell;
            c.fill(pxm + 1, pym + 1, pxm + cell - 2, pym + cell - 2, 0xFFFFFFFF); // you
        }

        // right: top hits
        int rx = x + mapPx + 28, rw = w - (mapPx + 28);
        c.drawText(textRenderer, "TOP CHUNKS", rx, y + 2, TEXT, false);
        c.drawText(textRenderer, "SCORE = STORAGE x3 + ODD BLOCKS x2, NEIGHBOURS ADD HALF", rx, y + 14, MUTED, false);
        List<SusChunks.Hit> list = SusChunks.hits();
        int rowY = y + 30;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            SusChunks.Hit hit = list.get(i);
            Draw.roundBorder(c, rx, rowY, rw, 22, 6, BORDER, CARD);
            String xz = (hit.pos().x * 16 + 8) + ", " + (hit.pos().z * 16 + 8);
            c.drawText(textRenderer, xz, rx + 10, rowY + 7, TEXT, false);
            String info = hit.storage() + " STORAGE";
            c.drawText(textRenderer, info, rx + 110, rowY + 7, MUTED, false);
            String sc = String.valueOf(hit.clusterScore());
            int bw = textRenderer.getWidth(sc) + 12;
            Draw.round(c, rx + rw - bw - 6, rowY + 4, bw, 14, 5, ACCENT_BG);
            c.drawText(textRenderer, sc, rx + rw - bw, rowY + 7, ACCENT, false);
            rowY += 26;
        }
        if (list.isEmpty()) c.drawText(textRenderer, "NOTHING SUSPICIOUS YET", rx, rowY, MUTED, false);
    }

    // ------------------------------------------------------------------ settings tab
    private void settingsTab(DrawContext c, int x, int y, int w, int h, int mx, int my) {
        stepper(c, x, y, w, "SCAN RADIUS (CHUNKS)", SusChunks.radius, mx, my,
                () -> SusChunks.radius = Math.max(4, SusChunks.radius - 2),
                () -> SusChunks.radius = Math.min(32, SusChunks.radius + 2));
        stepper(c, x, y + 40, w, "CHUNKS PER TICK", SusChunks.perTick, mx, my,
                () -> SusChunks.perTick = Math.max(1, SusChunks.perTick - 1),
                () -> SusChunks.perTick = Math.min(24, SusChunks.perTick + 1));
        stepper(c, x, y + 80, w, "SUS THRESHOLD", SusChunks.threshold, mx, my,
                () -> { SusChunks.threshold = Math.max(4, SusChunks.threshold - 2); SusChunks.clear(); },
                () -> { SusChunks.threshold = Math.min(60, SusChunks.threshold + 2); SusChunks.clear(); });
        c.drawText(textRenderer, "LOWER THRESHOLD = MORE RESULTS, MORE FALSE POSITIVES", x, y + 128, MUTED, false);
        c.drawText(textRenderer, "OPEN / CLOSE MENU: RIGHT SHIFT", x, y + 142, MUTED, false);
    }

    private void stepper(DrawContext c, int x, int y, int w, String label, int value, int mx, int my, Runnable minus, Runnable plus) {
        Draw.roundBorder(c, x, y, w, 30, 7, BORDER, CARD);
        c.drawText(textRenderer, label, x + 12, y + 11, TEXT, false);
        int bx = x + w - 96;
        button(c, bx, y + 6, 18, 18, "-", mx, my, minus);
        String v = String.valueOf(value);
        c.drawText(textRenderer, v, bx + 18 + (36 - textRenderer.getWidth(v)) / 2, y + 11, ACCENT, false);
        button(c, bx + 54, y + 6, 18, 18, "+", mx, my, plus);
    }

    private void button(DrawContext c, int x, int y, int w, int h, String label, int mx, int my, Runnable r) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Draw.roundBorder(c, x, y, w, h, 5, hover ? ACCENT : BORDER, hover ? ACCENT_BG : 0xFF14101A);
        c.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, hover ? ACCENT : MUTED, false);
        hits.add(new Hit(x, y, w, h, r));
    }

    // ------------------------------------------------------------------ INPUT (1.21.9+ signatures)
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0) {
            boolean hitSearch = false;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(mx, my)) { searchFocus = false; h.onClick().run(); hitSearch = searchFocus; return true; }
            }
            searchFocus = false;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (searchFocus && key == GLFW.GLFW_KEY_ESCAPE) { searchFocus = false; return true; }
            close();
            return true;
        }
        if (searchFocus && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (searchFocus && tab == 0 && search.length() < 24) {
            search += input.asString();
            return true;
        }
        return super.charTyped(input);
    }
}
