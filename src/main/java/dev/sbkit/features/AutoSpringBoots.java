package dev.sbkit.features;

import dev.sbkit.Dungeon;
import dev.sbkit.Feature;
import dev.sbkit.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static dev.sbkit.Util.*;

/**
 * Once per run, when you enter the boss room or the team score passes your limit, open the inventory,
 * put your Spring Boots on and close it again. Settings are per dungeon class.
 * Timing follows NoammAddons' AutoI4: a trigger starts a tick countdown, then the steps run from a queue,
 * one per step gap, while your mouse and keys are locked. (Client ticks, not Hypixel server ticks.)
 */
public final class AutoSpringBoots extends Feature {
    private static final int FEET = 8; // armor slot in the player's own menu
    private static final int WAIT_TICKS = 100; // how long to wait for another screen (chest, menu) to close

    private final Setting.Choice editing = add(new Setting.Choice("editing", "Editing for", Dungeon.CLASSES.get(0), Dungeon.CLASSES));
    private final Map<String, Setting.Bool> onFor = new HashMap<>();
    private final Map<String, Setting.Bool> onBoss = new HashMap<>();
    private final Map<String, Setting.Int> atScore = new HashMap<>();

    private final Setting.Int delay = add(new Setting.Int("delay", "Delay after trigger (ticks)", 0, 0, 40, 1));
    private final Setting.Int gap = add(new Setting.Int("gap", "Pause between steps (ticks, 0 = one tick)", 0, 0, 5, 1));

    private final Queue<Runnable> steps = new ArrayDeque<>();
    private boolean armed, done;
    private int score = -1, sinceCheck, waited, countdown, pause;

    public AutoSpringBoots() {
        super("springboots", "Auto Spring Boots", true);
        for (String cls : Dungeon.CLASSES) {
            var classOn = add(new Setting.Bool(cls + ".on", "On for this class", true));
            var boss = add(new Setting.Bool(cls + ".boss", "Swap on boss entry", true));
            var limit = add(new Setting.Int(cls + ".score", "Swap above score (0 = off)", 290, 0, 317, 1));
            classOn.visible = () -> editing.get().equals(cls);
            boss.visible = () -> editing.get().equals(cls);
            limit.visible = () -> editing.get().equals(cls);
            onFor.put(cls, classOn);
            onBoss.put(cls, boss);
            atScore.put(cls, limit);
        }
    }

    @Override
    public String status() {
        String cls = myClass(Minecraft.getInstance());
        return (cls == null ? "?" : cls) + " · " + (score < 0 ? "no score" : "score " + score);
    }

    @Override
    public void onWorldChange(Minecraft mc) { armed = done = false; score = -1; steps.clear(); }

    @Override
    public void onMessage(Minecraft mc, String text) {
        if (text.startsWith("[BOSS] ") && onBoss.get(settingsClass(mc)).get()) trigger("boss entry");
    }

    /** Runs before the game reads your keys this tick. */
    @Override
    public void onStartTick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        if (++sinceCheck >= 10) { sinceCheck = 0; score = dungeonScore(mc); }
        runSteps();
        if (!on()) return;
        int limit = atScore.get(settingsClass(mc)).get();
        if (limit > 0 && score > limit) trigger("score " + score);
        if (armed && --countdown < 0) swap(mc);
    }

    /** One queued step per tick, with {@code gap} idle ticks between steps; gap 0 runs the whole queue at once. */
    private void runSteps() {
        while (!steps.isEmpty()) {
            if (pause > 0) { pause--; return; }
            steps.poll().run();
            pause = gap.get();
        }
    }

    private void trigger(String why) {
        if (!on() || done || armed || !onFor.get(settingsClass(Minecraft.getInstance())).get()) return;
        armed = true;
        waited = 0;
        countdown = delay.get();
        debug("triggered by " + why);
    }

    private void swap(Minecraft mc) {
        if (mc.gui.screen() != null) { // a chest or menu is open: wait for it, then go
            if (++waited > WAIT_TICKS) { armed = false; warn("another screen stayed open, boots not swapped"); }
            return;
        }
        armed = false;
        done = true;

        var menu = mc.player.inventoryMenu;
        ItemStack worn = menu.slots.get(FEET).getItem();
        if (isSpringBoots(worn)) { debug("already wearing Spring Boots"); return; }

        int from = -1; // menu slot: hotbar 36..44, main inventory 9..35
        for (int i = 0; i < 36 && from < 0; i++) {
            if (isSpringBoots(mc.player.getInventory().getItem(i))) from = i < 9 ? 36 + i : i;
        }
        if (from < 0) { warn("no Spring Boots in your inventory"); return; }

        final int src = from;
        // Opening the screen releases every held key; Locked swallows clicks and keys until the last step closes it.
        steps.add(() -> mc.gui.setScreen(new Locked(mc.player)));
        steps.add(() -> click(mc, src));
        steps.add(() -> click(mc, FEET));
        if (!worn.isEmpty()) steps.add(() -> click(mc, src)); // the old boots are on the cursor: put them where the new ones were
        steps.add(() -> {
            mc.player.closeContainer();
            debug("swapped to " + name(menu.slots.get(FEET).getItem()));
        });
        pause = 0;
    }

    /** The inventory screen with player input switched off. */
    private static final class Locked extends InventoryScreen {
        Locked(Player p) { super(p); }
        @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { return true; }
        @Override public boolean keyPressed(KeyEvent e) { return true; }
    }

    private void click(Minecraft mc, int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }

    private static boolean isSpringBoots(ItemStack s) {
        return skyblockId(s).equals("SPRING_BOOTS") || lower(name(s)).contains("spring boots");
    }

    /** Settings are per class; with no class in the tab list (not in a dungeon) the first class's are used. */
    private static String settingsClass(Minecraft mc) {
        String cls = myClass(mc);
        return cls == null ? Dungeon.CLASSES.get(0) : cls;
    }
}
