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
	int clickDelayTicks = 3; // ticks the leap menu must be open before we click
	int cooldownTicks = 10; // min ticks between two leaps
	Map<String, String> target = new HashMap<>(); // "Class:STAGE" (or legacy "STAGE") -> class

	String get(String me, FastLeap.Stage s) {
		String fallback = target.getOrDefault(s.name(), CLASSES[0]);
		return me == null ? fallback : target.getOrDefault(me + ":" + s.name(), fallback);
	}

	void cycle(String me, FastLeap.Stage s) {
		int i = Arrays.asList(CLASSES).indexOf(get(me, s));
		target.put(me + ":" + s.name(), CLASSES[(i + 1) % CLASSES.length]);
	}

	private static Config load() {
		try {
			Config c = GSON.fromJson(Files.readString(FILE), Config.class);
			if (c == null) return new Config();
			if (c.target == null) c.target = new HashMap<>(); // "target": null in a hand-edited file would NPE every tick
			c.clickDelayTicks = Math.max(1, Math.min(10, c.clickDelayTicks));
			c.cooldownTicks = Math.max(0, Math.min(40, c.cooldownTicks));
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
