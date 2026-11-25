package dev.sbkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.BooleanSupplier;

/** One persisted, GUI-editable value. The settings screen renders these by type, so features never touch the GUI. */
public abstract class Setting<T> {
    public final String key, label;
    /** Hide the row (e.g. when another setting selects a different group). */
    public BooleanSupplier visible = () -> true;
    /** Optional accent bar on the row while this returns true. */
    public BooleanSupplier highlight;
    private T value;

    private Setting(String key, String label, T def) { this.key = key; this.label = label; this.value = def; }

    public T get() { return value; }
    public void set(T v) { value = v; }
    abstract JsonElement toJson();
    abstract void fromJson(JsonElement e);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String key, String label, boolean def) { super(key, label, def); }
        JsonElement toJson() { return new JsonPrimitive(get()); }
        void fromJson(JsonElement e) { set(e.getAsBoolean()); }
    }

    public static final class Int extends Setting<Integer> {
        public final int min, max, step;
        public Int(String key, String label, int def, int min, int max, int step) {
            super(key, label, def); this.min = min; this.max = max; this.step = step;
        }
        /** Slider position 0..1 to a value. Squared curve (fine control at the low end), snapped to step. Same as fastleap 1.0.0. */
        public int fromFraction(double t) {
            t = Math.max(0, Math.min(1, t));
            int v = (int) Math.round((min + (max - min) * t * t) / step) * step;
            return Math.max(min, Math.min(max, v));
        }
        @Override public void set(Integer v) { super.set(Math.max(min, Math.min(max, v))); }
        JsonElement toJson() { return new JsonPrimitive(get()); }
        void fromJson(JsonElement e) { set(e.getAsInt()); }
    }

    public static final class Text extends Setting<String> {
        public Text(String key, String label, String def) { super(key, label, def); }
        JsonElement toJson() { return new JsonPrimitive(get()); }
        void fromJson(JsonElement e) { set(e.getAsString()); }
    }

    public static final class Choice extends Setting<String> {
        public final List<String> options;
        public Choice(String key, String label, String def, List<String> options) {
            super(key, label, def); this.options = options;
        }
        public void cycle() { set(options.get((options.indexOf(get()) + 1) % options.size())); }
        JsonElement toJson() { return new JsonPrimitive(get()); }
        void fromJson(JsonElement e) { if (options.contains(e.getAsString())) set(e.getAsString()); }
    }
}
