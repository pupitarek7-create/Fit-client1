package net.fitclient;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FitClient implements ClientModInitializer {
    public static final List<Module> MODULES = List.of(SusChunks.MODULE, SusChunks.HUD);
    private boolean shiftWasDown = false;
    private boolean f7WasDown = false;
    private static boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("fit").executes(ctx -> {
                    pendingOpen = true; // opened on the next tick, after the chat screen closes
                    return Command.SINGLE_SUCCESS;
                })));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
                mc.execute(() -> {
                    if (mc.player != null)
                        mc.player.displayClientMessage(Component.literal("[Fit] loaded. Press Right Shift or type /fit"), false);
                }));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long win = GLFW.glfwGetCurrentContext();

            // Right Shift opens the menu (edge-detected, ignored while typing in another screen)
            boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (((shift && !shiftWasDown) || pendingOpen) && mc.screen == null) {
                pendingOpen = false;
                mc.setScreen(new ClickGui(MODULES));
            }
            shiftWasDown = shift;

            // F7 toggles Sus Chunks in-game
            boolean f7 = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_F7) == GLFW.GLFW_PRESS;
            if (f7 && !f7WasDown && mc.screen == null) SusChunks.MODULE.toggle();
            f7WasDown = f7;

            if (mc.level == null) SusChunks.clear();
            SusChunks.tick(mc);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!SusChunks.MODULE.enabled || !SusChunks.HUD.enabled || mc.screen != null) return;
            int y = 8;
            ctx.drawString(mc.font, "SUS CHUNKS", 8, y, ClickGui.ACCENT, true);
            y += 11;
            var hits = SusChunks.hits();
            for (int i = 0; i < Math.min(3, hits.size()); i++) {
                var h = hits.get(i);
                ctx.drawString(mc.font,
                        (SusChunks.cx(h.pos()) * 16 + 8) + ", " + (SusChunks.cz(h.pos()) * 16 + 8) + "  [" + h.clusterScore() + "]", 8, y, 0xFFFFFFFF, true);
                y += 10;
            }
        });
    }
}
