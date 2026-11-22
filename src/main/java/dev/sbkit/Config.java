package dev.sbkit;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One file, config/sbkit.json: { featureId: { settingKey: value } }. Unknown or bad entries fall back to defaults. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("sbkit.json"); }

    public static void load(List<Feature> features) {
        JsonObject root;
        try { root = JsonParser.parseString(Files.readString(file())).getAsJsonObject(); }
        catch (IOException | RuntimeException e) { return; } // first run or corrupt file: keep defaults
        for (Feature f : features) {
            if (!(root.get(f.id) instanceof JsonObject o)) continue;
            for (Setting<?> s : f.settings) {
                try { if (o.has(s.key)) s.fromJson(o.get(s.key)); } catch (RuntimeException ignored) {}
            }
        }
    }

    public static void save(List<Feature> features) {
        JsonObject root = new JsonObject();
        for (Feature f : features) {
            JsonObject o = new JsonObject();
            for (Setting<?> s : f.settings) o.add(s.key, s.toJson());
            root.add(f.id, o);
        }
        try { Files.writeString(file(), GSON.toJson(root)); } catch (IOException ignored) {}
    }
}
