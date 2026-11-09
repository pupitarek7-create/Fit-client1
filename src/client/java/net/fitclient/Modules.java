package net.fitclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

public final class Modules {
    private Modules() {}

    public static final Module DUMBBELL = new Module("Dumbbell Sword", "Swords turn into dumbbells", "")
            .onToggle(Modules::applyDumbbell);

    public static final Module FULLBRIGHT = new Module("Fullbright", "See everything, no darkness", "")
            .onToggle(m -> {
                Minecraft mc = Minecraft.getInstance();
                if (!m.enabled && mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
            })
            .onTick(mc -> {
                if (mc.player == null) return;
                MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 400)
                    mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000, 0, false, false, false));
            });

    public static final Module COORDS = new Module("Coords HUD", "Position and facing on screen", "");
    public static final Module KEYS = new Module("Keystrokes", "WASD and jump overlay", "");

    public static final List<Module> ALL = List.of(
            SusChunks.MODULE, SusChunks.BEAMS, SusChunks.STORAGE_ESP, SusChunks.HUD, DUMBBELL, FULLBRIGHT, COORDS, KEYS);

    // ------------------------------------------------------------ dumbbell pack toggle
    private static void applyDumbbell(Module m) {
        Minecraft mc = Minecraft.getInstance();
        try {
            PackRepository repo = mc.getResourcePackRepository();
            repo.reload();
            String id = repo.getAvailableIds().stream().filter(s -> s.contains("dumbbell")).findFirst().orElse(null);
            if (id == null) {
                m.enabled = false;
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Fit] dumbbell pack not found"), false);
                return;
            }
            List<String> sel = new ArrayList<>(repo.getSelectedIds());
            if (m.enabled) { if (!sel.contains(id)) sel.add(id); } else sel.remove(id);
            repo.setSelected(sel);
            mc.options.updateResourcePacks(repo); // reloads resources
        } catch (Throwable t) {
            m.enabled = false;
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Fit] dumbbell toggle failed: " + t), false);
        }
    }

    // ------------------------------------------------------------ in-game HUD
    public static void drawHud(GuiGraphics g, Minecraft mc) {
        if (mc.screen != null || mc.player == null) return;
        boolean any = COORDS.enabled || KEYS.enabled || (SusChunks.MODULE.enabled && SusChunks.HUD.enabled);
        if (!any) return;

        double gs = mc.getWindow().getGuiScale();
        int u = Math.max(1, Math.round(mc.getWindow().getHeight() / 1080f));
        g.pose().pushMatrix();
        g.pose().scale((float) (u / gs), (float) (u / gs));
        int dh = mc.getWindow().getHeight() / u;

        int y = 16;
        if (COORDS.enabled) {
            String pos = String.format("%d  %d  %d", (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            String face = mc.player.getDirection().getName().toUpperCase();
            int w = Math.max(190, Ui.width(pos, Ui.UI) + 40);
            Ui.round(g, 16, y, w, 62, 12, 0xD0100C16);
            Ui.round(g, 16, y + 14, 3, 34, 1, ClickGui.ACCENT);
            Ui.textMid(g, pos, 32, y + 22, ClickGui.TEXT, Ui.UI);
            Ui.textMid(g, "FACING " + face, 32, y + 44, ClickGui.MUTED, Ui.SMALL);
            y += 74;
        }
        if (SusChunks.MODULE.enabled && SusChunks.HUD.enabled) {
            var hits = SusChunks.hits();
            int rows = Math.min(3, hits.size());
            int h = 40 + Math.max(1, rows) * 26;
            Ui.round(g, 16, y, 260, h, 12, 0xD0100C16);
            Ui.textMid(g, "SUS CHUNKS", 32, y + 22, ClickGui.ACCENT, Ui.SMALL);
            if (rows == 0) Ui.textMid(g, "nothing yet", 32, y + 48, ClickGui.MUTED, Ui.SMALL);
            for (int i = 0; i < rows; i++) {
                var hit = hits.get(i);
                String t = (SusChunks.cx(hit.pos()) * 16 + 8) + ", " + (SusChunks.cz(hit.pos()) * 16 + 8);
                Ui.textMid(g, t, 32, y + 48 + i * 26, ClickGui.TEXT, Ui.SMALL);
                Ui.textRight(g, String.valueOf(hit.clusterScore()), 260, y + 48 + i * 26, ClickGui.ACCENT, Ui.SMALL);
            }
            y += h + 12;
        }
        if (KEYS.enabled) {
            int s = 44, gap = 6, bx = 16, by = dh - 16 - (s * 2 + gap * 2 + 30);
            key(g, "W", bx + s + gap, by, s, mc.options.keyUp.isDown());
            key(g, "A", bx, by + s + gap, s, mc.options.keyLeft.isDown());
            key(g, "S", bx + s + gap, by + s + gap, s, mc.options.keyDown.isDown());
            key(g, "D", bx + 2 * (s + gap), by + s + gap, s, mc.options.keyRight.isDown());
            int jw = 3 * s + 2 * gap;
            boolean j = mc.options.keyJump.isDown();
            Ui.round(g, bx, by + 2 * (s + gap), jw, 26, 8, j ? ClickGui.ACCENT : 0xD0100C16);
            Ui.textMid(g, "SPACE", bx + (jw - Ui.width("SPACE", Ui.SMALL)) / 2, by + 2 * (s + gap) + 13, j ? 0xFF1A0F17 : ClickGui.MUTED, Ui.SMALL);
        }
        g.pose().popMatrix();
    }

    private static void key(GuiGraphics g, String k, int x, int y, int s, boolean down) {
        Ui.round(g, x, y, s, s, 10, down ? ClickGui.ACCENT : 0xD0100C16);
        Ui.textMid(g, k, x + (s - Ui.width(k, Ui.UI)) / 2, y + s / 2, down ? 0xFF1A0F17 : ClickGui.TEXT, Ui.UI);
    }
}
