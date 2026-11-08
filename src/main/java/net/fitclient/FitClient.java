package net.fitclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FitClient implements ClientModInitializer {
    public static final List<Module> MODULES = List.of(SusChunks.MODULE, SusChunks.HUD);
    private boolean shiftWasDown = false;
    private boolean f7WasDown = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long win = mc.getWindow().getHandle();

            // Right Shift opens the menu (edge-detected, ignored while typing in another screen)
            boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (shift && !shiftWasDown && mc.currentScreen == null) mc.setScreen(new ClickGui(MODULES));
            shiftWasDown = shift;

            // F7 toggles Sus Chunks in-game
            boolean f7 = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_F7) == GLFW.GLFW_PRESS;
            if (f7 && !f7WasDown && mc.currentScreen == null) SusChunks.MODULE.toggle();
            f7WasDown = f7;

            if (mc.world == null) SusChunks.clear();
            SusChunks.tick(mc);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!SusChunks.MODULE.enabled || !SusChunks.HUD.enabled || mc.currentScreen != null) return;
            int y = 8;
            ctx.drawTextWithShadow(mc.textRenderer, "SUS CHUNKS", 8, y, ClickGui.ACCENT);
            y += 11;
            var hits = SusChunks.hits();
            for (int i = 0; i < Math.min(3, hits.size()); i++) {
                var h = hits.get(i);
                ctx.drawTextWithShadow(mc.textRenderer,
                        (h.pos().x * 16 + 8) + ", " + (h.pos().z * 16 + 8) + "  [" + h.clusterScore() + "]", 8, y, 0xFFFFFFFF);
                y += 10;
            }
        });
    }
}
