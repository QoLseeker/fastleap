package dev.sbkit.features;

import dev.sbkit.Feature;
import dev.sbkit.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.regex.Pattern;

import static dev.sbkit.Util.*;

/**
 * Right-click a Ragnarock Axe and, shortly before the cast finishes, open /pets and equip a chosen pet
 * (default: the Golden Dragon holding Antique Remedies). Matching is by pet name + "Held Item" lore text.
 */
public final class RagPetSwap extends Feature {
    private enum Phase { IDLE, WAITING, MENU }

    // Same patterns NoammAddons uses (Ragnarock.kt, PetUtils.kt).
    private static final Pattern CANCELLED = Pattern.compile("Ragnarock was cancelled due to (?:being hit|taking damage)!");
    private static final Pattern PETS_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?$");

    private final Setting.Int castMs = add(new Setting.Int("castMs", "Cast time (ms)", 3000, 1000, 6000, 100));
    private final Setting.Int leadMs = add(new Setting.Int("leadMs", "Open early (ms)", 700, 0, 3000, 50));
    // Cast start to next usable rag: 3 s cast + 10 s buff + 7 s cooldown. Right-clicks inside this window are ignored (0 = off).
    private final Setting.Int cycleS = add(new Setting.Int("cycleS", "Rag cycle (s)", 20, 0, 60, 1));
    private final Setting.Text pet = add(new Setting.Text("pet", "Pet name", "Golden Dragon"));
    private final Setting.Text heldItem = add(new Setting.Text("heldItem", "Held item", "Antique Remedies"));

    private Phase phase = Phase.IDLE;
    private boolean wasDown;
    private long swapAt, giveUpAt, waitUntil, lastCast = Long.MIN_VALUE / 2;
    private int seenContainer = -1;

    public RagPetSwap() { super("ragswap", "Rag Pet Swap", true); }

    @Override
    public void onWorldChange(Minecraft mc) { phase = Phase.IDLE; }

    @Override
    public void onMessage(Minecraft mc, String text) {
        if (phase == Phase.IDLE || !CANCELLED.matcher(text).matches()) return;
        debug("rag cancelled, aborting swap");
        finish(mc);
    }

    @Override
    public void onStartTick(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!pressed || !on() || phase != Phase.IDLE || mc.player == null || mc.gui.screen() != null) return;
        if (!lower(heldName(mc)).contains("ragnarock axe")) return;
        if (now() - lastCast < cycleS.get() * 1000L) { debug("ignored: rag still on cooldown"); return; }

        lastCast = now();
        swapAt = now() + Math.max(0, castMs.get() - leadMs.get());
        phase = Phase.WAITING;
        debug("rag used, opening /pets in " + (swapAt - now()) + " ms");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (phase == Phase.IDLE || mc.player == null || mc.gameMode == null) return;

        if (phase == Phase.WAITING) {
            if (now() < swapAt) return;
            mc.player.connection.sendCommand("pets");
            phase = Phase.MENU;
            giveUpAt = now() + 4000;
            waitUntil = 0;
            seenContainer = -1;
            return;
        }

        if (now() > giveUpAt) { fail("the Pets menu did not open in time"); return; }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !PETS_MENU.matcher(cs.getTitle().getString()).matches()) return;

        int id = cs.getMenu().containerId;
        if (id != seenContainer) { seenContainer = id; waitUntil = now() + 120; } // let the server fill the slots
        if (now() < waitUntil) return;

        var slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size() - 36; i++) {
            var item = slots.get(i).getItem();
            if (!item.is(Items.PLAYER_HEAD) || !matches(item)) continue;

            if (lore(item).contains("Click to despawn!")) debug("already equipped");
            else {
                mc.gameMode.handleContainerInput(id, i, 0, ContainerInput.PICKUP, mc.player);
                debug("equipped " + name(item));
            }
            finish(mc);
            return;
        }

        fail("no pet matching \"" + pet.get() + "\" with held item \"" + heldItem.get() + "\" in the Pets menu");
    }

    private boolean matches(net.minecraft.world.item.ItemStack item) {
        String want = pet.get().toLowerCase(Locale.ROOT).trim(), held = heldItem.get().toLowerCase(Locale.ROOT).trim();
        return lower(name(item)).contains(want) && (held.isEmpty() || lower(lore(item)).contains(held));
    }

    /** Report why nothing was equipped and leave the menu open so you can see it. */
    private void fail(String why) {
        phase = Phase.IDLE;
        warn(why);
    }

    /** Back to idle; closes the pets menu if it is the open screen. */
    private void finish(Minecraft mc) {
        phase = Phase.IDLE;
        if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?> cs && PETS_MENU.matcher(cs.getTitle().getString()).matches()) {
            mc.player.closeContainer();
        }
    }
}
