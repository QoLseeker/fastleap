package dev.fastleap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ConfigTest {
	@Test
	void perClassBeatsSharedBeatsDefault() {
		Config c = new Config();
		assertEquals("Archer", c.get("Healer", Config.BLOOD_RUSH));
		c.target.put(Config.BLOOD_RUSH, "Mage");
		assertEquals("Mage", c.get("Healer", Config.BLOOD_RUSH));
		assertEquals("Mage", c.get(null, Config.BLOOD_RUSH)); // own class unknown: only the shared value applies
		c.target.put("Healer:" + Config.BLOOD_RUSH, "Tank");
		assertEquals("Tank", c.get("Healer", Config.BLOOD_RUSH));
		assertEquals("Mage", c.get("Mage", Config.BLOOD_RUSH));
	}

	@Test
	void crystalLeapIsOptIn() {
		assertFalse(new Config().crystalLeap);
	}

	@Test
	void cycleWalksTheClassesThenOffThenBack() {
		Config c = new Config();
		for (int i = 0; i < 5; i++) c.cycle("Healer", Config.BLOOD_RUSH); // Archer -> Berserk, Healer, Mage, Tank, Off
		assertEquals(Config.OFF, c.get("Healer", Config.BLOOD_RUSH));
		c.cycle("Healer", Config.BLOOD_RUSH);
		assertEquals("Archer", c.get("Healer", Config.BLOOD_RUSH));
	}
}
