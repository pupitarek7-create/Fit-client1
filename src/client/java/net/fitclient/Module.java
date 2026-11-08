package net.fitclient;

public class Module {
    public final String name;
    public final String description;
    public final String keyLabel; // display only, e.g. "F7"; empty = unbound
    public boolean enabled;
    public float anim; // 0..1 toggle animation, driven by the GUI

    public Module(String name, String description, String keyLabel) {
        this.name = name;
        this.description = description;
        this.keyLabel = keyLabel;
    }

    public void toggle() {
        enabled = !enabled;
    }
}
