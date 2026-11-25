package dev.sbkit.features;

import dev.sbkit.Dungeon;
import dev.sbkit.Feature;
import dev.sbkit.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static dev.sbkit.Util.*;

/**
 * Once per run, when you enter the boss room or the team score passes your limit, open the inventory,
 * put your Spring Boots on and close it again, all in one tick. Settings are per dungeon class.
 */
public final class AutoSpringBoots extends Feature {
    private static final int FEET = 8; // armor slot in the player's own menu
    private static final int WAIT_TICKS = 100; // how long to wait for another screen (chest, menu) to close

    private final Setting.Choice editing = add(new Setting.Choice("editing", "Editing for", Dungeon.CLASSES.get(0), Dungeon.CLASSES));
    private final Map<String, Setting.Bool> onBoss = new HashMap<>();
    private final Map<String, Setting.Int> atScore = new HashMap<>();

    private boolean armed, done;
    private int score = -1, sinceCheck, waited;

    public AutoSpringBoots() {
        super("springboots", "Auto Spring Boots", true);
        for (String cls : Dungeon.CLASSES) {
            var boss = add(new Setting.Bool(cls + ".boss", "Swap on boss entry", true));
            var limit = add(new Setting.Int(cls + ".score", "Swap above score (0 = off)", 290, 0, 317, 1));
            boss.visible = () -> editing.get().equals(cls);
            limit.visible = () -> editing.get().equals(cls);
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
    public void onWorldChange(Minecraft mc) { armed = done = false; score = -1; }

    @Override
    public void onMessage(Minecraft mc, String text) {
        if (text.startsWith("[BOSS] ") && onBoss.get(settingsClass(mc)).get()) trigger("boss entry");
    }

    /** Runs before the game reads your keys this tick. */
    @Override
    public void onStartTick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) return;
        if (++sinceCheck >= 10) { sinceCheck = 0; score = dungeonScore(mc); }
        if (!on()) return;
        int limit = atScore.get(settingsClass(mc)).get();
        if (limit > 0 && score > limit) trigger("score " + score);
        if (armed) swap(mc);
    }

    private void trigger(String why) {
        if (!on() || done || armed) return;
        armed = true;
        waited = 0;
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

        mc.gui.setScreen(new InventoryScreen(mc.player)); // opening it releases every held key, like doing it by hand
        click(mc, from);
        click(mc, FEET);
        if (!worn.isEmpty()) click(mc, from); // the old boots are on the cursor: put them where the new ones were
        mc.player.closeContainer();
        debug("swapped to " + name(menu.slots.get(FEET).getItem()));
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
