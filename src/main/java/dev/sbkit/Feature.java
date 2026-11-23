package dev.sbkit;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a feature. To add one: extend this, declare settings with {@link #add}, override the hooks you
 * need, then add one line to {@link Features}. Hooks run for every feature regardless of {@link #on()}, so a
 * feature can keep its state tracking alive and checks {@code on()} only where it acts.
 */
public abstract class Feature {
    public final String id, name;
    public final List<Setting<?>> settings = new ArrayList<>();
    public final Setting.Bool enabled, debug;

    protected Feature(String id, String name, boolean defaultOn) {
        this.id = id;
        this.name = name;
        enabled = add(new Setting.Bool("enabled", "Enabled", defaultOn));
        debug = add(new Setting.Bool("debug", "Debug messages", false));
    }

    protected final <S extends Setting<?>> S add(S s) { settings.add(s); return s; }
    public final boolean on() { return enabled.get(); }
    protected final void debug(String msg) { if (debug.get()) Util.chat("§7[" + name + "] " + msg); }

    /** Failures the player must see: not gated by the debug switch. */
    protected final void warn(String msg) { Util.chat("§c[" + name + "] " + msg); }

    /** Short text shown at the right of the settings header. */
    public String status() { return ""; }

    public void init() {}
    /** Start of the client tick, before the game consumes key clicks. */
    public void onStartTick(Minecraft mc) {}
    public void onTick(Minecraft mc) {}
    /** A chat line (formatting codes already stripped by Component#getString). */
    public void onMessage(Minecraft mc, String text) {}
    /** The client level object changed (join, dimension change, disconnect). */
    public void onWorldChange(Minecraft mc) {}
}
