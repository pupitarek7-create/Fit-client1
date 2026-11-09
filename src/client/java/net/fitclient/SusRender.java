package net.fitclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * World overlays:
 *  - Sus Highlight: glowing translucent pink plane + outline over sus chunks (nearest few only)
 *  - Storage ESP: coloured boxes around chests, barrels, shulkers, etc.
 * Kept light on purpose: capped counts, distance limits, no per-frame allocations in the hot loops.
 */
public final class SusRender {
    private SusRender() {}

    private static final int R = 255, G = 70, B = 160;
    private static final int MAX_SUS = 12;          // chunks drawn at once
    private static final double SUS_RANGE = 220;    // blocks
    private static final int MAX_BOXES = 300;       // storage boxes drawn at once
    private static final double ESP_RANGE = 96;     // blocks
    private static boolean failed = false;

    /** Render types used for overlays. Change here if you want different depth behaviour. */
    private static RenderType lineType() { return RenderTypes.lines(); }
    private static RenderType fillType() { return RenderTypes.debugQuads(); }

    // colour per storage kind: chest, barrel, shulker, ender chest, other
    private static final int[][] KIND = {
            {255, 176, 48}, {214, 150, 84}, {196, 120, 255}, {120, 90, 230}, {170, 176, 196}};

    public static void init() {
        WorldRenderEvents.END_MAIN.register(ctx -> {
            if (failed) return;
            try {
                render(ctx);
            } catch (Throwable t) {
                failed = true; // never take the game down because of an overlay
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.literal("[Fit] overlay disabled after error: " + t), false);
                t.printStackTrace();
            }
        });
    }

    private static void render(WorldRenderContext ctx) {
        boolean sus = SusChunks.MODULE.enabled && SusChunks.BEAMS.enabled && !SusChunks.hits().isEmpty();
        boolean esp = SusChunks.STORAGE_ESP.enabled;
        if (!sus && !esp) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack.Pose pose = ctx.matrices().last();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double px = mc.player.getX(), pz = mc.player.getZ();

        // pick the nearest sus chunks
        List<SusChunks.Hit> near = new ArrayList<>();
        if (sus) {
            for (var h : SusChunks.hits()) {
                double dx = SusChunks.cx(h.pos()) * 16 + 8 - px, dz = SusChunks.cz(h.pos()) * 16 + 8 - pz;
                if (dx * dx + dz * dz <= SUS_RANGE * SUS_RANGE) near.add(h);
            }
            near.sort((a, b) -> Double.compare(dist2(a, px, pz), dist2(b, px, pz)));
            if (near.size() > MAX_SUS) near = near.subList(0, MAX_SUS);
        }

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);

        // ---- pass 1: translucent fills (own buffer, finished before the line buffer is touched)
        if (!near.isEmpty()) {
            VertexConsumer fill = ctx.consumers().getBuffer(fillType());
            for (var h : near) {
                double x0 = SusChunks.cx(h.pos()) * 16 - cam.x, z0 = SusChunks.cz(h.pos()) * 16 - cam.z;
                double y = h.surfaceY() + 0.08 - cam.y;
                float k = Math.min(1f, h.clusterScore() / (float) (SusChunks.threshold * 2));
                quad(fill, pose, (float) x0, (float) y, (float) z0, (float) (x0 + 16), (float) (z0 + 16), (int) (70 + 50 * k + 30 * pulse));
            }
        }

        // ---- pass 2: lines (sus outlines + storage boxes)
        VertexConsumer lines = ctx.consumers().getBuffer(lineType());
        for (var h : near) {
            double x0 = SusChunks.cx(h.pos()) * 16 - cam.x, z0 = SusChunks.cz(h.pos()) * 16 - cam.z;
            double y = h.surfaceY() + 0.08 - cam.y;
            float k = Math.min(1f, h.clusterScore() / (float) (SusChunks.threshold * 2));
            int ca = (int) (170 + 85 * k);
            rect(lines, pose, x0, y, z0, 16, 7f, (int) (ca * (0.20f + 0.12f * pulse)), R, G, B);  // glow
            rect(lines, pose, x0, y, z0, 16, 2.2f, ca, R, G, B);                                    // core
            // tall edge lines (cheap: 4 lines)
            double yb = mc.level.getMinY() - cam.y, yt = mc.level.getMaxY() - cam.y;
            int ba = (int) (50 + 50 * k);
            line(lines, pose, x0, yb, z0, x0, yt, z0, 2f, ba);
            line(lines, pose, x0 + 16, yb, z0, x0 + 16, yt, z0, 2f, ba);
            line(lines, pose, x0, yb, z0 + 16, x0, yt, z0 + 16, 2f, ba);
            line(lines, pose, x0 + 16, yb, z0 + 16, x0 + 16, yt, z0 + 16, 2f, ba);
        }

        if (esp) {
            int drawn = 0;
            int pcx = (int) Math.floor(px / 16.0), pcz = (int) Math.floor(pz / 16.0);
            int rc = (int) (ESP_RANGE / 16) + 1;
            double r2 = ESP_RANGE * ESP_RANGE;
            outer:
            for (int cx = pcx - rc; cx <= pcx + rc; cx++) {
                for (int cz = pcz - rc; cz <= pcz + rc; cz++) {
                    var res = SusChunks.resultAt(cx, cz);
                    if (res == null) continue;
                    long[] pos = res.storagePos();
                    byte[] kinds = res.storageKind();
                    for (int i = 0; i < pos.length; i++) {
                        int bx = BlockPos.getX(pos[i]), by = BlockPos.getY(pos[i]), bz = BlockPos.getZ(pos[i]);
                        double dx = bx + 0.5 - px, dz = bz + 0.5 - pz;
                        if (dx * dx + dz * dz > r2) continue;
                        int[] c = KIND[kinds[i]];
                        box(lines, pose, bx - cam.x, by - cam.y, bz - cam.z, c[0], c[1], c[2]);
                        if (++drawn >= MAX_BOXES) break outer;
                    }
                }
            }
        }
    }

    private static double dist2(SusChunks.Hit h, double px, double pz) {
        double dx = SusChunks.cx(h.pos()) * 16 + 8 - px, dz = SusChunks.cz(h.pos()) * 16 + 8 - pz;
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------ primitives
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y, float z0, float x1, float z1, int a) {
        vc.addVertex(pose, x0, y, z0).setColor(R, G, B, a);
        vc.addVertex(pose, x1, y, z0).setColor(R, G, B, a);
        vc.addVertex(pose, x1, y, z1).setColor(R, G, B, a);
        vc.addVertex(pose, x0, y, z1).setColor(R, G, B, a);
        vc.addVertex(pose, x0, y, z1).setColor(R, G, B, a);
        vc.addVertex(pose, x1, y, z1).setColor(R, G, B, a);
        vc.addVertex(pose, x1, y, z0).setColor(R, G, B, a);
        vc.addVertex(pose, x0, y, z0).setColor(R, G, B, a);
    }

    private static void rect(VertexConsumer vc, PoseStack.Pose pose, double x0, double y, double z0, double s, float w, int a, int r, int g, int b) {
        if (a <= 0) return;
        double x1 = x0 + s, z1 = z0 + s;
        seg(vc, pose, x0, y, z0, x1, y, z0, w, a, r, g, b);
        seg(vc, pose, x1, y, z0, x1, y, z1, w, a, r, g, b);
        seg(vc, pose, x1, y, z1, x0, y, z1, w, a, r, g, b);
        seg(vc, pose, x0, y, z1, x0, y, z0, w, a, r, g, b);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, int r, int g, int b) {
        double e = 0.003, x1 = x + 1 + e, y1 = y + 1 + e, z1 = z + 1 + e;
        x -= e; y -= e; z -= e;
        float w = 2f; int a = 235;
        seg(vc, pose, x, y, z, x1, y, z, w, a, r, g, b);   seg(vc, pose, x1, y, z, x1, y, z1, w, a, r, g, b);
        seg(vc, pose, x1, y, z1, x, y, z1, w, a, r, g, b); seg(vc, pose, x, y, z1, x, y, z, w, a, r, g, b);
        seg(vc, pose, x, y1, z, x1, y1, z, w, a, r, g, b); seg(vc, pose, x1, y1, z, x1, y1, z1, w, a, r, g, b);
        seg(vc, pose, x1, y1, z1, x, y1, z1, w, a, r, g, b); seg(vc, pose, x, y1, z1, x, y1, z, w, a, r, g, b);
        seg(vc, pose, x, y, z, x, y1, z, w, a, r, g, b);   seg(vc, pose, x1, y, z, x1, y1, z, w, a, r, g, b);
        seg(vc, pose, x1, y, z1, x1, y1, z1, w, a, r, g, b); seg(vc, pose, x, y, z1, x, y1, z1, w, a, r, g, b);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1, float w, int a) {
        seg(vc, pose, x0, y0, z0, x1, y1, z1, w, a, R, G, B);
    }

    private static void seg(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1, float w, int a, int r, int g, int b) {
        a = Math.max(0, Math.min(255, a));
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
    }
}
