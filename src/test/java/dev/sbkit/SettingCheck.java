package dev.sbkit;

import com.google.gson.JsonPrimitive;
import java.util.List;

/** Run: javac -cp gson.jar -d out src/main/java/dev/sbkit/Setting.java src/test/java/dev/sbkit/SettingCheck.java && java -ea -cp out:gson.jar dev.sbkit.SettingCheck */
public final class SettingCheck {
    public static void main(String[] a) {
        var i = new Setting.Int("n", "N", 150, 50, 3000, 50);
        i.set(10_000); assert i.get() == 3000;
        i.set(-5); assert i.get() == 50;
        i.fromJson(new JsonPrimitive(700)); assert i.get() == 700;

        var c = new Setting.Choice("c", "C", "Archer", List.of("Archer", "Mage", "Off"));
        c.cycle(); assert c.get().equals("Mage");
        c.cycle(); c.cycle(); assert c.get().equals("Archer");
        c.fromJson(new JsonPrimitive("Bogus")); assert c.get().equals("Archer"); // unknown option ignored

        var t = new Setting.Text("t", "T", "Golden Dragon");
        t.fromJson(t.toJson()); assert t.get().equals("Golden Dragon");
        System.out.println("ok");
    }
}
