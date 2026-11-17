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
}
