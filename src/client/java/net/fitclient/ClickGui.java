package net.fitclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fit Client menu. Everything is drawn with GuiGraphics, no native libraries.
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
        super(Component.literal("Fit Client"));
        this.modules = modules;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics c, int mx, int my, float delta) {
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

    private void header(GuiGraphics c, int px, int py) {
        Draw.fMark(c, px + 18, py + 14, 22, ACCENT);
        c.drawString(font, "FIT CLIENT", px + 52, py + 18, TEXT, false);
        c.drawString(font, "v1.0", px + 52, py + 29, MUTED, false);
        c.fill(px + 1, py + 50, px + W - 1, py + 51, BORDER);
    }

    private void tabs(GuiGraphics c, int px, int py, int mx, int my) {
        int x = px + 16, y = py + 58;
        for (int i = 0; i < TABS.length; i++) {
            int tw = font.width(TABS[i]) + 22;
            boolean on = i == tab;
            boolean hover = mx >= x && mx < x + tw && my >= y && my < y + 20;
            Draw.roundBorder(c, x, y, tw, 20, 6, on ? ACCENT : BORDER, on ? ACCENT_BG : (hover ? CARD_HOT : CARD));
            c.drawString(font, TABS[i], x + 11, y + 6, on ? ACCENT : MUTED, false);
            final int idx = i;
            hits.add(new Hit(x, y, tw, 20, () -> tab = idx));
            x += tw + 6;
        }
    }

    // ------------------------------------------------------------------ modules tab
    private void modulesTab(GuiGraphics c, int x, int y, int w, int h, int mx, int my, float delta) {
        // search box
        Draw.roundBorder(c, x, y, 190, 20, 6, searchFocus ? ACCENT : BORDER, CARD);
        String shown = search.isEmpty() && !searchFocus ? "SEARCH MODULES" : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        c.drawString(font, shown, x + 8, y + 6, search.isEmpty() ? MUTED : TEXT, false);
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
        if (i == 0) c.drawString(font, "NO MODULES FOUND", x, y + 40, MUTED, false);
    }

    private void card(GuiGraphics c, Module m, int x, int y, int w, int h, int mx, int my, float delta) {
        m.anim += ((m.enabled ? 1f : 0f) - m.anim) * Math.min(1f, delta * 0.5f);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Draw.roundBorder(c, x, y, w, h, 7, m.enabled ? Draw.lerp(BORDER, ACCENT, m.anim * 0.6f) : BORDER, hover ? CARD_HOT : CARD);
        if (m.enabled) c.fill(x + 1, y + 8, x + 3, y + h - 8, ACCENT); // accent bar

        c.drawString(font, m.name.toUpperCase(), x + 12, y + 7, m.enabled ? ACCENT : TEXT, false);
        String desc = font.plainSubstrByWidth(m.description.toUpperCase(), w - 110);
        c.drawString(font, desc, x + 12, y + 20, MUTED, false);

        // key chip
        int tx = x + w - 40;
        if (!m.keyLabel.isEmpty()) {
            int kw = font.width(m.keyLabel) + 8;
            Draw.roundBorder(c, tx - kw - 8, y + 11, kw, 12, 4, BORDER, 0xFF14101A);
            c.drawString(font, m.keyLabel, tx - kw - 4, y + 13, MUTED, false);
        }
        // toggle pill
        Draw.round(c, tx, y + 10, 28, 14, 7, Draw.lerp(TRACK_OFF, ACCENT, m.anim));
        int knobX = tx + 2 + (int) (14 * m.anim);
        Draw.round(c, knobX, y + 12, 10, 10, 5, 0xFFFFFFFF);

        hits.add(new Hit(x, y, w, h, m::toggle));
    }

    // ------------------------------------------------------------------ sus chunks tab
    private void susTab(GuiGraphics c, int x, int y, int w, int h, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();

        // left: status + chunk map
        int cell = 7, r = Math.min(SusChunks.radius, 14), side = r * 2 + 1, mapPx = side * cell;
        Draw.roundBorder(c, x, y, mapPx + 12, mapPx + 12, 8, BORDER, CARD);
        if (!SusChunks.MODULE.enabled) {
            c.drawString(font, "MODULE OFF", x + 12, y + 12, MUTED, false);
            c.drawString(font, "ENABLE IT ON THE", x + 12, y + 26, MUTED, false);
            c.drawString(font, "MODULES TAB", x + 12, y + 38, MUTED, false);
        } else if (mc.player != null) {
            ChunkPos me = mc.player.chunkPosition();
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    SusChunks.Result res = SusChunks.resultAt(SusChunks.cx(me) + dx, SusChunks.cz(me) + dz);
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
        c.drawString(font, "TOP CHUNKS", rx, y + 2, TEXT, false);
        c.drawString(font, "SCORE = STORAGE x3 + ODD BLOCKS x2, NEIGHBOURS ADD HALF", rx, y + 14, MUTED, false);
        List<SusChunks.Hit> list = SusChunks.hits();
        int rowY = y + 30;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            SusChunks.Hit hit = list.get(i);
            Draw.roundBorder(c, rx, rowY, rw, 22, 6, BORDER, CARD);
            String xz = (SusChunks.cx(hit.pos()) * 16 + 8) + ", " + (SusChunks.cz(hit.pos()) * 16 + 8);
            c.drawString(font, xz, rx + 10, rowY + 7, TEXT, false);
            String info = hit.storage() + " STORAGE";
            c.drawString(font, info, rx + 110, rowY + 7, MUTED, false);
            String sc = String.valueOf(hit.clusterScore());
            int bw = font.width(sc) + 12;
            Draw.round(c, rx + rw - bw - 6, rowY + 4, bw, 14, 5, ACCENT_BG);
            c.drawString(font, sc, rx + rw - bw, rowY + 7, ACCENT, false);
            rowY += 26;
        }
        if (list.isEmpty()) c.drawString(font, "NOTHING SUSPICIOUS YET", rx, rowY, MUTED, false);
    }

    // ------------------------------------------------------------------ settings tab
    private void settingsTab(GuiGraphics c, int x, int y, int w, int h, int mx, int my) {
        stepper(c, x, y, w, "SCAN RADIUS (CHUNKS)", SusChunks.radius, mx, my,
                () -> SusChunks.radius = Math.max(4, SusChunks.radius - 2),
                () -> SusChunks.radius = Math.min(32, SusChunks.radius + 2));
        stepper(c, x, y + 40, w, "CHUNKS PER TICK", SusChunks.perTick, mx, my,
                () -> SusChunks.perTick = Math.max(1, SusChunks.perTick - 1),
                () -> SusChunks.perTick = Math.min(24, SusChunks.perTick + 1));
        stepper(c, x, y + 80, w, "SUS THRESHOLD", SusChunks.threshold, mx, my,
                () -> { SusChunks.threshold = Math.max(4, SusChunks.threshold - 2); SusChunks.clear(); },
                () -> { SusChunks.threshold = Math.min(60, SusChunks.threshold + 2); SusChunks.clear(); });
        c.drawString(font, "LOWER THRESHOLD = MORE RESULTS, MORE FALSE POSITIVES", x, y + 128, MUTED, false);
        c.drawString(font, "OPEN / CLOSE MENU: RIGHT SHIFT", x, y + 142, MUTED, false);
    }

    private void stepper(GuiGraphics c, int x, int y, int w, String label, int value, int mx, int my, Runnable minus, Runnable plus) {
        Draw.roundBorder(c, x, y, w, 30, 7, BORDER, CARD);
        c.drawString(font, label, x + 12, y + 11, TEXT, false);
        int bx = x + w - 96;
        button(c, bx, y + 6, 18, 18, "-", mx, my, minus);
        String v = String.valueOf(value);
        c.drawString(font, v, bx + 18 + (36 - font.width(v)) / 2, y + 11, ACCENT, false);
        button(c, bx + 54, y + 6, 18, 18, "+", mx, my, plus);
    }

    private void button(GuiGraphics c, int x, int y, int w, int h, String label, int mx, int my, Runnable r) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Draw.roundBorder(c, x, y, w, h, 5, hover ? ACCENT : BORDER, hover ? ACCENT_BG : 0xFF14101A);
        c.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hover ? ACCENT : MUTED, false);
        hits.add(new Hit(x, y, w, h, r));
    }

    // ------------------------------------------------------------------ INPUT (1.21.9+ signatures)
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0) {
                        for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(mx, my)) { searchFocus = false; h.onClick().run(); return true; }
            }
            searchFocus = false;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (searchFocus && key == GLFW.GLFW_KEY_ESCAPE) { searchFocus = false; return true; }
            onClose();
            return true;
        }
        if (searchFocus && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (searchFocus && tab == 0 && search.length() < 24) {
            search += Character.toString(input.codepoint());
            return true;
        }
        return super.charTyped(input);
    }
}
