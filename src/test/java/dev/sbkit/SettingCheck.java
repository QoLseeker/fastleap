package dev.sbkit;

import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Set;

/** Run: javac -cp gson.jar -d out src/main/java/dev/sbkit/Setting.java src/main/java/dev/sbkit/Dungeon.java src/test/java/dev/sbkit/SettingCheck.java && java -ea -cp out:gson.jar dev.sbkit.SettingCheck */
public final class SettingCheck {
    public static void main(String[] a) {
        var i = new Setting.Int("n", "N", 150, 50, 3000, 50);
        i.set(10_000); assert i.get() == 3000;
        i.set(-5); assert i.get() == 50;
        i.fromJson(new JsonPrimitive(700)); assert i.get() == 700;

        assert i.fromFraction(0) == 50 && i.fromFraction(1) == 3000;
        assert i.fromFraction(-1) == 50 && i.fromFraction(2) == 3000; // out of range is clamped
        assert i.fromFraction(0.5) == 800;                            // 50 + 2950 * 0.25 = 787.5, snapped to step 50

        var c = new Setting.Choice("c", "C", "Archer", List.of("Archer", "Mage", "Off"));
        c.cycle(); assert c.get().equals("Mage");
        c.cycle(); c.cycle(); assert c.get().equals("Archer");
        c.fromJson(new JsonPrimitive("Bogus")); assert c.get().equals("Archer"); // unknown option ignored

        var t = new Setting.Text("t", "T", "Golden Dragon");
        t.fromJson(t.toJson()); assert t.get().equals("Golden Dragon");

        assert Dungeon.member("[247] Thor5487 (Mage 42)")[1].equals("Mage");
        assert Dungeon.member("[247] Thor5487 (Mage 42)")[0].equals("Thor5487");
        assert Dungeon.member("Ghost") == null;
        assert Dungeon.score("Team Score: 300 (S+)") == 300 && Dungeon.score("Team Score: 0 (D)") == 0;
        assert Dungeon.score("Crypts: 5") == -1;
        String order = "Mage, healer/Tank, Mage";
        var all = Set.of("Mage", "Tank");
        assert Dungeon.pick(order, 0, all).equals(new Dungeon.Pick(0, "Mage"));
        assert Dungeon.pick(order, 1, all).equals(new Dungeon.Pick(1, "Tank")); // no Healer: falls to Tank
        assert Dungeon.pick(order, 1, Set.of("Mage")).equals(new Dungeon.Pick(2, "Mage")); // empty step is skipped
        assert Dungeon.pick(order, 3, all) == null && Dungeon.pick("", 0, all) == null;
        System.out.println("ok");
    }
}
