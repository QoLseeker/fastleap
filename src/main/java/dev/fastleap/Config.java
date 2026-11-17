package dev.fastleap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class Config {
	static final String[] CLASSES = {"Archer", "Berserk", "Healer", "Mage", "Tank"};
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fastleap.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static Config cfg = load();

	boolean enabled = true;
	Map<String, String> target = new HashMap<>(); // stage name -> class

	String get(FastLeap.Stage s) {
		return target.getOrDefault(s.name(), CLASSES[0]);
	}

	void cycle(FastLeap.Stage s) {
		int i = Arrays.asList(CLASSES).indexOf(get(s));
		target.put(s.name(), CLASSES[(i + 1) % CLASSES.length]);
	}

	private static Config load() {
		try {
			Config c = GSON.fromJson(Files.readString(FILE), Config.class);
			if (c == null) return new Config();
			if (c.target == null) c.target = new HashMap<>(); // "target": null in a hand-edited file would NPE every tick
			return c;
		} catch (Exception e) { // missing or corrupt file: start from defaults
			return new Config();
		}
	}

	static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(cfg));
		} catch (IOException ignored) {
			// shortcut: a failed save is silent, the setting just resets next launch
		}
	}
}
