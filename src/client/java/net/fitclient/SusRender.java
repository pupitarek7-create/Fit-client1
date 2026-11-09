package net.fitclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws sus chunks in the world: a translucent glowing pink plane over the chunk at ground level,
 * a bright glowing outline, and faint tall edge beams so you can spot it from far away.
 */
public final class SusRender {
    private SusRender() {}

    private static final int R = 255, G = 70, B = 160;

    public static void init() {
        WorldRenderEvents.END_MAIN.register(SusRender::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!SusChunks.MODULE.enabled || !SusChunks.BEAMS.enabled) return;
        var hits = SusChunks.hits();
        if (hits.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack.Pose pose = ctx.matrices().last();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        VertexConsumer fill = ctx.consumers().getBuffer(RenderTypes.debugQuads());
        VertexConsumer lines = ctx.consumers().getBuffer(RenderTypes.lines());

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();

        for (var hit : hits) {
            int bx = SusChunks.cx(hit.pos()) * 16, bz = SusChunks.cz(hit.pos()) * 16;
            float k = Math.min(1f, hit.clusterScore() / (float) (SusChunks.threshold * 2));

            // plane height: highest surface sample across the chunk, so it sits on top of the ground
            int surf = minY;
            for (int sx = 0; sx <= 16; sx += 8)
                for (int sz = 0; sz <= 16; sz += 8)
                    surf = Math.max(surf, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx + Math.min(sx, 15), bz + Math.min(sz, 15)));
            double y = surf + 0.08 - cam.y;
            double x0 = bx - cam.x, z0 = bz - cam.z, x1 = x0 + 16, z1 = z0 + 16;

            // translucent fill (both windings so it shows from above and below)
            int fa = (int) (70 + 50 * k + 30 * pulse);
            quad(fill, pose, x0, y, z0, x1, y, z1, fa);

            // glowing outline: wide faint passes, then the bright core
            int ca = (int) (170 + 85 * k);
            outline(lines, pose, x0, y, z0, x1, z1, 14f, (int) (ca * (0.10f + 0.10f * pulse)));
            outline(lines, pose, x0, y, z0, x1, z1, 8f, (int) (ca * (0.22f + 0.12f * pulse)));
            outline(lines, pose, x0, y, z0, x1, z1, 4f, (int) (ca * 0.55f));
            outline(lines, pose, x0, y, z0, x1, z1, 2.2f, ca);

            // faint tall edges so the chunk is visible from far away / underground
            double yb = minY - cam.y, yt = maxY - cam.y;
            int ba = (int) (45 + 50 * k);
            for (double[] c : new double[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                line(lines, pose, c[0], yb, c[1], c[0], yt, c[1], 6f, ba / 2);
                line(lines, pose, c[0], yb, c[1], c[0], yt, c[1], 2f, ba);
            }
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double x0, double y, double z0, double x1, double y1, double z1, int a) {
        float ax = (float) x0, bx = (float) x1, az = (float) z0, bz = (float) z1, yy = (float) y;
        vc.addVertex(pose, ax, yy, az).setColor(R, G, B, a);
        vc.addVertex(pose, bx, yy, az).setColor(R, G, B, a);
        vc.addVertex(pose, bx, yy, bz).setColor(R, G, B, a);
        vc.addVertex(pose, ax, yy, bz).setColor(R, G, B, a);
        // reverse winding
        vc.addVertex(pose, ax, yy, bz).setColor(R, G, B, a);
        vc.addVertex(pose, bx, yy, bz).setColor(R, G, B, a);
        vc.addVertex(pose, bx, yy, az).setColor(R, G, B, a);
        vc.addVertex(pose, ax, yy, az).setColor(R, G, B, a);
    }

    private static void outline(VertexConsumer vc, PoseStack.Pose pose, double x0, double y, double z0, double x1, double z1, float w, int a) {
        if (a <= 0) return;
        line(vc, pose, x0, y, z0, x1, y, z0, w, a);
        line(vc, pose, x1, y, z0, x1, y, z1, w, a);
        line(vc, pose, x1, y, z1, x0, y, z1, w, a);
        line(vc, pose, x0, y, z1, x0, y, z0, w, a);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1, float w, int a) {
        a = Math.max(0, Math.min(255, a));
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(R, G, B, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(R, G, B, a).setNormal(pose, dx, dy, dz).setLineWidth(w);
    }
}
