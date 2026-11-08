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
 * Fit Client menu. Drawn in "design units" (1 unit = u physical pixels, u is a whole number),
 * so shapes are crisp and the Poppins font is rendered at full resolution.
 */
public class ClickGui extends Screen {
    // palette
    static final int BG        = 0xF2100C16;
    static final int BORDER    = 0xFF2B2334;
    static final int CARD      = 0xFF1A1521;
    static final int CARD_HOT  = 0xFF221B2B;
    static final int TEXT      = 0xFFF3ECF7;
    static final int MUTED     = 0xFF8A7E9C;
    static final int ACCENT    = 0xFFFF8EC8;
    static final int ACCENT_BG = 0xFF3A1E30;
    static final int TRACK_OFF = 0xFF2F2739;
    static final int ON_ACCENT = 0xFF1A0F17;

    static final String[] TABS = {"MODULES", "SUS CHUNKS", "SETTINGS"};
    static final int W = 900, H = 560;

    private final List<Module> modules;
    private int tab = 0;
    private String search = "";
    private boolean searchFocus = false;
    private double scaleGui = 1; // gui scale factor
    private int u = 1;           // physical pixels per design unit

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui(List<Module> modules) {
        super(Component.literal("Fit Client"));
        this.modules = modules;
    }

    @Override public boolean isPauseScreen() { return false; }

    private int toDesign(double guiCoord) { return (int) Math.floor(guiCoord * scaleGui / u); }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        scaleGui = mc.getWindow().getGuiScale();
        int fbW = mc.getWindow().getWidth(), fbH = mc.getWindow().getHeight();
        u = Math.max(1, Math.min(fbW / (W + 80), fbH / (H + 80)));
        int dw = fbW / u, dh = fbH / u;
        int mx = toDesign(mouseX), my = toDesign(mouseY);

        hits.clear();
        g.pose().pushMatrix();
        g.pose().scale((float) (u / scaleGui), (float) (u / scaleGui));

        g.fill(0, 0, dw, dh, 0x99050308); // dim the world

        int px = (dw - W) / 2, py = (dh - H) / 2;
        Ui.glow(g, px, py, W, H, 18, 0xFF8EC8, 22);
        Ui.roundBorder(g, px, py, W, H, 18, BORDER, BG);

        header(g, px, py, mx, my);

        int cx = px + 32, cy = py + 104, cw = W - 64, ch = H - 136;
        switch (tab) {
            case 0 -> modulesTab(g, cx, cy, cw, ch, mx, my, delta);
            case 1 -> susTab(g, cx, cy, cw, ch, mx, my);
            default -> settingsTab(g, cx, cy, cw, ch, mx, my);
        }
        g.pose().popMatrix();
    }

    private void header(GuiGraphics g, int px, int py, int mx, int my) {
        Ui.fMark(g, px + 32, py + 22, 36, ACCENT);
        Ui.textMid(g, "FIT CLIENT", px + 84, py + 34, TEXT, Ui.BIG);
        Ui.textMid(g, "v1.0", px + 84, py + 56, MUTED, Ui.SMALL);

        int x = px + 300, y = py + 22;
        for (int i = 0; i < TABS.length; i++) {
            int tw = Ui.width(TABS[i], Ui.SMALL) + 40;
            boolean on = i == tab;
            boolean hover = mx >= x && mx < x + tw && my >= y && my < y + 36;
            Ui.roundBorder(g, x, y, tw, 36, 10, on ? ACCENT : BORDER, on ? ACCENT_BG : (hover ? CARD_HOT : CARD));
            Ui.textMid(g, TABS[i], x + 20, y + 18, on ? ACCENT : MUTED, Ui.SMALL);
            final int idx = i;
            hits.add(new Hit(x, y, tw, 36, () -> tab = idx));
            x += tw + 10;
        }
        g.fill(px + 24, py + 80, px + W - 24, py + 81, BORDER);
    }

    // ------------------------------------------------------------------ modules tab
    private void modulesTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float delta) {
        Ui.roundBorder(g, x, y, 320, 40, 12, searchFocus ? ACCENT : BORDER, CARD);
        boolean blink = searchFocus && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = search.isEmpty() && !searchFocus ? "SEARCH MODULES" : search + (blink ? "|" : "");
        Ui.textMid(g, shown, x + 18, y + 20, search.isEmpty() ? MUTED : TEXT, Ui.SMALL);
        hits.add(new Hit(x, y, 320, 40, () -> searchFocus = true));

        int colW = (w - 16) / 2, cardH = 68, gap = 14;
        int i = 0;
        for (Module m : modules) {
            if (!search.isEmpty() && !m.name.toLowerCase().contains(search.toLowerCase())) continue;
            int cx = x + (i % 2) * (colW + 16);
            int cy = y + 60 + (i / 2) * (cardH + gap);
            card(g, m, cx, cy, colW, cardH, mx, my, delta);
            i++;
        }
        if (i == 0) Ui.textMid(g, "NO MODULES FOUND", x, y + 80, MUTED, Ui.SMALL);
    }

    private void card(GuiGraphics g, Module m, int x, int y, int w, int h, int mx, int my, float delta) {
        m.anim += ((m.enabled ? 1f : 0f) - m.anim) * Math.min(1f, delta * 0.35f);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Ui.roundBorder(g, x, y, w, h, 14, Ui.lerp(BORDER, ACCENT, m.anim * 0.55f), hover ? CARD_HOT : CARD);
        if (m.enabled) Ui.round(g, x + 1, y + 16, 3, h - 32, 1, ACCENT);

        Ui.textMid(g, m.name.toUpperCase(), x + 24, y + 25, m.enabled ? ACCENT : TEXT, Ui.UI);
        String desc = m.description.toUpperCase();
        int maxW = w - 190;
        while (desc.length() > 3 && Ui.width(desc, Ui.SMALL) > maxW) desc = desc.substring(0, desc.length() - 2);
        if (!desc.equals(m.description.toUpperCase())) desc += "..";
        Ui.textMid(g, desc, x + 24, y + 48, MUTED, Ui.SMALL);

        int tx = x + w - 24 - 52;
        if (!m.keyLabel.isEmpty()) {
            int kw = Ui.width(m.keyLabel, Ui.SMALL) + 20;
            Ui.roundBorder(g, tx - kw - 14, y + 22, kw, 24, 8, BORDER, 0xFF14101A);
            Ui.textMid(g, m.keyLabel, tx - kw - 4, y + 34, MUTED, Ui.SMALL);
        }
        Ui.round(g, tx, y + 20, 52, 28, 14, Ui.lerp(TRACK_OFF, ACCENT, m.anim));
        int knobX = tx + 3 + (int) (24 * m.anim);
        Ui.round(g, knobX, y + 23, 22, 22, 11, 0xFFFFFFFF);

        hits.add(new Hit(x, y, w, h, m::toggle));
    }

    // ------------------------------------------------------------------ sus chunks tab
    private void susTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int cell = 11, pitch = 13, r = Math.min(SusChunks.radius, 14), side = r * 2 + 1;
        int mapPx = side * pitch - (pitch - cell);
        Ui.roundBorder(g, x, y, mapPx + 28, mapPx + 28, 16, BORDER, CARD);

        if (!SusChunks.MODULE.enabled) {
            Ui.textMid(g, "SUS CHUNKS IS OFF", x + 28, y + 40, TEXT, Ui.UI);
            Ui.textMid(g, "TURN IT ON IN THE MODULES TAB", x + 28, y + 64, MUTED, Ui.SMALL);
        } else if (mc.player != null) {
            ChunkPos me = mc.player.chunkPosition();
            // soft glow under hot cells first
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    SusChunks.Result res = SusChunks.resultAt(SusChunks.cx(me) + dx, SusChunks.cz(me) + dz);
                    if (res != null && res.score() >= SusChunks.threshold) {
                        int gx = x + 14 + (dx + r) * pitch, gy = y + 14 + (dz + r) * pitch;
                        Ui.glow(g, gx, gy, cell, cell, 4, 0xFF8EC8, 30);
                    }
                }
            }
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    SusChunks.Result res = SusChunks.resultAt(SusChunks.cx(me) + dx, SusChunks.cz(me) + dz);
                    int col = 0xFF1E1827;
                    if (res != null) {
                        if (res.score() >= SusChunks.threshold) col = ACCENT;
                        else if (res.score() >= SusChunks.threshold / 2) col = 0xFFB4608F;
                        else if (res.score() > 0) col = 0xFF5B3A50;
                        else col = 0xFF2B2336;
                    }
                    int gx = x + 14 + (dx + r) * pitch, gy = y + 14 + (dz + r) * pitch;
                    Ui.round(g, gx, gy, cell, cell, 3, col);
                }
            }
            int pxm = x + 14 + r * pitch, pym = y + 14 + r * pitch;
            Ui.round(g, pxm + 2, pym + 2, cell - 4, cell - 4, 3, 0xFFFFFFFF); // you
        }

        int rx = x + mapPx + 28 + 24, rw = w - (mapPx + 28 + 24);
        Ui.textMid(g, "TOP CHUNKS", rx, y + 14, TEXT, Ui.UI);
        Ui.textMid(g, "SCORE = STORAGE x3 + ODD BLOCKS x2, NEIGHBOURS ADD HALF", rx, y + 38, MUTED, Ui.SMALL);
        List<SusChunks.Hit> list = SusChunks.hits();
        int rowY = y + 64;
        for (int i = 0; i < Math.min(6, list.size()); i++) {
            SusChunks.Hit hit = list.get(i);
            Ui.roundBorder(g, rx, rowY, rw, 44, 12, i == 0 ? ACCENT : BORDER, CARD);
            String xz = (SusChunks.cx(hit.pos()) * 16 + 8) + ", " + (SusChunks.cz(hit.pos()) * 16 + 8);
            Ui.textMid(g, xz, rx + 18, rowY + 22, TEXT, Ui.UI);
            Ui.textMid(g, hit.storage() + " STORAGE", rx + 170, rowY + 22, MUTED, Ui.SMALL);
            String sc = String.valueOf(hit.clusterScore());
            int bw = Ui.width(sc, Ui.SMALL) + 24;
            Ui.round(g, rx + rw - bw - 10, rowY + 10, bw, 24, 8, ACCENT_BG);
            Ui.textMid(g, sc, rx + rw - bw + 2, rowY + 22, ACCENT, Ui.SMALL);
            rowY += 52;
        }
        if (list.isEmpty()) Ui.textMid(g, "NOTHING SUSPICIOUS YET", rx, rowY + 12, MUTED, Ui.SMALL);
    }

    // ------------------------------------------------------------------ settings tab
    private void settingsTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        stepper(g, x, y, w, "SCAN RADIUS (CHUNKS)", SusChunks.radius, mx, my,
                () -> SusChunks.radius = Math.max(4, SusChunks.radius - 2),
                () -> SusChunks.radius = Math.min(32, SusChunks.radius + 2));
        stepper(g, x, y + 64, w, "CHUNKS PER TICK", SusChunks.perTick, mx, my,
                () -> SusChunks.perTick = Math.max(1, SusChunks.perTick - 1),
                () -> SusChunks.perTick = Math.min(24, SusChunks.perTick + 1));
        stepper(g, x, y + 128, w, "SUS THRESHOLD", SusChunks.threshold, mx, my,
                () -> { SusChunks.threshold = Math.max(4, SusChunks.threshold - 2); SusChunks.clear(); },
                () -> { SusChunks.threshold = Math.min(60, SusChunks.threshold + 2); SusChunks.clear(); });
        Ui.textMid(g, "LOWER THRESHOLD = MORE RESULTS, MORE FALSE POSITIVES", x, y + 210, MUTED, Ui.SMALL);
        Ui.textMid(g, "OPEN / CLOSE MENU: RIGHT SHIFT   OR   /fit", x, y + 236, MUTED, Ui.SMALL);
    }

    private void stepper(GuiGraphics g, int x, int y, int w, String label, int value, int mx, int my, Runnable minus, Runnable plus) {
        Ui.roundBorder(g, x, y, w, 52, 14, BORDER, CARD);
        Ui.textMid(g, label, x + 22, y + 26, TEXT, Ui.UI);
        int bx = x + w - 170;
        button(g, bx, y + 10, 32, 32, "-", mx, my, minus);
        String v = String.valueOf(value);
        Ui.textMid(g, v, bx + 32 + (74 - Ui.width(v, Ui.UI)) / 2, y + 26, ACCENT, Ui.UI);
        button(g, bx + 106, y + 10, 32, 32, "+", mx, my, plus);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, Runnable r) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Ui.roundBorder(g, x, y, w, h, 10, hover ? ACCENT : BORDER, hover ? ACCENT_BG : 0xFF14101A);
        Ui.textMid(g, label, x + (w - Ui.width(label, Ui.UI)) / 2, y + h / 2, hover ? ACCENT : MUTED, Ui.UI);
        hits.add(new Hit(x, y, w, h, r));
    }

    // ------------------------------------------------------------------ INPUT (1.21.9+ signatures)
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = toDesign(click.x()), my = toDesign(click.y());
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
