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
	static final String OFF = "Off"; // a target value: don't intercept the click at all
	static final String[] OPTIONS = {"Archer", "Berserk", "Healer", "Mage", "Tank", OFF};
	static final String BLOOD_RUSH = "BLOOD_RUSH"; // target keys: the Blood Rush left click, and the leap after a Maxor crystal pickup
	static final String CRYSTAL = "CRYSTAL";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static Config cfg = load();

	boolean enabled = true;
	static final int MIN_DELAY_MS = 50, MAX_MS = 3000; // the click is never instant
	int clickDelayMs = 150; // how long the leap menu must be open before we click
	int cooldownMs = 500; // min time between two leaps
	Map<String, String> target = new HashMap<>(); // "Class:KEY" (or just "KEY") -> class or Off

	String get(String me, String key) {
		String fallback = target.getOrDefault(key, CRYSTAL.equals(key) ? OFF : CLASSES[0]); // the auto leap is opt-in
		return me == null ? fallback : target.getOrDefault(me + ":" + key, fallback);
	}

	void cycle(String me, String key) {
		int i = Arrays.asList(OPTIONS).indexOf(get(me, key));
		target.put(me + ":" + key, OPTIONS[(i + 1) % OPTIONS.length]);
	}

	// resolved inside the callers' try blocks, so the class still loads outside Fabric (unit tests)
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("fastleap.json");
	}

	private static Config load() {
		try {
			Config c = GSON.fromJson(Files.readString(file()), Config.class);
			if (c == null) return new Config();
			if (c.target == null) c.target = new HashMap<>(); // "target": null in a hand-edited file would NPE every tick
			c.clickDelayMs = Math.max(MIN_DELAY_MS, Math.min(MAX_MS, c.clickDelayMs));
			c.cooldownMs = Math.max(0, Math.min(MAX_MS, c.cooldownMs));
			return c;
		} catch (Exception e) { // missing or corrupt file: start from defaults
			return new Config();
		}
	}

	static void save() {
		try {
			Files.writeString(file(), GSON.toJson(cfg));
		} catch (IOException ignored) {
			// shortcut: a failed save is silent, the setting just resets next launch
		}
	}
}
