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
 * Sus chunk highlight: a translucent pink slab on the ground, a bright outline, a glowing halo around
 * the edge and fading curtains going up, plus faint tall corner beams so you can spot it from far away.
 */
public final class SusRender {
    private SusRender() {}

    private static final int R = 255, G = 70, B = 160; // glow colour

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
        VertexConsumer quads = ctx.consumers().getBuffer(RenderTypes.debugQuads());
        Vec3 cam = mc.gameRenderer.getMainCamera().position();

        // gentle pulse 0.8 .. 1.0
        float pulse = 0.9f + 0.1f * (float) Math.sin(System.currentTimeMillis() / 380.0);

        for (var hit : hits) {
            int cx = SusChunks.cx(hit.pos()), cz = SusChunks.cz(hit.pos());
            int bx = cx * 16, bz = cz * 16;
            float k = Math.min(1f, hit.clusterScore() / (float) (SusChunks.threshold * 2));
            float strength = (0.55f + 0.45f * k) * pulse;

            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx + 8, bz + 8);
            float x0 = (float) (bx - cam.x), x1 = x0 + 16;
            float z0 = (float) (bz - cam.z), z1 = z0 + 16;
            float y = (float) (ground + 0.06 - cam.y);

            // 1) slab
            quad(quads, pose, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, a(95 * strength), a(95 * strength), a(95 * strength), a(95 * strength));
            // a second, slightly higher slab makes the middle a bit denser
            float y2 = y + 0.02f;
            quad(quads, pose, x0 + 1, y2, z0 + 1, x1 - 1, y2, z0 + 1, x1 - 1, y2, z1 - 1, x0 + 1, y2, z1 - 1,
                    a(35 * strength), a(35 * strength), a(35 * strength), a(35 * strength));

            // 2) halo: bands around the edge that fade outward (the glow)
            float h = 3.0f; // halo width in blocks
            int hi = a(120 * strength);
            // north / south / west / east strips (inner edge bright, outer edge transparent)
            quad(quads, pose, x0, y, z0, x1, y, z0, x1 + 0, y, z0 - h, x0, y, z0 - h, hi, hi, 0, 0);
            quad(quads, pose, x0, y, z1, x1, y, z1, x1, y, z1 + h, x0, y, z1 + h, hi, hi, 0, 0);
            quad(quads, pose, x0, y, z0, x0, y, z1, x0 - h, y, z1, x0 - h, y, z0, hi, hi, 0, 0);
            quad(quads, pose, x1, y, z0, x1, y, z1, x1 + h, y, z1, x1 + h, y, z0, hi, hi, 0, 0);

            // 3) curtains: edge walls that fade out going up
            float top = y + 10f + 8f * k;
            int wb = a(110 * strength);
            quad(quads, pose, x0, y, z0, x1, y, z0, x1, top, z0, x0, top, z0, wb, wb, 0, 0);
            quad(quads, pose, x0, y, z1, x1, y, z1, x1, top, z1, x0, top, z1, wb, wb, 0, 0);
            quad(quads, pose, x0, y, z0, x0, y, z1, x0, top, z1, x0, top, z0, wb, wb, 0, 0);
            quad(quads, pose, x1, y, z0, x1, y, z1, x1, top, z1, x1, top, z0, wb, wb, 0, 0);
        }

        // outline + tall beams
        VertexConsumer lines = ctx.consumers().getBuffer(RenderTypes.linesTranslucent());
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        for (var hit : hits) {
            int bx = SusChunks.cx(hit.pos()) * 16, bz = SusChunks.cz(hit.pos()) * 16;
            float k = Math.min(1f, hit.clusterScore() / (float) (SusChunks.threshold * 2));
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx + 8, bz + 8);
            float x0 = (float) (bx - cam.x), x1 = x0 + 16;
            float z0 = (float) (bz - cam.z), z1 = z0 + 16;
            float y = (float) (ground + 0.1 - cam.y);
            int al = (int) (200 + 55 * k);
            // bright outline on the ground
            line(lines, pose, x0, y, z0, x1, y, z0, al, 4f); line(lines, pose, x1, y, z0, x1, y, z1, al, 4f);
            line(lines, pose, x1, y, z1, x0, y, z1, al, 4f); line(lines, pose, x0, y, z1, x0, y, z0, al, 4f);
            // faint tall beams on the corners (visible from far away, and show underground height)
            float yb = (float) (minY - cam.y), yt = (float) (maxY - cam.y);
            int ab = 70;
            line(lines, pose, x0, yb, z0, x0, yt, z0, ab, 2f); line(lines, pose, x1, yb, z0, x1, yt, z0, ab, 2f);
            line(lines, pose, x0, yb, z1, x0, yt, z1, ab, 2f); line(lines, pose, x1, yb, z1, x1, yt, z1, ab, 2f);
        }
    }

    private static int a(float v) { return Math.max(0, Math.min(255, (int) v)); }

    private static void quad(VertexConsumer v, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int a1, int a2, int a3, int a4) {
        v.addVertex(p, ax, ay, az).setColor(R, G, B, a1);
        v.addVertex(p, bx, by, bz).setColor(R, G, B, a2);
        v.addVertex(p, cx, cy, cz).setColor(R, G, B, a3);
        v.addVertex(p, dx, dy, dz).setColor(R, G, B, a4);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int a, float width) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        vc.addVertex(pose, x0, y0, z0).setColor(255, 120, 190, a).setNormal(pose, dx, dy, dz).setLineWidth(width);
        vc.addVertex(pose, x1, y1, z1).setColor(255, 120, 190, a).setNormal(pose, dx, dy, dz).setLineWidth(width);
    }
}
