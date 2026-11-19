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
	static final String CRYSTAL = "CRYSTAL"; // target key for the auto crystal leap
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fastleap.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static Config cfg = load();

	boolean enabled = true;
	static final int MIN_DELAY_MS = 50, MAX_MS = 3000; // the click is never instant
	int clickDelayMs = 150; // how long the leap menu must be open before we click
	int cooldownMs = 500; // min time between two leaps
	boolean crystalLeap = false; // auto-switch to the leap item and leap after picking up a crystal
	String crystalItem = "Crystal"; // shortcut: substring of the crystal item's name, unverified; edit fastleap.json if it differs
	Map<String, String> target = new HashMap<>(); // "Class:STAGE" (or legacy "STAGE") -> class

	String get(String me, String key) {
		String fallback = target.getOrDefault(key, CLASSES[0]);
		return me == null ? fallback : target.getOrDefault(me + ":" + key, fallback);
	}

	void cycle(String me, String key) {
		int i = Arrays.asList(OPTIONS).indexOf(get(me, key));
		target.put(me + ":" + key, OPTIONS[(i + 1) % OPTIONS.length]);
	}

	private static Config load() {
		try {
			Config c = GSON.fromJson(Files.readString(FILE), Config.class);
			if (c == null) return new Config();
			if (c.target == null) c.target = new HashMap<>(); // "target": null in a hand-edited file would NPE every tick
			if (c.crystalItem == null || c.crystalItem.isBlank()) c.crystalItem = "Crystal";
			c.clickDelayMs = Math.max(MIN_DELAY_MS, Math.min(MAX_MS, c.clickDelayMs));
			c.cooldownMs = Math.max(0, Math.min(MAX_MS, c.cooldownMs));
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
