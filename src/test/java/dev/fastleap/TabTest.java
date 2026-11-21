package dev.fastleap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class TabTest {
	@Test
	void findsNameAndClassWhateverSurroundsThem() {
		for (String line : new String[] {
				"Steve (Archer XVII)", "[123] Steve (Archer 45)", "[MVP+] Steve (Archer 45)",
				"[123] [MVP+] Steve ♲ (Archer 45) Ⓑ"}) {
			Matcher m = FastLeap.TAB.matcher(line);
			assertTrue(m.find(), line);
			assertEquals("Steve", m.group(1), line);
			assertEquals("Archer", m.group(2), line);
		}
		assertFalse(FastLeap.TAB.matcher("Steve (DEAD)").find());
		assertFalse(FastLeap.TAB.matcher("Steve (EMPTY)").find());
	}

	@Test
	void stageFollowsBossLinesButNotTheWatcher() {
		assertEquals(FastLeap.Stage.MAXOR, FastLeap.stageFor("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"));
		assertEquals(FastLeap.Stage.LATER, FastLeap.stageFor("[BOSS] Storm: Pathetic Maxor, just like expected."));
		assertEquals(FastLeap.Stage.LATER, FastLeap.stageFor("[BOSS] Wither King: Oh, you're still alive."));
		assertNull(FastLeap.stageFor("[BOSS] The Watcher: That will be enough for now.")); // blood room: still Blood Rush
		assertNull(FastLeap.stageFor("Steve: [BOSS] Storm: hi"));
	}

	@Test
	void pickupLineNamesThePlayer() {
		Matcher m = FastLeap.PICKUP.matcher("useQoL picked up an Energy Crystal!"); // a real line from a run
		assertTrue(m.find());
		assertEquals("useQoL", m.group(1));
		m = FastLeap.PICKUP.matcher("Steve picked up an Energy Crystal!");
		assertTrue(m.find());
		assertEquals("Steve", m.group(1));
		m = FastLeap.PICKUP.matcher("[MVP+] Steve picked up an Energy Crystal!");
		assertTrue(m.find());
		assertEquals("Steve", m.group(1));
		m = FastLeap.PICKUP.matcher("You picked up an Energy Crystal!");
		assertTrue(m.find());
		assertEquals("You", m.group(1));
		assertFalse(FastLeap.PICKUP.matcher("Steve placed an Energy Crystal!").find());
	}

	@Test
	void sliderScaleIsClampedSnappedAndSquared() {
		assertEquals(50, FastLeap.scale(0, 50, 3000, 10));
		assertEquals(3000, FastLeap.scale(1, 50, 3000, 10));
		assertEquals(50, FastLeap.scale(-5, 50, 3000, 10));
		assertEquals(3000, FastLeap.scale(9, 50, 3000, 10));
		assertEquals(790, FastLeap.scale(0.5, 50, 3000, 10)); // squared: the middle is ~790 ms, not 1525
	}

	@Test
	void infinileapWaitsAtLeastTwoSeconds() {
		assertEquals(2000, FastLeap.cooldownFor("Infinileap", 500));
		assertEquals(3000, FastLeap.cooldownFor("Infinileap", 3000));
		assertEquals(500, FastLeap.cooldownFor("Spirit Leap", 500));
	}
}
