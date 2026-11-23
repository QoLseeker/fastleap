package dev.sbkit.features;

import dev.sbkit.Feature;
import dev.sbkit.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.sbkit.Util.*;

/**
 * Port of the standalone fastleap 1.0.0 mod: one key press uses your Spirit Leap / Infinileap and clicks the
 * class you set for the current M7 stage (Blood Rush: left click, Maxor: crystal pickup).
 */
public final class FastLeap extends Feature {
    private enum Stage {
        BLOOD_RUSH("Blood Rush"), MAXOR("Maxor"), LATER("later phases");
        final String label;
        Stage(String label) { this.label = label; }
    }

    private static final List<String> CLASSES = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");
    private static final List<String> OPTIONS = List.of("Archer", "Berserk", "Healer", "Mage", "Tank", "Off");
    private static final String BLOOD_RUSH = "BLOOD_RUSH", CRYSTAL = "CRYSTAL";
    private static final long NONE = Long.MIN_VALUE;
    private static final int WAIT_TICKS = 40;

    private static final Pattern TAB = Pattern.compile("(\\w{1,16})[^\\w(]*\\((Archer|Berserk|Healer|Mage|Tank)\\b");
    private static final Pattern PICKUP = Pattern.compile("(\\w+) picked up an Energy Crystal");
    private static final Pattern LATER = Pattern.compile("^\\[BOSS] (Storm|Goldor|Necron|Wither King):");
    private static final Pattern HEAD_NAME = Pattern.compile("(?:\\[.+?] )?(\\w{1,16})");

    private final Setting.Bool crystalLeap = add(new Setting.Bool("crystalLeap", "Crystal auto-leap", false));
    private final Setting.Int clickDelayMs = add(new Setting.Int("clickDelayMs", "Click delay (ms)", 150, 50, 3000, 50));
    private final Setting.Int cooldownMs = add(new Setting.Int("cooldownMs", "Cooldown (ms)", 500, 0, 3000, 50));
    private final Setting.Choice editing = add(new Setting.Choice("editing", "Editing for", CLASSES.get(0), CLASSES));
    /** "<class>.<stage key>" -> class to leap to, or Off. */
    private final Map<String, Setting.Choice> targets = new HashMap<>();

    private Stage stage = Stage.BLOOD_RUSH;
    private int pending;
    private boolean wasDown, hadCrystal;
    private long lastLeap = Long.MIN_VALUE / 2, opened = NONE, autoAt = NONE, lastCrystal = Long.MIN_VALUE / 2;
    private String want;

    public FastLeap() {
        super("fastleap", "Fast Leap", true);
        for (String cls : CLASSES) {
            for (String[] st : new String[][] {{BLOOD_RUSH, "Blood Rush click"}, {CRYSTAL, "Crystal pickup"}}) {
                Setting.Choice c = add(new Setting.Choice(cls + "." + st[0], st[1], CLASSES.get(0), OPTIONS));
                Stage live = st[0].equals(BLOOD_RUSH) ? Stage.BLOOD_RUSH : Stage.MAXOR;
                c.visible = () -> editing.get().equals(cls);
                c.highlight = () -> stage == live; // accent bar on the row for the current stage
                targets.put(cls + "." + st[0], c);
            }
        }
    }

    @Override
    public String status() {
        String cls = myClass(Minecraft.getInstance());
        return (cls == null ? "?" : cls) + " · " + stage.label;
    }

    @Override
    public void onWorldChange(Minecraft mc) {
        stage = Stage.BLOOD_RUSH;
        pending = 0;
        autoAt = NONE;
        hadCrystal = false;
    }

    @Override
    public void onMessage(Minecraft mc, String text) {
        Stage s = text.startsWith("[BOSS] Maxor:") ? Stage.MAXOR : LATER.matcher(text).find() ? Stage.LATER : null;
        if (s != null && s.ordinal() > stage.ordinal()) {
            stage = s;
            debug("stage is now " + s.label);
        }
        if (mc.player != null) crystalPickup(mc, text);
    }

    @Override
    public void onStartTick(Minecraft mc) {
        boolean down = mc.options.keyAttack.isDown();
        String t = down && !wasDown && stage == Stage.BLOOD_RUSH ? leapTarget(mc, BLOOD_RUSH) : null;
        if (t != null) {
            startLeap(mc, t);
            while (mc.options.keyAttack.consumeClick()) { } // swallow the click so the leap item isn't also swung
            mc.options.keyAttack.setDown(false);
        }
        wasDown = down;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        crystalItem(mc);
        if (autoAt != NONE && now() >= autoAt) {
            autoAt = NONE;
            String t = leapTarget(mc, CRYSTAL);
            if (t == null) {
                debug("auto leap dropped: holding \"" + heldName(mc) + "\", screen "
                    + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
            }
            startLeap(mc, t);
        }
        if (pending > 0) pending = tryClick(mc) ? 0 : pending - 1;
    }

    // ---- crystal triggers -------------------------------------------------------------------------------

    private void crystalPickup(Minecraft mc, String raw) {
        String line = raw.replaceAll("§.", "").replaceAll("\\p{C}", "").replaceAll("[\\p{Z}\\s]+", " ");
        Matcher m = PICKUP.matcher(line);
        if (!m.find()) {
            if (line.toLowerCase().contains("crystal")) debug("crystal line, not matched: " + line);
            return;
        }
        String who = m.group(1);
        if (!who.equalsIgnoreCase("You") && !who.equalsIgnoreCase(mc.player.getName().getString())) {
            debug("crystal leap skipped: " + who + " picked it up, not you");
            return;
        }
        crystalLeap(mc, "pickup message");
    }

    /** The crystal also shows up in hotbar slot 9; that is a second, faster signal. */
    private void crystalItem(Minecraft mc) {
        boolean has = name(mc.player.getInventory().getItem(8)).equalsIgnoreCase("energy crystal");
        if (has && !hadCrystal) crystalLeap(mc, "crystal in hotbar slot 9");
        hadCrystal = has;
    }

    private void crystalLeap(Minecraft mc, String why) {
        if (now() - lastCrystal < 1500) return;
        String skip = !crystalLeap.get() ? "Crystal auto-leap is off"
            : stage != Stage.MAXOR ? "stage is " + stage.label + ", not Maxor"
            : mc.gui.screen() != null ? "a screen is open (a leap menu included)" : null;
        if (skip != null) { debug("crystal leap skipped: " + skip); return; }

        boolean holding = isLeapItem(heldName(mc));
        int slot = holding ? -1 : leapSlot(mc);
        if ((!holding && slot < 0) || target(mc, CRYSTAL) == null) {
            debug("crystal leap skipped: no leap item in your hotbar, or the Crystal target is Off");
            return;
        }
        if (!holding) mc.player.getInventory().setSelectedSlot(slot);
        autoAt = now() + 100; // let the server see the slot change before right-clicking
        lastCrystal = now();
        debug(why + ", " + (holding ? "already holding a leap" : "switched to hotbar slot " + (slot + 1)) + ", leaping in 100 ms");
    }

    // ---- leap ---------------------------------------------------------------------------------------------

    private void startLeap(Minecraft mc, String target) {
        if (target == null) return;
        if (mc.gameMode == null || pending > 0 || now() - lastLeap < cooldownFor(heldName(mc))) {
            debug("leap not started: " + (pending > 0 ? "the last one is still in flight" : "cooldown"));
            return;
        }
        want = target;
        debug("leap started, target " + target);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        pending = WAIT_TICKS + clickDelayMs.get() / 50;
        lastLeap = now();
        opened = NONE;
    }

    /** True when done (clicked, or the menu opened and is gone again); false to keep waiting. */
    private boolean tryClick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !isLeapMenu(cs)) return opened != NONE;
        if (opened == NONE) opened = now();
        if (now() - opened < clickDelayMs.get()) return false;

        Map<String, String> classes = teammateClasses(mc);
        var slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size() - 36; i++) { // last 36 are the player's inventory
            var item = slots.get(i).getItem();
            if (!item.is(Items.PLAYER_HEAD)) continue;
            Matcher m = HEAD_NAME.matcher(name(item).replaceAll("§.", ""));
            if (m.find() && want.equalsIgnoreCase(classes.get(m.group(1)))) {
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, i, 0, ContainerInput.PICKUP, mc.player);
                debug("clicked " + want);
                return true;
            }
        }
        return false;
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private long cooldownFor(String held) {
        // Infinileap has a 2 s floor. The original jar looked for "INFINITE_SPIRIT_LEAP" in the display name, which never matched.
        return lower(held).contains("infinileap") ? Math.max(2000, cooldownMs.get()) : cooldownMs.get();
    }

    private static boolean isLeapMenu(AbstractContainerScreen<?> s) {
        String t = s.getTitle().getString();
        return t.equals("Spirit Leap") || t.equals("Teleport to Player");
    }

    private static boolean isLeapItem(String name) {
        if (name == null) return false;
        String n = lower(name);
        return n.contains("spirit leap") || n.contains("infinileap") || n.contains("infinite_spirit_leap");
    }

    private static int leapSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) if (isLeapItem(name(mc.player.getInventory().getItem(i)))) return i;
        return -1;
    }

    /** The class to leap to for this stage, or null (disabled / Off). */
    private String target(Minecraft mc, String stageKey) {
        if (!on() || mc.player == null) return null;
        String cls = myClass(mc);
        String t = cls == null ? CLASSES.get(0) : targets.get(cls + "." + stageKey).get();
        return t.equals("Off") ? null : t;
    }

    /** Like {@link #target} but also requires: no screen open and a leap in hand. */
    private String leapTarget(Minecraft mc, String stageKey) {
        if (mc.player == null || mc.gui.screen() != null || !isLeapItem(heldName(mc))) return null;
        return target(mc, stageKey);
    }

    private static String myClass(Minecraft mc) {
        return mc.player == null ? null : teammateClasses(mc).get(mc.player.getName().getString());
    }

    /** Player name -> dungeon class, read from the tab list. */
    private static Map<String, String> teammateClasses(Minecraft mc) {
        Map<String, String> out = new HashMap<>();
        if (mc.getConnection() == null) return out;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getTabListDisplayName() == null) continue;
            Matcher m = TAB.matcher(info.getTabListDisplayName().getString());
            if (m.find()) out.put(m.group(1), m.group(2));
        }
        return out;
    }
}
