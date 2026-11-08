package net.fitclient;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public class Module {
    public final String name;
    public final String description;
    public final String keyLabel; // display only; empty = none
    public boolean enabled;
    public float anim;            // 0..1 toggle animation, driven by the GUI
    private Consumer<Module> onToggle = m -> {};
    private Consumer<Minecraft> onTick = mc -> {};

    public Module(String name, String description, String keyLabel) {
        this.name = name; this.description = description; this.keyLabel = keyLabel;
    }

    public Module onToggle(Consumer<Module> c) { this.onToggle = c; return this; }
    public Module onTick(Consumer<Minecraft> c) { this.onTick = c; return this; }

    public void toggle() { enabled = !enabled; onToggle.accept(this); }
    public void tick(Minecraft mc) { if (enabled) onTick.accept(mc); }
}
