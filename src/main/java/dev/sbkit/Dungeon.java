package dev.sbkit;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing of dungeon tab-list lines. No Minecraft types, so it can be tested on its own. */
public final class Dungeon {
    public static final List<String> CLASSES = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");

    private static final Pattern MEMBER = Pattern.compile("(\\w{1,16})[^\\w(]*\\((Archer|Berserk|Healer|Mage|Tank)\\b");
    private static final Pattern SCORE = Pattern.compile("Team Score: (\\d+)");

    private Dungeon() {}

    /** A tab line like "[123] Name (Mage 42)" gives {name, class}; anything else gives null. */
    public static String[] member(String line) {
        Matcher m = MEMBER.matcher(line);
        return m.find() ? new String[] {m.group(1), m.group(2)} : null;
    }

    /** A tab line like "Team Score: 300 (S+)" gives 300; anything else gives -1. */
    public static int score(String line) {
        Matcher m = SCORE.matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public record Pick(int step, String cls) {}

    /**
     * The leap order is "Mage, Healer/Tank, Mage": steps split by commas, alternatives by slashes.
     * Returns the first step at or after {@code from} that has a class in {@code present}, with the first such class
     * of that step (alternatives are in order of preference). A step nobody can fill is skipped. Null when none is left.
     */
    public static Pick pick(String order, int from, Set<String> present) {
        String[] steps = order.split(",");
        for (int i = Math.max(0, from); i < steps.length; i++) {
            for (String alt : steps[i].split("/")) {
                for (String c : CLASSES) {
                    if (c.equalsIgnoreCase(alt.trim()) && present.contains(c)) return new Pick(i, c);
                }
            }
        }
        return null;
    }
}
