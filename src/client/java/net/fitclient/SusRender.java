package net.fitclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Draws glowing beam outlines around sus chunks in the world. */
public final class SusRender {
    private SusRender() {}

    public static void init() {
        WorldRenderEvents.END_MAIN.register(SusRender::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!SusChunks.MODULE.enabled || !SusChunks.BEAMS.enabled) return;
        var hits = SusChunks.hits();
        if (hits.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack ps = ctx.matrices();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.lines());
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = ps.last();

        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        int py = (int) mc.player.getY();

        for (var hit : hits) {
            double x0 = SusChunks.cx(hit.pos()) * 16 - cam.x, z0 = SusChunks.cz(hit.pos()) * 16 - cam.z;
            double x1 = x0 + 16, z1 = z0 + 16;
            double yb = minY - cam.y, yt = maxY - cam.y;
            float k = Math.min(1f, hit.clusterScore() / (float) (SusChunks.threshold * 2));
            int a = (int) (110 + 145 * k);
            // four tall edges
            vline(vc, pose, x0, z0, yb, yt, a); vline(vc, pose, x1, z0, yb, yt, a);
            vline(vc, pose, x0, z1, yb, yt, a); vline(vc, pose, x1, z1, yb, yt, a);
            // rings every 16 blocks near the player so you can read the height
            int ry0 = Math.max(minY, (py - 48) / 16 * 16), ry1 = Math.min(maxY, py + 64);
            for (int ry = ry0; ry <= ry1; ry += 16) {
                double y = ry - cam.y;
                line(vc, pose, x0, y, z0, x1, y, z0, a / 2); line(vc, pose, x1, y, z0, x1, y, z1, a / 2);
                line(vc, pose, x1, y, z1, x0, y, z1, a / 2); line(vc, pose, x0, y, z1, x0, y, z0, a / 2);
            }
        }
    }

    private static void vline(VertexConsumer vc, PoseStack.Pose pose, double x, double z, double y0, double y1, int a) {
        line(vc, pose, x, y0, z, x, y1, z, a);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double x1, double y1, double z1, int a) {
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(255, 142, 200, a).setNormal(pose, dx, dy, dz).setLineWidth(3f);
        vc.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(255, 142, 200, a).setNormal(pose, dx, dy, dz).setLineWidth(3f);
    }
}
